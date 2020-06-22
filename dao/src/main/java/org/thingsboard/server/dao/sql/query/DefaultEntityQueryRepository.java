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
package org.thingsboard.server.dao.sql.query;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Repository;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.query.AssetSearchQueryFilter;
import org.thingsboard.server.common.data.query.AssetTypeFilter;
import org.thingsboard.server.common.data.query.DeviceSearchQueryFilter;
import org.thingsboard.server.common.data.query.DeviceTypeFilter;
import org.thingsboard.server.common.data.query.EntityCountQuery;
import org.thingsboard.server.common.data.query.EntityData;
import org.thingsboard.server.common.data.query.EntityDataPageLink;
import org.thingsboard.server.common.data.query.EntityDataQuery;
import org.thingsboard.server.common.data.query.EntityDataSortOrder;
import org.thingsboard.server.common.data.query.EntityFilter;
import org.thingsboard.server.common.data.query.EntityFilterType;
import org.thingsboard.server.common.data.query.EntityListFilter;
import org.thingsboard.server.common.data.query.EntityNameFilter;
import org.thingsboard.server.common.data.query.EntitySearchQueryFilter;
import org.thingsboard.server.common.data.query.EntityViewTypeFilter;
import org.thingsboard.server.common.data.query.RelationsQueryFilter;
import org.thingsboard.server.common.data.query.SingleEntityFilter;
import org.thingsboard.server.common.data.relation.EntitySearchDirection;
import org.thingsboard.server.common.data.relation.EntityTypeFilter;
import org.thingsboard.server.dao.util.SqlDao;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.math.BigInteger;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@SqlDao
@Repository
@Slf4j
public class DefaultEntityQueryRepository implements EntityQueryRepository {
    //TODO: rafactoring to protect from SQL injections;
    private static final Map<EntityType, String> entityTableMap = new HashMap<>();

    static {
        entityTableMap.put(EntityType.ASSET, "asset");
        entityTableMap.put(EntityType.DEVICE, "device");
        entityTableMap.put(EntityType.ENTITY_VIEW, "entity_view");
        entityTableMap.put(EntityType.DASHBOARD, "dashboard");
        entityTableMap.put(EntityType.CUSTOMER, "customer");
        entityTableMap.put(EntityType.USER, "tb_user");
        entityTableMap.put(EntityType.TENANT, "tenant");
    }

    public static final String HIERARCHICAL_QUERY_TEMPLATE = " FROM (WITH RECURSIVE related_entities(from_id, from_type, to_id, to_type, relation_type, lvl) AS (" +
            " SELECT from_id, from_type, to_id, to_type, relation_type, 1 as lvl" +
            " FROM relation" +
            " WHERE $in_id = '%s' and $in_type = '%s' and relation_type_group = 'COMMON'" +
            " UNION ALL" +
            " SELECT r.from_id, r.from_type, r.to_id, r.to_type, r.relation_type, lvl + 1" +
            " FROM relation r" +
            " INNER JOIN related_entities re ON" +
            " r.$in_id = re.$out_id and r.$in_type = re.$out_type and" +
            " relation_type_group = 'COMMON' %s)" +
            " SELECT re.$out_id entity_id, re.$out_type entity_type, re.lvl lvl" +
            " from related_entities re" +
            " %s ) entity";
    public static final String HIERARCHICAL_TO_QUERY_TEMPLATE = HIERARCHICAL_QUERY_TEMPLATE.replace("$in", "to").replace("$out", "from");
    public static final String HIERARCHICAL_FROM_QUERY_TEMPLATE = HIERARCHICAL_QUERY_TEMPLATE.replace("$in", "from").replace("$out", "to");

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public long countEntitiesByQuery(TenantId tenantId, CustomerId customerId, EntityCountQuery query) {
        EntityType entityType = resolveEntityType(query.getEntityFilter());
        String countQuery = String.format("select count(e.id) from %s e where %s",
                getEntityTableQuery(query.getEntityFilter(), entityType), this.buildEntityWhere(tenantId, customerId, query.getEntityFilter(),
                        Collections.emptyList(), entityType));
        return ((BigInteger) entityManager.createNativeQuery(countQuery)
                .getSingleResult()).longValue();
    }

