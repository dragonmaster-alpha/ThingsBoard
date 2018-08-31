/**
 * Copyright © 2016-2018 The Thingsboard Authors
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
package org.thingsboard.server.dao.entityview;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.thingsboard.server.common.data.Customer;
import org.thingsboard.server.common.data.EntityView;
import org.thingsboard.server.common.data.Tenant;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.EntityViewId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.page.TextPageData;
import org.thingsboard.server.common.data.page.TextPageLink;
import org.thingsboard.server.dao.customer.CustomerDao;
import org.thingsboard.server.dao.entity.AbstractEntityService;
import org.thingsboard.server.dao.exception.DataValidationException;
import org.thingsboard.server.dao.service.DataValidator;
import org.thingsboard.server.dao.service.PaginatedRemover;
import org.thingsboard.server.dao.tenant.TenantDao;

import java.util.ArrayList;
import java.util.List;

import static org.thingsboard.server.dao.model.ModelConstants.NULL_UUID;
import static org.thingsboard.server.dao.service.Validator.validateId;
import static org.thingsboard.server.dao.service.Validator.validatePageLink;
import static org.thingsboard.server.dao.service.Validator.validateString;

/**
 * Created by Victor Basanets on 8/28/2017.
 */
@Service
@Slf4j
public class EntityViewServiceImpl extends AbstractEntityService
        implements EntityViewService {

    public static final String INCORRECT_TENANT_ID = "Incorrect tenantId ";
    public static final String INCORRECT_PAGE_LINK = "Incorrect page link ";
    public static final String INCORRECT_CUSTOMER_ID = "Incorrect customerId ";
    public static final String INCORRECT_ENTITY_VIEW_ID = "Incorrect entityViewId ";

    @Autowired
    private EntityViewDao entityViewDao;

    @Autowired
    private TenantDao tenantDao;

    @Autowired
    private CustomerDao customerDao;

    @Override
    public EntityView findEntityViewById(EntityViewId entityViewId) {
        log.trace("Executing findEntityViewById [{}]", entityViewId);
        validateId(entityViewId, INCORRECT_ENTITY_VIEW_ID + entityViewId);
        return entityViewDao.findById(entityViewId.getId());
    }

    @Override
    public EntityView findEntityViewByTenantIdAndName(TenantId tenantId, String name) {
        log.trace("Executing findEntityViewByTenantIdAndName [{}][{}]", tenantId, name);
        validateId(tenantId, INCORRECT_TENANT_ID + tenantId);
        return entityViewDao.findEntityViewByTenantIdAndName(tenantId.getId(), name)
                .orElse(null);
    }

    @Override
    public EntityView saveEntityView(EntityView entityView) {
        log.trace("Executing save entity view [{}]", entityView);
        entityViewValidator.validate(entityView);
        return entityViewDao.save(entityView);
    }

    @Override
    public EntityView assignEntityViewToCustomer(EntityViewId entityViewId, CustomerId customerId) {
        EntityView entityView = findEntityViewById(entityViewId);
        entityView.setCustomerId(customerId);
        return saveEntityView(entityView);
    }

    @Override
    public EntityView unassignEntityViewFromCustomer(EntityViewId entityViewId) {
        EntityView entityView = findEntityViewById(entityViewId);
        entityView.setCustomerId(null);
        return saveEntityView(entityView);
    }

    @Override
    public void deleteEntityView(EntityViewId entityViewId) {
        log.trace("Executing deleteEntityView [{}]", entityViewId);
        validateId(entityViewId, INCORRECT_ENTITY_VIEW_ID + entityViewId);
        deleteEntityRelations(entityViewId);
        EntityView entityView = entityViewDao.findById(entityViewId.getId());
        List<Object> list = new ArrayList<>();
        list.add(entityView.getTenantId());
        list.add(entityView.getName());
        entityViewDao.removeById(entityViewId.getId());
    }

    @Override
    public TextPageData<EntityView> findEntityViewByTenantId(TenantId tenantId, TextPageLink pageLink) {
        log.trace("Executing findEntityViewByTenantId, tenantId [{}], pageLink [{}]", tenantId, pageLink);
        validateId(tenantId, INCORRECT_TENANT_ID + tenantId);
        validatePageLink(pageLink, INCORRECT_PAGE_LINK + pageLink);
        List<EntityView> entityViews = entityViewDao.findEntityViewByTenantId(tenantId.getId(), pageLink);
        return new TextPageData<>(entityViews, pageLink);
    }

    @Override
    public TextPageData<EntityView> findEntityViewByTenantIdAndEntityId(TenantId tenantId, EntityId entityId,
                                                                    TextPageLink pageLink) {

        log.trace("Executing findEntityViewByTenantIdAndType, tenantId [{}], entityId [{}], pageLink [{}]",
                tenantId, entityId, pageLink);

        validateId(tenantId, INCORRECT_TENANT_ID + tenantId);
        validateString(entityId.toString(), "Incorrect entityId " + entityId.toString());
        validatePageLink(pageLink, INCORRECT_PAGE_LINK + pageLink);
        List<EntityView> entityViews = entityViewDao.findEntityViewByTenantIdAndEntityId(tenantId.getId(),
                entityId, pageLink);

        return new TextPageData<>(entityViews, pageLink);
    }

    @Override
    public void deleteEntityViewByTenantId(TenantId tenantId) {
        log.trace("Executing deleteEntityViewByTenantId, tenantId [{}]", tenantId);
        validateId(tenantId, INCORRECT_TENANT_ID + tenantId);
        tenantEntityViewRemover.removeEntities(tenantId);
    }

    @Override
    public TextPageData<EntityView> findEntityViewsByTenantIdAndCustomerId(TenantId tenantId, CustomerId customerId,
                                                                          TextPageLink pageLink) {

        log.trace("Executing findEntityViewByTenantIdAndCustomerId, tenantId [{}], customerId [{}]," +
                        " pageLink [{}]", tenantId, customerId, pageLink);

        validateId(tenantId, INCORRECT_TENANT_ID + tenantId);
        validateId(customerId, INCORRECT_CUSTOMER_ID + customerId);
        validatePageLink(pageLink, INCORRECT_PAGE_LINK + pageLink);
        List<EntityView> entityViews = entityViewDao.findEntityViewsByTenantIdAndCustomerId(tenantId.getId(),
                customerId.getId(), pageLink);

        return new TextPageData<>(entityViews, pageLink);
    }

    @Override
    public TextPageData<EntityView> findEntityViewsByTenantIdAndCustomerIdAndEntityId(TenantId tenantId,
                                                                                      CustomerId customerId,
                                                                                      EntityId entityId,
                                                                                      TextPageLink pageLink) {

        log.trace("Executing findEntityViewsByTenantIdAndCustomerIdAndType, tenantId [{}], customerId [{}]," +
                " entityId [{}], pageLink [{}]", tenantId, customerId, entityId, pageLink);

        validateId(tenantId, INCORRECT_TENANT_ID + tenantId);
        validateId(customerId, INCORRECT_CUSTOMER_ID + customerId);
        validateString(entityId.toString(), "Incorrect entityId " + entityId.toString());
        validatePageLink(pageLink, INCORRECT_PAGE_LINK + pageLink);
        List<EntityView> entityViews = entityViewDao.findEntityViewsByTenantIdAndCustomerIdAndEntityId(
                tenantId.getId(), customerId.getId(), entityId, pageLink);

        return new TextPageData<>(entityViews, pageLink);
    }

    @Override
    public void unassignCustomerEntityViews(TenantId tenantId, CustomerId customerId) {
        log.trace("Executing unassignCustomerEntityViews, tenantId [{}], customerId [{}]", tenantId, customerId);
        validateId(tenantId, INCORRECT_TENANT_ID + tenantId);
        validateId(customerId, INCORRECT_CUSTOMER_ID + customerId);
        new CustomerEntityViewsUnAssigner(tenantId).removeEntities(customerId);
    }

    private DataValidator<EntityView> entityViewValidator =
            new DataValidator<EntityView>() {

                @Override
                protected void validateCreate(EntityView entityView) {
                    entityViewDao.findEntityViewByTenantIdAndName(entityView.getTenantId().getId(), entityView.getName())
                            .ifPresent( e -> {
                                throw new DataValidationException("Entity view with such name already exists!");
                            });
                }

                @Override
                protected void validateUpdate(EntityView entityView) {
                    entityViewDao.findEntityViewByTenantIdAndName(entityView.getTenantId().getId(), entityView.getName())
                            .ifPresent( e -> {
                                if (!e.getUuidId().equals(entityView.getUuidId())) {
                                    throw new DataValidationException("Entity view with such name already exists!");
                                }
                            });
                }

                @Override
                protected void validateDataImpl(EntityView entityView) {
                    if (StringUtils.isEmpty(String.join("", entityView.getKeys()))) {
                        throw new DataValidationException("Entity view type should be specified!");
                    }
                    if (StringUtils.isEmpty(entityView.getName())) {
                        throw new DataValidationException("Entity view name should be specified!");
                    }
                    if (entityView.getTenantId() == null) {
                        throw new DataValidationException("Entity view should be assigned to tenant!");
                    } else {
                        Tenant tenant = tenantDao.findById(entityView.getTenantId().getId());
                        if (tenant == null) {
                            throw new DataValidationException("Entity view is referencing to non-existent tenant!");
                        }
                    }
                    if (entityView.getCustomerId() == null) {
                        entityView.setCustomerId(new CustomerId(NULL_UUID));
                    } else if (!entityView.getCustomerId().getId().equals(NULL_UUID)) {
                        Customer customer = customerDao.findById(entityView.getCustomerId().getId());
                        if (customer == null) {
                            throw new DataValidationException("Can't assign entity view to non-existent customer!");
                        }
                        if (!customer.getTenantId().getId().equals(entityView.getTenantId().getId())) {
                            throw new DataValidationException("Can't assign entity view to customer from different tenant!");
                        }
                    }
                }
            };

    private PaginatedRemover<TenantId, EntityView> tenantEntityViewRemover =
            new PaginatedRemover<TenantId, EntityView>() {

                @Override
                protected List<EntityView> findEntities(TenantId id, TextPageLink pageLink) {
                    return entityViewDao.findEntityViewByTenantId(id.getId(), pageLink);
                }

                @Override
                protected void removeEntity(EntityView entity) {
                    deleteEntityView(new EntityViewId(entity.getUuidId()));
                }
            };

    private class CustomerEntityViewsUnAssigner extends PaginatedRemover<CustomerId, EntityView> {

        private TenantId tenantId;

        CustomerEntityViewsUnAssigner(TenantId tenantId) {
            this.tenantId = tenantId;
        }

        @Override
        protected List<EntityView> findEntities(CustomerId id, TextPageLink pageLink) {
            return entityViewDao.findEntityViewsByTenantIdAndCustomerId(tenantId.getId(), id.getId(), pageLink);
        }

        @Override
        protected void removeEntity(EntityView entity) {
            unassignEntityViewFromCustomer(new EntityViewId(entity.getUuidId()));
        }

    }
}
