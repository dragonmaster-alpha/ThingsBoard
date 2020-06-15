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
package org.thingsboard.server.dao.sql.query;

import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.UUIDConverter;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.EntityIdFactory;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.query.EntityData;
import org.thingsboard.server.common.data.query.EntityDataPageLink;
import org.thingsboard.server.common.data.query.EntityKey;
import org.thingsboard.server.common.data.query.EntityKeyType;
import org.thingsboard.server.common.data.query.TsValue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class EntityDataAdapter {

    public static PageData<EntityData> createEntityData(EntityDataPageLink pageLink,
                                                        List<EntityKeyMapping> selectionMapping,
                                                        List<Object[]> rows,
                                                        int totalElements) {
        int totalPages = pageLink.getPageSize() > 0 ? (int)Math.ceil((float)totalElements / pageLink.getPageSize()) : 1;
        int startIndex = pageLink.getPageSize() * pageLink.getPage();
        boolean hasNext = pageLink.getPageSize() > 0 && totalElements > startIndex + rows.size();
        List<EntityData> entitiesData = convertListToEntityData(rows, selectionMapping);
        return new PageData<>(entitiesData, totalPages, totalElements, hasNext);
    }

    private static List<EntityData> convertListToEntityData(List<Object[]> result, List<EntityKeyMapping> selectionMapping) {
        return result.stream().map(row -> toEntityData(row, selectionMapping)).collect(Collectors.toList());
    }

    private static EntityData toEntityData(Object[] row, List<EntityKeyMapping> selectionMapping) {
        String id = (String)row[0];
        EntityType entityType = EntityType.valueOf((String)row[1]);
        EntityId entityId = EntityIdFactory.getByTypeAndUuid(entityType, UUIDConverter.fromString(id));
        Map<EntityKeyType, Map<String, TsValue>> latest = new HashMap<>();
        Map<String, TsValue[]> timeseries = new HashMap<>();
        EntityData entityData = new EntityData(entityId, latest, timeseries);
        for (EntityKeyMapping mapping: selectionMapping) {
            EntityKey entityKey = mapping.getEntityKey();
            Object value = row[mapping.getIndex()];
            String strValue;
            long ts;
            if (entityKey.getType().equals(EntityKeyType.ENTITY_FIELD)) {
                strValue = value != null ? value.toString() : null;
                ts = System.currentTimeMillis();
            } else {
                strValue = convertValue(value);
                Object tsObject = row[mapping.getIndex()+1];
                ts = Long.parseLong(tsObject.toString());
            }
            TsValue tsValue = new TsValue(ts, strValue);
            latest.computeIfAbsent(entityKey.getType(), entityKeyType -> new HashMap<>()).put(entityKey.getKey(), tsValue);
        }
        return entityData;
    }

    private static String convertValue(Object value) {
        if (value != null) {
            String strVal = value.toString();
            // check number
            if (strVal.length() > 0) {
                try {
                    int intVal = Integer.parseInt(strVal);
                    return Integer.toString(intVal);
                } catch (NumberFormatException ignored) {
                }
                try {
                    double dblVal = Double.parseDouble(strVal);
                    if (!Double.isInfinite(dblVal)) {
                        return Double.toString(dblVal);
                    }
                } catch (NumberFormatException ignored) {
                }
            }
            return strVal;
        } else {
            return null;
        }
    }

}
