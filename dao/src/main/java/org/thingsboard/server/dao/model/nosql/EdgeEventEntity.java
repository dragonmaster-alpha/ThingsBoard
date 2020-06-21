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
package org.thingsboard.server.dao.model.nosql;

import com.datastax.driver.core.utils.UUIDs;
import com.datastax.driver.mapping.annotations.ClusteringColumn;
import com.datastax.driver.mapping.annotations.Column;
import com.datastax.driver.mapping.annotations.PartitionKey;
import com.datastax.driver.mapping.annotations.Table;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.Event;
import org.thingsboard.server.common.data.edge.EdgeEvent;
import org.thingsboard.server.common.data.edge.EdgeEventType;
import org.thingsboard.server.common.data.id.EdgeEventId;
import org.thingsboard.server.common.data.id.EdgeId;
import org.thingsboard.server.common.data.id.EntityIdFactory;
import org.thingsboard.server.common.data.id.EventId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.dao.model.BaseEntity;
import org.thingsboard.server.dao.model.type.EdgeEventTypeCodec;
import org.thingsboard.server.dao.model.type.EntityTypeCodec;
import org.thingsboard.server.dao.model.type.JsonCodec;

import java.util.UUID;

import static org.thingsboard.server.dao.model.ModelConstants.EDGE_EVENT_ACTION_PROPERTY;
import static org.thingsboard.server.dao.model.ModelConstants.EDGE_EVENT_COLUMN_FAMILY_NAME;
import static org.thingsboard.server.dao.model.ModelConstants.EDGE_EVENT_EDGE_ID_PROPERTY;
import static org.thingsboard.server.dao.model.ModelConstants.EDGE_EVENT_ENTITY_BODY_PROPERTY;
import static org.thingsboard.server.dao.model.ModelConstants.EDGE_EVENT_ENTITY_ID_PROPERTY;
import static org.thingsboard.server.dao.model.ModelConstants.EDGE_EVENT_TENANT_ID_PROPERTY;
import static org.thingsboard.server.dao.model.ModelConstants.EDGE_EVENT_TYPE_PROPERTY;
import static org.thingsboard.server.dao.model.ModelConstants.EVENT_BODY_PROPERTY;
import static org.thingsboard.server.dao.model.ModelConstants.EVENT_COLUMN_FAMILY_NAME;
import static org.thingsboard.server.dao.model.ModelConstants.EVENT_ENTITY_ID_PROPERTY;
import static org.thingsboard.server.dao.model.ModelConstants.EVENT_ENTITY_TYPE_PROPERTY;
import static org.thingsboard.server.dao.model.ModelConstants.EVENT_TENANT_ID_PROPERTY;
import static org.thingsboard.server.dao.model.ModelConstants.EVENT_TYPE_PROPERTY;
import static org.thingsboard.server.dao.model.ModelConstants.EVENT_UID_PROPERTY;
import static org.thingsboard.server.dao.model.ModelConstants.ID_PROPERTY;

@Data
@NoArgsConstructor
@Table(name = EDGE_EVENT_COLUMN_FAMILY_NAME)
public class EdgeEventEntity implements BaseEntity<EdgeEvent> {

    @Column(name = ID_PROPERTY)
    private UUID id;

    @PartitionKey()
    @Column(name = EDGE_EVENT_TENANT_ID_PROPERTY)
    private UUID tenantId;

    @PartitionKey(value = 1)
    @Column(name = EDGE_EVENT_EDGE_ID_PROPERTY)
    private UUID edgeId;

    @PartitionKey(value = 2)
    @Column(name = EDGE_EVENT_TYPE_PROPERTY, codec = EdgeEventTypeCodec.class)
    private EdgeEventType edgeEventType;

    @PartitionKey(value = 3)
    @Column(name = EDGE_EVENT_ENTITY_ID_PROPERTY)
    private UUID entityId;

    @ClusteringColumn()
    @Column(name = EDGE_EVENT_ACTION_PROPERTY)
    private String edgeEventAction;

    // TODO
    @ClusteringColumn(value = 1)
    @Column(name = EVENT_UID_PROPERTY)
    private String eventUid;

    @Column(name = EDGE_EVENT_ENTITY_BODY_PROPERTY, codec = JsonCodec.class)
    private JsonNode entityBody;

    public EdgeEventEntity(EdgeEvent edgeEvent) {
        if (edgeEvent.getId() != null) {
            this.id = edgeEvent.getId().getId();
        }
        if (edgeEvent.getTenantId() != null) {
            this.tenantId = edgeEvent.getTenantId().getId();
        }
        if (edgeEvent.getEdgeId() != null) {
            this.edgeId = edgeEvent.getEdgeId().getId();
        }
//        if (event.getEntityId() != null) {
//            this.entityType = event.getEntityId().getEntityType();
//            this.entityId = event.getEntityId().getId();
//        }
//        this.edgeEventType = edgeEvent.getEdgeEventType();
//        this.edgeEventAction = edgeEvent.getEdgeEventAction();
//        this.entityBody = edgeEvent.getEntityBody();
    }

    @Override
    public UUID getUuid() {
        return id;
    }

    @Override
    public void setUuid(UUID id) {
        this.id = id;
    }

    @Override
    public EdgeEvent toData() {
        EdgeEvent edgeEvent = new EdgeEvent(new EdgeEventId(id));
//        edgeEvent.setCreatedTime(UUIDs.unixTimestamp(id));
//        edgeEvent.setTenantId(new TenantId(tenantId));
//        edgeEvent.setEdgeId(new EdgeId(edgeId));
//        edgeEvent.setEntityId(entityId);
//        event.setBody(body);
//        event.setType(eventType);
//        event.setUid(eventUid);
        return edgeEvent;
    }
}
