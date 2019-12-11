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
package org.thingsboard.server.dao.edge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Function;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.thingsboard.server.common.data.Customer;
import org.thingsboard.server.common.data.Dashboard;
import org.thingsboard.server.common.data.DataConstants;
import org.thingsboard.server.common.data.Device;
import org.thingsboard.server.common.data.EntitySubtype;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.EntityView;
import org.thingsboard.server.common.data.Event;
import org.thingsboard.server.common.data.ShortEdgeInfo;
import org.thingsboard.server.common.data.Tenant;
import org.thingsboard.server.common.data.alarm.Alarm;
import org.thingsboard.server.common.data.asset.Asset;
import org.thingsboard.server.common.data.edge.Edge;
import org.thingsboard.server.common.data.edge.EdgeQueueEntityType;
import org.thingsboard.server.common.data.edge.EdgeQueueEntry;
import org.thingsboard.server.common.data.edge.EdgeSearchQuery;
import org.thingsboard.server.common.data.id.AssetId;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.DeviceId;
import org.thingsboard.server.common.data.id.EdgeId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.EntityViewId;
import org.thingsboard.server.common.data.id.RuleChainId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.page.TextPageData;
import org.thingsboard.server.common.data.page.TextPageLink;
import org.thingsboard.server.common.data.page.TimePageData;
import org.thingsboard.server.common.data.page.TimePageLink;
import org.thingsboard.server.common.data.relation.EntityRelation;
import org.thingsboard.server.common.data.relation.EntitySearchDirection;
import org.thingsboard.server.common.data.rule.RuleChain;
import org.thingsboard.server.common.data.rule.RuleChainMetaData;
import org.thingsboard.server.common.msg.TbMsg;
import org.thingsboard.server.common.msg.session.SessionMsgType;
import org.thingsboard.server.dao.asset.AssetService;
import org.thingsboard.server.dao.customer.CustomerDao;
import org.thingsboard.server.dao.dashboard.DashboardService;
import org.thingsboard.server.dao.device.DeviceService;
import org.thingsboard.server.dao.entity.AbstractEntityService;
import org.thingsboard.server.dao.entityview.EntityViewService;
import org.thingsboard.server.dao.event.EventService;
import org.thingsboard.server.dao.exception.DataValidationException;
import org.thingsboard.server.dao.rule.RuleChainService;
import org.thingsboard.server.dao.service.DataValidator;
import org.thingsboard.server.dao.service.PaginatedRemover;
import org.thingsboard.server.dao.service.Validator;
import org.thingsboard.server.dao.tenant.TenantDao;

import javax.annotation.Nullable;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import static org.thingsboard.server.common.data.CacheConstants.EDGE_CACHE;
import static org.thingsboard.server.dao.DaoUtil.toUUIDs;
import static org.thingsboard.server.dao.model.ModelConstants.NULL_UUID;
import static org.thingsboard.server.dao.service.Validator.validateId;
import static org.thingsboard.server.dao.service.Validator.validateIds;
import static org.thingsboard.server.dao.service.Validator.validatePageLink;
import static org.thingsboard.server.dao.service.Validator.validateString;

@Service
@Slf4j
public class BaseEdgeService extends AbstractEntityService implements EdgeService {

    private static final ObjectMapper mapper = new ObjectMapper();

    public static final String INCORRECT_TENANT_ID = "Incorrect tenantId ";
    public static final String INCORRECT_PAGE_LINK = "Incorrect page link ";
    public static final String INCORRECT_CUSTOMER_ID = "Incorrect customerId ";
    public static final String INCORRECT_EDGE_ID = "Incorrect edgeId ";

    @Autowired
    private EdgeDao edgeDao;

    @Autowired
    private TenantDao tenantDao;

    @Autowired
    private CustomerDao customerDao;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private EventService eventService;

    @Autowired
    private DashboardService dashboardService;

    @Autowired
    private RuleChainService ruleChainService;

    @Autowired
    private DeviceService deviceService;

    @Autowired
    private AssetService assetService;

    @Autowired
    private EntityViewService entityViewService;

    private ExecutorService tsCallBackExecutor;

    @PostConstruct
    public void initExecutor() {
        tsCallBackExecutor = Executors.newSingleThreadExecutor();
    }

