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
package org.thingsboard.server.edge;

import com.datastax.driver.core.utils.UUIDs;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.thingsboard.server.common.data.Dashboard;
import org.thingsboard.server.common.data.DashboardInfo;
import org.thingsboard.server.common.data.DataConstants;
import org.thingsboard.server.common.data.Device;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.Tenant;
import org.thingsboard.server.common.data.User;
import org.thingsboard.server.common.data.alarm.Alarm;
import org.thingsboard.server.common.data.alarm.AlarmInfo;
import org.thingsboard.server.common.data.alarm.AlarmSeverity;
import org.thingsboard.server.common.data.alarm.AlarmStatus;
import org.thingsboard.server.common.data.asset.Asset;
import org.thingsboard.server.common.data.audit.ActionType;
import org.thingsboard.server.common.data.edge.Edge;
import org.thingsboard.server.common.data.edge.EdgeEvent;
import org.thingsboard.server.common.data.edge.EdgeEventType;
import org.thingsboard.server.common.data.id.EdgeId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.page.TextPageLink;
import org.thingsboard.server.common.data.page.TimePageData;
import org.thingsboard.server.common.data.relation.EntityRelation;
import org.thingsboard.server.common.data.relation.RelationTypeGroup;
import org.thingsboard.server.common.data.rule.RuleChain;
import org.thingsboard.server.common.data.rule.RuleChainType;
import org.thingsboard.server.common.data.security.Authority;
import org.thingsboard.server.common.transport.adaptor.JsonConverter;
import org.thingsboard.server.controller.AbstractControllerTest;
import org.thingsboard.server.dao.edge.EdgeEventService;;
import org.thingsboard.server.edge.imitator.EdgeImitator;
import org.thingsboard.server.gen.edge.AlarmUpdateMsg;
import org.thingsboard.server.gen.edge.DeviceUpdateMsg;
import org.thingsboard.server.gen.edge.EdgeConfiguration;
import org.thingsboard.server.gen.edge.EntityDataProto;
import org.thingsboard.server.gen.edge.RelationUpdateMsg;
import org.thingsboard.server.gen.edge.UpdateMsgType;
import org.thingsboard.server.gen.edge.UplinkMsg;
import org.thingsboard.server.gen.transport.TransportProtos;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@Slf4j
abstract public class BaseEdgeTest extends AbstractControllerTest {

    private Tenant savedTenant;
    private TenantId tenantId;
    private User tenantAdmin;

    private EdgeImitator edgeImitator;
    private Edge edge;

    @Autowired
    private EdgeEventService edgeEventService;

    @Before
    public void beforeTest() throws Exception {
        loginSysAdmin();

        Tenant tenant = new Tenant();
        tenant.setTitle("My tenant");
        savedTenant = doPost("/api/tenant", tenant, Tenant.class);
        tenantId = savedTenant.getId();
        Assert.assertNotNull(savedTenant);

        tenantAdmin = new User();
        tenantAdmin.setAuthority(Authority.TENANT_ADMIN);
        tenantAdmin.setTenantId(savedTenant.getId());
        tenantAdmin.setEmail("tenant2@thingsboard.org");
        tenantAdmin.setFirstName("Joe");
        tenantAdmin.setLastName("Downs");

        tenantAdmin = createUserAndLogin(tenantAdmin, "testPassword1");
        installation();

        edgeImitator = new EdgeImitator("localhost", 7070, edge.getRoutingKey(), edge.getSecret());
        // should be 3, but 3 events from sync service + 3 from controller. will be fixed in next releases
        edgeImitator.getStorage().expectMessageAmount(6);
        edgeImitator.connect();
    }

    @After
    public void afterTest() throws Exception {
        edgeImitator.disconnect();
        uninstallation();

        loginSysAdmin();

        doDelete("/api/tenant/" + savedTenant.getId().getId().toString())
                .andExpect(status().isOk());
    }


    @Test
    public void test() throws Exception {
        testReceivedInitialData();
        testDevices();
        testAssets();
        testRuleChains();
        testDashboards();
        testRelations();
        testAlarms();
        testTimeseries();
        testAttributes();
        testSendMessagesToCloud();
    }

