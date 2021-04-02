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
package org.thingsboard.server.common.data.device.data;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.thingsboard.server.common.data.DeviceTransportType;
import org.thingsboard.server.common.data.transport.snmp.SnmpProtocolVersion;

@Data
public class SnmpDeviceTransportConfiguration implements DeviceTransportConfiguration {
    private String address;
    private int port;
    private SnmpProtocolVersion protocolVersion;
    private String securityName;
    private String authenticationPassphrase; // for SNMP v3
    private String privacyPassphrase; // for SNMP v3

    @Override
    public DeviceTransportType getType() {
        return DeviceTransportType.SNMP;
    }

    @Override
    public void validate() {
        if (!isValid()) {
            throw new IllegalArgumentException("Transport configuration is not valid");
        }
    }

    @JsonIgnore
    private boolean isValid() {
        return StringUtils.isNotBlank(address) && port > 0 &&
                StringUtils.isNotBlank(securityName) && protocolVersion != null;
    }
}
