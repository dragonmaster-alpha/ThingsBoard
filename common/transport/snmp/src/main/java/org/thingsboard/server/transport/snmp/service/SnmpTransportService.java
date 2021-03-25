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
package org.thingsboard.server.transport.snmp.service;

import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.snmp4j.PDU;
import org.snmp4j.Snmp;
import org.snmp4j.TransportMapping;
import org.snmp4j.event.ResponseEvent;
import org.snmp4j.smi.Null;
import org.snmp4j.smi.VariableBinding;
import org.snmp4j.transport.DefaultTcpTransportMapping;
import org.snmp4j.transport.DefaultUdpTransportMapping;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.thingsboard.common.util.ThingsBoardThreadFactory;
import org.thingsboard.server.common.data.TbTransportService;
import org.thingsboard.server.common.data.id.DeviceProfileId;
import org.thingsboard.server.common.data.kv.DataType;
import org.thingsboard.server.common.transport.TransportService;
import org.thingsboard.server.common.transport.TransportServiceCallback;
import org.thingsboard.server.common.transport.adaptor.JsonConverter;
import org.thingsboard.server.gen.transport.TransportProtos;
import org.thingsboard.server.queue.util.AfterStartUp;
import org.thingsboard.server.queue.util.TbSnmpTransportComponent;
import org.thingsboard.server.transport.snmp.SnmpTransportContext;
import org.thingsboard.server.transport.snmp.session.DeviceSessionContext;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@TbSnmpTransportComponent
@Service
@Slf4j
public class SnmpTransportService implements TbTransportService {
    private final SnmpTransportContext snmpTransportContext;
    private final TransportService transportService;

    private Snmp snmp;
    private ScheduledExecutorService pollingExecutor;
    private ExecutorService snmpResponseProcessingExecutor;

    @Value("${transport.snmp.response_processing.parallelism_level}")
    private Integer responseProcessingParallelismLevel;
    @Value("${transport.snmp.underlying_protocol}")
    private String snmpUnderlyingProtocol;

    public SnmpTransportService(@Lazy SnmpTransportContext snmpTransportContext,
                                TransportService transportService) {
        this.snmpTransportContext = snmpTransportContext;
        this.transportService = transportService;
    }

    @PostConstruct
    private void init() throws IOException {
        log.info("Initializing SNMP transport service");

        pollingExecutor = Executors.newScheduledThreadPool(1, ThingsBoardThreadFactory.forName("snmp-polling"));
        snmpResponseProcessingExecutor = Executors.newWorkStealingPool(responseProcessingParallelismLevel);

        initializeSnmp();

        log.info("SNMP transport service initialized");
    }

    private void initializeSnmp() throws IOException {
        TransportMapping<?> transportMapping;
        switch (snmpUnderlyingProtocol) {
            case "udp":
                transportMapping = new DefaultUdpTransportMapping();
                break;
            case "tcp":
                transportMapping = new DefaultTcpTransportMapping();
                break;
            default:
                throw new IllegalArgumentException("Underlying protocol " + snmpUnderlyingProtocol + " for SNMP is not supported");
        }
        snmp = new Snmp(transportMapping);
        snmp.listen();
    }

    @AfterStartUp(order = 10)
    public void startPolling() {
        log.info("Starting SNMP polling");

        pollingExecutor.scheduleWithFixedDelay(() -> {
            snmpTransportContext.getSessions().forEach(this::executeSnmpRequest);
        }, 0, 1, TimeUnit.SECONDS);
    }

    private void executeSnmpRequest(DeviceSessionContext sessionContext) {
        long timeNow = System.currentTimeMillis();
        long nextRequestExecutionTime = sessionContext.getPreviousRequestExecutedAt() + sessionContext.getProfileTransportConfiguration().getPollPeriodMs();

        if (nextRequestExecutionTime < timeNow) {
            sessionContext.setPreviousRequestExecutedAt(timeNow);

            DeviceProfileId deviceProfileId = sessionContext.getDeviceProfile().getId();
            snmpTransportContext.getProfilesPdus().get(deviceProfileId).forEach(pdu -> {
                try {
                    log.debug("[{}] Sending SNMP message for device {}", pdu.getRequestID(), sessionContext.getDeviceId());
                    snmp.send(pdu, sessionContext.getTarget(), deviceProfileId, sessionContext);
                } catch (Exception e) {
                    log.error("Failed to send SNMP request: {}", e.getMessage());
                }
            });
        }
    }