    private void testReceivedInitialData() throws Exception {
        log.info("Checking received data");
        edgeImitator.getStorage().waitForMessages();

        EdgeConfiguration configuration = edgeImitator.getStorage().getConfiguration();
        Assert.assertNotNull(configuration);

        Map<UUID, EntityType> entities = edgeImitator.getStorage().getEntities();
        Assert.assertFalse(entities.isEmpty());

        Set<UUID> devices = edgeImitator.getStorage().getEntitiesByType(EntityType.DEVICE);
        Assert.assertEquals(1, devices.size());
        TimePageData<Device> pageDataDevices = doGetTypedWithPageLink("/api/edge/" + edge.getId().getId().toString() + "/devices?",
                new TypeReference<TimePageData<Device>>() {}, new TextPageLink(100));
        for (Device device: pageDataDevices.getData()) {
            Assert.assertTrue(devices.contains(device.getUuidId()));
        }

        Set<UUID> assets = edgeImitator.getStorage().getEntitiesByType(EntityType.ASSET);
        Assert.assertEquals(1, assets.size());
        TimePageData<Asset> pageDataAssets = doGetTypedWithPageLink("/api/edge/" + edge.getId().getId().toString() + "/assets?",
                new TypeReference<TimePageData<Asset>>() {}, new TextPageLink(100));
        for (Asset asset: pageDataAssets.getData()) {
            Assert.assertTrue(assets.contains(asset.getUuidId()));
        }

        Set<UUID> ruleChains = edgeImitator.getStorage().getEntitiesByType(EntityType.RULE_CHAIN);
        Assert.assertEquals(1, ruleChains.size());
        TimePageData<RuleChain> pageDataRuleChains = doGetTypedWithPageLink("/api/edge/" + edge.getId().getId().toString() + "/ruleChains?",
                new TypeReference<TimePageData<RuleChain>>() {}, new TextPageLink(100));
        for (RuleChain ruleChain: pageDataRuleChains.getData()) {
            Assert.assertTrue(ruleChains.contains(ruleChain.getUuidId()));
        }
        log.info("Received data checked");
    }

    private void testDevices() throws Exception {
        log.info("Testing devices");
        Device device = new Device();
        device.setName("Edge Device 2");
        device.setType("test");
        Device savedDevice = doPost("/api/device", device, Device.class);
        edgeImitator.getStorage().expectMessageAmount(1);
        doPost("/api/edge/" + edge.getId().getId().toString()
                + "/device/" + savedDevice.getId().getId().toString(), Device.class);

        TimePageData<Device> pageDataDevices = doGetTypedWithPageLink("/api/edge/" + edge.getId().getId().toString() + "/devices?",
                new TypeReference<TimePageData<Device>>() {}, new TextPageLink(100));
        Assert.assertTrue(pageDataDevices.getData().contains(savedDevice));
        edgeImitator.getStorage().waitForMessages();
        Set<UUID> devices = edgeImitator.getStorage().getEntitiesByType(EntityType.DEVICE);
        Assert.assertEquals(2, devices.size());
        Assert.assertTrue(devices.contains(savedDevice.getUuidId()));

        edgeImitator.getStorage().expectMessageAmount(1);
        doDelete("/api/edge/" + edge.getId().getId().toString()
                + "/device/" + savedDevice.getId().getId().toString(), Device.class);
        pageDataDevices = doGetTypedWithPageLink("/api/edge/" + edge.getId().getId().toString() + "/devices?",
                new TypeReference<TimePageData<Device>>() {}, new TextPageLink(100));
        Assert.assertFalse(pageDataDevices.getData().contains(savedDevice));
        edgeImitator.getStorage().waitForMessages();
        devices = edgeImitator.getStorage().getEntitiesByType(EntityType.DEVICE);
        Assert.assertEquals(1, devices.size());
        Assert.assertFalse(devices.contains(savedDevice.getUuidId()));

        doDelete("/api/device/" + savedDevice.getId().getId().toString())
                .andExpect(status().isOk());
        log.info("Devices tested successfully");
    }

    private void testAssets() throws Exception {
        log.info("Testing assets");
        Asset asset = new Asset();
        asset.setName("Edge Asset 2");
        asset.setType("test");
        Asset savedAsset = doPost("/api/asset", asset, Asset.class);
        edgeImitator.getStorage().expectMessageAmount(1);
        doPost("/api/edge/" + edge.getId().getId().toString()
                + "/asset/" + savedAsset.getId().getId().toString(), Asset.class);

        TimePageData<Asset> pageDataAssets = doGetTypedWithPageLink("/api/edge/" + edge.getId().getId().toString() + "/assets?",
                new TypeReference<TimePageData<Asset>>() {}, new TextPageLink(100));
        Assert.assertTrue(pageDataAssets.getData().contains(savedAsset));
        edgeImitator.getStorage().waitForMessages();
        Set<UUID> assets = edgeImitator.getStorage().getEntitiesByType(EntityType.ASSET);
        Assert.assertEquals(2, assets.size());
        Assert.assertTrue(assets.contains(savedAsset.getUuidId()));

        edgeImitator.getStorage().expectMessageAmount(1);
        doDelete("/api/edge/" + edge.getId().getId().toString()
                + "/asset/" + savedAsset.getId().getId().toString(), Asset.class);
        pageDataAssets = doGetTypedWithPageLink("/api/edge/" + edge.getId().getId().toString() + "/assets?",
                new TypeReference<TimePageData<Asset>>() {}, new TextPageLink(100));
        Assert.assertFalse(pageDataAssets.getData().contains(savedAsset));
        edgeImitator.getStorage().waitForMessages();
        assets = edgeImitator.getStorage().getEntitiesByType(EntityType.ASSET);
        Assert.assertEquals(1, assets.size());
        Assert.assertFalse(assets.contains(savedAsset.getUuidId()));

        doDelete("/api/asset/" + savedAsset.getId().getId().toString())
                .andExpect(status().isOk());
        log.info("Assets tested successfully");
    }

