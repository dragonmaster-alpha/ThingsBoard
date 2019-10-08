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
package org.thingsboard.server.dao.model.sql;

import com.datastax.driver.core.utils.UUIDs;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;
import org.thingsboard.server.common.data.UUIDConverter;
import org.thingsboard.server.common.data.edge.Edge;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.EdgeId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.dao.model.BaseSqlEntity;
import org.thingsboard.server.dao.model.ModelConstants;
import org.thingsboard.server.dao.model.SearchTextEntity;
import org.thingsboard.server.dao.util.mapping.JsonStringType;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;

import static org.thingsboard.server.dao.model.ModelConstants.ASSET_CUSTOMER_ID_PROPERTY;
import static org.thingsboard.server.dao.model.ModelConstants.EDGE_COLUMN_FAMILY_NAME;
import static org.thingsboard.server.dao.model.ModelConstants.EDGE_CUSTOMER_ID_PROPERTY;
import static org.thingsboard.server.dao.model.ModelConstants.EDGE_LABEL_PROPERTY;
import static org.thingsboard.server.dao.model.ModelConstants.EDGE_NAME_PROPERTY;
import static org.thingsboard.server.dao.model.ModelConstants.EDGE_TENANT_ID_PROPERTY;
import static org.thingsboard.server.dao.model.ModelConstants.EDGE_TYPE_PROPERTY;
import static org.thingsboard.server.dao.model.ModelConstants.SEARCH_TEXT_PROPERTY;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@TypeDef(name = "json", typeClass = JsonStringType.class)
@Table(name = EDGE_COLUMN_FAMILY_NAME)
public class EdgeEntity extends BaseSqlEntity<Edge> implements SearchTextEntity<Edge> {

    @Column(name = EDGE_TENANT_ID_PROPERTY)
    private String tenantId;

    @Column(name = EDGE_CUSTOMER_ID_PROPERTY)
    private String customerId;

    @Column(name = EDGE_TYPE_PROPERTY)
    private String type;

    @Column(name = EDGE_NAME_PROPERTY)
    private String name;

    @Column(name = EDGE_LABEL_PROPERTY)
    private String label;

    @Column(name = SEARCH_TEXT_PROPERTY)
    private String searchText;

    @Type(type = "json")
    @Column(name = ModelConstants.EDGE_CONFIGURATION_PROPERTY)
    private JsonNode configuration;

    @Type(type = "json")
    @Column(name = ModelConstants.EDGE_ADDITIONAL_INFO_PROPERTY)
    private JsonNode additionalInfo;

    public EdgeEntity() {
        super();
    }

    public EdgeEntity(Edge edge) {
        if (edge.getId() != null) {
            this.setId(edge.getId().getId());
        }
        if (edge.getTenantId() != null) {
            this.tenantId = UUIDConverter.fromTimeUUID(edge.getTenantId().getId());
        }
        if (edge.getCustomerId() != null) {
            this.customerId = UUIDConverter.fromTimeUUID(edge.getCustomerId().getId());
        }
        this.type = edge.getType();
        this.name = edge.getName();
        this.label = edge.getLabel();
        this.configuration = edge.getConfiguration();
        this.additionalInfo = edge.getAdditionalInfo();
    }

    public String getSearchText() {
        return searchText;
    }

    @Override
    public String getSearchTextSource() {
        return name;
    }

    @Override
    public void setSearchText(String searchText) {
        this.searchText = searchText;
    }

    @Override
    public Edge toData() {
        Edge edge = new Edge(new EdgeId(UUIDConverter.fromString(id)));
        edge.setCreatedTime(UUIDs.unixTimestamp(UUIDConverter.fromString(id)));
        if (tenantId != null) {
            edge.setTenantId(new TenantId(UUIDConverter.fromString(tenantId)));
        }
        if (customerId != null) {
            edge.setCustomerId(new CustomerId(UUIDConverter.fromString(customerId)));
        }
        edge.setType(type);
        edge.setName(name);
        edge.setLabel(label);
        edge.setConfiguration(configuration);
        edge.setAdditionalInfo(additionalInfo);
        return edge;
    }
}
