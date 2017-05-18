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
package org.thingsboard.server.dao;

import org.cassandraunit.dataset.cql.ClassPathCQLDataSet;
import org.junit.ClassRule;
import org.junit.extensions.cpsuite.ClasspathSuite;
import org.junit.extensions.cpsuite.ClasspathSuite.ClassnameFilters;
import org.junit.runner.RunWith;

import java.util.Arrays;

@RunWith(ClasspathSuite.class)
@ClassnameFilters({
        "org.thingsboard.server.dao.service.*Test",
        "org.thingsboard.server.dao.kv.*Test",
        "org.thingsboard.server.dao.plugin.*Test",
        "org.thingsboard.server.dao.rule.*Test",
        "org.thingsboard.server.dao.attributes.*Test",
        "org.thingsboard.server.dao.timeseries.*Test"
})
public class DaoTestSuite {

    @ClassRule
    public static CustomCassandraCQLUnit cassandraUnit =
            new CustomCassandraCQLUnit(
                    Arrays.asList(new ClassPathCQLDataSet("schema.cql", false, false),
                                  new ClassPathCQLDataSet("system-data.cql", false, false),
                                  new ClassPathCQLDataSet("system-test.cql", false, false)),
                    "cassandra-test.yaml", 30000l);

}