    private void testRuleChains() throws Exception {
        log.info("Testing RuleChains");
        RuleChain ruleChain = new RuleChain();
        ruleChain.setName("Edge Test Rule Chain");
        ruleChain.setType(RuleChainType.EDGE);
        RuleChain savedRuleChain = doPost("/api/ruleChain", ruleChain, RuleChain.class);
        edgeImitator.getStorage().expectMessageAmount(1);
        doPost("/api/edge/" + edge.getId().getId().toString()
                + "/ruleChain/" + savedRuleChain.getId().getId().toString(), RuleChain.class);

        TimePageData<RuleChain> pageDataRuleChain = doGetTypedWithPageLink("/api/edge/" + edge.getId().getId().toString() + "/ruleChains?",
                new TypeReference<TimePageData<RuleChain>>() {}, new TextPageLink(100));
        Assert.assertTrue(pageDataRuleChain.getData().contains(savedRuleChain));
        edgeImitator.getStorage().waitForMessages();
        Set<UUID> ruleChains = edgeImitator.getStorage().getEntitiesByType(EntityType.RULE_CHAIN);
        Assert.assertEquals(2, ruleChains.size());
        Assert.assertTrue(ruleChains.contains(savedRuleChain.getUuidId()));

        edgeImitator.getStorage().expectMessageAmount(1);
        doDelete("/api/edge/" + edge.getId().getId().toString()
                + "/ruleChain/" + savedRuleChain.getId().getId().toString(), RuleChain.class);
        pageDataRuleChain = doGetTypedWithPageLink("/api/edge/" + edge.getId().getId().toString() + "/ruleChains?",
                new TypeReference<TimePageData<RuleChain>>() {}, new TextPageLink(100));
        Assert.assertFalse(pageDataRuleChain.getData().contains(savedRuleChain));
        edgeImitator.getStorage().waitForMessages();
        ruleChains = edgeImitator.getStorage().getEntitiesByType(EntityType.RULE_CHAIN);
        Assert.assertEquals(1, ruleChains.size());
        Assert.assertFalse(ruleChains.contains(savedRuleChain.getUuidId()));

        doDelete("/api/ruleChain/" + savedRuleChain.getId().getId().toString())
                .andExpect(status().isOk());
        log.info("RuleChains tested successfully");

    }

    private void testDashboards() throws Exception {
        log.info("Testing Dashboards");
        Dashboard dashboard = new Dashboard();
        dashboard.setTitle("Edge Test Dashboard");
        Dashboard savedDashboard = doPost("/api/dashboard", dashboard, Dashboard.class);
        edgeImitator.getStorage().expectMessageAmount(1);
        doPost("/api/edge/" + edge.getId().getId().toString()
                + "/dashboard/" + savedDashboard.getId().getId().toString(), Dashboard.class);

        TimePageData<DashboardInfo> pageDataDashboard = doGetTypedWithPageLink("/api/edge/" + edge.getId().getId().toString() + "/dashboards?",
                new TypeReference<TimePageData<DashboardInfo>>() {}, new TextPageLink(100));
        Assert.assertTrue(pageDataDashboard.getData().stream().allMatch(dashboardInfo -> dashboardInfo.getUuidId().equals(savedDashboard.getUuidId())));
        edgeImitator.getStorage().waitForMessages();
        Set<UUID> dashboards = edgeImitator.getStorage().getEntitiesByType(EntityType.DASHBOARD);
        Assert.assertEquals(1, dashboards.size());
        Assert.assertTrue(dashboards.contains(savedDashboard.getUuidId()));

        edgeImitator.getStorage().expectMessageAmount(1);
        doDelete("/api/edge/" + edge.getId().getId().toString()
                + "/dashboard/" + savedDashboard.getId().getId().toString(), Dashboard.class);
        pageDataDashboard = doGetTypedWithPageLink("/api/edge/" + edge.getId().getId().toString() + "/dashboards?",
                new TypeReference<TimePageData<DashboardInfo>>() {}, new TextPageLink(100));
        Assert.assertFalse(pageDataDashboard.getData().stream().anyMatch(dashboardInfo -> dashboardInfo.getUuidId().equals(savedDashboard.getUuidId())));
        edgeImitator.getStorage().waitForMessages();
        dashboards = edgeImitator.getStorage().getEntitiesByType(EntityType.DASHBOARD);
        Assert.assertEquals(0, dashboards.size());
        Assert.assertFalse(dashboards.contains(savedDashboard.getUuidId()));

        doDelete("/api/dashboard/" + savedDashboard.getId().getId().toString())
                .andExpect(status().isOk());
        log.info("Dashboards tested successfully");

    }

