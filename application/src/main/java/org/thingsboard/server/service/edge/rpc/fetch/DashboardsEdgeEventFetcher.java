/**
 * Copyright © 2016-2021 The Thingsboard Authors
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
package org.thingsboard.server.service.edge.rpc.fetch;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.thingsboard.server.common.data.DashboardInfo;
import org.thingsboard.server.common.data.edge.EdgeEvent;
import org.thingsboard.server.common.data.edge.EdgeEventActionType;
import org.thingsboard.server.common.data.edge.EdgeEventType;
import org.thingsboard.server.common.data.id.EdgeId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.page.PageLink;
import org.thingsboard.server.dao.dashboard.DashboardService;
import org.thingsboard.server.service.edge.rpc.EdgeEventUtils;

import java.util.ArrayList;
import java.util.List;

@AllArgsConstructor
@Slf4j
public class DashboardsEdgeEventFetcher implements EdgeEventFetcher {

    private final DashboardService dashboardService;

    @Override
    public PageLink getPageLink() {
        return new PageLink(DEFAULT_LIMIT);
    }

    @Override
    public PageData<EdgeEvent> fetchEdgeEvents(TenantId tenantId, EdgeId edgeId, PageLink pageLink) {
        log.trace("[{}] start fetching edge events [{}]", tenantId, edgeId);
        PageData<DashboardInfo> pageData = dashboardService.findDashboardsByTenantIdAndEdgeId(tenantId, edgeId, pageLink);
        List<EdgeEvent> result = new ArrayList<>();
        if (!pageData.getData().isEmpty()) {
            for (DashboardInfo dashboardInfo : pageData.getData()) {
                result.add(EdgeEventUtils.constructEdgeEvent(tenantId, edgeId, EdgeEventType.DASHBOARD,
                        EdgeEventActionType.ADDED, dashboardInfo.getId(), null));
            }
        }
        return new PageData<>(result, pageData.getTotalPages(), pageData.getTotalElements(), pageData.hasNext());
    }
}
