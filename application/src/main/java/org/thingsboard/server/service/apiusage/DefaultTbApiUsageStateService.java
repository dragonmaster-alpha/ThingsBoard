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
package org.thingsboard.server.service.apiusage;

import com.google.common.util.concurrent.FutureCallback;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;
import org.thingsboard.server.common.data.ApiUsageRecordKey;
import org.thingsboard.server.common.data.ApiUsageState;
import org.thingsboard.server.common.data.Tenant;
import org.thingsboard.server.common.data.TenantProfile;
import org.thingsboard.server.common.data.id.ApiUsageStateId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.id.TenantProfileId;
import org.thingsboard.server.common.data.kv.BasicTsKvEntry;
import org.thingsboard.server.common.data.kv.BooleanDataEntry;
import org.thingsboard.server.common.data.kv.LongDataEntry;
import org.thingsboard.server.common.data.kv.TsKvEntry;
import org.thingsboard.server.common.data.page.PageDataIterable;
import org.thingsboard.server.common.data.tenant.profile.TenantProfileConfiguration;
import org.thingsboard.server.common.data.tenant.profile.TenantProfileData;
import org.thingsboard.server.common.msg.queue.ServiceType;
import org.thingsboard.server.common.msg.queue.TbCallback;
import org.thingsboard.server.common.msg.tools.SchedulerUtils;
import org.thingsboard.server.dao.tenant.TenantService;
import org.thingsboard.server.dao.timeseries.TimeseriesService;
import org.thingsboard.server.dao.usagerecord.ApiUsageStateService;
import org.thingsboard.server.gen.transport.TransportProtos.ToUsageStatsServiceMsg;
import org.thingsboard.server.gen.transport.TransportProtos.UsageStatsKVProto;
import org.thingsboard.server.queue.common.TbProtoQueueMsg;
import org.thingsboard.server.queue.discovery.PartitionChangeEvent;
import org.thingsboard.server.queue.discovery.PartitionService;
import org.thingsboard.server.queue.scheduler.SchedulerComponent;
import org.thingsboard.server.queue.util.TbCoreComponent;
import org.thingsboard.server.service.profile.TbTenantProfileCache;
import org.thingsboard.server.service.queue.TbClusterService;
import org.thingsboard.server.service.telemetry.TelemetrySubscriptionService;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
@TbCoreComponent
@Service
public class DefaultTbApiUsageStateService implements TbApiUsageStateService {

    public static final String HOURLY = "Hourly";
    public static final FutureCallback<Void> VOID_CALLBACK = new FutureCallback<Void>() {
        @Override
        public void onSuccess(@Nullable Void result) {}

        @Override
        public void onFailure(Throwable t) {}
    };
    private final TbClusterService clusterService;
    private final PartitionService partitionService;
    private final TenantService tenantService;
    private final ApiUsageStateService apiUsageStateService;
    private final TimeseriesService tsService;
    private final TelemetrySubscriptionService tsWsService;
    private final SchedulerComponent scheduler;
    private final TbTenantProfileCache tenantProfileCache;

    // Tenants that should be processed on this server
    private final Map<TenantId, TenantApiUsageState> myTenantStates = new ConcurrentHashMap<>();
    // Tenants that should be processed on other servers
    private final Map<TenantId, ApiUsageState> otherTenantStates = new ConcurrentHashMap<>();

    @Value("${usage.stats.report.enabled:true}")
    private boolean enabled;

    @Value("${usage.stats.check.cycle:60000}")
    private long nextCycleCheckInterval;

    private final Lock updateLock = new ReentrantLock();

    public DefaultTbApiUsageStateService(TbClusterService clusterService,
                                         PartitionService partitionService,
                                         TenantService tenantService, ApiUsageStateService apiUsageStateService,
                                         TimeseriesService tsService, TelemetrySubscriptionService tsWsService,
                                         SchedulerComponent scheduler,
                                         TbTenantProfileCache tenantProfileCache) {
        this.clusterService = clusterService;
        this.partitionService = partitionService;
        this.tenantService = tenantService;
        this.apiUsageStateService = apiUsageStateService;
        this.tsService = tsService;
        this.tsWsService = tsWsService;
        this.scheduler = scheduler;
        this.tenantProfileCache = tenantProfileCache;
    }

