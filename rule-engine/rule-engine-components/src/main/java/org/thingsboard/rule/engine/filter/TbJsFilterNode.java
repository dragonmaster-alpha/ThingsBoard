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
package org.thingsboard.rule.engine.filter;

import lombok.extern.slf4j.Slf4j;
import org.thingsboard.rule.engine.TbNodeUtils;
import org.thingsboard.rule.engine.api.*;
import org.thingsboard.rule.engine.js.NashornJsEngine;
import org.thingsboard.server.common.msg.TbMsg;

import javax.script.Bindings;

import static org.thingsboard.rule.engine.DonAsynchron.withCallback;

@Slf4j
@FilterNode(name = "Filter Node", relationTypes = {"True", "False", "Failure"})
public class TbJsFilterNode implements TbNode {

    private TbJsFilterNodeConfiguration config;
    private NashornJsEngine jsEngine;

    @Override
    public void init(TbNodeConfiguration configuration, TbNodeState state) throws TbNodeException {
        this.config = TbNodeUtils.convert(configuration, TbJsFilterNodeConfiguration.class);
        this.jsEngine = new NashornJsEngine(config.getJsScript());
    }

    @Override
    public void onMsg(TbContext ctx, TbMsg msg) {
        ListeningExecutor jsExecutor = ctx.getJsExecutor();
        withCallback(jsExecutor.executeAsync(() -> jsEngine.executeFilter(toBindings(msg))),
                filterResult -> ctx.tellNext(msg, Boolean.toString(filterResult)),
                t -> ctx.tellError(msg, t));
    }

    private Bindings toBindings(TbMsg msg) {
        return NashornJsEngine.bindMsg(msg);
    }

    @Override
    public void destroy() {
        if (jsEngine != null) {
            jsEngine.destroy();
        }
    }
}