    @PreDestroy
    public void shutdownExecutor() {
        if (tsCallBackExecutor != null) {
            tsCallBackExecutor.shutdownNow();
        }
    }

    @Override
    public Edge findEdgeById(TenantId tenantId, EdgeId edgeId) {
        log.trace("Executing findEdgeById [{}]", edgeId);
        validateId(edgeId, INCORRECT_EDGE_ID + edgeId);
        return edgeDao.findById(tenantId, edgeId.getId());
    }

    @Override
    public ListenableFuture<Edge> findEdgeByIdAsync(TenantId tenantId, EdgeId edgeId) {
        log.trace("Executing findEdgeById [{}]", edgeId);
        validateId(edgeId, INCORRECT_EDGE_ID + edgeId);
        return edgeDao.findByIdAsync(tenantId, edgeId.getId());
    }

    @Cacheable(cacheNames = EDGE_CACHE, key = "{#tenantId, #name}")
    @Override
    public Edge findEdgeByTenantIdAndName(TenantId tenantId, String name) {
        log.trace("Executing findEdgeByTenantIdAndName [{}][{}]", tenantId, name);
        validateId(tenantId, INCORRECT_TENANT_ID + tenantId);
        Optional<Edge> edgeOpt = edgeDao.findEdgeByTenantIdAndName(tenantId.getId(), name);
        return edgeOpt.orElse(null);
    }

    @Override
    public Optional<Edge> findEdgeByRoutingKey(TenantId tenantId, String routingKey) {
        log.trace("Executing findEdgeByRoutingKey [{}]", routingKey);
        Validator.validateString(routingKey, "Incorrect edge routingKey for search request.");
        return edgeDao.findByRoutingKey(tenantId.getId(), routingKey);
    }

    @CacheEvict(cacheNames = EDGE_CACHE, key = "{#edge.tenantId, #edge.name}")
    @Override
    public Edge saveEdge(Edge edge) {
        log.trace("Executing saveEdge [{}]", edge);
        edgeValidator.validate(edge, Edge::getTenantId);
        Edge savedEdge = edgeDao.save(edge.getTenantId(), edge);
        dashboardService.updateEdgeDashboards(savedEdge.getTenantId(), savedEdge.getId());
        return savedEdge;
    }

    @Override
    public Edge assignEdgeToCustomer(TenantId tenantId, EdgeId edgeId, CustomerId customerId) {
        Edge edge = findEdgeById(tenantId, edgeId);
        edge.setCustomerId(customerId);
        return saveEdge(edge);
    }

    @Override
    public Edge unassignEdgeFromCustomer(TenantId tenantId, EdgeId edgeId) {
        Edge edge = findEdgeById(tenantId, edgeId);
        edge.setCustomerId(null);
        return saveEdge(edge);
    }

    @Override
    public void deleteEdge(TenantId tenantId, EdgeId edgeId) {
        log.trace("Executing deleteEdge [{}]", edgeId);
        validateId(edgeId, INCORRECT_EDGE_ID + edgeId);

        Edge edge = edgeDao.findById(tenantId, edgeId.getId());

        dashboardService.unassignEdgeDashboards(tenantId, edgeId);
        ruleChainService.unassignEdgeRuleChains(tenantId, edgeId);

        List<Object> list = new ArrayList<>();
        list.add(edge.getTenantId());
        list.add(edge.getName());
        Cache cache = cacheManager.getCache(EDGE_CACHE);
        cache.evict(list);

        deleteEntityRelations(tenantId, edgeId);

        edgeDao.removeById(tenantId, edgeId.getId());
    }

    @Override
    public TextPageData<Edge> findEdgesByTenantId(TenantId tenantId, TextPageLink pageLink) {
        log.trace("Executing findEdgesByTenantId, tenantId [{}], pageLink [{}]", tenantId, pageLink);
        validateId(tenantId, INCORRECT_TENANT_ID + tenantId);
        validatePageLink(pageLink, INCORRECT_PAGE_LINK + pageLink);
        List<Edge> edges = edgeDao.findEdgesByTenantId(tenantId.getId(), pageLink);
        return new TextPageData<>(edges, pageLink);
    }