    @Override
    public PageData<EntityData> findEntityDataByQuery(TenantId tenantId, CustomerId customerId, EntityDataQuery query) {
        EntityType entityType = resolveEntityType(query.getEntityFilter());
        EntityDataPageLink pageLink = query.getPageLink();

        List<EntityKeyMapping> mappings = EntityKeyMapping.prepareKeyMapping(query);

        List<EntityKeyMapping> selectionMapping = mappings.stream().filter(EntityKeyMapping::isSelection)
                .collect(Collectors.toList());
        List<EntityKeyMapping> entityFieldsSelectionMapping = selectionMapping.stream().filter(mapping -> !mapping.isLatest())
                .collect(Collectors.toList());
        List<EntityKeyMapping> latestSelectionMapping = selectionMapping.stream().filter(EntityKeyMapping::isLatest)
                .collect(Collectors.toList());

        List<EntityKeyMapping> filterMapping = mappings.stream().filter(EntityKeyMapping::hasFilter)
                .collect(Collectors.toList());
        List<EntityKeyMapping> entityFieldsFiltersMapping = filterMapping.stream().filter(mapping -> !mapping.isLatest())
                .collect(Collectors.toList());
        List<EntityKeyMapping> latestFiltersMapping = filterMapping.stream().filter(EntityKeyMapping::isLatest)
                .collect(Collectors.toList());

        List<EntityKeyMapping> allLatestMappings = mappings.stream().filter(EntityKeyMapping::isLatest)
                .collect(Collectors.toList());


        String entityWhereClause = this.buildEntityWhere(tenantId, customerId, query.getEntityFilter(), entityFieldsFiltersMapping, entityType);
        String latestJoins = EntityKeyMapping.buildLatestJoins(query.getEntityFilter(), entityType, allLatestMappings);
        String whereClause = this.buildWhere(selectionMapping, latestFiltersMapping, pageLink.getTextSearch());
        String entityFieldsSelection = EntityKeyMapping.buildSelections(entityFieldsSelectionMapping);
        String entityTypeStr;
        if (query.getEntityFilter().getType().equals(EntityFilterType.RELATIONS_QUERY)) {
            entityTypeStr = "e.entity_type";
        } else {
            entityTypeStr = "'" + entityType.name() + "'";
        }
        if (!StringUtils.isEmpty(entityFieldsSelection)) {
            entityFieldsSelection = String.format("e.id, %s, %s", entityTypeStr, entityFieldsSelection);
        } else {
            entityFieldsSelection = String.format("e.id, %s", entityTypeStr);
        }
        String latestSelection = EntityKeyMapping.buildSelections(latestSelectionMapping);
        String topSelection = "entities.*";
        if (!StringUtils.isEmpty(latestSelection)) {
            topSelection = topSelection + ", " + latestSelection;
        }

        String fromClause = String.format("from (select %s from (select %s from %s e where %s) entities %s %s) result",
                topSelection,
                entityFieldsSelection,
                getEntityTableQuery(query.getEntityFilter(), entityType),
                entityWhereClause,
                latestJoins,
                whereClause);

        int totalElements = ((BigInteger) entityManager.createNativeQuery(String.format("select count(*) %s", fromClause))
                .getSingleResult()).intValue();

        String dataQuery = String.format("select * %s", fromClause);

        EntityDataSortOrder sortOrder = pageLink.getSortOrder();
        if (sortOrder != null) {
            Optional<EntityKeyMapping> sortOrderMappingOpt = mappings.stream().filter(EntityKeyMapping::isSortOrder).findFirst();
            if (sortOrderMappingOpt.isPresent()) {
                EntityKeyMapping sortOrderMapping = sortOrderMappingOpt.get();
                dataQuery = String.format("%s order by %s", dataQuery, sortOrderMapping.getValueAlias());
                if (sortOrder.getDirection() == EntityDataSortOrder.Direction.ASC) {
                    dataQuery += " asc";
                } else {
                    dataQuery += " desc";
                }
            }
        }
        int startIndex = pageLink.getPageSize() * pageLink.getPage();
        if (pageLink.getPageSize() > 0) {
            dataQuery = String.format("%s limit %s offset %s", dataQuery, pageLink.getPageSize(), startIndex);
        }
        List rows = entityManager.createNativeQuery(dataQuery).getResultList();
        return EntityDataAdapter.createEntityData(pageLink, selectionMapping, rows, totalElements);
    }

