/**
 * Copyright © 2016-2019 The Thingsboard Authors
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
package org.thingsboard.server.service.edge.rpc;

import com.datastax.driver.core.utils.UUIDs;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.protobuf.ByteString;
import io.grpc.stub.StreamObserver;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.thingsboard.server.common.data.Customer;
import org.thingsboard.server.common.data.Dashboard;
import org.thingsboard.server.common.data.DataConstants;
import org.thingsboard.server.common.data.Device;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.EntityView;
import org.thingsboard.server.common.data.Event;
import org.thingsboard.server.common.data.User;
import org.thingsboard.server.common.data.alarm.Alarm;
import org.thingsboard.server.common.data.alarm.AlarmSeverity;
import org.thingsboard.server.common.data.alarm.AlarmStatus;
import org.thingsboard.server.common.data.asset.Asset;
import org.thingsboard.server.common.data.audit.ActionType;
import org.thingsboard.server.common.data.edge.Edge;
import org.thingsboard.server.common.data.edge.EdgeQueueEntry;
import org.thingsboard.server.common.data.id.AssetId;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.DeviceId;
import org.thingsboard.server.common.data.id.EdgeId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.EntityIdFactory;
import org.thingsboard.server.common.data.id.EntityViewId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.kv.AttributeKvEntry;
import org.thingsboard.server.common.data.kv.BaseAttributeKvEntry;
import org.thingsboard.server.common.data.kv.DataType;
import org.thingsboard.server.common.data.kv.LongDataEntry;
import org.thingsboard.server.common.data.page.TimePageData;
import org.thingsboard.server.common.data.page.TimePageLink;
import org.thingsboard.server.common.data.relation.EntityRelation;
import org.thingsboard.server.common.data.relation.RelationTypeGroup;
import org.thingsboard.server.common.data.rule.NodeConnectionInfo;
import org.thingsboard.server.common.data.rule.RuleChain;
import org.thingsboard.server.common.data.rule.RuleChainConnectionInfo;
import org.thingsboard.server.common.data.rule.RuleChainMetaData;
import org.thingsboard.server.common.data.rule.RuleNode;
import org.thingsboard.server.common.msg.TbMsg;
import org.thingsboard.server.common.msg.TbMsgDataType;
import org.thingsboard.server.common.msg.TbMsgMetaData;
import org.thingsboard.server.common.msg.cluster.SendToClusterMsg;
import org.thingsboard.server.common.msg.session.SessionMsgType;
import org.thingsboard.server.common.msg.system.ServiceToRuleEngineMsg;
import org.thingsboard.server.dao.util.mapping.JacksonUtil;
import org.thingsboard.server.gen.edge.AlarmUpdateMsg;
import org.thingsboard.server.gen.edge.AssetUpdateMsg;
import org.thingsboard.server.gen.edge.ConnectRequestMsg;
import org.thingsboard.server.gen.edge.ConnectResponseCode;
import org.thingsboard.server.gen.edge.ConnectResponseMsg;
import org.thingsboard.server.gen.edge.CustomerUpdateMsg;
import org.thingsboard.server.gen.edge.DashboardUpdateMsg;
import org.thingsboard.server.gen.edge.DeviceUpdateMsg;
import org.thingsboard.server.gen.edge.DownlinkMsg;
import org.thingsboard.server.gen.edge.EdgeConfiguration;
import org.thingsboard.server.gen.edge.EntityDataProto;
import org.thingsboard.server.gen.edge.EntityUpdateMsg;
import org.thingsboard.server.gen.edge.EntityViewUpdateMsg;
import org.thingsboard.server.gen.edge.NodeConnectionInfoProto;
import org.thingsboard.server.gen.edge.RequestMsg;
import org.thingsboard.server.gen.edge.RequestMsgType;
import org.thingsboard.server.gen.edge.ResponseMsg;
import org.thingsboard.server.gen.edge.RuleChainConnectionInfoProto;
import org.thingsboard.server.gen.edge.RuleChainMetadataUpdateMsg;
import org.thingsboard.server.gen.edge.RuleChainUpdateMsg;
import org.thingsboard.server.gen.edge.RuleNodeProto;
import org.thingsboard.server.gen.edge.UpdateMsgType;
import org.thingsboard.server.gen.edge.UplinkMsg;
import org.thingsboard.server.gen.edge.UplinkResponseMsg;
import org.thingsboard.server.gen.edge.UserUpdateMsg;
import org.thingsboard.server.service.edge.EdgeContextComponent;

import javax.swing.text.html.parser.Entity;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static org.thingsboard.server.gen.edge.UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE;
import static org.thingsboard.server.gen.edge.UpdateMsgType.ENTITY_UPDATED_RPC_MESSAGE;

@Slf4j
@Data
public final class EdgeGrpcSession implements Cloneable {

    private static final ReentrantLock entityCreationLock = new ReentrantLock();

    private final UUID sessionId;
    private final BiConsumer<EdgeId, EdgeGrpcSession> sessionOpenListener;
    private final Consumer<EdgeId> sessionCloseListener;
    private final ObjectMapper objectMapper;

    private EdgeContextComponent ctx;
    private Edge edge;
    private StreamObserver<RequestMsg> inputStream;
    private StreamObserver<ResponseMsg> outputStream;
    private boolean connected;

    EdgeGrpcSession(EdgeContextComponent ctx, StreamObserver<ResponseMsg> outputStream, BiConsumer<EdgeId, EdgeGrpcSession> sessionOpenListener,
                    Consumer<EdgeId> sessionCloseListener, ObjectMapper objectMapper) {
        this.sessionId = UUID.randomUUID();
        this.ctx = ctx;
        this.outputStream = outputStream;
        this.sessionOpenListener = sessionOpenListener;
        this.sessionCloseListener = sessionCloseListener;
        this.objectMapper = objectMapper;
        initInputStream();
    }

    private void initInputStream() {
        this.inputStream = new StreamObserver<RequestMsg>() {
            @Override
            public void onNext(RequestMsg requestMsg) {
                if (!connected && requestMsg.getMsgType().equals(RequestMsgType.CONNECT_RPC_MESSAGE)) {
                    ConnectResponseMsg responseMsg = processConnect(requestMsg.getConnectRequestMsg());
                    outputStream.onNext(ResponseMsg.newBuilder()
                            .setConnectResponseMsg(responseMsg)
                            .build());
                    if (ConnectResponseCode.ACCEPTED != responseMsg.getResponseCode()) {
                        outputStream.onError(new RuntimeException(responseMsg.getErrorMsg()));
                    }
                }
                if (connected) {
                    if (requestMsg.getMsgType().equals(RequestMsgType.UPLINK_RPC_MESSAGE) && requestMsg.hasUplinkMsg()) {
                        outputStream.onNext(ResponseMsg.newBuilder()
                                .setUplinkResponseMsg(processUplinkMsg(requestMsg.getUplinkMsg()))
                                .build());
                    }
                }
            }

            @Override
            public void onError(Throwable t) {
                log.error("Failed to deliver message from client!", t);
            }

            @Override
            public void onCompleted() {
                sessionCloseListener.accept(edge.getId());
                outputStream.onCompleted();
            }
        };
    }


    void processHandleMessages() throws ExecutionException, InterruptedException {
        Long queueStartTs = getQueueStartTs().get();
        // TODO: this 100 value must be changed properly
        TimePageLink pageLink = new TimePageLink(30, queueStartTs + 1000);
        TimePageData<Event> pageData;
        UUID ifOffset = null;
        do {
            pageData =  ctx.getEdgeService().findQueueEvents(edge.getTenantId(), edge.getId(), pageLink);
            if (!pageData.getData().isEmpty()) {
                log.trace("[{}] [{}] event(s) are going to be processed.", this.sessionId, pageData.getData().size());
                for (Event event : pageData.getData()) {
                    log.trace("[{}] Processing event [{}]", this.sessionId, event);
                    EdgeQueueEntry entry;
                    try {
                        entry = objectMapper.treeToValue(event.getBody(), EdgeQueueEntry.class);

                        UpdateMsgType msgType = getResponseMsgType(entry.getType());
                        switch (msgType) {
                            case ENTITY_DELETED_RPC_MESSAGE:
                            case ENTITY_UPDATED_RPC_MESSAGE:
                            case ENTITY_CREATED_RPC_MESSAGE:
                            case ALARM_ACK_RPC_MESSAGE:
                            case ALARM_CLEAR_RPC_MESSAGE:
                                processEntityCRUDMessage(entry, msgType);
                                break;
                            case RULE_CHAIN_CUSTOM_MESSAGE:
                                processCustomDownlinkMessage(entry);
                                break;
                        }
                        if (ENTITY_CREATED_RPC_MESSAGE.equals(msgType)) {
                            pushEntityAttributesToEdge(entry);
                        }
                    } catch (Exception e) {
                        log.error("Exception during processing records from queue", e);
                    }
                    ifOffset = event.getUuidId();
                }
            }
            if (pageData.hasNext()) {
                pageLink = pageData.getNextPageLink();
            }
        } while (pageData.hasNext());

        if (ifOffset != null) {
            Long newStartTs = UUIDs.unixTimestamp(ifOffset);
            updateQueueStartTs(newStartTs);
        }
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            log.error("Error during sleep", e);
        }
    }

    private void pushEntityAttributesToEdge(EdgeQueueEntry entry) throws IOException {
        EntityId entityId = null;
        String entityName = null;
        switch (entry.getEntityType()) {
            case EDGE:
                entityId = objectMapper.readValue(entry.getData(), Edge.class).getId();
                break;
            case DEVICE:
                entityId = objectMapper.readValue(entry.getData(), Device.class).getId();
                break;
            case ASSET:
                entityId = objectMapper.readValue(entry.getData(), Asset.class).getId();
                break;
            case ENTITY_VIEW:
                entityId = objectMapper.readValue(entry.getData(), EntityView.class).getId();
                break;
            case DASHBOARD:
                entityId = objectMapper.readValue(entry.getData(), Dashboard.class).getId();
                break;
        }
        if (entityId != null) {
            final EntityId finalEntityId = entityId;
            ListenableFuture<List<AttributeKvEntry>> ssAttrFuture = ctx.getAttributesService().findAll(edge.getTenantId(), entityId, DataConstants.SERVER_SCOPE);
            Futures.transform(ssAttrFuture, ssAttributes -> {
                if (ssAttributes != null && !ssAttributes.isEmpty()) {
                    try {
                        TbMsgMetaData metaData = new TbMsgMetaData();
                        ObjectNode entityNode = objectMapper.createObjectNode();
                        metaData.putValue("scope", DataConstants.SERVER_SCOPE);
                        for (AttributeKvEntry attr : ssAttributes) {
                            if (attr.getDataType() == DataType.BOOLEAN) {
                                entityNode.put(attr.getKey(), attr.getBooleanValue().get());
                            } else if (attr.getDataType() == DataType.DOUBLE) {
                                entityNode.put(attr.getKey(), attr.getDoubleValue().get());
                            } else if (attr.getDataType() == DataType.LONG) {
                                entityNode.put(attr.getKey(), attr.getLongValue().get());
                            } else {
                                entityNode.put(attr.getKey(), attr.getValueAsString());
                            }
                        }
                        TbMsg tbMsg = new TbMsg(UUIDs.timeBased(), DataConstants.ATTRIBUTES_UPDATED, finalEntityId, metaData, TbMsgDataType.JSON
                                , objectMapper.writeValueAsString(entityNode)
                                , null, null, 0L);
                        log.debug("Sending donwlink entity data msg, entityName [{}], tbMsg [{}]", entityName, tbMsg);
                        outputStream.onNext(ResponseMsg.newBuilder()
                                .setDownlinkMsg(constructDownlinkEntityDataMsg(entityName, tbMsg))
                                .build());
                    } catch (Exception e) {
                        log.error("[{}] Failed to send attribute updates to the edge", edge.getName(), e);
                    }
                }
                return null;
            });
            ListenableFuture<List<AttributeKvEntry>> shAttrFuture = ctx.getAttributesService().findAll(edge.getTenantId(), entityId, DataConstants.SHARED_SCOPE);
            ListenableFuture<List<AttributeKvEntry>> clAttrFuture = ctx.getAttributesService().findAll(edge.getTenantId(), entityId, DataConstants.CLIENT_SCOPE);
        }
    }

    private void processCustomDownlinkMessage(EdgeQueueEntry entry) throws IOException {
        log.trace("Executing processCustomDownlinkMessage, entry [{}]", entry);
        TbMsg tbMsg = objectMapper.readValue(entry.getData(), TbMsg.class);
        String entityName = null;
        switch (entry.getEntityType()) {
            case DEVICE:
                Device device = ctx.getDeviceService().findDeviceById(edge.getTenantId(), new DeviceId(tbMsg.getOriginator().getId()));
                entityName = device.getName();
                break;
            case ASSET:
                Asset asset = ctx.getAssetService().findAssetById(edge.getTenantId(), new AssetId(tbMsg.getOriginator().getId()));
                entityName = asset.getName();
                break;
            case ENTITY_VIEW:
                EntityView entityView = ctx.getEntityViewService().findEntityViewById(edge.getTenantId(), new EntityViewId(tbMsg.getOriginator().getId()));
                entityName = entityView.getName();
                break;

        }
        if (entityName != null) {
            log.debug("Sending donwlink entity data msg, entityName [{}], tbMsg [{}]", entityName, tbMsg);
            outputStream.onNext(ResponseMsg.newBuilder()
                    .setDownlinkMsg(constructDownlinkEntityDataMsg(entityName, tbMsg))
                    .build());
        }
    }

    private void processEntityCRUDMessage(EdgeQueueEntry entry, UpdateMsgType msgType) throws java.io.IOException {
        log.trace("Executing processEntityCRUDMessage, entry [{}], msgType [{}]", entry, msgType);
        switch (entry.getEntityType()) {
            case EDGE:
                Edge edge = objectMapper.readValue(entry.getData(), Edge.class);
                onEdgeUpdated(msgType, edge);
                break;
            case DEVICE:
                Device device = objectMapper.readValue(entry.getData(), Device.class);
                onDeviceUpdated(msgType, device);
                break;
            case ASSET:
                Asset asset = objectMapper.readValue(entry.getData(), Asset.class);
                onAssetUpdated(msgType, asset);
                break;
            case ENTITY_VIEW:
                EntityView entityView = objectMapper.readValue(entry.getData(), EntityView.class);
                onEntityViewUpdated(msgType, entityView);
                break;
            case DASHBOARD:
                Dashboard dashboard = objectMapper.readValue(entry.getData(), Dashboard.class);
                onDashboardUpdated(msgType, dashboard);
                break;
            case RULE_CHAIN:
                RuleChain ruleChain = objectMapper.readValue(entry.getData(), RuleChain.class);
                onRuleChainUpdated(msgType, ruleChain);
                break;
            case RULE_CHAIN_METADATA:
                RuleChainMetaData ruleChainMetaData = objectMapper.readValue(entry.getData(), RuleChainMetaData.class);
                onRuleChainMetadataUpdated(msgType, ruleChainMetaData);
                break;
            case ALARM:
                Alarm alarm = objectMapper.readValue(entry.getData(), Alarm.class);
                onAlarmUpdated(msgType, alarm);
                break;
        }
    }

    private void updateQueueStartTs(Long newStartTs) {
        List<AttributeKvEntry> attributes = Collections.singletonList(new BaseAttributeKvEntry(new LongDataEntry("queueStartTs", newStartTs), System.currentTimeMillis()));
        ctx.getAttributesService().save(edge.getTenantId(), edge.getId(), DataConstants.SERVER_SCOPE, attributes);
    }

    private ListenableFuture<Long> getQueueStartTs() {
        ListenableFuture<Optional<AttributeKvEntry>> future =
                ctx.getAttributesService().find(edge.getTenantId(), edge.getId(), DataConstants.SERVER_SCOPE, "queueStartTs");
        return Futures.transform(future, attributeKvEntryOpt -> {
                    if (attributeKvEntryOpt != null && attributeKvEntryOpt.isPresent()) {
                        AttributeKvEntry attributeKvEntry = attributeKvEntryOpt.get();
                        return attributeKvEntry.getLongValue().isPresent() ? attributeKvEntry.getLongValue().get() : 0L;
                    } else {
                        return 0L;
                    }
                } );
    }

    private void onEdgeUpdated(UpdateMsgType msgType, Edge edge) {
        // TODO: voba add configuration update to edge
        this.edge = edge;
    }

    private void onDeviceUpdated(UpdateMsgType msgType, Device device) {
        EntityUpdateMsg entityUpdateMsg = EntityUpdateMsg.newBuilder()
                .setDeviceUpdateMsg(constructDeviceUpdatedMsg(msgType, device))
                .build();
        outputStream.onNext(ResponseMsg.newBuilder()
                .setEntityUpdateMsg(entityUpdateMsg)
                .build());
    }

    private void onAssetUpdated(UpdateMsgType msgType, Asset asset) {
        EntityUpdateMsg entityUpdateMsg = EntityUpdateMsg.newBuilder()
                .setAssetUpdateMsg(constructAssetUpdatedMsg(msgType, asset))
                .build();
        outputStream.onNext(ResponseMsg.newBuilder()
                .setEntityUpdateMsg(entityUpdateMsg)
                .build());
    }

    private void onEntityViewUpdated(UpdateMsgType msgType, EntityView entityView) {
        EntityUpdateMsg entityUpdateMsg = EntityUpdateMsg.newBuilder()
                .setEntityViewUpdateMsg(constructEntityViewUpdatedMsg(msgType, entityView))
                .build();
        outputStream.onNext(ResponseMsg.newBuilder()
                .setEntityUpdateMsg(entityUpdateMsg)
                .build());
    }

    private void onRuleChainUpdated(UpdateMsgType msgType, RuleChain ruleChain) {
        EntityUpdateMsg entityUpdateMsg = EntityUpdateMsg.newBuilder()
                .setRuleChainUpdateMsg(constructRuleChainUpdatedMsg(msgType, ruleChain))
                .build();
        outputStream.onNext(ResponseMsg.newBuilder()
                .setEntityUpdateMsg(entityUpdateMsg)
                .build());
    }

    private void onRuleChainMetadataUpdated(UpdateMsgType msgType, RuleChainMetaData ruleChainMetaData) {
        RuleChainMetadataUpdateMsg ruleChainMetadataUpdateMsg = constructRuleChainMetadataUpdatedMsg(msgType, ruleChainMetaData);
        if (ruleChainMetadataUpdateMsg != null) {
            EntityUpdateMsg entityUpdateMsg = EntityUpdateMsg.newBuilder()
                    .setRuleChainMetadataUpdateMsg(ruleChainMetadataUpdateMsg)
                    .build();
            outputStream.onNext(ResponseMsg.newBuilder()
                    .setEntityUpdateMsg(entityUpdateMsg)
                    .build());
        }
    }

    private void onDashboardUpdated(UpdateMsgType msgType, Dashboard dashboard) {
        EntityUpdateMsg entityUpdateMsg = EntityUpdateMsg.newBuilder()
                .setDashboardUpdateMsg(constructDashboardUpdatedMsg(msgType, dashboard))
                .build();
        outputStream.onNext(ResponseMsg.newBuilder()
                .setEntityUpdateMsg(entityUpdateMsg)
                .build());
    }

    private void onAlarmUpdated(UpdateMsgType msgType, Alarm alarm) {
        EntityUpdateMsg entityUpdateMsg = EntityUpdateMsg.newBuilder()
                .setAlarmUpdateMsg(constructAlarmUpdatedMsg(msgType, alarm))
                .build();
        outputStream.onNext(ResponseMsg.newBuilder()
                .setEntityUpdateMsg(entityUpdateMsg)
                .build());
    }

    private AlarmUpdateMsg constructAlarmUpdatedMsg(UpdateMsgType msgType, Alarm alarm) {
        String entityName = null;
        switch (alarm.getOriginator().getEntityType()) {
            case DEVICE:
                entityName = ctx.getDeviceService().findDeviceById(edge.getTenantId(), new DeviceId(alarm.getOriginator().getId())).getName();
                break;
            case ASSET:
                entityName = ctx.getAssetService().findAssetById(edge.getTenantId(), new AssetId(alarm.getOriginator().getId())).getName();
                break;
            case ENTITY_VIEW:
                entityName = ctx.getEntityViewService().findEntityViewById(edge.getTenantId(), new EntityViewId(alarm.getOriginator().getId())).getName();
                break;
        }
        AlarmUpdateMsg.Builder builder = AlarmUpdateMsg.newBuilder()
                .setMsgType(msgType)
                .setName(alarm.getName())
                .setType(alarm.getType())
                .setOriginatorName(entityName)
                .setOriginatorType(alarm.getOriginator().getEntityType().name())
                .setSeverity(alarm.getSeverity().name())
                .setStatus(alarm.getStatus().name())
                .setStartTs(alarm.getStartTs())
                .setEndTs(alarm.getEndTs())
                .setAckTs(alarm.getAckTs())
                .setClearTs(alarm.getClearTs())
                .setDetails(JacksonUtil.toString(alarm.getDetails()))
                .setPropagate(alarm.isPropagate());
        return builder.build();
    }

    private UpdateMsgType getResponseMsgType(String msgType) {
        if (msgType.equals(SessionMsgType.POST_TELEMETRY_REQUEST.name()) ||
                msgType.equals(SessionMsgType.POST_ATTRIBUTES_REQUEST.name()) ||
                msgType.equals(DataConstants.ATTRIBUTES_UPDATED) ||
                msgType.equals(DataConstants.ATTRIBUTES_DELETED)) {
            return UpdateMsgType.RULE_CHAIN_CUSTOM_MESSAGE;
        } else {
            switch (msgType) {
                case DataConstants.ENTITY_UPDATED:
                    return UpdateMsgType.ENTITY_UPDATED_RPC_MESSAGE;
                case DataConstants.ENTITY_CREATED:
                case DataConstants.ENTITY_ASSIGNED_TO_EDGE:
                    return ENTITY_CREATED_RPC_MESSAGE;
                case DataConstants.ENTITY_DELETED:
                case DataConstants.ENTITY_UNASSIGNED_FROM_EDGE:
                    return UpdateMsgType.ENTITY_DELETED_RPC_MESSAGE;
                case DataConstants.ALARM_ACK:
                    return UpdateMsgType.ALARM_ACK_RPC_MESSAGE;
                case DataConstants.ALARM_CLEAR:
                    return UpdateMsgType.ALARM_CLEAR_RPC_MESSAGE;
                default:
                    throw new RuntimeException("Unsupported msgType [" + msgType + "]");
            }
        }
    }

    private RuleChainUpdateMsg constructRuleChainUpdatedMsg(UpdateMsgType msgType, RuleChain ruleChain) {
        RuleChainUpdateMsg.Builder builder = RuleChainUpdateMsg.newBuilder()
                .setMsgType(msgType)
                .setIdMSB(ruleChain.getId().getId().getMostSignificantBits())
                .setIdLSB(ruleChain.getId().getId().getLeastSignificantBits())
                .setName(ruleChain.getName())
                .setRoot(ruleChain.getId().equals(edge.getRootRuleChainId()))
                .setDebugMode(ruleChain.isDebugMode())
                .setConfiguration(JacksonUtil.toString(ruleChain.getConfiguration()));
        if (ruleChain.getFirstRuleNodeId() != null) {
             builder.setFirstRuleNodeIdMSB(ruleChain.getFirstRuleNodeId().getId().getMostSignificantBits())
                    .setFirstRuleNodeIdLSB(ruleChain.getFirstRuleNodeId().getId().getLeastSignificantBits());
        }
        return builder.build();
    }

    private DownlinkMsg constructDownlinkEntityDataMsg(String entityName, TbMsg tbMsg) {
        EntityDataProto entityData = EntityDataProto.newBuilder()
                .setEntityName(entityName)
                .setTbMsg(ByteString.copyFrom(TbMsg.toBytes(tbMsg))).build();

        DownlinkMsg.Builder builder = DownlinkMsg.newBuilder()
                .addAllEntityData(Collections.singletonList(entityData));

        return builder.build();
    }

    private RuleChainMetadataUpdateMsg constructRuleChainMetadataUpdatedMsg(UpdateMsgType msgType, RuleChainMetaData ruleChainMetaData) {
        try {
            RuleChainMetadataUpdateMsg.Builder builder = RuleChainMetadataUpdateMsg.newBuilder()
                    .setRuleChainIdMSB(ruleChainMetaData.getRuleChainId().getId().getMostSignificantBits())
                    .setRuleChainIdLSB(ruleChainMetaData.getRuleChainId().getId().getLeastSignificantBits())
                    .addAllNodes(constructNodes(ruleChainMetaData.getNodes()))
                    .addAllConnections(constructConnections(ruleChainMetaData.getConnections()))
                    .addAllRuleChainConnections(constructRuleChainConnections(ruleChainMetaData.getRuleChainConnections()));
            if (ruleChainMetaData.getFirstNodeIndex() != null) {
                builder.setFirstNodeIndex(ruleChainMetaData.getFirstNodeIndex());
            }
            builder.setMsgType(msgType);
            return builder.build();
        } catch (JsonProcessingException ex) {
            log.error("Can't construct RuleChainMetadataUpdateMsg", ex);
        }
        return null;
    }

    private List<RuleChainConnectionInfoProto> constructRuleChainConnections(List<RuleChainConnectionInfo> ruleChainConnections) throws JsonProcessingException {
        List<RuleChainConnectionInfoProto> result = new ArrayList<>();
        if (ruleChainConnections != null && !ruleChainConnections.isEmpty()) {
            for (RuleChainConnectionInfo ruleChainConnectionInfo : ruleChainConnections) {
                result.add(constructRuleChainConnection(ruleChainConnectionInfo));
            }
        }
        return result;
    }

    private RuleChainConnectionInfoProto constructRuleChainConnection(RuleChainConnectionInfo ruleChainConnectionInfo) throws JsonProcessingException {
        return RuleChainConnectionInfoProto.newBuilder()
                .setFromIndex(ruleChainConnectionInfo.getFromIndex())
                .setTargetRuleChainIdMSB(ruleChainConnectionInfo.getTargetRuleChainId().getId().getMostSignificantBits())
                .setTargetRuleChainIdLSB(ruleChainConnectionInfo.getTargetRuleChainId().getId().getLeastSignificantBits())
                .setType(ruleChainConnectionInfo.getType())
                .setAdditionalInfo(objectMapper.writeValueAsString(ruleChainConnectionInfo.getAdditionalInfo()))
                .build();
    }

    private List<NodeConnectionInfoProto> constructConnections(List<NodeConnectionInfo> connections) {
        List<NodeConnectionInfoProto> result = new ArrayList<>();
        if (connections != null && !connections.isEmpty()) {
            for (NodeConnectionInfo connection : connections) {
                result.add(constructConnection(connection));
            }
        }
        return result;
    }

    private NodeConnectionInfoProto constructConnection(NodeConnectionInfo connection) {
        return NodeConnectionInfoProto.newBuilder()
                .setFromIndex(connection.getFromIndex())
                .setToIndex(connection.getToIndex())
                .setType(connection.getType())
                .build();
    }

    private List<RuleNodeProto> constructNodes(List<RuleNode> nodes) throws JsonProcessingException {
        List<RuleNodeProto> result = new ArrayList<>();
        if (nodes != null && !nodes.isEmpty()) {
            for (RuleNode node : nodes) {
                result.add(constructNode(node));
            }
        }
        return result;
    }

    private RuleNodeProto constructNode(RuleNode node) throws JsonProcessingException {
        return RuleNodeProto.newBuilder()
                .setIdMSB(node.getId().getId().getMostSignificantBits())
                .setIdLSB(node.getId().getId().getLeastSignificantBits())
                .setType(node.getType())
                .setName(node.getName())
                .setDebugMode(node.isDebugMode())
                .setConfiguration(objectMapper.writeValueAsString(node.getConfiguration()))
                .setAdditionalInfo(objectMapper.writeValueAsString(node.getAdditionalInfo()))
                .build();
    }

    private DashboardUpdateMsg constructDashboardUpdatedMsg(UpdateMsgType msgType, Dashboard dashboard) {
        dashboard = ctx.getDashboardService().findDashboardById(edge.getTenantId(), dashboard.getId());
        DashboardUpdateMsg.Builder builder = DashboardUpdateMsg.newBuilder()
                .setMsgType(msgType)
                .setIdMSB(dashboard.getId().getId().getMostSignificantBits())
                .setIdLSB(dashboard.getId().getId().getLeastSignificantBits())
                .setTitle(dashboard.getTitle())
                .setConfiguration(JacksonUtil.toString(dashboard.getConfiguration()));
        return builder.build();
    }

    private CustomerUpdateMsg constructCustomerUpdatedMsg(UpdateMsgType msgType, Customer customer) {
        CustomerUpdateMsg.Builder builder = CustomerUpdateMsg.newBuilder()
                .setMsgType(msgType);
        return builder.build();
    }

    private UserUpdateMsg constructUserUpdatedMsg(UpdateMsgType msgType, User user) {
        UserUpdateMsg.Builder builder = UserUpdateMsg.newBuilder()
                .setMsgType(msgType);
        return builder.build();
    }

    private DeviceUpdateMsg constructDeviceUpdatedMsg(UpdateMsgType msgType, Device device) {
        DeviceUpdateMsg.Builder builder = DeviceUpdateMsg.newBuilder()
                .setMsgType(msgType)
                .setName(device.getName())
                .setType(device.getType());
        return builder.build();
    }

    private AssetUpdateMsg constructAssetUpdatedMsg(UpdateMsgType msgType, Asset asset) {
        AssetUpdateMsg.Builder builder = AssetUpdateMsg.newBuilder()
                .setMsgType(msgType)
                .setName(asset.getName())
                .setType(asset.getType());
        return builder.build();
    }

    private EntityViewUpdateMsg constructEntityViewUpdatedMsg(UpdateMsgType msgType, EntityView entityView) {
        String relatedName;
        String relatedType;
        org.thingsboard.server.gen.edge.EntityType relatedEntityType;
        if (entityView.getEntityId().getEntityType().equals(EntityType.DEVICE)) {
            Device device = ctx.getDeviceService().findDeviceById(entityView.getTenantId(), new DeviceId(entityView.getEntityId().getId()));
            relatedName = device.getName();
            relatedType = device.getType();
            relatedEntityType = org.thingsboard.server.gen.edge.EntityType.DEVICE;
        } else {
            Asset asset = ctx.getAssetService().findAssetById(entityView.getTenantId(), new AssetId(entityView.getEntityId().getId()));
            relatedName = asset.getName();
            relatedType = asset.getType();
            relatedEntityType = org.thingsboard.server.gen.edge.EntityType.ASSET;
        }
        EntityViewUpdateMsg.Builder builder = EntityViewUpdateMsg.newBuilder()
                .setMsgType(msgType)
                .setName(entityView.getName())
                .setType(entityView.getType())
                .setRelatedName(relatedName)
                .setRelatedType(relatedType)
                .setRelatedEntityType(relatedEntityType);
        return builder.build();
    }
    
    private UplinkResponseMsg processUplinkMsg(UplinkMsg uplinkMsg) {
        try {
            if (uplinkMsg.getEntityDataList() != null && !uplinkMsg.getEntityDataList().isEmpty()) {
                for (EntityDataProto entityData : uplinkMsg.getEntityDataList()) {
                    TbMsg tbMsg = null;
                    TbMsg originalTbMsg = TbMsg.fromBytes(entityData.getTbMsg().toByteArray());
                    switch (originalTbMsg.getOriginator().getEntityType()) {
                        case DEVICE:
                            String deviceName = entityData.getEntityName();
                            String deviceType = entityData.getEntityType();
                            Device device = getOrCreateDevice(deviceName, deviceType);
                            if (device != null) {
                                tbMsg = new TbMsg(UUIDs.timeBased(), originalTbMsg.getType(), device.getId(), originalTbMsg.getMetaData().copy(),
                                        originalTbMsg.getDataType(), originalTbMsg.getData(), null, null, 0L);
                            }
                            break;
                        case ASSET:
                            String assetName = entityData.getEntityName();
                            Asset asset = ctx.getAssetService().findAssetByTenantIdAndName(edge.getTenantId(), assetName);
                            if (asset != null) {
                                tbMsg = new TbMsg(UUIDs.timeBased(), originalTbMsg.getType(), asset.getId(), originalTbMsg.getMetaData().copy(),
                                        originalTbMsg.getDataType(), originalTbMsg.getData(), null, null, 0L);
                            }
                            break;
                        case ENTITY_VIEW:
                            String entityViewName = entityData.getEntityName();
                            EntityView entityView = ctx.getEntityViewService().findEntityViewByTenantIdAndName(edge.getTenantId(), entityViewName);
                            if (entityView != null) {
                                tbMsg = new TbMsg(UUIDs.timeBased(), originalTbMsg.getType(), entityView.getId(), originalTbMsg.getMetaData().copy(),
                                        originalTbMsg.getDataType(), originalTbMsg.getData(), null, null, 0L);
                            }
                            break;
                    }
                    if (tbMsg != null) {
                        ctx.getActorService().onMsg(new SendToClusterMsg(tbMsg.getOriginator(), new ServiceToRuleEngineMsg(edge.getTenantId(), tbMsg)));
                    }
                }
            }
            if (uplinkMsg.getDeviceUpdateMsgList() != null && !uplinkMsg.getDeviceUpdateMsgList().isEmpty()) {
                for (DeviceUpdateMsg deviceUpdateMsg : uplinkMsg.getDeviceUpdateMsgList()) {
                    String deviceName = deviceUpdateMsg.getName();
                    String deviceType = deviceUpdateMsg.getType();
                    switch (deviceUpdateMsg.getMsgType()) {
                        case ENTITY_CREATED_RPC_MESSAGE:
                            getOrCreateDevice(deviceName, deviceType);
                            break;
                    }
                }
            }
            if (uplinkMsg.getAlarmUpdatemsgList() != null && !uplinkMsg.getAlarmUpdatemsgList().isEmpty()) {
                for (AlarmUpdateMsg alarmUpdateMsg : uplinkMsg.getAlarmUpdatemsgList()) {
                    onAlarmUpdate(alarmUpdateMsg);
                }
            }
        } catch (Exception e) {
            return UplinkResponseMsg.newBuilder().setSuccess(false).setErrorMsg(e.getMessage()).build();
        }

        return UplinkResponseMsg.newBuilder().setSuccess(true).build();
    }

    private EntityId getAlarmOriginator(String entityName, org.thingsboard.server.common.data.EntityType entityType) {
        switch (entityType) {
            case DEVICE:
                return ctx.getDeviceService().findDeviceByTenantIdAndName(edge.getTenantId(), entityName).getId();
            case ASSET:
                return ctx.getAssetService().findAssetByTenantIdAndName(edge.getTenantId(), entityName).getId();
            case ENTITY_VIEW:
                return ctx.getEntityViewService().findEntityViewByTenantIdAndName(edge.getTenantId(), entityName).getId();
            default:
                return null;
        }
    }

    private void onAlarmUpdate(AlarmUpdateMsg alarmUpdateMsg) {
        EntityId originatorId = getAlarmOriginator(alarmUpdateMsg.getOriginatorName(), org.thingsboard.server.common.data.EntityType.valueOf(alarmUpdateMsg.getOriginatorType()));
        if (originatorId != null) {
            try {
                Alarm existentAlarm = ctx.getAlarmService().findLatestByOriginatorAndType(edge.getTenantId(), originatorId, alarmUpdateMsg.getType()).get();
                switch (alarmUpdateMsg.getMsgType()) {
                    case ENTITY_CREATED_RPC_MESSAGE:
                    case ENTITY_UPDATED_RPC_MESSAGE:
                        if (existentAlarm == null) {
                            existentAlarm = new Alarm();
                            existentAlarm.setTenantId(edge.getTenantId());
                            existentAlarm.setType(alarmUpdateMsg.getName());
                            existentAlarm.setOriginator(originatorId);
                            existentAlarm.setSeverity(AlarmSeverity.valueOf(alarmUpdateMsg.getSeverity()));
                            existentAlarm.setStatus(AlarmStatus.valueOf(alarmUpdateMsg.getStatus()));
                            existentAlarm.setStartTs(alarmUpdateMsg.getStartTs());
                            existentAlarm.setAckTs(alarmUpdateMsg.getAckTs());
                            existentAlarm.setClearTs(alarmUpdateMsg.getClearTs());
                            existentAlarm.setPropagate(alarmUpdateMsg.getPropagate());
                        }
                        existentAlarm.setEndTs(alarmUpdateMsg.getEndTs());
                        existentAlarm.setDetails(objectMapper.readTree(alarmUpdateMsg.getDetails()));
                        ctx.getAlarmService().createOrUpdateAlarm(existentAlarm);
                        break;
                    case ALARM_ACK_RPC_MESSAGE:
                        if (existentAlarm != null) {
                            ctx.getAlarmService().ackAlarm(edge.getTenantId(), existentAlarm.getId(), alarmUpdateMsg.getAckTs());
                        }
                        break;
                    case ALARM_CLEAR_RPC_MESSAGE:
                        if (existentAlarm != null) {
                            ctx.getAlarmService().clearAlarm(edge.getTenantId(), existentAlarm.getId(), objectMapper.readTree(alarmUpdateMsg.getDetails()), alarmUpdateMsg.getAckTs());
                        }
                        break;
                    case ENTITY_DELETED_RPC_MESSAGE:
                        if (existentAlarm != null) {
                            ctx.getAlarmService().deleteAlarm(edge.getTenantId(), existentAlarm.getId());
                        }
                        break;
                }
            } catch (Exception e) {
                log.error("Error during finding existent alarm", e);
            }
        }
    }

    private Device getOrCreateDevice(String deviceName, String deviceType) {
        Device device = ctx.getDeviceService().findDeviceByTenantIdAndName(edge.getTenantId(), deviceName);
        if (device == null) {
            entityCreationLock.lock();
            try {
                return processGetOrCreateDevice(deviceName, deviceType);
            } finally {
                entityCreationLock.unlock();
            }
        }
        return device;
    }

    private Device processGetOrCreateDevice(String deviceName, String deviceType) {
        Device device = ctx.getDeviceService().findDeviceByTenantIdAndName(edge.getTenantId(), deviceName);
        if (device == null) {
            device = new Device();
            device.setName(deviceName);
            device.setType(deviceType);
            device.setTenantId(edge.getTenantId());
            device.setCustomerId(edge.getCustomerId());
            device = ctx.getDeviceService().saveDevice(device);
            device = ctx.getDeviceService().assignDeviceToEdge(edge.getTenantId(), device.getId(), edge.getId());
            createRelationFromEdge(device.getId());
            ctx.getActorService().onDeviceAdded(device);
            pushDeviceCreatedEventToRuleEngine(device);
        }
        return device;
    }

    private void pushDeviceCreatedEventToRuleEngine(Device device) {
        try {
            ObjectNode entityNode = objectMapper.valueToTree(device);
            TbMsg msg = new TbMsg(UUIDs.timeBased(), DataConstants.ENTITY_CREATED, device.getId(), deviceActionTbMsgMetaData(device), objectMapper.writeValueAsString(entityNode), null, null, 0L);
            ctx.getActorService().onMsg(new SendToClusterMsg(device.getId(), new ServiceToRuleEngineMsg(edge.getTenantId(), msg)));
        } catch (JsonProcessingException | IllegalArgumentException e) {
            log.warn("[{}] Failed to push device action to rule engine: {}", device.getId(), DataConstants.ENTITY_CREATED, e);
        }
    }

    private TbMsgMetaData deviceActionTbMsgMetaData(Device device) {
        TbMsgMetaData metaData = getTbMsgMetaData();
        CustomerId customerId = device.getCustomerId();
        if (customerId != null && !customerId.isNullUid()) {
            metaData.putValue("customerId", customerId.toString());
        }
        return metaData;
    }

    private TbMsgMetaData getTbMsgMetaData() {
        TbMsgMetaData metaData = new TbMsgMetaData();
        metaData.putValue("edgeId", edge.getId().toString());
        metaData.putValue("edgeName", edge.getName());
        return metaData;
    }

    private ConnectResponseMsg processConnect(ConnectRequestMsg request) {
        Optional<Edge> optional = ctx.getEdgeService().findEdgeByRoutingKey(TenantId.SYS_TENANT_ID, request.getEdgeRoutingKey());
        if (optional.isPresent()) {
            edge = optional.get();
            try {
                if (edge.getSecret().equals(request.getEdgeSecret())) {
                    connected = true;
                    sessionOpenListener.accept(edge.getId(), this);
                    return ConnectResponseMsg.newBuilder()
                            .setResponseCode(ConnectResponseCode.ACCEPTED)
                            .setErrorMsg("")
                            .setConfiguration(constructEdgeConfigProto(edge)).build();
                }
                return ConnectResponseMsg.newBuilder()
                        .setResponseCode(ConnectResponseCode.BAD_CREDENTIALS)
                        .setErrorMsg("Failed to validate the edge!")
                        .setConfiguration(EdgeConfiguration.getDefaultInstance()).build();
            } catch (Exception e) {
                log.error("[{}] Failed to process edge connection!", request.getEdgeRoutingKey(), e);
                return ConnectResponseMsg.newBuilder()
                        .setResponseCode(ConnectResponseCode.SERVER_UNAVAILABLE)
                        .setErrorMsg("Failed to process edge connection!")
                        .setConfiguration(EdgeConfiguration.getDefaultInstance()).build();
            }
        }
        return ConnectResponseMsg.newBuilder()
                .setResponseCode(ConnectResponseCode.BAD_CREDENTIALS)
                .setErrorMsg("Failed to find the edge! Routing key: " + request.getEdgeRoutingKey())
                .setConfiguration(EdgeConfiguration.getDefaultInstance()).build();
    }

    private void createRelationFromEdge(EntityId entityId) {
        EntityRelation relation = new EntityRelation();
        relation.setFrom(edge.getId());
        relation.setTo(entityId);
        relation.setTypeGroup(RelationTypeGroup.COMMON);
        relation.setType(EntityRelation.EDGE_TYPE);
        ctx.getRelationService().saveRelation(edge.getTenantId(), relation);
    }

    private EdgeConfiguration constructEdgeConfigProto(Edge edge) throws JsonProcessingException {
        return EdgeConfiguration.newBuilder()
                .setTenantIdMSB(edge.getTenantId().getId().getMostSignificantBits())
                .setTenantIdLSB(edge.getTenantId().getId().getLeastSignificantBits())
                .setName(edge.getName())
                .setRoutingKey(edge.getRoutingKey())
                .setType(edge.getType().toString())
                .build();
    }
}
