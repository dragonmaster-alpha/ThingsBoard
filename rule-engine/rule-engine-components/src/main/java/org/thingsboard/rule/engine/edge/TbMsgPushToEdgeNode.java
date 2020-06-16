/**
 * Copyright © 2016-2020 The Thingsboard Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.thingsboard.rule.engine.edge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import lombok.extern.slf4j.Slf4j;
import org.thingsboard.rule.engine.api.EmptyNodeConfiguration;
import org.thingsboard.rule.engine.api.RuleNode;
import org.thingsboard.rule.engine.api.TbContext;
import org.thingsboard.rule.engine.api.TbNode;
import org.thingsboard.rule.engine.api.TbNodeConfiguration;
import org.thingsboard.rule.engine.api.TbNodeException;
import org.thingsboard.rule.engine.api.util.TbNodeUtils;
import org.thingsboard.server.common.data.DataConstants;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.audit.ActionType;
import org.thingsboard.server.common.data.edge.EdgeEvent;
import org.thingsboard.server.common.data.edge.EdgeEventType;
import org.thingsboard.server.common.data.id.EdgeId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.plugin.ComponentType;
import org.thingsboard.server.common.data.relation.EntityRelation;
import org.thingsboard.server.common.data.relation.RelationTypeGroup;
import org.thingsboard.server.common.msg.TbMsg;
import org.thingsboard.server.common.msg.session.SessionMsgType;

import javax.annotation.Nullable;
import java.util.List;

import static org.thingsboard.rule.engine.api.TbRelationTypes.SUCCESS;

@Slf4j
@RuleNode(
        type = ComponentType.ACTION,
        name = "push to edge",
        configClazz = EmptyNodeConfiguration.class,
        nodeDescription = "Pushes messages to edge",
        nodeDetails = "Pushes messages to edge, if Message Originator assigned to particular edge or is EDGE entity. This node is used only on Cloud instances to push messages from Cloud to Edge. Supports only DEVICE, ENTITY_VIEW, ASSET and EDGE Message Originator(s).",
        uiResources = {"static/rulenode/rulenode-core-config.js", "static/rulenode/rulenode-core-config.css"},
        configDirective = "tbNodeEmptyConfig",
        icon = "cloud_download"
)
public class TbMsgPushToEdgeNode implements TbNode {

    private EmptyNodeConfiguration config;

    private static final ObjectMapper json = new ObjectMapper();

    @Override
    public void init(TbContext ctx, TbNodeConfiguration configuration) throws TbNodeException {
        this.config = TbNodeUtils.convert(configuration, EmptyNodeConfiguration.class);
    }

    @Override
    public void onMsg(TbContext ctx, TbMsg msg) {
        if (EntityType.DEVICE.equals(msg.getOriginator().getEntityType()) ||
                EntityType.DEVICE.equals(msg.getOriginator().getEntityType()) ||
        EntityType.DEVICE.equals(msg.getOriginator().getEntityType()) ||
                EntityType.DEVICE.equals(msg.getOriginator().getEntityType())) {
            if (SessionMsgType.POST_TELEMETRY_REQUEST.name().equals(msg.getType()) ||
                    SessionMsgType.POST_ATTRIBUTES_REQUEST.name().equals(msg.getType()) ||
                    DataConstants.ATTRIBUTES_UPDATED.equals(msg.getType()) ||
                    DataConstants.ATTRIBUTES_DELETED.equals(msg.getType())) {
                ListenableFuture<EdgeId> getEdgeIdFuture = getEdgeIdByOriginatorId(ctx, ctx.getTenantId(), msg.getOriginator());
                Futures.transform(getEdgeIdFuture, edgeId -> {
                    EdgeEventType edgeEventTypeByEntityType = ctx.getEdgeEventService().getEdgeEventTypeByEntityType(msg.getOriginator().getEntityType());
                    if (edgeEventTypeByEntityType == null) {
                        log.debug("Edge event type is null. Entity Type {}", msg.getOriginator().getEntityType());
                        ctx.tellFailure(msg, new RuntimeException("Edge event type is null. Entity Type '"+ msg.getOriginator().getEntityType() + "'"));
                    }
                    ActionType actionType;
                    if (SessionMsgType.POST_TELEMETRY_REQUEST.name().equals(msg.getType())) {
                        actionType = ActionType.TIMESERIES_UPDATED;
                    } else if (SessionMsgType.POST_ATTRIBUTES_REQUEST.name().equals(msg.getType()) ||
                            DataConstants.ATTRIBUTES_UPDATED.equals(msg.getType())) {
                        actionType = ActionType.ATTRIBUTES_UPDATED;
                    } else {
                        actionType = ActionType.ATTRIBUTES_DELETED;
                    }
                    EdgeEvent edgeEvent = new EdgeEvent();
                    edgeEvent.setTenantId(ctx.getTenantId());
                    edgeEvent.setEdgeId(edgeId);
                    edgeEvent.setEdgeEventAction(actionType.name());
                    edgeEvent.setEntityId(msg.getOriginator().getId());
                    edgeEvent.setEdgeEventType(edgeEventTypeByEntityType);
                    edgeEvent.setEntityBody(json.valueToTree(msg.getData()));
                    ListenableFuture<EdgeEvent> saveFuture = ctx.getEdgeEventService().saveAsync(edgeEvent);
                    Futures.addCallback(saveFuture, new FutureCallback<EdgeEvent>() {
                        @Override
                        public void onSuccess(@Nullable EdgeEvent event) {
                            ctx.tellNext(msg, SUCCESS);
                        }

                        @Override
                        public void onFailure(Throwable th) {
                            log.error("Could not save edge event", th);
                            ctx.tellFailure(msg, th);
                        }
                    }, ctx.getDbCallbackExecutor());
                    return null;
                }, ctx.getDbCallbackExecutor());
            } else {
                log.debug("Unsupported msg type {}", msg.getType());
                ctx.tellFailure(msg, new RuntimeException("Unsupported msg type '" + msg.getType() + "'"));
            }
        } else {
            log.debug("Unsupported originator type {}", msg.getOriginator().getEntityType());
            ctx.tellFailure(msg, new RuntimeException("Unsupported originator type '" + msg.getOriginator().getEntityType() + "'"));
        }
    }

    private ListenableFuture<EdgeId> getEdgeIdByOriginatorId(TbContext ctx, TenantId tenantId, EntityId originatorId) {
        ListenableFuture<List<EntityRelation>> future = ctx.getRelationService().findByToAndTypeAsync(tenantId, originatorId, EntityRelation.CONTAINS_TYPE, RelationTypeGroup.EDGE);
        return Futures.transform(future, relations -> {
            if (relations != null && relations.size() > 0) {
                return new EdgeId(relations.get(0).getFrom().getId());
            } else {
                return null;
            }
        }, ctx.getDbCallbackExecutor());
    }

    @Override
    public void destroy() {
    }

}
