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
package org.thingsboard.server.dao.model.sql;

import com.datastax.driver.core.utils.UUIDs;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Transient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.thingsboard.server.common.data.AdminSettings;
import org.thingsboard.server.common.data.id.AdminSettingsId;
import org.thingsboard.server.dao.model.BaseEntity;

import java.io.IOException;
import java.util.UUID;

import static org.thingsboard.server.dao.model.ModelConstants.*;

@Data
@Slf4j
@Entity
@Table(name = ADMIN_SETTINGS_COLUMN_FAMILY_NAME)
public final class AdminSettingsEntity implements BaseEntity<AdminSettings> {

    @Transient
    private static final long serialVersionUID = 842759712850362147L;

    @Id
    @Column(name = ID_PROPERTY)
    private UUID id;
    
    @Column(name = ADMIN_SETTINGS_KEY_PROPERTY)
    private String key;

    @Column(name = ADMIN_SETTINGS_JSON_VALUE_PROPERTY)
    private String jsonValue;

    public AdminSettingsEntity() {
        super();
    }

    public AdminSettingsEntity(AdminSettings adminSettings) {
        if (adminSettings.getId() != null) {
            this.id = adminSettings.getId().getId();
        }
        this.key = adminSettings.getKey();
        if (jsonValue != null) {
            this.jsonValue = adminSettings.getJsonValue().toString();
        }
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public void setId(UUID id) {
        this.id = id;
    }
    
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((id == null) ? 0 : id.hashCode());
        result = prime * result + ((jsonValue == null) ? 0 : jsonValue.hashCode());
        result = prime * result + ((key == null) ? 0 : key.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        AdminSettingsEntity other = (AdminSettingsEntity) obj;
        if (id == null) {
            if (other.id != null)
                return false;
        } else if (!id.equals(other.id))
            return false;
        if (jsonValue == null) {
            if (other.jsonValue != null)
                return false;
        } else if (!jsonValue.equals(other.jsonValue))
            return false;
        if (key == null) {
            if (other.key != null)
                return false;
        } else if (!key.equals(other.key))
            return false;
        return true;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("AdminSettingsEntity [id=");
        builder.append(id);
        builder.append(", key=");
        builder.append(key);
        builder.append(", jsonValue=");
        builder.append(jsonValue);
        builder.append("]");
        return builder.toString();
    }

    @Override
    public AdminSettings toData() {
        AdminSettings adminSettings = new AdminSettings(new AdminSettingsId(id));
        adminSettings.setCreatedTime(UUIDs.unixTimestamp(id));
        adminSettings.setKey(key);
        if (jsonValue != null) {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode jsonNode = null;
            try {
                jsonNode = mapper.readTree(jsonValue);
                adminSettings.setJsonValue(jsonNode);
            } catch (IOException e) {
               log.error(e.getMessage(), e);
            }
        }
        return adminSettings;
    }

}