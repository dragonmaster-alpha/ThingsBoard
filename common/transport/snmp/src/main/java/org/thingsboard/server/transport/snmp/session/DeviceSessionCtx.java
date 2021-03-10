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
package org.thingsboard.server.transport.snmp.session;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.snmp4j.CommunityTarget;
import org.snmp4j.Snmp;
import org.snmp4j.Target;
import org.snmp4j.mp.SnmpConstants;
import org.snmp4j.smi.GenericAddress;
import org.snmp4j.smi.OctetString;
import org.thingsboard.server.common.data.Device;
import org.thingsboard.server.common.data.DeviceProfile;
import org.thingsboard.server.common.data.DeviceTransportType;
import org.thingsboard.server.common.data.device.data.SnmpDeviceTransportConfiguration;
import org.thingsboard.server.common.data.device.profile.SnmpProfileTransportConfiguration;
import org.thingsboard.server.common.data.id.DeviceId;
import org.thingsboard.server.common.transport.SessionMsgListener;
import org.thingsboard.server.common.transport.TransportServiceCallback;
import org.thingsboard.server.common.transport.auth.SessionInfoCreator;
import org.thingsboard.server.common.transport.auth.ValidateDeviceCredentialsResponse;
import org.thingsboard.server.common.transport.session.DeviceAwareSessionContext;
import org.thingsboard.server.gen.transport.TransportProtos;
import org.thingsboard.server.gen.transport.TransportProtos.AttributeUpdateNotificationMsg;
import org.thingsboard.server.gen.transport.TransportProtos.GetAttributeResponseMsg;
import org.thingsboard.server.gen.transport.TransportProtos.SessionCloseNotificationProto;
import org.thingsboard.server.gen.transport.TransportProtos.ToDeviceRpcRequestMsg;
import org.thingsboard.server.gen.transport.TransportProtos.ToServerRpcResponseMsg;
import org.thingsboard.server.transport.snmp.SnmpTransportContext;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

@Slf4j
public class DeviceSessionCtx extends DeviceAwareSessionContext implements SessionMsgListener {
    private final AtomicInteger msgIdSeq = new AtomicInteger(0);

    @Getter
    @Setter
    private SnmpDeviceTransportConfiguration deviceTransportConfig;
    @Getter
    @Setter
    private SnmpSessionListener snmpSessionListener;
    @Getter
    @Setter
    private Target target;
    @Getter
    @Setter
    private volatile TransportProtos.SessionInfoProto sessionInfo;

    private Snmp snmp;
    private SnmpProfileTransportConfiguration snmpProfileTransportConfiguration;
    private long previousRequestExecutedAt = 0;

    public DeviceSessionCtx(SnmpTransportContext transportContext, String token, SnmpDeviceTransportConfiguration deviceTransportConfig,
                            Snmp snmp, DeviceId deviceId, DeviceProfile deviceProfile) {
        super(UUID.randomUUID());
        this.snmpSessionListener = new SnmpSessionListener(transportContext, token);
        super.setDeviceId(deviceId);
        super.setDeviceProfile(deviceProfile);
        //TODO: What should be done if snmp null?
        if (snmp != null) {
            this.snmp = snmp;
        }
        this.snmpProfileTransportConfiguration = (SnmpProfileTransportConfiguration) deviceProfile.getProfileData().getTransportConfiguration();
        initTarget(this.snmpProfileTransportConfiguration, deviceTransportConfig);
    }

    @Override
    public int nextMsgId() {
        return msgIdSeq.incrementAndGet();
    }

    @Override
    public void onGetAttributesResponse(GetAttributeResponseMsg getAttributesResponse) {
    }

    @Override
    public void onAttributeUpdate(AttributeUpdateNotificationMsg attributeUpdateNotification) {
    }

    @Override
    public void onRemoteSessionCloseCommand(SessionCloseNotificationProto sessionCloseNotification) {
    }

    @Override
    public void onToDeviceRpcRequest(ToDeviceRpcRequestMsg toDeviceRequest) {
    }

    @Override
    public void onToServerRpcResponse(ToServerRpcResponseMsg toServerResponse) {
    }

    @Override
    public void onDeviceProfileUpdate(TransportProtos.SessionInfoProto newSessionInfo, DeviceProfile deviceProfile) {
        super.onDeviceProfileUpdate(sessionInfo, deviceProfile);
        if (DeviceTransportType.SNMP.equals(deviceProfile.getTransportType())) {
            snmpProfileTransportConfiguration = (SnmpProfileTransportConfiguration) deviceProfile.getProfileData().getTransportConfiguration();
            snmpSessionListener.getSnmpTransportContext().getProfileTransportConfig().put(
                    deviceProfile.getId(),
                    snmpProfileTransportConfiguration);
            snmpSessionListener.getSnmpTransportContext().updatePduListPerProfile(deviceProfile.getId(), snmpProfileTransportConfiguration);
        } else {
            //TODO: should the context be removed from the map?
        }
    }