    @PostConstruct
    public void init() {
        if (enabled) {
            log.info("Starting api usage service.");
            initStatesFromDataBase();
            scheduler.scheduleAtFixedRate(this::checkStartOfNextCycle, nextCycleCheckInterval, nextCycleCheckInterval, TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public void process(TbProtoQueueMsg<ToUsageStatsServiceMsg> msg, TbCallback callback) {
        ToUsageStatsServiceMsg statsMsg = msg.getValue();
        TenantId tenantId = new TenantId(new UUID(statsMsg.getTenantIdMSB(), statsMsg.getTenantIdLSB()));
        TenantApiUsageState tenantState;
        List<TsKvEntry> updatedEntries;
        Map<ApiFeature, Boolean> result = new HashMap<>();
        updateLock.lock();
        try {
            tenantState = getOrFetchState(tenantId);
            long ts = tenantState.getCurrentCycleTs();
            long hourTs = tenantState.getCurrentHourTs();
            long newHourTs = SchedulerUtils.getStartOfCurrentHour();
            if (newHourTs != hourTs) {
                tenantState.setHour(newHourTs);
            }
            updatedEntries = new ArrayList<>(ApiUsageRecordKey.values().length);
            for (UsageStatsKVProto kvProto : statsMsg.getValuesList()) {
                ApiUsageRecordKey recordKey = ApiUsageRecordKey.valueOf(kvProto.getKey());
                long newValue = tenantState.add(recordKey, kvProto.getValue());
                updatedEntries.add(new BasicTsKvEntry(ts, new LongDataEntry(recordKey.getApiCountKey(), newValue)));
                long newHourlyValue = tenantState.addToHourly(recordKey, kvProto.getValue());
                updatedEntries.add(new BasicTsKvEntry(hourTs, new LongDataEntry(recordKey.getApiCountKey() + HOURLY, newHourlyValue)));
                Pair<ApiFeature, Boolean> update = tenantState.checkStateUpdatedDueToThreshold(recordKey);
                if (update != null) {
                    result.put(update.getFirst(), update.getSecond());
                }
            }
        } finally {
            updateLock.unlock();
        }
        tsWsService.saveAndNotify(tenantId, tenantState.getApiUsageState().getId(), updatedEntries, 0L, VOID_CALLBACK);
        if (!result.isEmpty()) {
            persistAndNotify(tenantState, result);
        }
        callback.onSuccess();
    }

    @Override
    public void onApplicationEvent(PartitionChangeEvent partitionChangeEvent) {
        if (partitionChangeEvent.getServiceType().equals(ServiceType.TB_CORE)) {
            myTenantStates.entrySet().removeIf(entry -> !partitionService.resolve(ServiceType.TB_CORE, entry.getKey(), entry.getKey()).isMyPartition());
            otherTenantStates.entrySet().removeIf(entry -> partitionService.resolve(ServiceType.TB_CORE, entry.getKey(), entry.getKey()).isMyPartition());
            initStatesFromDataBase();
        }
    }

    @Override
    public ApiUsageState getApiUsageState(TenantId tenantId) {
        TenantApiUsageState tenantState = myTenantStates.get(tenantId);
        if (tenantState != null) {
            return tenantState.getApiUsageState();
        } else {
            ApiUsageState state = otherTenantStates.get(tenantId);
            if (state != null) {
                return state;
            } else {
                if (partitionService.resolve(ServiceType.TB_CORE, tenantId, tenantId).isMyPartition()) {
                    return getOrFetchState(tenantId).getApiUsageState();
                } else {
                    updateLock.lock();
                    try {
                        state = otherTenantStates.get(tenantId);
                        if (state == null) {
                            state = apiUsageStateService.findTenantApiUsageState(tenantId);
                            otherTenantStates.put(tenantId, state);
                        }
                    } finally {
                        updateLock.unlock();
                    }
                    return state;
                }
            }
        }
    }

    @Override
    public void onApiUsageStateUpdate(TenantId tenantId) {
        otherTenantStates.remove(tenantId);
    }

    @Override
    public void onTenantProfileUpdate(TenantProfileId tenantProfileId) {
        TenantProfile tenantProfile = tenantProfileCache.get(tenantProfileId);
        updateLock.lock();
        try {
            myTenantStates.values().forEach(state -> {
                if (tenantProfile.getId().equals(state.getTenantProfileId())) {
                    updateTenantState(state, tenantProfile);
                }
            });
        } finally {
            updateLock.unlock();
        }
    }

    @Override
    public void onTenantUpdate(TenantId tenantId) {
        TenantProfile tenantProfile = tenantProfileCache.get(tenantId);
        updateLock.lock();
        try {
            TenantApiUsageState state = myTenantStates.get(tenantId);
            if (state != null && !state.getTenantProfileId().equals(tenantProfile.getId())) {
                updateTenantState(state, tenantProfile);
            }
        } finally {
            updateLock.unlock();
        }
    }

    private void updateTenantState(TenantApiUsageState state, TenantProfile tenantProfile) {
        TenantProfileData oldProfileData = state.getTenantProfileData();
        state.setTenantProfileData(tenantProfile.getProfileData());
        Map<ApiFeature, Boolean> result = state.checkStateUpdatedDueToThresholds();
        if (!result.isEmpty()) {
            persistAndNotify(state, result);
        }
        updateProfileThresholds(state.getTenantId(), state.getApiUsageState().getId(),
                oldProfileData.getConfiguration(), tenantProfile.getProfileData().getConfiguration());
    }

    private void updateProfileThresholds(TenantId tenantId, ApiUsageStateId id,
                                         TenantProfileConfiguration oldData, TenantProfileConfiguration newData) {
        long ts = System.currentTimeMillis();
        List<TsKvEntry> profileThresholds = new ArrayList<>();
        for (ApiUsageRecordKey key : ApiUsageRecordKey.values()) {
            if (oldData == null || oldData.getProfileThreshold(key) != newData.getProfileThreshold(key)) {
                profileThresholds.add(new BasicTsKvEntry(ts, new LongDataEntry(key.getApiLimitKey(), newData.getProfileThreshold(key))));
            }
        }
        if (!profileThresholds.isEmpty()) {
            tsWsService.saveAndNotify(tenantId, id, profileThresholds, 0L, VOID_CALLBACK);
        }
    }

    private void persistAndNotify(TenantApiUsageState state, Map<ApiFeature, Boolean> result) {
        apiUsageStateService.update(state.getApiUsageState());
        clusterService.onApiStateChange(state.getApiUsageState(), null);
        long ts = System.currentTimeMillis();
        List<TsKvEntry> stateTelemetry = new ArrayList<>();
        result.forEach(((apiFeature, aState) -> stateTelemetry.add(new BasicTsKvEntry(ts, new BooleanDataEntry(apiFeature.getApiStateKey(), aState)))));
        tsWsService.saveAndNotify(state.getTenantId(), state.getApiUsageState().getId(), stateTelemetry, 0L, VOID_CALLBACK);
    }

    private void checkStartOfNextCycle() {
        updateLock.lock();
        try {
            long now = System.currentTimeMillis();
            myTenantStates.values().forEach(state -> {
                if ((state.getNextCycleTs() > now) && (state.getNextCycleTs() - now < TimeUnit.HOURS.toMillis(1))) {
                    state.setCycles(state.getNextCycleTs(), SchedulerUtils.getStartOfNextNextMonth());
                }
            });
        } finally {
            updateLock.unlock();
        }
    }

    private TenantApiUsageState getOrFetchState(TenantId tenantId) {
        TenantApiUsageState tenantState = myTenantStates.get(tenantId);
        if (tenantState == null) {
            ApiUsageState dbStateEntity = apiUsageStateService.findTenantApiUsageState(tenantId);
            if (dbStateEntity == null) {
                try {
                    dbStateEntity = apiUsageStateService.createDefaultApiUsageState(tenantId);
                } catch (Exception e) {
                    dbStateEntity = apiUsageStateService.findTenantApiUsageState(tenantId);
                }
            }
            TenantProfile tenantProfile = tenantProfileCache.get(tenantId);
            tenantState = new TenantApiUsageState(tenantProfile, dbStateEntity);
            try {
                List<TsKvEntry> dbValues = tsService.findAllLatest(tenantId, dbStateEntity.getId()).get();
                for (ApiUsageRecordKey key : ApiUsageRecordKey.values()) {
                    boolean cycleEntryFound = false;
                    boolean hourlyEntryFound = false;
                    for (TsKvEntry tsKvEntry : dbValues) {
                        if (tsKvEntry.getKey().equals(key.getApiCountKey())) {
                            cycleEntryFound = true;
                            tenantState.put(key, tsKvEntry.getTs() == tenantState.getCurrentCycleTs() ? tsKvEntry.getLongValue().get() : 0L);
                        } else if (tsKvEntry.getKey().equals(key.getApiCountKey() + HOURLY)) {
                            hourlyEntryFound = true;
                            tenantState.putHourly(key, tsKvEntry.getTs() == tenantState.getCurrentHourTs() ? tsKvEntry.getLongValue().get() : 0L);
                        }
                        if (cycleEntryFound && hourlyEntryFound) {
                            break;
                        }
                    }
                }
                myTenantStates.put(tenantId, tenantState);
            } catch (InterruptedException | ExecutionException e) {
                log.warn("[{}] Failed to fetch api usage state from db.", tenantId, e);
            }
        }
        return tenantState;
    }

    private void initStatesFromDataBase() {
        try {
            PageDataIterable<Tenant> tenantIterator = new PageDataIterable<>(tenantService::findTenants, 1024);
            for (Tenant tenant : tenantIterator) {
                if (!myTenantStates.containsKey(tenant.getId()) && partitionService.resolve(ServiceType.TB_CORE, tenant.getId(), tenant.getId()).isMyPartition()) {
                    updateLock.lock();
                    try {
                        updateTenantState(getOrFetchState(tenant.getId()), tenantProfileCache.get(tenant.getTenantProfileId()));
                    } catch (Exception e) {
                        log.warn("[{}] Failed to initialize tenant API state", tenant.getId(), e);
                    } finally {
                        updateLock.unlock();
                    }
                }
            }
            log.info("Api usage service started.");
        } catch (Exception e) {
            log.warn("Unknown failure", e);
        }
    }

}
