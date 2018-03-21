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

package org.thingsboard.server.dao.rule;

import com.google.common.util.concurrent.ListenableFuture;
import org.thingsboard.server.common.data.id.RuleChainId;
import org.thingsboard.server.common.data.id.RuleNodeId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.page.TextPageData;
import org.thingsboard.server.common.data.page.TextPageLink;
import org.thingsboard.server.common.data.relation.EntityRelation;
import org.thingsboard.server.common.data.rule.RuleChain;
import org.thingsboard.server.common.data.rule.RuleChainMetaData;
import org.thingsboard.server.common.data.rule.RuleNode;

import java.util.List;

/**
 * Created by igor on 3/12/18.
 */
public interface RuleChainService {

    RuleChain saveRuleChain(RuleChain ruleChain);

    RuleChainMetaData saveRuleChainMetaData(RuleChainMetaData ruleChainMetaData);

    RuleChainMetaData loadRuleChainMetaData(RuleChainId ruleChainId);

    RuleChain findRuleChainById(RuleChainId ruleChainId);

    RuleNode findRuleNodeById(RuleNodeId ruleNodeId);

    ListenableFuture<RuleChain> findRuleChainByIdAsync(RuleChainId ruleChainId);

    RuleChain getRootTenantRuleChain(TenantId tenantId);

    List<RuleNode> getRuleChainNodes(RuleChainId ruleChainId);

    List<EntityRelation> getRuleNodeRelations(RuleNodeId ruleNodeId);

    TextPageData<RuleChain> findSystemRuleChains(TextPageLink pageLink);

    TextPageData<RuleChain> findTenantRuleChains(TenantId tenantId, TextPageLink pageLink);

    TextPageData<RuleChain> findAllTenantRuleChainsByTenantIdAndPageLink(TenantId tenantId, TextPageLink pageLink);

    void deleteRuleChainById(RuleChainId ruleChainId);

    void deleteRuleChainsByTenantId(TenantId tenantId);

}