    private String buildEntityWhere(TenantId tenantId,
                                    CustomerId customerId,
                                    EntityFilter entityFilter,
                                    List<EntityKeyMapping> entityFieldsFilters,
                                    EntityType entityType) {
        String permissionQuery = this.buildPermissionQuery(entityFilter, tenantId, customerId, entityType);
        String entityFilterQuery = this.buildEntityFilterQuery(entityFilter);
        String result = permissionQuery;
        if (!entityFilterQuery.isEmpty()) {
            result += " and " + entityFilterQuery;
        }
        if (!entityFieldsFilters.isEmpty()) {
            result += " and " + entityFieldsFilters;
        }
        return result;
    }

    private String buildPermissionQuery(EntityFilter entityFilter, TenantId tenantId, CustomerId customerId, EntityType entityType) {
        switch (entityFilter.getType()) {
            case RELATIONS_QUERY:
            case DEVICE_SEARCH_QUERY:
            case ASSET_SEARCH_QUERY:
                return String.format("e.tenant_id='%s' and e.customer_id='%s'", tenantId.getId(), customerId.getId());
            default:
                if (entityType == EntityType.TENANT) {
                    return String.format("e.id='%s'", tenantId.getId());
                } else if (entityType == EntityType.CUSTOMER) {
                    return String.format("e.tenant_id='%s' and e.id='%s'", tenantId.getId(), customerId.getId());
                } else {
                    return String.format("e.tenant_id='%s' and e.customer_id='%s'", tenantId.getId(), customerId.getId());
                }
        }
    }

    private String buildEntityFilterQuery(EntityFilter entityFilter) {
        switch (entityFilter.getType()) {
            case SINGLE_ENTITY:
                return this.singleEntityQuery((SingleEntityFilter) entityFilter);
            case ENTITY_LIST:
                return this.entityListQuery((EntityListFilter) entityFilter);
            case ENTITY_NAME:
                return this.entityNameQuery((EntityNameFilter) entityFilter);
            case ASSET_TYPE:
            case DEVICE_TYPE:
            case ENTITY_VIEW_TYPE:
                return this.typeQuery(entityFilter);
            case RELATIONS_QUERY:
            case DEVICE_SEARCH_QUERY:
            case ASSET_SEARCH_QUERY:
                return "";
            default:
                throw new RuntimeException("Not implemented!");
        }
    }

    private String getEntityTableQuery(EntityFilter entityFilter, EntityType entityType) {
        switch (entityFilter.getType()) {
            case RELATIONS_QUERY:
                return relationQuery((RelationsQueryFilter) entityFilter);
            case DEVICE_SEARCH_QUERY:
                DeviceSearchQueryFilter deviceQuery = (DeviceSearchQueryFilter) entityFilter;
                return entitySearchQuery(deviceQuery, EntityType.DEVICE, deviceQuery.getDeviceTypes());
            case ASSET_SEARCH_QUERY:
                AssetSearchQueryFilter assetQuery = (AssetSearchQueryFilter) entityFilter;
                return entitySearchQuery(assetQuery, EntityType.ASSET, assetQuery.getAssetTypes());
            default:
                return entityTableMap.get(entityType);
        }
    }

