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
package org.thingsboard.rule.engine.api;

import org.thingsboard.server.common.msg.TbMsg;
import org.thingsboard.server.common.msg.cluster.ServerAddress;
import org.thingsboard.server.dao.attributes.AttributesService;

import java.util.UUID;

/**
 * Created by ashvayka on 13.01.18.
 */
public interface TbContext {

    void tellNext(TbMsg msg);

    void tellNext(TbMsg msg, String relationType);

    void tellSelf(TbMsg msg, long delayMs);

    void tellOthers(TbMsg msg);

    void tellSibling(TbMsg msg, ServerAddress address);

    void spawn(TbMsg msg);

    void ack(TbMsg msg);

    AttributesService getAttributesService();

}