    @Override
    public TextPageData<Edge> findEdgesByTenantIdAndType(TenantId tenantId, String type, TextPageLink pageLink) {
        log.trace("Executing findEdgesByTenantIdAndType, tenantId [{}], type [{}], pageLink [{}]", tenantId, type, pageLink);
        validateId(tenantId, INCORRECT_TENANT_ID + tenantId);
        validateString(type, "Incorrect type " + type);
        validatePageLink(pageLink, INCORRECT_PAGE_LINK + pageLink);
        List<Edge> edges = edgeDao.findEdgesByTenantIdAndType(tenantId.getId(), type, pageLink);
        return new TextPageData<>(edges, pageLink);
    }

    @Override
    public ListenableFuture<List<Edge>> findEdgesByTenantIdAndIdsAsync(TenantId tenantId, List<EdgeId> edgeIds) {
        log.trace("Executing findEdgesByTenantIdAndIdsAsync, tenantId [{}], edgeIds [{}]", tenantId, edgeIds);
        validateId(tenantId, INCORRECT_TENANT_ID + tenantId);
        validateIds(edgeIds, "Incorrect edgeIds " + edgeIds);
        return edgeDao.findEdgesByTenantIdAndIdsAsync(tenantId.getId(), toUUIDs(edgeIds));
    }


    @Override
    public void deleteEdgesByTenantId(TenantId tenantId) {
        log.trace("Executing deleteEdgesByTenantId, tenantId [{}]", tenantId);
        validateId(tenantId, INCORRECT_TENANT_ID + tenantId);
        tenantEdgesRemover.removeEntities(tenantId, tenantId);
    }

    @Override
    public TextPageData<Edge> findEdgesByTenantIdAndCustomerId(TenantId tenantId, CustomerId customerId, TextPageLink pageLink) {
        log.trace("Executing findEdgesByTenantIdAndCustomerId, tenantId [{}], customerId [{}], pageLink [{}]", tenantId, customerId, pageLink);
        validateId(tenantId, INCORRECT_TENANT_ID + tenantId);
        validateId(customerId, INCORRECT_CUSTOMER_ID + customerId);
        validatePageLink(pageLink, INCORRECT_PAGE_LINK + pageLink);
        List<Edge> edges = edgeDao.findEdgesByTenantIdAndCustomerId(tenantId.getId(), customerId.getId(), pageLink);
        return new TextPageData<>(edges, pageLink);
    }

    @Override
    public TextPageData<Edge> findEdgesByTenantIdAndCustomerIdAndType(TenantId tenantId, CustomerId customerId, String type, TextPageLink pageLink) {
        log.trace("Executing findEdgesByTenantIdAndCustomerIdAndType, tenantId [{}], customerId [{}], type [{}], pageLink [{}]", tenantId, customerId, type, pageLink);
        validateId(tenantId, INCORRECT_TENANT_ID + tenantId);
        validateId(customerId, INCORRECT_CUSTOMER_ID + customerId);
        validateString(type, "Incorrect type " + type);
        validatePageLink(pageLink, INCORRECT_PAGE_LINK + pageLink);
        List<Edge> edges = edgeDao.findEdgesByTenantIdAndCustomerIdAndType(tenantId.getId(), customerId.getId(), type, pageLink);
        return new TextPageData<>(edges, pageLink);
    }

    @Override
    public ListenableFuture<List<Edge>> findEdgesByTenantIdCustomerIdAndIdsAsync(TenantId tenantId, CustomerId customerId, List<EdgeId> edgeIds) {
        log.trace("Executing findEdgesByTenantIdCustomerIdAndIdsAsync, tenantId [{}], customerId [{}], edgeIds [{}]", tenantId, customerId, edgeIds);
        validateId(tenantId, INCORRECT_TENANT_ID + tenantId);
        validateId(customerId, INCORRECT_CUSTOMER_ID + customerId);
        validateIds(edgeIds, "Incorrect edgeIds " + edgeIds);
        return edgeDao.findEdgesByTenantIdCustomerIdAndIdsAsync(tenantId.getId(),
                customerId.getId(), toUUIDs(edgeIds));
    }

    @Override
    public void unassignCustomerEdges(TenantId tenantId, CustomerId customerId) {
        log.trace("Executing unassignCustomerEdges, tenantId [{}], customerId [{}]", tenantId, customerId);
        validateId(tenantId, INCORRECT_TENANT_ID + tenantId);
        validateId(customerId, INCORRECT_CUSTOMER_ID + customerId);
        customerEdgeUnasigner.removeEntities(tenantId, customerId);
    }