    private String entitySearchQuery(EntitySearchQueryFilter entityFilter, EntityType entityType, List<String> types) {
        EntityId rootId = entityFilter.getRootEntity();
        //TODO: fetch last level only.
        //TODO: fetch distinct records.
        String lvlFilter = getLvlFilter(entityFilter.getMaxLevel());
        String selectFields = "SELECT tenant_id, customer_id, id, type, name, label FROM " + entityType.name() + " WHERE id in ( SELECT entity_id";
        String from = getQueryTemplate(entityFilter.getDirection());

        String whereFilter = " WHERE " + " re.relation_type = '" + entityFilter.getRelationType() + "'" +
                " AND re.to_type = '" + entityType.name() + "'";
        from = String.format(from, rootId.getId(), rootId.getEntityType().name(), lvlFilter, whereFilter);
        String query = "( " + selectFields + from + ")";
        if (types != null && !types.isEmpty()) {
            query += " and type in (" + types.stream().map(type -> "'" + type + "'").collect(Collectors.joining(", ")) + ")";
        }
        query += " )";
        return query;
    }

    private String relationQuery(RelationsQueryFilter entityFilter) {
        EntityId rootId = entityFilter.getRootEntity();
        String lvlFilter = getLvlFilter(entityFilter.getMaxLevel());
        String selectFields = getSelectTenantId() + ", " + getSelectCustomerId() + ", " +
                " entity.entity_id as id," + getSelectType() + ", " + getSelectName() + ", " +
                getSelectLabel() + ", entity.entity_type as entity_type";
        String from = getQueryTemplate(entityFilter.getDirection());

        StringBuilder whereFilter;
        if (entityFilter.getFilters() != null && !entityFilter.getFilters().isEmpty()) {
            whereFilter = new StringBuilder(" WHERE ");
            boolean first = true;
            boolean single = entityFilter.getFilters().size() == 1;
            for (EntityTypeFilter etf : entityFilter.getFilters()) {
                if (first) {
                    first = false;
                } else {
                    whereFilter.append(" AND ");
                }
                String relationType = etf.getRelationType();
                String entityTypes = etf.getEntityTypes().stream().map(type -> "'" + type + "'").collect(Collectors.joining(", "));
                if (!single) {
                    whereFilter.append(" (");
                }
                whereFilter.append(" re.relation_type = '").append(relationType).append("' and re.")
                        .append(entityFilter.getDirection().equals(EntitySearchDirection.FROM) ? "to" : "from")
                        .append("_type in (").append(entityTypes).append(")");
                if (!single) {
                    whereFilter.append(" )");
                }
            }
        } else {
            whereFilter = new StringBuilder();
        }
        from = String.format(from, rootId.getId(), rootId.getEntityType().name(), lvlFilter, whereFilter);
        return "( " + selectFields + from + ")";
    }

    private String getLvlFilter(int maxLevel) {
        return maxLevel > 0 ? ("and lvl <= " + (maxLevel - 1)) : "";
    }

    private String getQueryTemplate(EntitySearchDirection direction) {
        String from;
        if (direction.equals(EntitySearchDirection.FROM)) {
            from = HIERARCHICAL_FROM_QUERY_TEMPLATE;
        } else {
            from = HIERARCHICAL_TO_QUERY_TEMPLATE;
        }
        return from;
    }

    private String getSelectTenantId() {
        return "SELECT CASE" +
                " WHEN entity.entity_type = 'TENANT' THEN entity_id" +
                " WHEN entity.entity_type = 'CUSTOMER'" +
                " THEN (select tenant_id from customer where id = entity_id)" +
                " WHEN entity.entity_type = 'USER'" +
                " THEN (select tenant_id from tb_user where id = entity_id)" +
                " WHEN entity.entity_type = 'DASHBOARD'" +
                " THEN (select tenant_id from dashboard where id = entity_id)" +
                " WHEN entity.entity_type = 'ASSET'" +
                " THEN (select tenant_id from asset where id = entity_id)" +
                " WHEN entity.entity_type = 'DEVICE'" +
                " THEN (select tenant_id from device where id = entity_id)" +
                " WHEN entity.entity_type = 'ENTITY_VIEW'" +
                " THEN (select tenant_id from entity_view where id = entity_id)" +
                " END as tenant_id";
    }

