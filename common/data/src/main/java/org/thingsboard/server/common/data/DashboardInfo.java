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
package org.thingsboard.server.common.data;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;
import org.thingsboard.server.common.data.edge.Edge;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.DashboardId;
import org.thingsboard.server.common.data.id.EdgeId;
import org.thingsboard.server.common.data.id.TenantId;

import java.util.*;

public class DashboardInfo extends SearchTextBased<DashboardId> implements HasName, HasTenantId {

    private TenantId tenantId;
    private String title;
    private Set<ShortCustomerInfo> assignedCustomers;

    @Getter @Setter
    private Set<ShortEdgeInfo> assignedEdges;

    public DashboardInfo() {
        super();
    }

    public DashboardInfo(DashboardId id) {
        super(id);
    }

    public DashboardInfo(DashboardInfo dashboardInfo) {
        super(dashboardInfo);
        this.tenantId = dashboardInfo.getTenantId();
        this.title = dashboardInfo.getTitle();
        this.assignedCustomers = dashboardInfo.getAssignedCustomers();
    }

    public TenantId getTenantId() {
        return tenantId;
    }

    public void setTenantId(TenantId tenantId) {
        this.tenantId = tenantId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Set<ShortCustomerInfo> getAssignedCustomers() {
        return assignedCustomers;
    }

    public void setAssignedCustomers(Set<ShortCustomerInfo> assignedCustomers) {
        this.assignedCustomers = assignedCustomers;
    }

    public boolean isAssignedToCustomer(CustomerId customerId) {
        return this.assignedCustomers != null && this.assignedCustomers.contains(new ShortCustomerInfo(customerId, null, false));
    }

    public ShortCustomerInfo getAssignedCustomerInfo(CustomerId customerId) {
        if (this.assignedCustomers != null) {
            for (ShortCustomerInfo customerInfo : this.assignedCustomers) {
                if (customerInfo.getCustomerId().equals(customerId)) {
                    return customerInfo;
                }
            }
        }
        return null;
    }

    public boolean addAssignedCustomer(Customer customer) {
        ShortCustomerInfo customerInfo = customer.toShortCustomerInfo();
        if (this.assignedCustomers != null && this.assignedCustomers.contains(customerInfo)) {
            return false;
        } else {
            if (this.assignedCustomers == null) {
                this.assignedCustomers = new HashSet<>();
            }
            this.assignedCustomers.add(customerInfo);
            return true;
        }
    }

    public boolean updateAssignedCustomer(Customer customer) {
        ShortCustomerInfo customerInfo = customer.toShortCustomerInfo();
        if (this.assignedCustomers != null && this.assignedCustomers.contains(customerInfo)) {
            this.assignedCustomers.remove(customerInfo);
            this.assignedCustomers.add(customerInfo);
            return true;
        } else {
            return false;
        }
    }

    public boolean removeAssignedCustomer(Customer customer) {
        ShortCustomerInfo customerInfo = customer.toShortCustomerInfo();
        if (this.assignedCustomers != null && this.assignedCustomers.contains(customerInfo)) {
            this.assignedCustomers.remove(customerInfo);
            return true;
        } else {
            return false;
        }
    }

    public boolean isAssignedToEdge(EdgeId edgeId) {
        return this.assignedEdges != null && this.assignedEdges.contains(new ShortEdgeInfo(edgeId, null, null));
    }

    public ShortEdgeInfo getAssignedEdgeInfo(EdgeId edgeId) {
        if (this.assignedEdges != null) {
            for (ShortEdgeInfo edgeInfo : this.assignedEdges) {
                if (edgeInfo.getEdgeId().equals(edgeId)) {
                    return edgeInfo;
                }
            }
        }
        return null;
    }

    public boolean addAssignedEdge(Edge edge) {
        ShortEdgeInfo edgeInfo = edge.toShortEdgeInfo();
        if (this.assignedEdges != null && this.assignedEdges.contains(edgeInfo)) {
            return false;
        } else {
            if (this.assignedEdges == null) {
                this.assignedEdges = new HashSet<>();
            }
            this.assignedEdges.add(edgeInfo);
            return true;
        }
    }

    public boolean updateAssignedEdge(Edge edge) {
        ShortEdgeInfo edgeInfo = edge.toShortEdgeInfo();
        if (this.assignedEdges != null && this.assignedEdges.contains(edgeInfo)) {
            this.assignedEdges.remove(edgeInfo);
            this.assignedEdges.add(edgeInfo);
            return true;
        } else {
            return false;
        }
    }

    public boolean removeAssignedEdge(Edge edge) {
        ShortEdgeInfo edgeInfo = edge.toShortEdgeInfo();
        if (this.assignedEdges != null && this.assignedEdges.contains(edgeInfo)) {
            this.assignedEdges.remove(edgeInfo);
            return true;
        } else {
            return false;
        }
    }

    @Override
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public String getName() {
        return title;
    }

    @Override
    public String getSearchText() {
        return getTitle();
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((tenantId == null) ? 0 : tenantId.hashCode());
        result = prime * result + ((title == null) ? 0 : title.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!super.equals(obj))
            return false;
        if (getClass() != obj.getClass())
            return false;
        DashboardInfo other = (DashboardInfo) obj;
        if (tenantId == null) {
            if (other.tenantId != null)
                return false;
        } else if (!tenantId.equals(other.tenantId))
            return false;
        if (title == null) {
            if (other.title != null)
                return false;
        } else if (!title.equals(other.title))
            return false;
        return true;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("DashboardInfo [tenantId=");
        builder.append(tenantId);
        builder.append(", title=");
        builder.append(title);
        builder.append("]");
        return builder.toString();
    }

}
