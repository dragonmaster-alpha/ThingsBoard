--
-- Copyright © 2016-2017 The Thingsboard Authors
--
-- Licensed under the Apache License, Version 2.0 (the "License");
-- you may not use this file except in compliance with the License.
-- You may obtain a copy of the License at
--
--     http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing, software
-- distributed under the License is distributed on an "AS IS" BASIS,
-- WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
-- See the License for the specific language governing permissions and
-- limitations under the License.
--

CREATE TABLE IF NOT EXISTS audit_log (
    id varchar(31) NOT NULL CONSTRAINT audit_log_pkey PRIMARY KEY,
    tenant_id varchar(31),
    customer_id varchar(31),
    entity_id varchar(31),
    entity_type varchar(255),
    entity_name varchar(255),
    user_id varchar(31),
    user_name varchar(255),
    action_type varchar(255),
    action_data varchar(1000000),
    action_status varchar(255),
    action_failure_details varchar(1000000)
);