    @Override
    public ListenableFuture<List<Edge>> findEdgesByQuery(TenantId tenantId, EdgeSearchQuery query) {
        ListenableFuture<List<EntityRelation>> relations = relationService.findByQuery(tenantId, query.toEntitySearchQuery());
        ListenableFuture<List<Edge>> edges = Futures.transformAsync(relations, r -> {
            EntitySearchDirection direction = query.toEntitySearchQuery().getParameters().getDirection();
            List<ListenableFuture<Edge>> futures = new ArrayList<>();
            for (EntityRelation relation : r) {
                EntityId entityId = direction == EntitySearchDirection.FROM ? relation.getTo() : relation.getFrom();
                if (entityId.getEntityType() == EntityType.EDGE) {
                    futures.add(findEdgeByIdAsync(tenantId, new EdgeId(entityId.getId())));
                }
            }
            return Futures.successfulAsList(futures);
        });

        edges = Futures.transform(edges, new Function<List<Edge>, List<Edge>>() {
            @Nullable
            @Override
            public List<Edge> apply(@Nullable List<Edge> edgeList) {
                return edgeList == null ? Collections.emptyList() : edgeList.stream().filter(edge -> query.getEdgeTypes().contains(edge.getType())).collect(Collectors.toList());
            }
        });

        return edges;
    }

    @Override
    public ListenableFuture<List<EntitySubtype>> findEdgeTypesByTenantId(TenantId tenantId) {
        log.trace("Executing findEdgeTypesByTenantId, tenantId [{}]", tenantId);
        validateId(tenantId, INCORRECT_TENANT_ID + tenantId);
        ListenableFuture<List<EntitySubtype>> tenantEdgeTypes = edgeDao.findTenantEdgeTypesAsync(tenantId.getId());
        return Futures.transform(tenantEdgeTypes,
                edgeTypes -> {
                    edgeTypes.sort(Comparator.comparing(EntitySubtype::getType));
                    return edgeTypes;
                });
    }

    @Override
    public void pushEventToEdge(TenantId tenantId, TbMsg tbMsg, FutureCallback<Void> callback) {
        if (tbMsg.getType().equals(SessionMsgType.POST_TELEMETRY_REQUEST.name()) ||
                tbMsg.getType().equals(SessionMsgType.POST_ATTRIBUTES_REQUEST.name()) ||
                tbMsg.getType().equals(DataConstants.ATTRIBUTES_UPDATED) ||
                tbMsg.getType().equals(DataConstants.ATTRIBUTES_DELETED)) {
            processCustomTbMsg(tenantId, tbMsg, callback);
        } else {
            try {
                switch (tbMsg.getOriginator().getEntityType()) {
                    case EDGE:
                        processEdge(tenantId, tbMsg, callback);
                        break;
                    case ASSET:
                        processAsset(tenantId, tbMsg, callback);
                        break;
                    case DEVICE:
                        processDevice(tenantId, tbMsg, callback);
                        break;
                    case DASHBOARD:
                        processDashboard(tenantId, tbMsg, callback);
                        break;
                    case RULE_CHAIN:
                        processRuleChain(tenantId, tbMsg, callback);
                        break;
                    case ENTITY_VIEW:
                        processEntityView(tenantId, tbMsg, callback);
                        break;
                    case ALARM:
                        processAlarm(tenantId, tbMsg, callback);
                        break;
                    default:
                        log.debug("Entity type [{}] is not designed to be pushed to edge", tbMsg.getOriginator().getEntityType());
                }
            } catch (IOException e) {
                log.error("Can't push to edge updates, entity type [{}], data [{}]", tbMsg.getOriginator().getEntityType(), tbMsg.getData(), e);
            }
        }
    }