    private String getSelectCustomerId() {
        return "CASE" +
                " WHEN entity.entity_type = 'TENANT'" +
                " THEN UUID('" + TenantId.NULL_UUID + "')" +
                " WHEN entity.entity_type = 'CUSTOMER' THEN entity_id" +
                " WHEN entity.entity_type = 'USER'" +
                " THEN (select customer_id from tb_user where id = entity_id)" +
                " WHEN entity.entity_type = 'DASHBOARD'" +
                //TODO: parse assigned customers or use contains?
                " THEN NULL" +
                " WHEN entity.entity_type = 'ASSET'" +
                " THEN (select customer_id from asset where id = entity_id)" +
                " WHEN entity.entity_type = 'DEVICE'" +
                " THEN (select customer_id from device where id = entity_id)" +
                " WHEN entity.entity_type = 'ENTITY_VIEW'" +
                " THEN (select customer_id from entity_view where id = entity_id)" +
                " END as customer_id";
    }

    private String getSelectName() {
        return " CASE" +
                " WHEN entity.entity_type = 'TENANT'" +
                " THEN (select title from tenant where id = entity_id)" +
                " WHEN entity.entity_type = 'CUSTOMER' " +
                " THEN (select title from customer where id = entity_id)" +
                " WHEN entity.entity_type = 'USER'" +
                " THEN (select CONCAT (first_name, ' ', last_name) from tb_user where id = entity_id)" +
                " WHEN entity.entity_type = 'DASHBOARD'" +
                " THEN (select title from dashboard where id = entity_id)" +
                " WHEN entity.entity_type = 'ASSET'" +
                " THEN (select name from asset where id = entity_id)" +
                " WHEN entity.entity_type = 'DEVICE'" +
                " THEN (select name from device where id = entity_id)" +
                " WHEN entity.entity_type = 'ENTITY_VIEW'" +
                " THEN (select name from entity_view where id = entity_id)" +
                " END as name";
    }

    private String getSelectType() {
        return " CASE" +
                " WHEN entity.entity_type = 'USER'" +
                " THEN (select authority from tb_user where id = entity_id)" +
                " WHEN entity.entity_type = 'ASSET'" +
                " THEN (select type from asset where id = entity_id)" +
                " WHEN entity.entity_type = 'DEVICE'" +
                " THEN (select type from device where id = entity_id)" +
                " WHEN entity.entity_type = 'ENTITY_VIEW'" +
                " THEN (select type from entity_view where id = entity_id)" +
                " ELSE entity.entity_type END as type";
    }

    private String getSelectLabel() {
        return " CASE" +
                " WHEN entity.entity_type = 'TENANT'" +
                " THEN (select title from tenant where id = entity_id)" +
                " WHEN entity.entity_type = 'CUSTOMER' " +
                " THEN (select title from customer where id = entity_id)" +
                " WHEN entity.entity_type = 'USER'" +
                " THEN (select CONCAT (first_name, ' ', last_name) from tb_user where id = entity_id)" +
                " WHEN entity.entity_type = 'DASHBOARD'" +
                " THEN (select title from dashboard where id = entity_id)" +
                " WHEN entity.entity_type = 'ASSET'" +
                " THEN (select label from asset where id = entity_id)" +
                " WHEN entity.entity_type = 'DEVICE'" +
                " THEN (select label from device where id = entity_id)" +
                " WHEN entity.entity_type = 'ENTITY_VIEW'" +
                " THEN (select name from entity_view where id = entity_id)" +
                " END as label";
    }

