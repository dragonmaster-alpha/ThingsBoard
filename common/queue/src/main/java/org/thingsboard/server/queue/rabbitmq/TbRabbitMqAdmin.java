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
package org.thingsboard.server.queue.rabbitmq;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;
import org.thingsboard.server.queue.TbQueueAdmin;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

@Slf4j
@Component
@ConditionalOnExpression("'${queue.type:null}'=='rabbitmq'")
public class TbRabbitMqAdmin implements TbQueueAdmin {

    private final TbRabbitMqSettings rabbitMqSettings;
    private final Channel channel;
    private final Connection connection;

    public TbRabbitMqAdmin(TbRabbitMqSettings rabbitMqSettings) {
        this.rabbitMqSettings = rabbitMqSettings;

        try {
            connection = rabbitMqSettings.getConnectionFactory().newConnection();
        } catch (IOException | TimeoutException e) {
            log.error("Failed to create connection.", e);
            throw new RuntimeException("Failed to create connection.", e);
        }

        try {
            channel = connection.createChannel();
        } catch (IOException e) {
            log.error("Failed to create chanel.", e);
            throw new RuntimeException("Failed to create chanel.", e);
        }
    }

    @Override
    public void createTopicIfNotExists(String topic) {
        try {
            channel.queueDeclare(topic, false, false, false, null);
        } catch (IOException e) {
            log.error("Failed to bind queue: [{}]", topic, e);
        }
    }

    @Override
    public void destroy() {
        if (channel != null) {
            try {
                channel.close();
            } catch (IOException | TimeoutException e) {
                log.error("Failed to close Chanel.", e);
            }
        }
        if (connection != null) {
            try {
                connection.close();
            } catch (IOException e) {
                log.error("Failed to close Connection.", e);
            }
        }
    }
}