    private void processCustomTbMsg(TenantId tenantId, TbMsg tbMsg, FutureCallback<Void> callback) {
        EdgeId edgeId = getEdgeIdByOriginatorId(tenantId, tbMsg.getOriginator());
        EdgeQueueEntityType edgeQueueEntityType = getEdgeQueueTypeByEntityType(tbMsg.getOriginator().getEntityType());
        if (edgeId != null && edgeQueueEntityType != null) {
            try {
                saveEventToEdgeQueue(tenantId, edgeId, edgeQueueEntityType, tbMsg.getType(), mapper.writeValueAsString(tbMsg), callback);
            } catch (IOException e) {
                log.error("Error while saving custom tbMsg into Edge Queue", e);
            }
        }
    }

    private EdgeQueueEntityType getEdgeQueueTypeByEntityType(EntityType entityType) {
        switch (entityType) {
            case DEVICE:
                return EdgeQueueEntityType.DEVICE;
            case ASSET:
                return EdgeQueueEntityType.ASSET;
            case ENTITY_VIEW:
                return EdgeQueueEntityType.ENTITY_VIEW;
            default:
                log.info("Unsupported entity type: [{}]", entityType);
                return null;
        }
    }

    private EdgeId getEdgeIdByOriginatorId(TenantId tenantId, EntityId originatorId) {
        switch (originatorId.getEntityType()) {
            case DEVICE:
                Device device = deviceService.findDeviceById(tenantId, new DeviceId(originatorId.getId()));
                return device.getEdgeId();
            case ASSET:
                Asset asset = assetService.findAssetById(tenantId, new AssetId(originatorId.getId()));
                return asset.getEdgeId();
            case ENTITY_VIEW:
                EntityView entityView = entityViewService.findEntityViewById(tenantId, new EntityViewId(originatorId.getId()));
                return entityView.getEdgeId();
            default:
                log.info("Unsupported entity type: [{}]", originatorId.getEntityType());
                return null;
        }
    }

    private void processDevice(TenantId tenantId, TbMsg tbMsg, FutureCallback<Void> callback) throws IOException {
        switch (tbMsg.getType()) {
            case DataConstants.ENTITY_ASSIGNED_TO_EDGE:
            case DataConstants.ENTITY_UNASSIGNED_FROM_EDGE:
                processAssignedEntity(tenantId, tbMsg, EdgeQueueEntityType.DEVICE, callback);
                break;
            case DataConstants.ENTITY_DELETED:
            case DataConstants.ENTITY_CREATED:
            case DataConstants.ENTITY_UPDATED:
                Device device = mapper.readValue(tbMsg.getData(), Device.class);
                if (device.getEdgeId() != null) {
                    pushEventToEdge(tenantId, device.getEdgeId(), EdgeQueueEntityType.DEVICE, tbMsg, callback);
                }
                break;
            default:
                log.warn("Unsupported msgType [{}], tbMsg [{}]", tbMsg.getType(), tbMsg);
        }
    }

    private void processEdge(TenantId tenantId, TbMsg tbMsg, FutureCallback<Void> callback) throws IOException {
        switch (tbMsg.getType()) {
            case DataConstants.ENTITY_DELETED:
            case DataConstants.ENTITY_CREATED:
            case DataConstants.ENTITY_UPDATED:
                Edge edge = mapper.readValue(tbMsg.getData(), Edge.class);
                if (edge != null) {
                    pushEventToEdge(tenantId, edge.getId(), EdgeQueueEntityType.EDGE, tbMsg, callback);
                }
                break;
            default:
                log.warn("Unsupported msgType [{}], tbMsg [{}]", tbMsg.getType(), tbMsg);
        }
    }

    private void processAsset(TenantId tenantId, TbMsg tbMsg, FutureCallback<Void> callback) throws IOException {
        switch (tbMsg.getType()) {
            case DataConstants.ENTITY_ASSIGNED_TO_EDGE:
            case DataConstants.ENTITY_UNASSIGNED_FROM_EDGE:
                processAssignedEntity(tenantId, tbMsg, EdgeQueueEntityType.ASSET, callback);
                break;
            case DataConstants.ENTITY_DELETED:
            case DataConstants.ENTITY_CREATED:
            case DataConstants.ENTITY_UPDATED:
                Asset asset = mapper.readValue(tbMsg.getData(), Asset.class);
                if (asset.getEdgeId() != null) {
                    pushEventToEdge(tenantId, asset.getEdgeId(), EdgeQueueEntityType.ASSET, tbMsg, callback);
                }
                break;
            default:
                log.warn("Unsupported msgType [{}], tbMsg [{}]", tbMsg.getType(), tbMsg);
        }
    }

