/**
 * Copyright © 2016-2017 The Thingsboard Authors
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
package org.thingsboard.server.dao.customer;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.thingsboard.server.common.data.Customer;
import org.thingsboard.server.common.data.Tenant;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.page.TextPageData;
import org.thingsboard.server.common.data.page.TextPageLink;
import org.thingsboard.server.dao.dashboard.DashboardService;
import org.thingsboard.server.dao.device.DeviceService;
import org.thingsboard.server.dao.exception.DataValidationException;
import org.thingsboard.server.dao.exception.IncorrectParameterException;
import org.thingsboard.server.dao.service.DataValidator;
import org.thingsboard.server.dao.service.PaginatedRemover;
import org.thingsboard.server.dao.service.Validator;
import org.thingsboard.server.dao.tenant.TenantDao;
import org.thingsboard.server.dao.user.UserService;

import java.util.List;
@Service
@Slf4j
public class CustomerServiceImpl implements CustomerService {

    @Autowired
    private CustomerDao customerDao;
    
    @Autowired
    private UserService userService;
    
    @Autowired
    private TenantDao tenantDao;
    
    @Autowired
    private DeviceService deviceService;
    
    @Autowired
    private DashboardService dashboardService;
    
    @Override
    public Customer findCustomerById(CustomerId customerId) {
        log.trace("Executing findCustomerById [{}]", customerId);
        Validator.validateId(customerId, "Incorrect customerId " + customerId);
        return customerDao.findById(customerId.getId());
    }

    @Override
    public Customer saveCustomer(Customer customer) {
        log.trace("Executing saveCustomer [{}]", customer);
        customerValidator.validate(customer);
        return customerDao.save(customer);
    }

    @Override
    public void deleteCustomer(CustomerId customerId) {
        log.trace("Executing deleteCustomer [{}]", customerId);
        Validator.validateId(customerId, "Incorrect tenantId " + customerId);
        Customer customer = findCustomerById(customerId);
        if (customer == null) {
            throw new IncorrectParameterException("Unable to delete non-existent customer.");
        }
        dashboardService.unassignCustomerDashboards(customer.getTenantId(), customerId);
        deviceService.unassignCustomerDevices(customer.getTenantId(), customerId);
        userService.deleteCustomerUsers(customer.getTenantId(), customerId);               
        customerDao.removeById(customerId.getId());
    }

    @Override
    public TextPageData<Customer> findCustomersByTenantId(TenantId tenantId, TextPageLink pageLink) {
        log.trace("Executing findCustomersByTenantId, tenantId [{}], pageLink [{}]", tenantId, pageLink);
        Validator.validateId(tenantId, "Incorrect tenantId " + tenantId);
        Validator.validatePageLink(pageLink, "Incorrect page link " + pageLink);
        List<Customer> customers = customerDao.findCustomersByTenantId(tenantId.getId(), pageLink);
        return new TextPageData<Customer>(customers, pageLink);
    }

    @Override
    public void deleteCustomersByTenantId(TenantId tenantId) {
        log.trace("Executing deleteCustomersByTenantId, tenantId [{}]", tenantId);
        Validator.validateId(tenantId, "Incorrect tenantId " + tenantId);
        customersByTenantRemover.removeEntitites(tenantId);
    }
    
    private DataValidator<Customer> customerValidator =
            new DataValidator<Customer>() {
                @Override
                protected void validateDataImpl(Customer customer) {
                    if (StringUtils.isEmpty(customer.getTitle())) {
                        throw new DataValidationException("Customer title should be specified!");
                    }
                    if (!StringUtils.isEmpty(customer.getEmail())) {
                        validateEmail(customer.getEmail());
                    }
                    if (customer.getTenantId() == null) {
                        throw new DataValidationException("Customer should be assigned to tenant!");
                    } else {
                        Tenant tenant = tenantDao.findById(customer.getTenantId().getId());
                        if (tenant == null) {
                            throw new DataValidationException("Customer is referencing to non-existent tenant!");
                        }
                    }
                }
    };

    private PaginatedRemover<TenantId, Customer> customersByTenantRemover =
            new PaginatedRemover<TenantId, Customer>() {
        
        @Override
        protected List<Customer> findEntities(TenantId id, TextPageLink pageLink) {
            return customerDao.findCustomersByTenantId(id.getId(), pageLink);
        }

        @Override
        protected void removeEntity(Customer entity) {
            deleteCustomer(new CustomerId(entity.getUuidId()));
        }
    };
}