    private void testRelations() throws Exception {
        log.info("Testing Relations");
        List<Device> edgeDevices = doGetTypedWithPageLink("/api/edge/" + edge.getId().getId().toString() + "/devices?",
                new TypeReference<TimePageData<Device>>() {}, new TextPageLink(100)).getData();
        List<Asset> edgeAssets = doGetTypedWithPageLink("/api/edge/" + edge.getId().getId().toString() + "/assets?",
                new TypeReference<TimePageData<Asset>>() {}, new TextPageLink(100)).getData();

        Assert.assertEquals(1, edgeDevices.size());
        Assert.assertEquals(1, edgeAssets.size());
        Device device = edgeDevices.get(0);
        Asset asset = edgeAssets.get(0);
        Assert.assertEquals("Edge Device 1", device.getName());
        Assert.assertEquals("Edge Asset 1", asset.getName());

        EntityRelation relation = new EntityRelation();
        relation.setType("test");
        relation.setFrom(device.getId());
        relation.setTo(asset.getId());
        relation.setTypeGroup(RelationTypeGroup.COMMON);
        edgeImitator.getStorage().expectMessageAmount(1);
        doPost("/api/relation", relation);

        edgeImitator.getStorage().waitForMessages();
        List<EntityRelation> relations = edgeImitator.getStorage().getRelations();
        Assert.assertEquals(1, relations.size());
        Assert.assertTrue(relations.contains(relation));
        edgeImitator.getStorage().expectMessageAmount(1);
        doDelete("/api/relation?" +
                "fromId=" + relation.getFrom().getId().toString() +
                "&fromType=" + relation.getFrom().getEntityType().name() +
                "&relationType=" + relation.getType() +
                "&relationTypeGroup=" + relation.getTypeGroup().name() +
                "&toId=" + relation.getTo().getId().toString() +
                "&toType=" + relation.getTo().getEntityType().name())
                .andExpect(status().isOk());

        edgeImitator.getStorage().waitForMessages();
        relations = edgeImitator.getStorage().getRelations();
        Assert.assertEquals(0, relations.size());
        Assert.assertFalse(relations.contains(relation));
        log.info("Relations tested successfully");
    }


    private void testAlarms() throws Exception {
        log.info("Testing Alarms");
        List<Device> edgeDevices = doGetTypedWithPageLink("/api/edge/" + edge.getId().getId().toString() + "/devices?",
                new TypeReference<TimePageData<Device>>() {}, new TextPageLink(100)).getData();
        Assert.assertEquals(1, edgeDevices.size());
        Device device = edgeDevices.get(0);
        Assert.assertEquals("Edge Device 1", device.getName());

        Alarm alarm = new Alarm();
        alarm.setOriginator(device.getId());
        alarm.setStatus(AlarmStatus.ACTIVE_UNACK);
        alarm.setType("alarm");
        alarm.setSeverity(AlarmSeverity.CRITICAL);

        edgeImitator.getStorage().expectMessageAmount(1);
        Alarm savedAlarm = doPost("/api/alarm", alarm, Alarm.class);
        AlarmInfo alarmInfo = doGet("/api/alarm/info/" + savedAlarm.getId().getId().toString(), AlarmInfo.class);
        edgeImitator.getStorage().waitForMessages();

        Assert.assertEquals(1, edgeImitator.getStorage().getAlarms().size());
        Assert.assertTrue(edgeImitator.getStorage().getAlarms().containsKey(alarmInfo.getType()));
        Assert.assertEquals(edgeImitator.getStorage().getAlarms().get(alarmInfo.getType()), alarmInfo.getStatus());
        edgeImitator.getStorage().expectMessageAmount(1);
        doPost("/api/alarm/" + savedAlarm.getId().getId().toString() + "/ack");

        edgeImitator.getStorage().waitForMessages();
        alarmInfo = doGet("/api/alarm/info/" + savedAlarm.getId().getId().toString(), AlarmInfo.class);
        Assert.assertTrue(edgeImitator.getStorage().getAlarms().get(alarmInfo.getType()).isAck());
        Assert.assertEquals(edgeImitator.getStorage().getAlarms().get(alarmInfo.getType()), alarmInfo.getStatus());
        edgeImitator.getStorage().expectMessageAmount(1);
        doPost("/api/alarm/" + savedAlarm.getId().getId().toString() + "/clear");

        edgeImitator.getStorage().waitForMessages();
        alarmInfo = doGet("/api/alarm/info/" + savedAlarm.getId().getId().toString(), AlarmInfo.class);
        Assert.assertTrue(edgeImitator.getStorage().getAlarms().get(alarmInfo.getType()).isAck());
        Assert.assertTrue(edgeImitator.getStorage().getAlarms().get(alarmInfo.getType()).isCleared());
        Assert.assertEquals(edgeImitator.getStorage().getAlarms().get(alarmInfo.getType()), alarmInfo.getStatus());

        doDelete("/api/alarm/" + savedAlarm.getId().getId().toString())
            .andExpect(status().isOk());
        log.info("Alarms tested successfully");
    }

