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
package org.thingsboard.server.service.device;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.thingsboard.server.common.data.DataConstants;
import org.thingsboard.server.common.data.Device;
import org.thingsboard.server.common.data.DeviceProfile;
import org.thingsboard.server.common.data.audit.ActionType;
import org.thingsboard.server.common.data.device.credentials.BasicMqttCredentials;
import org.thingsboard.server.common.data.device.profile.AllowCreateNewDevicesDeviceProfileProvisionConfiguration;
import org.thingsboard.server.common.data.device.profile.CheckPreProvisionedDevicesDeviceProfileProvisionConfiguration;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.id.UserId;
import org.thingsboard.server.common.data.kv.AttributeKvEntry;
import org.thingsboard.server.common.data.kv.BaseAttributeKvEntry;
import org.thingsboard.server.common.data.kv.StringDataEntry;
import org.thingsboard.server.common.data.security.DeviceCredentials;
import org.thingsboard.server.common.msg.TbMsg;
import org.thingsboard.server.common.msg.TbMsgMetaData;
import org.thingsboard.server.common.msg.queue.ServiceType;
import org.thingsboard.server.common.msg.queue.TopicPartitionInfo;
import org.thingsboard.server.dao.attributes.AttributesService;
import org.thingsboard.server.dao.audit.AuditLogService;
import org.thingsboard.server.dao.device.DeviceCredentialsService;
import org.thingsboard.server.dao.device.DeviceDao;
import org.thingsboard.server.dao.device.DeviceProfileDao;
import org.thingsboard.server.dao.device.DeviceProvisionService;
import org.thingsboard.server.dao.device.DeviceService;
import org.thingsboard.server.dao.device.provision.ProvisionRequest;
import org.thingsboard.server.dao.device.provision.ProvisionResponse;
import org.thingsboard.server.dao.device.provision.ProvisionResponseStatus;
import org.thingsboard.server.dao.util.mapping.JacksonUtil;
import org.thingsboard.server.gen.transport.TransportProtos;
import org.thingsboard.server.gen.transport.TransportProtos.ToRuleEngineMsg;
import org.thingsboard.server.queue.TbQueueCallback;
import org.thingsboard.server.queue.TbQueueProducer;
import org.thingsboard.server.queue.common.TbProtoQueueMsg;
import org.thingsboard.server.queue.discovery.PartitionService;
import org.thingsboard.server.queue.provider.TbQueueProducerProvider;
import org.thingsboard.server.queue.util.TbCoreComponent;
import org.thingsboard.server.service.state.DeviceStateService;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;


@Service
@Slf4j
@TbCoreComponent
public class DeviceProvisionServiceImpl implements DeviceProvisionService {

    protected TbQueueProducer<TbProtoQueueMsg<ToRuleEngineMsg>> ruleEngineMsgProducer;

    private static final String DEVICE_PROVISION_STATE = "provisionState";
    private static final String PROVISIONED_STATE = "provisioned";

    private final ReentrantLock deviceCreationLock = new ReentrantLock();

    @Autowired
    DeviceDao deviceDao;

    @Autowired
    DeviceProfileDao deviceProfileDao;

    @Autowired
    DeviceService deviceService;

    @Autowired
    DeviceCredentialsService deviceCredentialsService;

    @Autowired
    AttributesService attributesService;

    @Autowired
    DeviceStateService deviceStateService;

    @Autowired
    AuditLogService auditLogService;

    @Autowired
    PartitionService partitionService;

    public DeviceProvisionServiceImpl(TbQueueProducerProvider producerProvider) {
        ruleEngineMsgProducer = producerProvider.getRuleEngineMsgProducer();
    }

