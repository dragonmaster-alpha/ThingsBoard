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
package org.thingsboard.server.transport.lwm2m.server.store;

import org.eclipse.leshan.server.californium.registration.CaliforniumRegistrationStore;
import org.eclipse.leshan.server.californium.registration.InMemoryRegistrationStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;
import org.thingsboard.server.cache.TBRedisCacheConfiguration;
import org.thingsboard.server.queue.util.TbLwM2mTransportComponent;
import org.thingsboard.server.transport.lwm2m.config.LwM2MTransportServerConfig;
import org.thingsboard.server.transport.lwm2m.secure.LwM2mCredentialsSecurityInfoValidator;

import java.util.Optional;

@Component
@TbLwM2mTransportComponent
public class TbLwM2mStoreFactory {

    @Autowired(required = false)
    private Optional<TBRedisCacheConfiguration> redisConfiguration;

    @Autowired
    private LwM2MTransportServerConfig config;

    @Autowired
    private LwM2mCredentialsSecurityInfoValidator validator;

    @Value("${transport.lwm2m.redis.enabled:false}")
    private boolean useRedis;

    @Bean
    private CaliforniumRegistrationStore registrationStore() {
        return isRedis() ?
                new TbLwM2mRedisRegistrationStore(getConnectionFactory()) : new InMemoryRegistrationStore(config.getCleanPeriodInSec());
    }

    @Bean
    private TbMainSecurityStore securityStore() {
        return new TbLwM2mSecurityStore(isRedis() ?
                new TbLwM2mRedisSecurityStore(getConnectionFactory()) : new TbInMemorySecurityStore(), validator);
    }

    @Bean
    private TbLwM2MClientStore clientStore() {
        return new TbDummyLwM2MClientStore();
    }

    @Bean
    private TbLwM2MClientOtaInfoStore otaStore() {
        return isRedis() ? new TbLwM2mRedisClientOtaInfoStore(getConnectionFactory()) : new TbDummyLwM2MClientOtaInfoStore();
    }

    @Bean
    private TbLwM2MDtlsSessionStore sessionStore() {
        return isRedis() ? new TbLwM2MDtlsSessionRedisStore(getConnectionFactory()) : new TbL2M2MDtlsSessionInMemoryStore();
    }

    private RedisConnectionFactory getConnectionFactory() {
        return redisConfiguration.get().redisConnectionFactory();
    }

    private boolean isRedis() {
        return redisConfiguration.isPresent() && useRedis;
    }

}
