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
package org.thingsboard.server.service.subscription;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.thingsboard.server.common.data.alarm.Alarm;
import org.thingsboard.server.common.data.alarm.AlarmSearchStatus;
import org.thingsboard.server.common.data.id.AlarmId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.query.AlarmData;
import org.thingsboard.server.common.data.query.AlarmDataPageLink;
import org.thingsboard.server.common.data.query.AlarmDataQuery;
import org.thingsboard.server.common.data.query.EntityData;
import org.thingsboard.server.dao.alarm.AlarmService;
import org.thingsboard.server.service.telemetry.TelemetryWebSocketService;
import org.thingsboard.server.service.telemetry.TelemetryWebSocketSessionRef;
import org.thingsboard.server.service.telemetry.cmd.v2.AlarmDataUpdate;
import org.thingsboard.server.service.telemetry.sub.AlarmSubscriptionUpdate;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
public class TbAlarmDataSubCtx extends TbAbstractDataSubCtx<AlarmDataQuery> {

    private final AlarmService alarmService;
    @Getter
    @Setter
    private final LinkedHashMap<EntityId, EntityData> entitiesMap;
    @Getter
    @Setter
    private final HashMap<AlarmId, AlarmData> alarmsMap;
    @Getter
    @Setter
    private PageData<AlarmData> alarms;
    @Getter
    @Setter
    private boolean tooManyEntities;

    public TbAlarmDataSubCtx(String serviceId, TelemetryWebSocketService wsService,
                             TbLocalSubscriptionService localSubscriptionService,
                             AlarmService alarmService,
                             TelemetryWebSocketSessionRef sessionRef, int cmdId) {
        super(serviceId, wsService, localSubscriptionService, sessionRef, cmdId);
        this.alarmService = alarmService;
        this.entitiesMap = new LinkedHashMap<>();
        this.alarmsMap = new HashMap<>();
    }

    public void fetchAlarms() {
        PageData<AlarmData> alarms = alarmService.findAlarmDataByQueryForEntities(getTenantId(), getCustomerId(),
                query.getPageLink(), getOrderedEntityIds());
        alarms = setAndMergeAlarmsData(alarms);
        AlarmDataUpdate update = new AlarmDataUpdate(cmdId, alarms, null);
        wsService.sendWsMsg(getSessionId(), update);
    }

    public void setEntitiesData(PageData<EntityData> entitiesData) {
        entitiesMap.clear();
        tooManyEntities = entitiesData.hasNext();
        for (EntityData entityData : entitiesData.getData()) {
            entitiesMap.put(entityData.getEntityId(), entityData);
        }
    }

    public Collection<EntityId> getOrderedEntityIds() {
        return entitiesMap.keySet();
    }

    public PageData<AlarmData> setAndMergeAlarmsData(PageData<AlarmData> alarms) {
        this.alarms = alarms;
        for (AlarmData alarmData : alarms.getData()) {
            EntityId entityId = alarmData.getEntityId();
            if (entityId != null) {
                EntityData entityData = entitiesMap.get(entityId);
                if (entityData != null) {
                    alarmData.getLatest().putAll(entityData.getLatest());
                }
            }
        }
        alarmsMap.clear();
        alarmsMap.putAll(alarms.getData().stream().collect(Collectors.toMap(AlarmData::getId, Function.identity())));
        return this.alarms;
    }

    public void createSubscriptions() {
        clearSubscriptions();
        this.subToEntityIdMap = new HashMap<>();
        AlarmDataPageLink pageLink = query.getPageLink();
        long startTs = System.currentTimeMillis() - pageLink.getTimeWindow();
        for (EntityData entityData : entitiesMap.values()) {
            int subIdx = sessionRef.getSessionSubIdSeq().incrementAndGet();
            subToEntityIdMap.put(subIdx, entityData.getEntityId());
            log.trace("[{}][{}][{}] Creating alarms subscription for [{}] with query: {}", serviceId, cmdId, subIdx, entityData.getEntityId(), pageLink);
            TbAlarmsSubscription subscription = TbAlarmsSubscription.builder()
                    .type(TbSubscriptionType.ALARMS)
                    .serviceId(serviceId)
                    .sessionId(sessionRef.getSessionId())
                    .subscriptionId(subIdx)
                    .tenantId(sessionRef.getSecurityCtx().getTenantId())
                    .entityId(entityData.getEntityId())
                    .updateConsumer(this::sendWsMsg)
                    .ts(startTs)
                    .build();
            localSubscriptionService.addSubscription(subscription);
        }
    }

    private void sendWsMsg(String sessionId, AlarmSubscriptionUpdate subscriptionUpdate) {
        Alarm alarm = subscriptionUpdate.getAlarm();
        AlarmId alarmId = alarm.getId();
        if (subscriptionUpdate.isAlarmDeleted()) {
            Alarm deleted = alarmsMap.remove(alarmId);
            if (deleted != null) {
                fetchAlarms();
            }
        } else {
            AlarmData current = alarmsMap.get(alarmId);
            boolean onCurrentPage = current != null;
            boolean matchesFilter = filter(alarm);
            if (onCurrentPage) {
                if (matchesFilter) {
                    AlarmData updated = new AlarmData(alarm, current.getOriginatorName(), current.getEntityId());
                    alarmsMap.put(alarmId, updated);
                    wsService.sendWsMsg(sessionId, new AlarmDataUpdate(cmdId, null, Collections.singletonList(updated)));
                } else {
                    fetchAlarms();
                }
            } else if (matchesFilter && query.getPageLink().getPage() == 0) {
                fetchAlarms();
            }
        }
    }

    public void cleanupOldAlarms() {
        long expTime = System.currentTimeMillis() - query.getPageLink().getTimeWindow();
        boolean shouldRefresh = false;
        for (AlarmData alarmData : alarms.getData()) {
            if (alarmData.getCreatedTime() < expTime) {
                shouldRefresh = true;
                break;
            }
        }
        if (shouldRefresh) {
            fetchAlarms();
        }
    }

    private boolean filter(Alarm alarm) {
        AlarmDataPageLink filter = query.getPageLink();
        long startTs = System.currentTimeMillis() - filter.getTimeWindow();
        if (alarm.getCreatedTime() < startTs) {
            //Skip update that does not match time window.
            return false;
        }
        if (filter.getTypeList() != null && !filter.getTypeList().isEmpty() && !filter.getTypeList().contains(alarm.getType())) {
            return false;
        }
        if (filter.getSeverityList() != null && !filter.getSeverityList().isEmpty()) {
            if (!filter.getSeverityList().contains(alarm.getSeverity())) {
                return false;
            }
        }
        if (filter.getStatusList() != null && !filter.getStatusList().isEmpty()) {
            boolean matches = false;
            for (AlarmSearchStatus status : filter.getStatusList()) {
                if (status.getStatuses().contains(alarm.getStatus())) {
                    matches = true;
                    break;
                }
            }
            if (!matches) {
                return false;
            }
        }
        return true;
    }
}