    private void processEntityView(TenantId tenantId, TbMsg tbMsg, FutureCallback<Void> callback) throws IOException {
        switch (tbMsg.getType()) {
            case DataConstants.ENTITY_ASSIGNED_TO_EDGE:
            case DataConstants.ENTITY_UNASSIGNED_FROM_EDGE:
                processAssignedEntity(tenantId, tbMsg, EdgeQueueEntityType.ENTITY_VIEW, callback);
                break;
            case DataConstants.ENTITY_DELETED:
            case DataConstants.ENTITY_CREATED:
            case DataConstants.ENTITY_UPDATED:
                EntityView entityView = mapper.readValue(tbMsg.getData(), EntityView.class);
                if (entityView.getEdgeId() != null) {
                    pushEventToEdge(tenantId, entityView.getEdgeId(), EdgeQueueEntityType.ENTITY_VIEW, tbMsg, callback);
                }
                break;
            default:
                log.warn("Unsupported msgType [{}], tbMsg [{}]", tbMsg.getType(), tbMsg);
        }
    }

    private void processAlarm(TenantId tenantId, TbMsg tbMsg, FutureCallback<Void> callback) throws IOException {
        switch (tbMsg.getType()) {
            case DataConstants.ENTITY_DELETED:
            case DataConstants.ENTITY_CREATED:
            case DataConstants.ENTITY_UPDATED:
            case DataConstants.ALARM_ACK:
            case DataConstants.ALARM_CLEAR:
                Alarm alarm = mapper.readValue(tbMsg.getData(), Alarm.class);
                EdgeId edgeId = getEdgeIdByOriginatorId(tenantId, alarm.getOriginator());
                EdgeQueueEntityType edgeQueueEntityType = getEdgeQueueTypeByEntityType(alarm.getOriginator().getEntityType());
                if (edgeId != null && edgeQueueEntityType != null) {
                    pushEventToEdge(tenantId, edgeId, EdgeQueueEntityType.ALARM, tbMsg, callback);
                }
                break;
            default:
                log.warn("Unsupported msgType [{}], tbMsg [{}]", tbMsg.getType(), tbMsg);
        }
    }

    private void processDashboard(TenantId tenantId, TbMsg tbMsg, FutureCallback<Void> callback) throws IOException {
        processAssignedEntity(tenantId, tbMsg, EdgeQueueEntityType.DASHBOARD, callback);
    }

    private void processRuleChain(TenantId tenantId, TbMsg tbMsg, FutureCallback<Void> callback) throws IOException {
        switch (tbMsg.getType()) {
            case DataConstants.ENTITY_ASSIGNED_TO_EDGE:
            case DataConstants.ENTITY_UNASSIGNED_FROM_EDGE:
                processAssignedEntity(tenantId, tbMsg, EdgeQueueEntityType.RULE_CHAIN, callback);
                break;
            case DataConstants.ENTITY_DELETED:
            case DataConstants.ENTITY_CREATED:
            case DataConstants.ENTITY_UPDATED:
                RuleChain ruleChain = mapper.readValue(tbMsg.getData(), RuleChain.class);
                if (ruleChain.getAssignedEdges() != null && !ruleChain.getAssignedEdges().isEmpty()) {
                    for (ShortEdgeInfo assignedEdge : ruleChain.getAssignedEdges()) {
                        pushEventToEdge(tenantId, assignedEdge.getEdgeId(), EdgeQueueEntityType.RULE_CHAIN, tbMsg, callback);
                    }
                }
                break;
            default:
                log.warn("Unsupported msgType [{}], tbMsg [{}]", tbMsg.getType(), tbMsg);
        }
    }

