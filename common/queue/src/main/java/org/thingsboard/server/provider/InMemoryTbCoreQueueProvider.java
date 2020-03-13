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
package org.thingsboard.server.provider;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;
import org.thingsboard.server.TbQueueConsumer;
import org.thingsboard.server.TbQueueCoreSettings;
import org.thingsboard.server.TbQueueProducer;
import org.thingsboard.server.common.TbProtoQueueMsg;
import org.thingsboard.server.gen.transport.TransportProtos.ToCoreMsg;
import org.thingsboard.server.gen.transport.TransportProtos.ToRuleEngineMsg;
import org.thingsboard.server.gen.transport.TransportProtos.ToTransportMsg;
import org.thingsboard.server.gen.transport.TransportProtos.TransportApiRequestMsg;
import org.thingsboard.server.gen.transport.TransportProtos.TransportApiResponseMsg;
import org.thingsboard.server.memory.InMemoryTbQueueConsumer;
import org.thingsboard.server.memory.InMemoryTbQueueProducer;

@Slf4j
@Component
@ConditionalOnExpression("('${service.type:null}'=='monolith' || '${service.type:null}'=='tb-core') && '${queue.type:null}'=='in-memory'")
public class InMemoryTbCoreQueueProvider implements TbCoreQueueProvider {

    private final TbQueueCoreSettings coreSettings;

    public InMemoryTbCoreQueueProvider(TbQueueCoreSettings coreSettings) {
        this.coreSettings = coreSettings;
    }

    @Override
    public TbQueueProducer<TbProtoQueueMsg<ToTransportMsg>> getTransportMsgProducer() {
        InMemoryTbQueueProducer<TbProtoQueueMsg<ToTransportMsg>> producer = new InMemoryTbQueueProducer<>(coreSettings.getTopic());
        return producer;
    }

    @Override
    public TbQueueProducer<TbProtoQueueMsg<ToRuleEngineMsg>> getRuleEngineMsgProducer() {
        InMemoryTbQueueProducer<TbProtoQueueMsg<ToRuleEngineMsg>> producer = new InMemoryTbQueueProducer<>(coreSettings.getTopic());
        return producer;
    }

    @Override
    public TbQueueProducer<TbProtoQueueMsg<ToCoreMsg>> getTbCoreMsgProducer() {
        InMemoryTbQueueProducer<TbProtoQueueMsg<ToCoreMsg>> producer = new InMemoryTbQueueProducer<>(coreSettings.getTopic());
        return producer;
    }

    @Override
    public TbQueueConsumer<TbProtoQueueMsg<ToCoreMsg>> getToCoreMsgConsumer() {
        InMemoryTbQueueConsumer<TbProtoQueueMsg<ToCoreMsg>> consumer = new InMemoryTbQueueConsumer<>(coreSettings.getTopic());
        return consumer;
    }

    @Override
    public TbQueueConsumer<TbProtoQueueMsg<TransportApiRequestMsg>> getTransportApiRequestConsumer() {
        InMemoryTbQueueConsumer<TbProtoQueueMsg<TransportApiRequestMsg>> consumer = new InMemoryTbQueueConsumer<>(coreSettings.getTopic());
        return consumer;
    }

    @Override
    public TbQueueProducer<TbProtoQueueMsg<TransportApiResponseMsg>> getTransportApiResponseProducer() {
        InMemoryTbQueueProducer<TbProtoQueueMsg<TransportApiResponseMsg>> producer = new InMemoryTbQueueProducer<>(coreSettings.getTopic());
        return producer;
    }
}
