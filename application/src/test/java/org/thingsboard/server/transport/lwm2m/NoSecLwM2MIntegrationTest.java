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
package org.thingsboard.server.transport.lwm2m;

import org.junit.Test;
import org.thingsboard.server.common.data.device.credentials.lwm2m.NoSecClientCredentials;

public class NoSecLwM2MIntegrationTest extends AbstractLwM2MIntegrationTest {

    @Test
    public void testConnectAndObserveTelemetry() throws Exception {
        NoSecClientCredentials clientCredentials = new NoSecClientCredentials();
        clientCredentials.setEndpoint(ENDPOINT);
        super.basicTestConnectionObserveTelemetry(SECURITY, clientCredentials, COAP_CONFIG, ENDPOINT);
    }

}
