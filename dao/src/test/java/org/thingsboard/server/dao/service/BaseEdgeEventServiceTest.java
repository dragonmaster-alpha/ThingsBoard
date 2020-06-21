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
package org.thingsboard.server.dao.service;

import com.datastax.driver.core.utils.UUIDs;
import org.junit.Assert;
import org.junit.Test;
import org.thingsboard.server.common.data.DataConstants;
import org.thingsboard.server.common.data.edge.EdgeEvent;
import org.thingsboard.server.common.data.edge.EdgeEventType;
import org.thingsboard.server.common.data.id.DeviceId;
import org.thingsboard.server.common.data.id.EdgeEventId;
import org.thingsboard.server.common.data.id.EdgeId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.page.TimePageData;
import org.thingsboard.server.common.data.page.TimePageLink;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.ZoneOffset;

public abstract class BaseEdgeEventServiceTest extends AbstractServiceTest {

    @Test
    public void saveEdgeEvent() throws Exception {
        EdgeId edgeId = new EdgeId(UUIDs.timeBased());
        DeviceId deviceId = new DeviceId(UUIDs.timeBased());
        EdgeEvent edgeEvent = generateEdgeEvent(null, edgeId, deviceId, DataConstants.ENTITY_CREATED);
        EdgeEvent saved = edgeEventService.saveAsync(edgeEvent).get();
        Assert.assertEquals(saved.getTenantId(), edgeEvent.getTenantId());
        Assert.assertEquals(saved.getEdgeId(), edgeEvent.getEdgeId());
        Assert.assertEquals(saved.getEntityId(), edgeEvent.getEntityId());
        Assert.assertEquals(saved.getEdgeEventType(), edgeEvent.getEdgeEventType());
        Assert.assertEquals(saved.getEdgeEventAction(), edgeEvent.getEdgeEventAction());
        Assert.assertEquals(saved.getEntityBody(), edgeEvent.getEntityBody());
    }

    protected EdgeEvent generateEdgeEvent(TenantId tenantId, EdgeId edgeId, EntityId entityId, String edgeEventAction) throws IOException {
        if (tenantId == null) {
            tenantId = new TenantId(UUIDs.timeBased());
        }
        EdgeEvent edgeEvent = new EdgeEvent();
        edgeEvent.setTenantId(tenantId);
        edgeEvent.setEdgeId(edgeId);
        edgeEvent.setEntityId(entityId.getId());
        edgeEvent.setEdgeEventType(EdgeEventType.DEVICE);
        edgeEvent.setEdgeEventAction(edgeEventAction);
        edgeEvent.setEntityBody(readFromResource("TestJsonData.json"));
        return edgeEvent;
    }


    @Test
    public void findEdgeEventsByTimeDescOrder() throws Exception {
        long timeBeforeStartTime = LocalDateTime.of(2020, Month.NOVEMBER, 1, 11, 30).toEpochSecond(ZoneOffset.UTC);
        long startTime = LocalDateTime.of(2020, Month.NOVEMBER, 1, 12, 0).toEpochSecond(ZoneOffset.UTC);
        long eventTime = LocalDateTime.of(2020, Month.NOVEMBER, 1, 12, 30).toEpochSecond(ZoneOffset.UTC);
        long endTime = LocalDateTime.of(2020, Month.NOVEMBER, 1, 13, 0).toEpochSecond(ZoneOffset.UTC);
        long timeAfterEndTime = LocalDateTime.of(2020, Month.NOVEMBER, 1, 13, 30).toEpochSecond(ZoneOffset.UTC);

        EdgeId edgeId = new EdgeId(UUIDs.timeBased());
        DeviceId deviceId = new DeviceId(UUIDs.timeBased());
        TenantId tenantId = new TenantId(UUIDs.timeBased());
        saveEdgeEventWithProvidedTime(timeBeforeStartTime, edgeId, deviceId, tenantId);
        EdgeEvent savedEdgeEvent = saveEdgeEventWithProvidedTime(eventTime, edgeId, deviceId, tenantId);
        EdgeEvent savedEdgeEvent2 = saveEdgeEventWithProvidedTime(eventTime + 1, edgeId, deviceId, tenantId);
        EdgeEvent savedEdgeEvent3 = saveEdgeEventWithProvidedTime(eventTime + 2, edgeId, deviceId, tenantId);
        saveEdgeEventWithProvidedTime(timeAfterEndTime, edgeId, deviceId, tenantId);

        TimePageData<EdgeEvent> edgeEvents = edgeEventService.findEdgeEvents(tenantId, edgeId, new TimePageLink(2, startTime, endTime, false));

        Assert.assertNotNull(edgeEvents.getData());
        Assert.assertTrue(edgeEvents.getData().size() == 2);
        Assert.assertTrue(edgeEvents.getData().get(0).getUuidId().equals(savedEdgeEvent3.getUuidId()));
        Assert.assertTrue(edgeEvents.getData().get(1).getUuidId().equals(savedEdgeEvent2.getUuidId()));
        Assert.assertTrue(edgeEvents.hasNext());
        Assert.assertNotNull(edgeEvents.getNextPageLink());

        edgeEvents = edgeEventService.findEdgeEvents(tenantId, edgeId, edgeEvents.getNextPageLink());

        Assert.assertNotNull(edgeEvents.getData());
        Assert.assertTrue(edgeEvents.getData().size() == 1);
        Assert.assertTrue(edgeEvents.getData().get(0).getUuidId().equals(savedEdgeEvent.getUuidId()));
        Assert.assertFalse(edgeEvents.hasNext());
        Assert.assertNull(edgeEvents.getNextPageLink());
    }

    private EdgeEvent saveEdgeEventWithProvidedTime(long time, EdgeId edgeId, EntityId entityId, TenantId tenantId) throws Exception {
        EdgeEvent edgeEvent = generateEdgeEvent(tenantId, edgeId, entityId, DataConstants.ENTITY_CREATED);
        edgeEvent.setId(new EdgeEventId(UUIDs.startOf(time)));
        return edgeEventService.saveAsync(edgeEvent).get();
    }
}