    @Override
    public void onDeviceUpdate(TransportProtos.SessionInfoProto sessionInfo, Device device, Optional<DeviceProfile> deviceProfileOpt) {
        super.onDeviceUpdate(sessionInfo, device, deviceProfileOpt);
        if (super.getDeviceProfile() != null && DeviceTransportType.SNMP.equals(super.getDeviceProfile().getTransportType())) {
            snmpSessionListener.getSnmpTransportContext().updateDeviceSessionCtx(device, deviceProfile, null);
            SnmpProfileTransportConfiguration profileTransportConfig = (SnmpProfileTransportConfiguration) deviceProfile.getProfileData().getTransportConfiguration();
            SnmpDeviceTransportConfiguration deviceTransportConfig = (SnmpDeviceTransportConfiguration) device.getDeviceData().getTransportConfiguration();
            initTarget(profileTransportConfig, deviceTransportConfig);
        } else {
            //TODO: should the context be removed from the map?
        }
    }

    public void createSessionInfo(Consumer<TransportProtos.SessionInfoProto> registerSession) {
        getSnmpSessionListener().getSnmpTransportContext().getTransportService().process(DeviceTransportType.SNMP,
                TransportProtos.ValidateDeviceTokenRequestMsg.newBuilder().setToken(getSnmpSessionListener().getToken()).build(),
                new TransportServiceCallback<ValidateDeviceCredentialsResponse>() {
                    @Override
                    public void onSuccess(ValidateDeviceCredentialsResponse msg) {
                        if (msg.hasDeviceInfo()) {
                            sessionInfo = SessionInfoCreator.create(msg, getSnmpSessionListener().getSnmpTransportContext(), UUID.randomUUID());
                            registerSession.accept(sessionInfo);
                            setDeviceInfo(msg.getDeviceInfo());
                        } else {
                            log.warn("[{}] Failed to process device auth", getDeviceId());
                        }
                    }

                    @Override
                    public void onError(Throwable e) {
                        log.warn("[{}] Failed to process device auth", getDeviceId(), e);
                    }
                });
    }

    public void executeSnmpRequest() {
        long timeNow = System.currentTimeMillis();
        long nextRequestExecutionTime = previousRequestExecutedAt + snmpProfileTransportConfiguration.getPoolPeriodMs();
        if (nextRequestExecutionTime < timeNow) {
            previousRequestExecutedAt = timeNow;

            snmpSessionListener.getSnmpTransportContext().getPdusPerProfile().get(deviceProfile.getId()).forEach(pdu -> {
                try {
                    log.debug("[{}] Sending SNMP message...", pdu.getRequestID());
                    snmp.send(pdu,
                            target,
                            deviceProfile.getId(),
                            snmpSessionListener);
                } catch (IOException e) {
                    log.error(e.getMessage(), e);
                }
            });
        }
    }

    private void initTarget(SnmpProfileTransportConfiguration profileTransportConfig, SnmpDeviceTransportConfiguration deviceTransportConfig) {
        this.deviceTransportConfig = deviceTransportConfig;
        CommunityTarget communityTarget = new CommunityTarget();
        communityTarget.setAddress(GenericAddress.parse(GenericAddress.TYPE_UDP + ":" + this.deviceTransportConfig.getAddress() + "/" + this.deviceTransportConfig.getPort()));
        communityTarget.setVersion(getSnmpVersion(this.deviceTransportConfig.getProtocolVersion()));
        communityTarget.setCommunity(new OctetString(this.deviceTransportConfig.getCommunity()));
        communityTarget.setTimeout(profileTransportConfig.getTimeoutMs());
        communityTarget.setRetries(profileTransportConfig.getRetries());
        this.target = communityTarget;
        log.info("SNMP target initialized: {}", this.target);
    }

    //TODO: replace with enum, wtih preliminary discussion of type version in config (string or integer)
    private int getSnmpVersion(String configSnmpVersion) {
        switch (configSnmpVersion) {
            case ("v1"):
                return SnmpConstants.version1;
            case ("v2c"):
                return SnmpConstants.version2c;
            case ("v3"):
                return SnmpConstants.version3;
            default:
                return -1;
        }
    }
}