    private void testTimeseries() throws Exception {
        log.info("Testing timeseries");
        ObjectMapper mapper = new ObjectMapper();
        List<Device> edgeDevices = doGetTypedWithPageLink("/api/edge/" + edge.getId().getId().toString() + "/devices?",
                new TypeReference<TimePageData<Device>>() {}, new TextPageLink(100)).getData();
        Assert.assertEquals(1, edgeDevices.size());
        Device device = edgeDevices.get(0);
        Assert.assertEquals("Edge Device 1", device.getName());

        String timeseriesData = "{\"data\":{\"temperature\":25},\"ts\":" + System.currentTimeMillis() + "}";
        JsonNode timeseriesEntityData = mapper.readTree(timeseriesData);
        EdgeEvent edgeEvent1 = constructEdgeEvent(tenantId, edge.getId(), ActionType.TIMESERIES_UPDATED, device.getId().getId(), EdgeEventType.DEVICE, timeseriesEntityData);
        edgeImitator.getStorage().expectMessageAmount(1);
        edgeEventService.saveAsync(edgeEvent1);
        edgeImitator.getStorage().waitForMessages();

        EntityDataProto latestEntityDataMsg = edgeImitator.getStorage().getLatestEntityDataMsg();
        Assert.assertNotNull(latestEntityDataMsg);
        UUID uuid = new UUID(latestEntityDataMsg.getEntityIdMSB(), latestEntityDataMsg.getEntityIdLSB());
        Assert.assertEquals(device.getId().getId(), uuid);
        Assert.assertEquals(device.getId().getEntityType().name(), latestEntityDataMsg.getEntityType());
        Assert.assertTrue(latestEntityDataMsg.hasPostTelemetryMsg());

        TransportProtos.PostTelemetryMsg postTelemetryMsg = latestEntityDataMsg.getPostTelemetryMsg();
        Assert.assertEquals(1, postTelemetryMsg.getTsKvListCount());
        TransportProtos.TsKvListProto tsKvListProto = postTelemetryMsg.getTsKvList(0);
        Assert.assertEquals(timeseriesEntityData.get("ts").asLong(), tsKvListProto.getTs());
        Assert.assertEquals(1, tsKvListProto.getKvCount());
        TransportProtos.KeyValueProto keyValueProto = tsKvListProto.getKv(0);
        Assert.assertEquals("temperature", keyValueProto.getKey());
        Assert.assertEquals(25, keyValueProto.getLongV());
        edgeImitator.getStorage().setLatestEntityDataMsg(null);
        log.info("Timeseries tested successfully");
    }

    private void testAttributes() throws Exception {
        log.info("Testing attributes");
        List<Device> edgeDevices = doGetTypedWithPageLink("/api/edge/" + edge.getId().getId().toString() + "/devices?",
                new TypeReference<TimePageData<Device>>() {}, new TextPageLink(100)).getData();
        Assert.assertEquals(1, edgeDevices.size());
        Device device = edgeDevices.get(0);
        Assert.assertEquals("Edge Device 1", device.getName());

        String attributesData = "{\"scope\":\"SERVER_SCOPE\",\"kv\":{\"test\":\"test\"}}";
        JsonNode attributesEntityData = mapper.readTree(attributesData);
        EdgeEvent edgeEvent2 = constructEdgeEvent(tenantId, edge.getId(), ActionType.ATTRIBUTES_UPDATED, device.getId().getId(), EdgeEventType.DEVICE, attributesEntityData);
        edgeImitator.getStorage().expectMessageAmount(1);
        edgeEventService.saveAsync(edgeEvent2);
        edgeImitator.getStorage().waitForMessages();

        EntityDataProto latestEntityDataMsg = edgeImitator.getStorage().getLatestEntityDataMsg();
        Assert.assertNotNull(latestEntityDataMsg);
        UUID uuid = new UUID(latestEntityDataMsg.getEntityIdMSB(), latestEntityDataMsg.getEntityIdLSB());
        Assert.assertEquals(device.getId().getId(), uuid);
        Assert.assertEquals(device.getId().getEntityType().name(), latestEntityDataMsg.getEntityType());
        Assert.assertEquals(attributesEntityData.get("scope").asText(), latestEntityDataMsg.getPostAttributeScope());
        Assert.assertTrue(latestEntityDataMsg.hasPostAttributesMsg());

        TransportProtos.PostAttributeMsg postAttributeMsg = latestEntityDataMsg.getPostAttributesMsg();
        Assert.assertEquals(1, postAttributeMsg.getKvCount());
        TransportProtos.KeyValueProto keyValueProto = postAttributeMsg.getKv(0);
        Assert.assertEquals("test", keyValueProto.getKey());
        Assert.assertEquals("test", keyValueProto.getStringV());
        edgeImitator.getStorage().setLatestEntityDataMsg(null);
        log.info("Attributes tested successfully");
    }

