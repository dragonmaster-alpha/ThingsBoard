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
package org.thingsboard.server.transport.lwm2m.server.downlink;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.leshan.core.request.ObserveRequest;
import org.eclipse.leshan.core.request.ReadRequest;
import org.eclipse.leshan.core.response.ObserveResponse;
import org.eclipse.leshan.core.response.ReadResponse;
import org.thingsboard.server.transport.lwm2m.server.uplink.LwM2mUplinkMsgHandler;
import org.thingsboard.server.transport.lwm2m.server.client.LwM2mClient;

import static org.thingsboard.server.transport.lwm2m.server.LwM2mTransportUtil.LOG_LWM2M_INFO;

@Slf4j
public class TbLwM2MObserveCallback extends TbLwM2MTargetedCallback<ObserveRequest, ObserveResponse> {

    public TbLwM2MObserveCallback(LwM2mUplinkMsgHandler handler, LwM2mClient client, String targetId) {
        super(handler, client, targetId);
    }

    @Override
    public void onSuccess(ObserveRequest request, ObserveResponse response) {
        super.onSuccess(request, response);
        handler.onUpdateValueAfterReadResponse(client.getRegistration(), targetId, response, null);
    }
}
