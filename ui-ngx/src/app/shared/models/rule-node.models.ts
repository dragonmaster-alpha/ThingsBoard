///
/// Copyright © 2016-2019 The Thingsboard Authors
///
/// Licensed under the Apache License, Version 2.0 (the "License");
/// you may not use this file except in compliance with the License.
/// You may obtain a copy of the License at
///
///     http://www.apache.org/licenses/LICENSE-2.0
///
/// Unless required by applicable law or agreed to in writing, software
/// distributed under the License is distributed on an "AS IS" BASIS,
/// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
/// See the License for the specific language governing permissions and
/// limitations under the License.
///

import {BaseData} from '@shared/models/base-data';
import {AssetId} from '@shared/models/id/asset-id';
import {TenantId} from '@shared/models/id/tenant-id';
import {CustomerId} from '@shared/models/id/customer-id';
import {RuleChainId} from '@shared/models/id/rule-chain-id';
import {RuleNodeId} from '@shared/models/id/rule-node-id';

export interface RuleNodeConfiguration {
  todo: Array<any>;
  // TODO:
}

export interface RuleNode extends BaseData<RuleNodeId> {
  ruleChainId: RuleChainId;
  type: string;
  name: string;
  debugMode: boolean;
  configuration: RuleNodeConfiguration;
  additionalInfo?: any;
}