    private void testSendMessagesToCloud() throws Exception {
        log.info("Sending messages to cloud");
        sendDevice();
        sendAlarm();
        sendTelemetry();
        sendRelation();
        sendDeleteDeviceOnEdge();
        log.info("Messages were sent successfully");
    }

    private void sendDevice() throws Exception {
        UUID uuid = UUIDs.timeBased();

        UplinkMsg.Builder builder =  UplinkMsg.newBuilder();
        DeviceUpdateMsg.Builder deviceUpdateMsgBuilder = DeviceUpdateMsg.newBuilder();
        deviceUpdateMsgBuilder.setIdMSB(uuid.getMostSignificantBits());
        deviceUpdateMsgBuilder.setIdLSB(uuid.getLeastSignificantBits());
        deviceUpdateMsgBuilder.setName("Edge Device 2");
        deviceUpdateMsgBuilder.setType("test");
        deviceUpdateMsgBuilder.setMsgType(UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE);
        builder.addDeviceUpdateMsg(deviceUpdateMsgBuilder.build());
        edgeImitator.expectResponsesAmount(1);
        edgeImitator.sendUplinkMsg(builder.build());
        edgeImitator.waitForResponses();

        Device device = doGet("/api/device/" + uuid.toString(), Device.class);
        Assert.assertNotNull(device);
        Assert.assertEquals("Edge Device 2", device.getName());
    }

    private void sendAlarm() throws Exception {
        List<Device> edgeDevices = doGetTypedWithPageLink("/api/edge/" + edge.getId().getId().toString() + "/devices?",
                new TypeReference<TimePageData<Device>>() {}, new TextPageLink(100)).getData();
        Optional<Device> foundDevice = edgeDevices.stream().filter(device1 -> device1.getName().equals("Edge Device 2")).findAny();
        Assert.assertTrue(foundDevice.isPresent());
        Device device = foundDevice.get();

        UplinkMsg.Builder builder = UplinkMsg.newBuilder();
        AlarmUpdateMsg.Builder alarmUpdateMgBuilder = AlarmUpdateMsg.newBuilder();
        alarmUpdateMgBuilder.setName("alarm from edge");
        alarmUpdateMgBuilder.setStatus(AlarmStatus.ACTIVE_UNACK.name());
        alarmUpdateMgBuilder.setSeverity(AlarmSeverity.CRITICAL.name());
        alarmUpdateMgBuilder.setOriginatorName(device.getName());
        alarmUpdateMgBuilder.setOriginatorType(EntityType.DEVICE.name());
        builder.addAlarmUpdateMsg(alarmUpdateMgBuilder.build());
        edgeImitator.expectResponsesAmount(1);
        edgeImitator.sendUplinkMsg(builder.build());
        edgeImitator.waitForResponses();


        List<AlarmInfo> alarms = doGetTypedWithPageLink("/api/alarm/{entityType}/{entityId}?",
                new TypeReference<TimePageData<AlarmInfo>>() {},
                new TextPageLink(100), device.getId().getEntityType().name(), device.getId().getId().toString())
                .getData();

        for (AlarmInfo alarmInfo: alarms) {
            log.info(String.valueOf(alarmInfo));
        }

        Optional<AlarmInfo> foundAlarm = alarms.stream().filter(alarm -> alarm.getType().equals("alarm from edge")).findAny();
        Assert.assertTrue(foundAlarm.isPresent());
        AlarmInfo alarmInfo = foundAlarm.get();
        Assert.assertEquals(device.getId(), alarmInfo.getOriginator());
        Assert.assertEquals(AlarmStatus.ACTIVE_UNACK, alarmInfo.getStatus());
        Assert.assertEquals(AlarmSeverity.CRITICAL, alarmInfo.getSeverity());
    }