    @Override
    public ListenableFuture<ProvisionResponse> provisionDevice(ProvisionRequest provisionRequest) {
        String provisionRequestKey = provisionRequest.getCredentials().getProvisionDeviceKey();
        String provisionRequestSecret = provisionRequest.getCredentials().getProvisionDeviceSecret();

        if (StringUtils.isEmpty(provisionRequestKey) || StringUtils.isEmpty(provisionRequestSecret)) {
            return Futures.immediateFuture(new ProvisionResponse(null, ProvisionResponseStatus.NOT_FOUND));
        }

        if (provisionRequest.getCredentialsType() != null) {
            ListenableFuture<ProvisionResponse> error = validateCredentials(provisionRequest);
            if (error != null) {
                return error;
            }
        }

        DeviceProfile targetProfile = deviceProfileDao.findByProvisionDeviceKey(provisionRequestKey);

        if (targetProfile == null) {
            return Futures.immediateFuture(new ProvisionResponse(null, ProvisionResponseStatus.NOT_FOUND));
        }

        Device targetDevice = deviceDao.findDeviceByTenantIdAndName(targetProfile.getTenantId().getId(), provisionRequest.getDeviceName()).orElse(null);

        switch (targetProfile.getProvisionType()) {
            case ALLOW_CREATE_NEW_DEVICES:
                if (((AllowCreateNewDevicesDeviceProfileProvisionConfiguration) targetProfile.getProfileData().getProvisionConfiguration()).getProvisionDeviceSecret().equals(provisionRequestSecret)) {
                    if (targetDevice != null) {
                        log.warn("[{}] The device is present and could not be provisioned once more!", targetDevice.getName());
                        notify(targetDevice, provisionRequest, DataConstants.PROVISION_FAILURE, false);
                        return Futures.immediateFuture(new ProvisionResponse(null, ProvisionResponseStatus.FAILURE));
                    } else {
                        return createDevice(provisionRequest, targetProfile);
                    }
                }
                break;
            case CHECK_PRE_PROVISIONED_DEVICES:
                if (((CheckPreProvisionedDevicesDeviceProfileProvisionConfiguration) targetProfile.getProfileData().getProvisionConfiguration()).getProvisionDeviceSecret().equals(provisionRequestSecret)) {
                    if (targetDevice != null && targetDevice.getDeviceProfileId().equals(targetProfile.getId())) {
                        return processProvision(targetDevice, provisionRequest);
                    } else {
                        log.warn("[{}] Failed to find pre provisioned device!", provisionRequest.getDeviceName());
                        return Futures.immediateFuture(new ProvisionResponse(null, ProvisionResponseStatus.FAILURE));
                    }
                }
                break;
        }
        return Futures.immediateFuture(new ProvisionResponse(null, ProvisionResponseStatus.NOT_FOUND));
    }

    private ListenableFuture<ProvisionResponse> validateCredentials(ProvisionRequest provisionRequest) {
        switch (provisionRequest.getCredentialsType()) {
            case MQTT_BASIC:
                if (StringUtils.isEmpty(provisionRequest.getCredentialsData().getClientId()) ||
                        StringUtils.isEmpty(provisionRequest.getCredentialsData().getUsername()) ||
                        StringUtils.isEmpty(provisionRequest.getCredentialsData().getPassword())) {
                    log.error("Failed to get basic mqtt credentials from credentials data!");
                    return Futures.immediateFuture(new ProvisionResponse(null, ProvisionResponseStatus.FAILURE));
                }
                break;
            case X509_CERTIFICATE:
                if (StringUtils.isEmpty(provisionRequest.getCredentialsData().getHash())) {
                    log.error("Failed to get hash from credentials data!");
                    return Futures.immediateFuture(new ProvisionResponse(null, ProvisionResponseStatus.FAILURE));
                }
                break;
        }
        return null;
    }