    private void processAssignedEntity(TenantId tenantId, TbMsg tbMsg, EdgeQueueEntityType entityType, FutureCallback<Void> callback) throws IOException {
        EdgeId edgeId;
        switch (tbMsg.getType()) {
            case DataConstants.ENTITY_ASSIGNED_TO_EDGE:
                edgeId = new EdgeId(UUID.fromString(tbMsg.getMetaData().getValue("assignedEdgeId")));
                pushEventToEdge(tenantId, edgeId, entityType, tbMsg, callback);
                break;
            case DataConstants.ENTITY_UNASSIGNED_FROM_EDGE:
                edgeId = new EdgeId(UUID.fromString(tbMsg.getMetaData().getValue("unassignedEdgeId")));
                pushEventToEdge(tenantId, edgeId, entityType, tbMsg, callback);
                break;
            case DataConstants.ENTITY_DELETED:
            case DataConstants.ENTITY_CREATED:
            case DataConstants.ENTITY_UPDATED:
                Dashboard dashboard = mapper.readValue(tbMsg.getData(), Dashboard.class);
                if (dashboard.getAssignedEdges() != null && !dashboard.getAssignedEdges().isEmpty()) {
                    for (ShortEdgeInfo assignedEdge : dashboard.getAssignedEdges()) {
                        pushEventToEdge(tenantId, assignedEdge.getEdgeId(), EdgeQueueEntityType.DASHBOARD, tbMsg, callback);
                    }
                }
                break;
        }
    }

    private void pushEventToEdge(TenantId tenantId, EdgeId edgeId, EdgeQueueEntityType entityType, TbMsg tbMsg, FutureCallback<Void> callback) throws IOException {
        log.debug("Pushing event(s) to edge queue. tenantId [{}], edgeId [{}], entityType [{}], tbMsg [{}]", tenantId, edgeId, entityType, tbMsg);

        saveEventToEdgeQueue(tenantId, edgeId, entityType, tbMsg.getType(), tbMsg.getData(), callback);

        if (entityType.equals(EdgeQueueEntityType.RULE_CHAIN)) {
            pushRuleChainMetadataToEdge(tenantId, edgeId, tbMsg, callback);
        }
    }

    private void saveEventToEdgeQueue(TenantId tenantId, EdgeId edgeId, EdgeQueueEntityType entityType, String type, String data, FutureCallback<Void> callback) throws IOException {
        log.debug("Pushing single event to edge queue. tenantId [{}], edgeId [{}], entityType [{}], type[{}], data [{}]", tenantId, edgeId, entityType, type, data);

        EdgeQueueEntry queueEntry = new EdgeQueueEntry();
        queueEntry.setEntityType(entityType);
        queueEntry.setType(type);
        queueEntry.setData(data);

        Event event = new Event();
        event.setEntityId(edgeId);
        event.setTenantId(tenantId);
        event.setType(DataConstants.EDGE_QUEUE_EVENT_TYPE);
        event.setBody(mapper.valueToTree(queueEntry));
        ListenableFuture<Event> saveFuture = eventService.saveAsync(event);

        addMainCallback(saveFuture, callback);
    }

    private void addMainCallback(ListenableFuture<Event> saveFuture, final FutureCallback<Void> callback) {
        Futures.addCallback(saveFuture, new FutureCallback<Event>() {
            @Override
            public void onSuccess(@Nullable Event result) {
                callback.onSuccess(null);
            }

            @Override
            public void onFailure(Throwable t) {
                callback.onFailure(t);
            }
        }, tsCallBackExecutor);
    }

    private void pushRuleChainMetadataToEdge(TenantId tenantId, EdgeId edgeId, TbMsg tbMsg, FutureCallback<Void> callback) throws IOException {
        RuleChain ruleChain = mapper.readValue(tbMsg.getData(), RuleChain.class);
        switch (tbMsg.getType()) {
            case DataConstants.ENTITY_ASSIGNED_TO_EDGE:
            case DataConstants.ENTITY_UNASSIGNED_FROM_EDGE:
            case DataConstants.ENTITY_UPDATED:
                RuleChainMetaData ruleChainMetaData = ruleChainService.loadRuleChainMetaData(tenantId, ruleChain.getId());
                saveEventToEdgeQueue(tenantId, edgeId, EdgeQueueEntityType.RULE_CHAIN_METADATA, tbMsg.getType(), mapper.writeValueAsString(ruleChainMetaData), callback);
                break;
            default:
                log.warn("Unsupported msgType [{}], tbMsg [{}]", tbMsg.getType(), tbMsg);
        }
    }

    @Override
    public TimePageData<Event> findQueueEvents(TenantId tenantId, EdgeId edgeId, TimePageLink pageLink) {
        return eventService.findEvents(tenantId, edgeId, DataConstants.EDGE_QUEUE_EVENT_TYPE, pageLink);
    }

