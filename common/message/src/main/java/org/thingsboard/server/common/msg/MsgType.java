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
package org.thingsboard.server.common.msg;

/**
 * Created by ashvayka on 15.03.18.
 */
public enum MsgType {

    /**
     * ADDED/UPDATED/DELETED events for main entities.
     *
     * @See {@link org.thingsboard.server.common.msg.plugin.ComponentLifecycleMsg}
     */
    COMPONENT_LIFE_CYCLE_MSG,

    /**
     * Misc messages from the REST API/SERVICE layer to the new rule engine.
     *
     * @See {@link org.thingsboard.server.common.msg.system.ServiceToRuleEngineMsg}
     */
    SERVICE_TO_RULE_ENGINE_MSG,


    SESSION_TO_DEVICE_ACTOR_MSG,
    DEVICE_ACTOR_TO_SESSION_MSG,


    /**
     * Message that is sent by RuleChainActor to RuleActor with command to process TbMsg.
     */
    RULE_CHAIN_TO_RULE_MSG,

    /**
     * Message that is sent by RuleActor to RuleChainActor with command to process TbMsg by next nodes in chain.
     */
    RULE_TO_RULE_CHAIN_TELL_NEXT_MSG,

    /**
     * Message that is sent by RuleActor implementation to RuleActor itself to log the error.
     */
    RULE_TO_SELF_ERROR_MSG,

    /**
     * Message that is sent by RuleActor implementation to RuleActor itself to process the message.
     */
    RULE_TO_SELF_MSG,

}