    private ListenableFuture<ProvisionResponse> processProvision(Device device, ProvisionRequest provisionRequest) {
        ListenableFuture<Optional<AttributeKvEntry>> provisionStateFuture = attributesService.find(device.getTenantId(), device.getId(),
                DataConstants.SERVER_SCOPE, DEVICE_PROVISION_STATE);
        ListenableFuture<Boolean> provisionedFuture = Futures.transformAsync(provisionStateFuture, optionalAtr -> {
            if (optionalAtr.isPresent()) {
                String state = optionalAtr.get().getValueAsString();
                if (state.equals(PROVISIONED_STATE)) {
                    return Futures.immediateFuture(true);
                } else {
                    log.error("[{}][{}] Unknown provision state: {}!", device.getName(), DEVICE_PROVISION_STATE, state);
                    return Futures.immediateCancelledFuture();
                }
            }
            return Futures.transform(saveProvisionStateAttribute(device), input -> false, MoreExecutors.directExecutor());
        }, MoreExecutors.directExecutor());
        if (provisionedFuture.isCancelled()) {
            throw new RuntimeException("Unknown provision state!");
        }
        return Futures.transform(provisionedFuture, provisioned -> {
            if (provisioned) {
                notify(device, provisionRequest, DataConstants.PROVISION_FAILURE, false);
                return new ProvisionResponse(null, ProvisionResponseStatus.FAILURE);
            }
            notify(device, provisionRequest, DataConstants.PROVISION_SUCCESS, true);
            return new ProvisionResponse(deviceCredentialsService.findDeviceCredentialsByDeviceId(device.getTenantId(), device.getId()), ProvisionResponseStatus.SUCCESS);
        }, MoreExecutors.directExecutor());
    }

    private ListenableFuture<ProvisionResponse> createDevice(ProvisionRequest provisionRequest, DeviceProfile profile) {
        deviceCreationLock.lock();
        try {
            return processCreateDevice(provisionRequest, profile);
        } finally {
            deviceCreationLock.unlock();
        }
    }

    private void notify(Device device, ProvisionRequest provisionRequest, String type, boolean success) {
        pushProvisionEventToRuleEngine(provisionRequest, device, type);
        logAction(device.getTenantId(), device.getCustomerId(), device, success, provisionRequest);
    }

    private ListenableFuture<ProvisionResponse> processCreateDevice(ProvisionRequest provisionRequest, DeviceProfile profile) {
        Device device = deviceService.findDeviceByTenantIdAndName(profile.getTenantId(), provisionRequest.getDeviceName());
        if (device == null) {
            Device savedDevice = saveDevice(provisionRequest, profile);

            deviceStateService.onDeviceAdded(savedDevice);
            pushDeviceCreatedEventToRuleEngine(savedDevice);
            notify(savedDevice, provisionRequest, DataConstants.PROVISION_SUCCESS, true);

            return Futures.transform(saveProvisionStateAttribute(savedDevice), input ->
                    new ProvisionResponse(
                            getDeviceCredentials(savedDevice),
                            ProvisionResponseStatus.SUCCESS), MoreExecutors.directExecutor());
        }
        log.warn("[{}] The device is already provisioned!", device.getName());
        notify(device, provisionRequest, DataConstants.PROVISION_FAILURE, false);
        return Futures.immediateFuture(new ProvisionResponse(null, ProvisionResponseStatus.FAILURE));
    }

    private ListenableFuture<List<Void>> saveProvisionStateAttribute(Device device) {
        return attributesService.save(device.getTenantId(), device.getId(), DataConstants.SERVER_SCOPE,
                Collections.singletonList(new BaseAttributeKvEntry(new StringDataEntry(DEVICE_PROVISION_STATE, PROVISIONED_STATE),
                        System.currentTimeMillis())));
    }