    public void onNewDeviceResponse(DeviceSessionContext sessionContext, ResponseEvent responseEvent) {
        ((Snmp) responseEvent.getSource()).cancel(responseEvent.getRequest(), sessionContext);
        snmpResponseProcessingExecutor.submit(() -> processSnmpResponse(sessionContext, responseEvent));
    }

    private void processSnmpResponse(DeviceSessionContext sessionContext, ResponseEvent event) {
        if (event.getError() != null) {
            log.warn("Response error: {}", event.getError().getMessage(), event.getError());
            return;
        }

        PDU response = event.getResponse();
        if (response == null) {
            log.warn("No response from SNMP device {}, requestId: {}", sessionContext.getDeviceId(), event.getRequest().getRequestID());
            return;
        }

        DeviceProfileId deviceProfileId = (DeviceProfileId) event.getUserObject();
        log.debug("[{}] Processing SNMP response for device {} with device profile {}: {}",
                response.getRequestID(), sessionContext.getDeviceId(), deviceProfileId, response);

        JsonObject telemetry = new JsonObject();
        JsonObject attributes = new JsonObject();

        for (int i = 0; i < response.size(); i++) {
            VariableBinding variableBinding = response.get(i);
            log.trace("Processing variable binding {}: {}", i, variableBinding);

            if (variableBinding.getVariable() instanceof Null) {
                log.debug("Response variable is empty");
                continue;
            }

            snmpTransportContext.getTelemetryMapping(deviceProfileId, variableBinding.getOid()).ifPresent(mapping -> {
                log.trace("Found telemetry mapping for oid {}: {}", variableBinding.getOid(), mapping);
                processValue(mapping.getKey(), mapping.getDataType(), variableBinding.toValueString(), telemetry);
            });

            snmpTransportContext.getAttributeMapping(deviceProfileId, variableBinding.getOid()).ifPresent(mapping -> {
                log.trace("Found attribute mapping for oid {}: {}", variableBinding.getOid(), mapping);
                processValue(mapping.getKey(), mapping.getDataType(), variableBinding.toValueString(), attributes);
            });
        }

        if (telemetry.entrySet().isEmpty() && attributes.entrySet().isEmpty()) {
            log.warn("No telemetry or attribute values is the SNMP response for device {}", sessionContext.getDeviceId());
            return;
        }

        if (!telemetry.entrySet().isEmpty()) {
            TransportProtos.PostTelemetryMsg postTelemetryMsg = JsonConverter.convertToTelemetryProto(telemetry);
            transportService.process(sessionContext.getSessionInfo(), postTelemetryMsg, TransportServiceCallback.EMPTY);
            log.debug("Posted telemetry for device {}: {}", sessionContext.getDeviceId(), telemetry);
        }

        if (!attributes.entrySet().isEmpty()) {
            TransportProtos.PostAttributeMsg postAttributesMsg = JsonConverter.convertToAttributesProto(attributes);
            transportService.process(sessionContext.getSessionInfo(), postAttributesMsg, TransportServiceCallback.EMPTY);
            log.debug("Posted attributes for device {}: {}", sessionContext.getDeviceId(), attributes);
        }

        reportActivity(sessionContext.getSessionInfo());
    }

    private void reportActivity(TransportProtos.SessionInfoProto sessionInfo) {
        transportService.process(sessionInfo, TransportProtos.SubscriptionInfoProto.newBuilder()
                .setAttributeSubscription(false)
                .setRpcSubscription(false)
                .setLastActivityTime(System.currentTimeMillis())
                .build(), TransportServiceCallback.EMPTY);
    }

    private void processValue(String key, DataType dataType, String value, JsonObject result) {
        switch (dataType) {
            case LONG:
                result.addProperty(key, Long.parseLong(value));
                break;
            case BOOLEAN:
                result.addProperty(key, Boolean.parseBoolean(value));
                break;
            case DOUBLE:
                result.addProperty(key, Double.parseDouble(value));
                break;
            default:
                result.addProperty(key, value);
        }
    }

    @Override
    public String getName() {
        return "SNMP";
    }

    @PreDestroy
    public void shutdown() {
        log.info("Stopping SNMP transport!");
        if (pollingExecutor != null) {
            pollingExecutor.shutdownNow();
        }
        if (snmpResponseProcessingExecutor != null) {
            snmpResponseProcessingExecutor.shutdownNow();
        }
        if (snmp != null) {
            try {
                snmp.close();
            } catch (IOException e) {
                log.error(e.getMessage(), e);
            }
        }
        log.info("SNMP transport stopped!");
    }
}
