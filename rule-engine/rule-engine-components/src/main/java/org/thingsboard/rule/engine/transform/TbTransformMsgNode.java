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
package org.thingsboard.rule.engine.transform;

import com.google.common.util.concurrent.ListenableFuture;
import org.thingsboard.rule.engine.TbNodeUtils;
import org.thingsboard.rule.engine.api.*;
import org.thingsboard.rule.engine.js.NashornJsEngine;
import org.thingsboard.server.common.data.plugin.ComponentType;
import org.thingsboard.server.common.msg.TbMsg;

import javax.script.Bindings;

@RuleNode(
        type = ComponentType.TRANSFORMATION,
        name = "script",
        configClazz = TbTransformMsgNodeConfiguration.class,
        nodeDescription = "Change Message payload, Metadata or Message type using JavaScript",
        nodeDetails = "JavaScript function receive 3 input parameters.<br/> " +
                "<code>metadata</code> - is a Message metadata.<br/>" +
                "<code>msg</code> - is a Message payload.<br/>" +
                "<code>msgType</code> - is a Message type.<br/>" +
                "Should return the following structure:<br/>" +
                "<code>{ msg: <new payload>, metadata: <new metadata>, msgType: <new msgType> }</code>" +
                "All fields in resulting object are optional and will be taken from original message if not specified.",
        uiResources = {"static/rulenode/rulenode-core-config.js", "static/rulenode/rulenode-core-config.css"},
        configDirective = "tbTransformationNodeScriptConfig")
public class TbTransformMsgNode extends TbAbstractTransformNode {

    private TbTransformMsgNodeConfiguration config;
    private NashornJsEngine jsEngine;

    @Override
    public void init(TbContext ctx, TbNodeConfiguration configuration) throws TbNodeException {
        this.config = TbNodeUtils.convert(configuration, TbTransformMsgNodeConfiguration.class);
        this.jsEngine = new NashornJsEngine(config.getJsScript(), "Transform");
        setConfig(config);
    }

    @Override
    protected ListenableFuture<TbMsg> transform(TbContext ctx, TbMsg msg) {
        return ctx.getJsExecutor().executeAsync(() -> jsEngine.executeUpdate(msg));
    }

    @Override
    public void destroy() {
        if (jsEngine != null) {
            jsEngine.destroy();
        }
    }
}
