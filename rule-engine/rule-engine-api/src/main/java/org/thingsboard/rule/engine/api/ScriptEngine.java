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
package org.thingsboard.rule.engine.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.util.concurrent.ListenableFuture;
import org.thingsboard.server.common.msg.TbMsg;

import javax.script.ScriptException;
import java.util.List;
import java.util.Set;

public interface ScriptEngine {

    List<TbMsg> executeUpdate(TbMsg msg) throws ScriptException;

    ListenableFuture<List<TbMsg>> executeUpdateAsync(TbMsg msg);

    ListenableFuture<TbMsg> executeGenerateAsync(TbMsg prevMsg);

    boolean executeFilter(TbMsg msg) throws ScriptException;

    ListenableFuture<Boolean> executeFilterAsync(TbMsg msg);

    Set<String> executeSwitch(TbMsg msg) throws ScriptException;

    JsonNode executeJson(TbMsg msg) throws ScriptException;

    ListenableFuture<JsonNode> executeJsonAsync(TbMsg msg) throws ScriptException;

    String executeToString(TbMsg msg) throws ScriptException;

    void destroy();

}
