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
package org.thingsboard.server.service.edge.rpc.constructor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.thingsboard.server.common.data.Dashboard;
import org.thingsboard.server.dao.dashboard.DashboardService;
import org.thingsboard.server.dao.util.mapping.JacksonUtil;
import org.thingsboard.server.gen.edge.DashboardUpdateMsg;
import org.thingsboard.server.gen.edge.UpdateMsgType;

@Component
@Slf4j
public class DashboardUpdateMsgConstructor {

    @Autowired
    private DashboardService dashboardService;

    public DashboardUpdateMsg constructDashboardUpdatedMsg(UpdateMsgType msgType, Dashboard dashboard) {
        dashboard = dashboardService.findDashboardById(dashboard.getTenantId(), dashboard.getId());
        DashboardUpdateMsg.Builder builder = DashboardUpdateMsg.newBuilder()
                .setMsgType(msgType)
                .setIdMSB(dashboard.getId().getId().getMostSignificantBits())
                .setIdLSB(dashboard.getId().getId().getLeastSignificantBits())
                .setTitle(dashboard.getTitle())
                .setConfiguration(JacksonUtil.toString(dashboard.getConfiguration()));
        return builder.build();
    }

}