    private String buildWhere
            (List<EntityKeyMapping> selectionMapping, List<EntityKeyMapping> latestFiltersMapping, String searchText) {
        String latestFilters = EntityKeyMapping.buildQuery(latestFiltersMapping);
        String textSearchQuery = this.buildTextSearchQuery(selectionMapping, searchText);
        String query;
        if (!StringUtils.isEmpty(latestFilters) && !StringUtils.isEmpty(textSearchQuery)) {
            query = String.join(" AND ", latestFilters, textSearchQuery);
        } else if (!StringUtils.isEmpty(latestFilters)) {
            query = latestFilters;
        } else {
            query = textSearchQuery;
        }
        if (!StringUtils.isEmpty(query)) {
            return String.format("where %s", query);
        } else {
            return "";
        }
    }

    private String buildTextSearchQuery(List<EntityKeyMapping> selectionMapping, String searchText) {
        if (!StringUtils.isEmpty(searchText) && !selectionMapping.isEmpty()) {
            String lowerSearchText = searchText.toLowerCase() + "%";
            List<String> searchPredicates = selectionMapping.stream().map(mapping -> String.format("LOWER(%s) LIKE '%s'",
                    mapping.getValueAlias(), lowerSearchText)).collect(Collectors.toList());
            return String.format("(%s)", String.join(" or ", searchPredicates));
        } else {
            return null;
        }
    }

    private String singleEntityQuery(SingleEntityFilter filter) {
        return String.format("e.id='%s'", filter.getSingleEntity().getId());
    }

    private String entityListQuery(EntityListFilter filter) {
        return String.format("e.id in (%s)",
                filter.getEntityList().stream().map(UUID::fromString)
                        .map(s -> String.format("'%s'", s)).collect(Collectors.joining(",")));
    }

    private String entityNameQuery(EntityNameFilter filter) {
        return String.format("lower(e.search_text) like lower(concat(%s, '%%'))", filter.getEntityNameFilter());
    }

    private String typeQuery(EntityFilter filter) {
        String type;
        String name;
        switch (filter.getType()) {
            case ASSET_TYPE:
                type = ((AssetTypeFilter) filter).getAssetType();
                name = ((AssetTypeFilter) filter).getAssetNameFilter();
                break;
            case DEVICE_TYPE:
                type = ((DeviceTypeFilter) filter).getDeviceType();
                name = ((DeviceTypeFilter) filter).getDeviceNameFilter();
                break;
            case ENTITY_VIEW_TYPE:
                type = ((EntityViewTypeFilter) filter).getEntityViewType();
                name = ((EntityViewTypeFilter) filter).getEntityViewNameFilter();
                break;
            default:
                throw new RuntimeException("Not supported!");
        }
        return String.format("e.type = '%s' and lower(e.search_text) like lower(concat('%s', '%%'))", type, name);
    }

    private EntityType resolveEntityType(EntityFilter entityFilter) {
        switch (entityFilter.getType()) {
            case SINGLE_ENTITY:
                return ((SingleEntityFilter) entityFilter).getSingleEntity().getEntityType();
            case ENTITY_LIST:
                return ((EntityListFilter) entityFilter).getEntityType();
            case ENTITY_NAME:
                return ((EntityNameFilter) entityFilter).getEntityType();
            case ASSET_TYPE:
            case ASSET_SEARCH_QUERY:
                return EntityType.ASSET;
            case DEVICE_TYPE:
            case DEVICE_SEARCH_QUERY:
                return EntityType.DEVICE;
            case ENTITY_VIEW_TYPE:
            case ENTITY_VIEW_SEARCH_QUERY:
                return EntityType.ENTITY_VIEW;
            case RELATIONS_QUERY:
                return ((RelationsQueryFilter) entityFilter).getRootEntity().getEntityType();
            default:
                throw new RuntimeException("Not implemented!");
        }
    }
}
