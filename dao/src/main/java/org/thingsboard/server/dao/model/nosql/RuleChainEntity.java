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
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.thingsboard.server.common.data.ShortEdgeInfo;
import org.thingsboard.server.common.data.id.RuleChainId;
import org.thingsboard.server.common.data.id.RuleNodeId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.rule.RuleChain;
import org.thingsboard.server.common.data.rule.RuleChainType;
import org.thingsboard.server.dao.DaoUtil;
import org.thingsboard.server.dao.model.SearchTextEntity;
import org.thingsboard.server.dao.model.type.JsonCodec;
import org.thingsboard.server.dao.model.type.RuleChainTypeCodec;

import java.io.IOException;
import java.util.HashSet;
import java.util.UUID;

import static org.thingsboard.server.dao.model.ModelConstants.ADDITIONAL_INFO_PROPERTY;
import static org.thingsboard.server.dao.model.ModelConstants.DEBUG_MODE;
import static org.thingsboard.server.dao.model.ModelConstants.ID_PROPERTY;
import static org.thingsboard.server.dao.model.ModelConstants.RULE_CHAIN_ASSIGNED_EDGES_PROPERTY;
import static org.thingsboard.server.dao.model.ModelConstants.RULE_CHAIN_COLUMN_FAMILY_NAME;
import static org.thingsboard.server.dao.model.ModelConstants.RULE_CHAIN_CONFIGURATION_PROPERTY;
import static org.thingsboard.server.dao.model.ModelConstants.RULE_CHAIN_FIRST_RULE_NODE_ID_PROPERTY;
import static org.thingsboard.server.dao.model.ModelConstants.RULE_CHAIN_NAME_PROPERTY;
import static org.thingsboard.server.dao.model.ModelConstants.RULE_CHAIN_ROOT_PROPERTY;
import static org.thingsboard.server.dao.model.ModelConstants.RULE_CHAIN_TENANT_ID_PROPERTY;
import static org.thingsboard.server.dao.model.ModelConstants.RULE_CHAIN_TYPE_PROPERTY;
import static org.thingsboard.server.dao.model.ModelConstants.SEARCH_TEXT_PROPERTY;

@Slf4j
@Table(name = RULE_CHAIN_COLUMN_FAMILY_NAME)
@EqualsAndHashCode
@ToString
public class RuleChainEntity implements SearchTextEntity<RuleChain> {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final JavaType assignedEdgesType =
            objectMapper.getTypeFactory().constructCollectionType(HashSet.class, ShortEdgeInfo.class);

    @PartitionKey
    @Column(name = ID_PROPERTY)
    private UUID id;
    @ClusteringColumn
    @Column(name = RULE_CHAIN_TENANT_ID_PROPERTY)
    private UUID tenantId;
    @Column(name = RULE_CHAIN_NAME_PROPERTY)
    private String name;
    @Column(name = RULE_CHAIN_TYPE_PROPERTY, codec = RuleChainTypeCodec.class)
    private RuleChainType type;
    @Column(name = SEARCH_TEXT_PROPERTY)
    private String searchText;
    @Column(name = RULE_CHAIN_FIRST_RULE_NODE_ID_PROPERTY)
    private UUID firstRuleNodeId;
    @Column(name = RULE_CHAIN_ROOT_PROPERTY)
    private boolean root;
    @Getter
    @Setter
    @Column(name = DEBUG_MODE)
    private boolean debugMode;
    @Column(name = RULE_CHAIN_CONFIGURATION_PROPERTY, codec = JsonCodec.class)
    private JsonNode configuration;
    @Column(name = ADDITIONAL_INFO_PROPERTY, codec = JsonCodec.class)
    private JsonNode additionalInfo;

    @Getter @Setter
    @Column(name = RULE_CHAIN_ASSIGNED_EDGES_PROPERTY)
    private String assignedEdges;

    public RuleChainEntity() {
    }

    public RuleChainEntity(RuleChain ruleChain) {
        if (ruleChain.getId() != null) {
            this.id = ruleChain.getUuidId();
        }
        this.tenantId = DaoUtil.getId(ruleChain.getTenantId());
        this.name = ruleChain.getName();
        this.type = ruleChain.getType();
        this.searchText = ruleChain.getName();
        this.firstRuleNodeId = DaoUtil.getId(ruleChain.getFirstRuleNodeId());
        this.root = ruleChain.isRoot();
        this.debugMode = ruleChain.isDebugMode();
        this.configuration = ruleChain.getConfiguration();
        this.additionalInfo = ruleChain.getAdditionalInfo();
        if (ruleChain.getAssignedEdges() != null) {
            try {
                this.assignedEdges = objectMapper.writeValueAsString(ruleChain.getAssignedEdges());
            } catch (JsonProcessingException e) {
                log.error("Unable to serialize assigned edges to string!", e);
            }
        }
    }

    @Override
    public String getSearchTextSource() {
        return getSearchText();
    }

    @Override
    public void setSearchText(String searchText) {
        this.searchText = searchText;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public UUID getFirstRuleNodeId() {
        return firstRuleNodeId;
    }

    public void setFirstRuleNodeId(UUID firstRuleNodeId) {
        this.firstRuleNodeId = firstRuleNodeId;
    }

    public boolean isRoot() {
        return root;
    }

    public void setRoot(boolean root) {
        this.root = root;
    }

    public String getSearchText() {
        return searchText;
    }

    public JsonNode getConfiguration() {
        return configuration;
    }

    public void setConfiguration(JsonNode configuration) {
        this.configuration = configuration;
    }

    public JsonNode getAdditionalInfo() {
        return additionalInfo;
    }

    public void setAdditionalInfo(JsonNode additionalInfo) {
        this.additionalInfo = additionalInfo;
    }

    @Override
    public RuleChain toData() {
        RuleChain ruleChain = new RuleChain(new RuleChainId(id));
        ruleChain.setCreatedTime(UUIDs.unixTimestamp(id));
        ruleChain.setTenantId(new TenantId(tenantId));
        ruleChain.setName(name);
        ruleChain.setType(type);
        if (this.firstRuleNodeId != null) {
            ruleChain.setFirstRuleNodeId(new RuleNodeId(this.firstRuleNodeId));
        }
        ruleChain.setRoot(this.root);
        ruleChain.setDebugMode(this.debugMode);
        ruleChain.setConfiguration(this.configuration);
        ruleChain.setAdditionalInfo(this.additionalInfo);
        if (!StringUtils.isEmpty(assignedEdges)) {
            try {
                ruleChain.setAssignedEdges(objectMapper.readValue(assignedEdges, assignedEdgesType));
            } catch (IOException e) {
                log.warn("Unable to parse assigned edges!", e);
            }
        }
        return ruleChain;
    }

}