    @Override
    public Edge setRootRuleChain(TenantId tenantId, Edge edge, RuleChainId ruleChainId) throws IOException {
        edge.setRootRuleChainId(ruleChainId);
        Edge savedEdge = saveEdge(edge);
        ruleChainService.updateEdgeRuleChains(tenantId, savedEdge.getId());
        RuleChain ruleChain = ruleChainService.findRuleChainById(tenantId, ruleChainId);
        saveEventToEdgeQueue(tenantId, edge.getId(), EdgeQueueEntityType.RULE_CHAIN, DataConstants.ENTITY_UPDATED, mapper.writeValueAsString(ruleChain), new FutureCallback<Void>() {
            @Override
            public void onSuccess(@Nullable Void aVoid) {
                log.debug("Event saved successfully!");
            }

            @Override
            public void onFailure(Throwable t) {
                log.debug("Failure during event save", t);
            }
        });
        return savedEdge;
    }

    private DataValidator<Edge> edgeValidator =
            new DataValidator<Edge>() {

                @Override
                protected void validateCreate(TenantId tenantId, Edge edge) {
                    edgeDao.findEdgeByTenantIdAndName(edge.getTenantId().getId(), edge.getName()).ifPresent(
                            d -> {
                                throw new DataValidationException("Edge with such name already exists!");
                            }
                    );
                }

                @Override
                protected void validateUpdate(TenantId tenantId, Edge edge) {
                    edgeDao.findEdgeByTenantIdAndName(edge.getTenantId().getId(), edge.getName()).ifPresent(
                            e -> {
                                if (!e.getUuidId().equals(edge.getUuidId())) {
                                    throw new DataValidationException("Edge with such name already exists!");
                                }
                            }
                    );
                }

                @Override
                protected void validateDataImpl(TenantId tenantId, Edge edge) {
                    if (StringUtils.isEmpty(edge.getType())) {
                        throw new DataValidationException("Edge type should be specified!");
                    }
                    if (StringUtils.isEmpty(edge.getName())) {
                        throw new DataValidationException("Edge name should be specified!");
                    }
                    if (edge.getTenantId() == null) {
                        throw new DataValidationException("Edge should be assigned to tenant!");
                    } else {
                        Tenant tenant = tenantDao.findById(edge.getTenantId(), edge.getTenantId().getId());
                        if (tenant == null) {
                            throw new DataValidationException("Edge is referencing to non-existent tenant!");
                        }
                    }
                    if (edge.getCustomerId() == null) {
                        edge.setCustomerId(new CustomerId(NULL_UUID));
                    } else if (!edge.getCustomerId().getId().equals(NULL_UUID)) {
                        Customer customer = customerDao.findById(edge.getTenantId(), edge.getCustomerId().getId());
                        if (customer == null) {
                            throw new DataValidationException("Can't assign edge to non-existent customer!");
                        }
                        if (!customer.getTenantId().getId().equals(edge.getTenantId().getId())) {
                            throw new DataValidationException("Can't assign edge to customer from different tenant!");
                        }
                    }
                }
            };

    private PaginatedRemover<TenantId, Edge> tenantEdgesRemover =
            new PaginatedRemover<TenantId, Edge>() {

                @Override
                protected List<Edge> findEntities(TenantId tenantId, TenantId id, TextPageLink pageLink) {
                    return edgeDao.findEdgesByTenantId(id.getId(), pageLink);
                }

                @Override
                protected void removeEntity(TenantId tenantId, Edge entity) {
                    deleteEdge(tenantId, new EdgeId(entity.getUuidId()));
                }
            };

    private PaginatedRemover<CustomerId, Edge> customerEdgeUnasigner = new PaginatedRemover<CustomerId, Edge>() {

        @Override
        protected List<Edge> findEntities(TenantId tenantId, CustomerId id, TextPageLink pageLink) {
            return edgeDao.findEdgesByTenantIdAndCustomerId(tenantId.getId(), id.getId(), pageLink);
        }

        @Override
        protected void removeEntity(TenantId tenantId, Edge entity) {
            unassignEdgeFromCustomer(tenantId, new EdgeId(entity.getUuidId()));
        }
    };

}