    private void sendRelation() throws Exception {
        List<Device> edgeDevices = doGetTypedWithPageLink("/api/edge/" + edge.getId().getId().toString() + "/devices?",
                new TypeReference<TimePageData<Device>>() {}, new TextPageLink(100)).getData();
        Optional<Device> foundDevice1 = edgeDevices.stream().filter(device1 -> device1.getName().equals("Edge Device 1")).findAny();
        Assert.assertTrue(foundDevice1.isPresent());
        Device device1 = foundDevice1.get();
        Optional<Device> foundDevice2 = edgeDevices.stream().filter(device2 -> device2.getName().equals("Edge Device 2")).findAny();
        Assert.assertTrue(foundDevice2.isPresent());
        Device device2 = foundDevice2.get();

        UplinkMsg.Builder builder = UplinkMsg.newBuilder();
        RelationUpdateMsg.Builder relationUpdateMsgBuilder = RelationUpdateMsg.newBuilder();
        relationUpdateMsgBuilder.setType("test");
        relationUpdateMsgBuilder.setTypeGroup(RelationTypeGroup.COMMON.name());
        relationUpdateMsgBuilder.setToIdMSB(device1.getId().getId().getMostSignificantBits());
        relationUpdateMsgBuilder.setToIdLSB(device1.getId().getId().getLeastSignificantBits());
        relationUpdateMsgBuilder.setToEntityType(device1.getId().getEntityType().name());
        relationUpdateMsgBuilder.setFromIdMSB(device2.getId().getId().getMostSignificantBits());
        relationUpdateMsgBuilder.setFromIdLSB(device2.getId().getId().getLeastSignificantBits());
        relationUpdateMsgBuilder.setFromEntityType(device2.getId().getEntityType().name());
        relationUpdateMsgBuilder.setAdditionalInfo("{}");
        builder.addRelationUpdateMsg(relationUpdateMsgBuilder.build());
        UplinkMsg msg = builder.build();
        edgeImitator.expectResponsesAmount(1);
        edgeImitator.sendUplinkMsg(msg);
        edgeImitator.waitForResponses();

        EntityRelation relation = doGet("/api/relation?" +
                "&fromId=" + device2.getId().getId().toString() +
                "&fromType=" + device2.getId().getEntityType().name() +
                "&relationType=" + "test" +
                "&relationTypeGroup=" + RelationTypeGroup.COMMON.name() +
                "&toId=" + device1.getId().getId().toString() +
                "&toType=" + device1.getId().getEntityType().name(), EntityRelation.class);
        Assert.assertNotNull(relation);
    }

    private void sendTelemetry() throws Exception {
        List<Device> edgeDevices = doGetTypedWithPageLink("/api/edge/" + edge.getId().getId().toString() + "/devices?",
                new TypeReference<TimePageData<Device>>() {}, new TextPageLink(100)).getData();
        Optional<Device> foundDevice = edgeDevices.stream().filter(device1 -> device1.getName().equals("Edge Device 2")).findAny();
        Assert.assertTrue(foundDevice.isPresent());
        Device device = foundDevice.get();

        edgeImitator.expectResponsesAmount(2);

        JsonObject data = new JsonObject();
        String timeseriesKey = "key";
        String timeseriesValue = "25";
        data.addProperty(timeseriesKey, timeseriesValue);
        UplinkMsg.Builder builder1 = UplinkMsg.newBuilder();
        EntityDataProto.Builder entityDataBuilder = EntityDataProto.newBuilder();
        entityDataBuilder.setPostTelemetryMsg(JsonConverter.convertToTelemetryProto(data, System.currentTimeMillis()));
        entityDataBuilder.setEntityType(device.getId().getEntityType().name());
        entityDataBuilder.setEntityIdMSB(device.getUuidId().getMostSignificantBits());
        entityDataBuilder.setEntityIdLSB(device.getUuidId().getLeastSignificantBits());
        builder1.addEntityData(entityDataBuilder.build());
        edgeImitator.sendUplinkMsg(builder1.build());

        JsonObject attributesData = new JsonObject();
        String attributesKey = "test_attr";
        String attributesValue = "test_value";
        attributesData.addProperty(attributesKey, attributesValue);
        UplinkMsg.Builder builder2 = UplinkMsg.newBuilder();
        EntityDataProto.Builder entityDataBuilder2 = EntityDataProto.newBuilder();
        entityDataBuilder2.setEntityType(device.getId().getEntityType().name());
        entityDataBuilder2.setEntityIdMSB(device.getId().getId().getMostSignificantBits());
        entityDataBuilder2.setEntityIdLSB(device.getId().getId().getLeastSignificantBits());
        entityDataBuilder2.setPostAttributesMsg(JsonConverter.convertToAttributesProto(attributesData));
        entityDataBuilder2.setPostAttributeScope(DataConstants.SERVER_SCOPE);
        builder2.addEntityData(entityDataBuilder2.build());
        edgeImitator.sendUplinkMsg(builder2.build());

        edgeImitator.waitForResponses();

        Thread.sleep(1000);
        Map<String, List<Map<String, String>>> timeseries = doGetAsync("/api/plugins/telemetry/DEVICE/" + device.getUuidId() + "/values/timeseries?keys=" + timeseriesKey, Map.class);
        Assert.assertTrue(timeseries.containsKey(timeseriesKey));
        Assert.assertEquals(1, timeseries.get(timeseriesKey).size());
        Assert.assertEquals(timeseriesValue, timeseries.get(timeseriesKey).get(0).get("value"));

        List<Map<String, String>> attributes = doGetAsync("/api/plugins/telemetry/DEVICE/" + device.getId() + "/values/attributes/" + DataConstants.SERVER_SCOPE, List.class);
        Assert.assertEquals(1, attributes.size());
        Assert.assertEquals(attributes.get(0).get("key"), attributesKey);
        Assert.assertEquals(attributes.get(0).get("value"), attributesValue);

    }

