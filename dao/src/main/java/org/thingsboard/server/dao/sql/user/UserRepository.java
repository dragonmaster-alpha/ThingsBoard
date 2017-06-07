/**
 * Copyright © 2016-2017 The Thingsboard Authors
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
package org.thingsboard.server.dao.sql.user;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.thingsboard.server.dao.model.sql.UserEntity;

import java.util.List;
import java.util.UUID;

/**
 * @author Valerii Sosliuk
 */
@ConditionalOnProperty(prefix = "sql", value = "enabled", havingValue = "true", matchIfMissing = false)
public interface UserRepository extends CrudRepository<UserEntity, UUID> {

    UserEntity findByEmail(String email);

    @Query(nativeQuery = true, value = "SELECT * FROM TB_USER WHERE TENANT_ID = :tenantId " +
            "AND CUSTOMER_ID IS NULL AND AUTHORITY = 'TENANT_ADMIN' " +
            "AND ID > :idOffset ORDER BY ID LIMIT :limit")
    List<UserEntity> findTenantAdmins(@Param("limit") int limit,
                                      @Param("tenantId") UUID tenantId,
                                      @Param("idOffset") UUID idOffset);

    @Query(nativeQuery = true, value = "SELECT * FROM TB_USER WHERE TENANT_ID = :tenantId " +
            "AND CUSTOMER_ID = :customerId AND AUTHORITY = 'CUSTOMER_USER' " +
            "AND ID > :idOffset ORDER BY ID LIMIT :limit")
    List<UserEntity> findCustomerUsers(@Param("limit") int limit,
                                       @Param("tenantId") UUID tenantId,
                                       @Param("customerId") UUID customerId,
                                       @Param("idOffset") UUID idOffset);

}