    private Device saveDevice(ProvisionRequest provisionRequest, DeviceProfile profile) {
        Device device = new Device();
        device.setName(provisionRequest.getDeviceName());
        device.setType(profile.getName());
        device.setTenantId(profile.getTenantId());
        Device savedDevice = deviceService.saveDevice(device);
        if (!StringUtils.isEmpty(provisionRequest.getCredentialsData().getToken()) ||
                !StringUtils.isEmpty(provisionRequest.getCredentialsData().getHash()) ||
                !StringUtils.isEmpty(provisionRequest.getCredentialsData().getUsername()) ||
                !StringUtils.isEmpty(provisionRequest.getCredentialsData().getPassword()) ||
                !StringUtils.isEmpty(provisionRequest.getCredentialsData().getClientId())) {
            DeviceCredentials deviceCredentials = deviceCredentialsService.findDeviceCredentialsByDeviceId(savedDevice.getTenantId(), savedDevice.getId());
            deviceCredentials.setCredentialsType(provisionRequest.getCredentialsType());
            switch (provisionRequest.getCredentialsType()) {
                case ACCESS_TOKEN:
                    deviceCredentials.setDeviceId(savedDevice.getId());
                    deviceCredentials.setCredentialsId(provisionRequest.getCredentialsData().getToken());
                    break;
                case MQTT_BASIC:
                    BasicMqttCredentials mqttCredentials = new BasicMqttCredentials();
                    mqttCredentials.setClientId(provisionRequest.getCredentialsData().getClientId());
                    mqttCredentials.setUserName(provisionRequest.getCredentialsData().getUsername());
                    mqttCredentials.setPassword(provisionRequest.getCredentialsData().getPassword());
                    deviceCredentials.setCredentialsValue(JacksonUtil.toString(mqttCredentials));
                    break;
                case X509_CERTIFICATE:
                    deviceCredentials.setCredentialsValue(provisionRequest.getCredentialsData().getHash());
                    break;
            }
            deviceCredentials.setCredentialsType(provisionRequest.getCredentialsType());
            deviceCredentialsService.updateDeviceCredentials(savedDevice.getTenantId(), deviceCredentials);
        }
        return savedDevice;
    }

    private DeviceCredentials getDeviceCredentials(Device device) {
        return deviceCredentialsService.findDeviceCredentialsByDeviceId(device.getTenantId(), device.getId());
    }

    private void pushProvisionEventToRuleEngine(ProvisionRequest request, Device device, String type) {
        try {
            ObjectNode entityNode = JacksonUtil.OBJECT_MAPPER.valueToTree(request);
            TbMsg msg = TbMsg.newMsg(type, device.getId(), createTbMsgMetaData(device), JacksonUtil.OBJECT_MAPPER.writeValueAsString(entityNode));
            sendToRuleEngine(device.getTenantId(), msg, null);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            log.warn("[{}] Failed to push device action to rule engine: {}", device.getId(), type, e);
        }
    }

    private void pushDeviceCreatedEventToRuleEngine(Device device) {
        try {
            ObjectNode entityNode = JacksonUtil.OBJECT_MAPPER.valueToTree(device);
            TbMsg msg = TbMsg.newMsg(DataConstants.ENTITY_CREATED, device.getId(), createTbMsgMetaData(device), JacksonUtil.OBJECT_MAPPER.writeValueAsString(entityNode));
            sendToRuleEngine(device.getTenantId(), msg, null);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            log.warn("[{}] Failed to push device action to rule engine: {}", device.getId(), DataConstants.ENTITY_CREATED, e);
        }
    }

    protected void sendToRuleEngine(TenantId tenantId, TbMsg tbMsg, TbQueueCallback callback) {
        TopicPartitionInfo tpi = partitionService.resolve(ServiceType.TB_RULE_ENGINE, tenantId, tbMsg.getOriginator());
        TransportProtos.ToRuleEngineMsg msg = TransportProtos.ToRuleEngineMsg.newBuilder().setTbMsg(TbMsg.toByteString(tbMsg))
                .setTenantIdMSB(tenantId.getId().getMostSignificantBits())
                .setTenantIdLSB(tenantId.getId().getLeastSignificantBits()).build();
        ruleEngineMsgProducer.send(tpi, new TbProtoQueueMsg<>(tbMsg.getId(), msg), callback);
    }

    private TbMsgMetaData createTbMsgMetaData(Device device) {
        TbMsgMetaData metaData = new TbMsgMetaData();
        metaData.putValue("tenantId", device.getTenantId().toString());
        return metaData;
    }

    private void logAction(TenantId tenantId, CustomerId customerId, Device device, boolean success, ProvisionRequest provisionRequest) {
        ActionType actionType = success ? ActionType.PROVISION_SUCCESS : ActionType.PROVISION_FAILURE;
        auditLogService.logEntityAction(tenantId, customerId, new UserId(UserId.NULL_UUID), device.getName(), device.getId(), device, actionType, null, provisionRequest);
    }
}