    private void sendDeleteDeviceOnEdge() throws Exception {
        List<Device> edgeDevices = doGetTypedWithPageLink("/api/edge/" + edge.getId().getId().toString() + "/devices?",
                new TypeReference<TimePageData<Device>>() {}, new TextPageLink(100)).getData();
        Optional<Device> foundDevice = edgeDevices.stream().filter(device1 -> device1.getName().equals("Edge Device 2")).findAny();
        Assert.assertTrue(foundDevice.isPresent());
        Device device = foundDevice.get();
        UplinkMsg.Builder builder = UplinkMsg.newBuilder();
        DeviceUpdateMsg.Builder deviceDeleteMsgBuilder = DeviceUpdateMsg.newBuilder();
        deviceDeleteMsgBuilder.setMsgType(UpdateMsgType.ENTITY_DELETED_RPC_MESSAGE);
        deviceDeleteMsgBuilder.setIdMSB(device.getId().getId().getMostSignificantBits());
        deviceDeleteMsgBuilder.setIdLSB(device.getId().getId().getLeastSignificantBits());
        builder.addDeviceUpdateMsg(deviceDeleteMsgBuilder.build());
        edgeImitator.expectResponsesAmount(1);
        edgeImitator.sendUplinkMsg(builder.build());
        edgeImitator.waitForResponses();
        device = doGet("/api/device/" + device.getId().getId().toString(), Device.class);
        Assert.assertNotNull(device);
        edgeDevices = doGetTypedWithPageLink("/api/edge/" + edge.getId().getId().toString() + "/devices?",
                new TypeReference<TimePageData<Device>>() {
                }, new TextPageLink(100)).getData();
        Assert.assertFalse(edgeDevices.contains(device));
    }

    private void installation() throws Exception {
        edge = doPost("/api/edge", constructEdge("Test Edge", "test"), Edge.class);

        Device device = new Device();
        device.setName("Edge Device 1");
        device.setType("test");
        Device savedDevice = doPost("/api/device", device, Device.class);
        doPost("/api/edge/" + edge.getId().getId().toString()
                + "/device/" + savedDevice.getId().getId().toString(), Device.class);

        Asset asset = new Asset();
        asset.setName("Edge Asset 1");
        asset.setType("test");
        Asset savedAsset = doPost("/api/asset", asset, Asset.class);
        doPost("/api/edge/" + edge.getId().getId().toString()
                + "/asset/" + savedAsset.getId().getId().toString(), Asset.class);
    }

    private void uninstallation() throws Exception {

        TimePageData<Device> pageDataDevices = doGetTypedWithPageLink("/api/edge/" + edge.getId().getId().toString() + "/devices?",
                new TypeReference<TimePageData<Device>>() {}, new TextPageLink(100));
        for (Device device: pageDataDevices.getData()) {
            doDelete("/api/device/" + device.getId().getId().toString())
                    .andExpect(status().isOk());
        }

        TimePageData<Asset> pageDataAssets = doGetTypedWithPageLink("/api/edge/" + edge.getId().getId().toString() + "/assets?",
                new TypeReference<TimePageData<Asset>>() {}, new TextPageLink(100));
        for (Asset asset: pageDataAssets.getData()) {
            doDelete("/api/asset/" + asset.getId().getId().toString())
                    .andExpect(status().isOk());
        }

        doDelete("/api/edge/" + edge.getId().getId().toString())
                .andExpect(status().isOk());
    }

    private EdgeEvent constructEdgeEvent(TenantId tenantId, EdgeId edgeId, ActionType edgeEventAction, UUID entityId, EdgeEventType edgeEventType, JsonNode entityBody) {
        EdgeEvent edgeEvent = new EdgeEvent();
        edgeEvent.setEdgeId(edgeId);
        edgeEvent.setTenantId(tenantId);
        edgeEvent.setAction(edgeEventAction.name());
        edgeEvent.setEntityId(entityId);
        edgeEvent.setType(edgeEventType);
        edgeEvent.setBody(entityBody);
        return edgeEvent;
    }
}
