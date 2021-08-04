/**
 * Copyright © 2016-2021 The Thingsboard Authors
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
package org.thingsboard.server.service.edge.rpc.processor;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.springframework.stereotype.Component;
import org.thingsboard.server.common.data.EdgeUtils;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.alarm.Alarm;
import org.thingsboard.server.common.data.alarm.AlarmSeverity;
import org.thingsboard.server.common.data.alarm.AlarmStatus;
import org.thingsboard.server.common.data.edge.Edge;
import org.thingsboard.server.common.data.edge.EdgeEvent;
import org.thingsboard.server.common.data.edge.EdgeEventActionType;
import org.thingsboard.server.common.data.edge.EdgeEventType;
import org.thingsboard.server.common.data.id.AlarmId;
import org.thingsboard.server.common.data.id.EdgeId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.page.PageLink;
import org.thingsboard.server.gen.edge.v1.AlarmUpdateMsg;
import org.thingsboard.server.gen.edge.v1.DownlinkMsg;
import org.thingsboard.server.gen.edge.v1.UpdateMsgType;
import org.thingsboard.server.gen.transport.TransportProtos;
import org.thingsboard.server.queue.util.TbCoreComponent;

import java.util.UUID;

@Component
@Slf4j
@TbCoreComponent
public class AlarmEdgeProcessor extends BaseEdgeProcessor {

    public ListenableFuture<Void> processAlarmFromEdge(TenantId tenantId, AlarmUpdateMsg alarmUpdateMsg) {
        log.trace("[{}] onAlarmUpdate [{}]", tenantId, alarmUpdateMsg);
        EntityId originatorId = getAlarmOriginator(tenantId, alarmUpdateMsg.getOriginatorName(),
                EntityType.valueOf(alarmUpdateMsg.getOriginatorType()));
        if (originatorId == null) {
            log.warn("Originator not found for the alarm msg {}", alarmUpdateMsg);
            return Futures.immediateFuture(null);
        }
        try {
            Alarm existentAlarm = alarmService.findLatestByOriginatorAndType(tenantId, originatorId, alarmUpdateMsg.getType()).get();
            switch (alarmUpdateMsg.getMsgType()) {
                case ENTITY_CREATED_RPC_MESSAGE:
                case ENTITY_UPDATED_RPC_MESSAGE:
                    if (existentAlarm == null || existentAlarm.getStatus().isCleared()) {
                        existentAlarm = new Alarm();
                        existentAlarm.setTenantId(tenantId);
                        existentAlarm.setType(alarmUpdateMsg.getName());
                        existentAlarm.setOriginator(originatorId);
                        existentAlarm.setSeverity(AlarmSeverity.valueOf(alarmUpdateMsg.getSeverity()));
                        existentAlarm.setStartTs(alarmUpdateMsg.getStartTs());
                        existentAlarm.setClearTs(alarmUpdateMsg.getClearTs());
                        existentAlarm.setPropagate(alarmUpdateMsg.getPropagate());
                    }
                    existentAlarm.setStatus(AlarmStatus.valueOf(alarmUpdateMsg.getStatus()));
                    existentAlarm.setAckTs(alarmUpdateMsg.getAckTs());
                    existentAlarm.setEndTs(alarmUpdateMsg.getEndTs());
                    existentAlarm.setDetails(mapper.readTree(alarmUpdateMsg.getDetails()));
                    alarmService.createOrUpdateAlarm(existentAlarm);
                    break;
                case ALARM_ACK_RPC_MESSAGE:
                    if (existentAlarm != null) {
                        alarmService.ackAlarm(tenantId, existentAlarm.getId(), alarmUpdateMsg.getAckTs());
                    }
                    break;
                case ALARM_CLEAR_RPC_MESSAGE:
                    if (existentAlarm != null) {
                        alarmService.clearAlarm(tenantId, existentAlarm.getId(), mapper.readTree(alarmUpdateMsg.getDetails()), alarmUpdateMsg.getAckTs());
                    }
                    break;
                case ENTITY_DELETED_RPC_MESSAGE:
                    if (existentAlarm != null) {
                        alarmService.deleteAlarm(tenantId, existentAlarm.getId());
                    }
                    break;
            }
            return Futures.immediateFuture(null);
        } catch (Exception e) {
            log.error("Failed to process alarm update msg [{}]", alarmUpdateMsg, e);
            return Futures.immediateFailedFuture(new RuntimeException("Failed to process alarm update msg", e));
        }
    }

    private EntityId getAlarmOriginator(TenantId tenantId, String entityName, EntityType entityType) {
        switch (entityType) {
            case DEVICE:
                return deviceService.findDeviceByTenantIdAndName(tenantId, entityName).getId();
            case ASSET:
                return assetService.findAssetByTenantIdAndName(tenantId, entityName).getId();
            case ENTITY_VIEW:
                return entityViewService.findEntityViewByTenantIdAndName(tenantId, entityName).getId();
            default:
                return null;
        }
    }

    public DownlinkMsg processAlarmToEdge(Edge edge, EdgeEvent edgeEvent, UpdateMsgType msgType) {
        DownlinkMsg downlinkMsg = null;
        try {
            AlarmId alarmId = new AlarmId(edgeEvent.getEntityId());
            Alarm alarm = alarmService.findAlarmByIdAsync(edgeEvent.getTenantId(), alarmId).get();
            if (alarm != null) {
                downlinkMsg = DownlinkMsg.newBuilder()
                        .setDownlinkMsgId(EdgeUtils.nextPositiveInt())
                        .addAlarmUpdateMsg(alarmMsgConstructor.constructAlarmUpdatedMsg(edge.getTenantId(), msgType, alarm))
                        .build();
            }
        } catch (Exception e) {
            log.error("Can't process alarm msg [{}] [{}]", edgeEvent, msgType, e);
        }
        return downlinkMsg;
    }

    public void processAlarmNotification(TenantId tenantId, TransportProtos.EdgeNotificationMsgProto edgeNotificationMsg) {
        AlarmId alarmId = new AlarmId(new UUID(edgeNotificationMsg.getEntityIdMSB(), edgeNotificationMsg.getEntityIdLSB()));
        ListenableFuture<Alarm> alarmFuture = alarmService.findAlarmByIdAsync(tenantId, alarmId);
        Futures.addCallback(alarmFuture, new FutureCallback<Alarm>() {
            @Override
            public void onSuccess(@Nullable Alarm alarm) {
                if (alarm != null) {
                    EdgeEventType type = EdgeUtils.getEdgeEventTypeByEntityType(alarm.getOriginator().getEntityType());
                    if (type != null) {
                        PageLink pageLink = new PageLink(DEFAULT_PAGE_SIZE);
                        PageData<EdgeId> pageData;
                        do {
                            pageData = edgeService.findRelatedEdgeIdsByEntityId(tenantId, alarm.getOriginator(), pageLink);
                            if (pageData != null && pageData.getData() != null && !pageData.getData().isEmpty()) {
                                for (EdgeId edgeId : pageData.getData()) {
                                    saveEdgeEvent(tenantId,
                                            edgeId,
                                            EdgeEventType.ALARM,
                                            EdgeEventActionType.valueOf(edgeNotificationMsg.getAction()),
                                            alarmId,
                                            null);
                                }
                                if (pageData.hasNext()) {
                                    pageLink = pageLink.nextPageLink();
                                }
                            }
                        } while (pageData != null && pageData.hasNext());
                    }
                }
            }

            @Override
            public void onFailure(Throwable t) {
                log.warn("[{}] can't find alarm by id [{}] {}", tenantId.getId(), alarmId.getId(), t);
            }
        }, dbCallbackExecutorService);
    }

}
