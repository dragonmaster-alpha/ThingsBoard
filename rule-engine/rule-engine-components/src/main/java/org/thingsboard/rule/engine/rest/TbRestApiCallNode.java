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
package org.thingsboard.rule.engine.rest;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.handler.ssl.SslContextBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.Netty4ClientHttpRequestFactory;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.util.concurrent.ListenableFutureCallback;
import org.springframework.web.client.*;
import org.thingsboard.rule.engine.TbNodeUtils;
import org.thingsboard.rule.engine.api.*;
import org.thingsboard.server.common.data.plugin.ComponentType;
import org.thingsboard.server.common.msg.TbMsg;
import org.thingsboard.server.common.msg.TbMsgMetaData;

import javax.net.ssl.SSLException;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@Slf4j
@RuleNode(
        type = ComponentType.ACTION,
        name = "rest api call",
        configClazz = TbRestApiCallNodeConfiguration.class,
        nodeDescription = "Invoke REST API calls to external REST server",
        nodeDetails = "Expects messages with any message type. Will invoke REST API call to external REST server.",
        uiResources = {"static/rulenode/rulenode-core-config.js"},
        configDirective = "tbActionNodeRestApiCallConfig"
)
public class TbRestApiCallNode implements TbNode {

    private static final String VARIABLE_TEMPLATE = "${%s}";
    private static final String STATUS = "status";
    private static final String STATUS_CODE = "statusCode";
    private static final String STATUS_REASON = "statusReason";
    private static final String ERROR = "error";
    private static final String ERROR_BODY = "error_body";

    private TbRestApiCallNodeConfiguration config;

    private EventLoopGroup eventLoopGroup;
    private AsyncRestTemplate httpClient;

    @Override
    public void init(TbContext ctx, TbNodeConfiguration configuration) throws TbNodeException {
        try {
            this.config = TbNodeUtils.convert(configuration, TbRestApiCallNodeConfiguration.class);
            this.eventLoopGroup = new NioEventLoopGroup();
            Netty4ClientHttpRequestFactory nettyFactory = new Netty4ClientHttpRequestFactory(this.eventLoopGroup);
            nettyFactory.setSslContext(SslContextBuilder.forClient().build());
            httpClient = new AsyncRestTemplate(nettyFactory);
        } catch (SSLException e) {
            throw new TbNodeException(e);
        }
    }

    @Override
    public void onMsg(TbContext ctx, TbMsg msg) throws ExecutionException, InterruptedException, TbNodeException {
        String endpointUrl = processPattern(config.getRestEndpointUrlPattern(), msg.getMetaData());
        HttpHeaders headers = prepareHeaders(msg.getMetaData());
        HttpMethod method = HttpMethod.valueOf(config.getRequestMethod());
        HttpEntity<String> entity = new HttpEntity<>(msg.getData(), headers);

        ListenableFuture<ResponseEntity<String>> future =httpClient.exchange(
                endpointUrl, method, entity, String.class);

        future.addCallback(new ListenableFutureCallback<ResponseEntity<String>>() {
            @Override
            public void onFailure(Throwable throwable) {
                TbMsg next = processException(ctx, msg, throwable);
                ctx.tellNext(next, TbRelationTypes.FAILURE);
            }

            @Override
            public void onSuccess(ResponseEntity<String> responseEntity) {
                if (responseEntity.getStatusCode().is2xxSuccessful()) {
                    TbMsg next = processResponse(ctx, msg, responseEntity);
                    ctx.tellNext(next, TbRelationTypes.SUCCESS);
                } else {
                    TbMsg next = processFailureResponse(ctx, msg, responseEntity);
                    ctx.tellNext(next, TbRelationTypes.FAILURE);
                }
            }
        });
    }

    @Override
    public void destroy() {
        if (this.eventLoopGroup != null) {
            this.eventLoopGroup.shutdownGracefully(0, 5, TimeUnit.SECONDS);
        }
    }

    private TbMsg processResponse(TbContext ctx, TbMsg origMsg, ResponseEntity<String> response) {
        TbMsgMetaData metaData = new TbMsgMetaData();
        metaData.putValue(STATUS, response.getStatusCode().name());
        metaData.putValue(STATUS_CODE, response.getStatusCode().value()+"");
        metaData.putValue(STATUS_REASON, response.getStatusCode().getReasonPhrase());
        response.getHeaders().toSingleValueMap().forEach((k,v) -> metaData.putValue(k,v) );
        return ctx.transformMsg(origMsg, origMsg.getType(), origMsg.getOriginator(), metaData, response.getBody());
    }

    private TbMsg processFailureResponse(TbContext ctx, TbMsg origMsg, ResponseEntity<String> response) {
        TbMsgMetaData metaData = origMsg.getMetaData().copy();
        metaData.putValue(STATUS, response.getStatusCode().name());
        metaData.putValue(STATUS_CODE, response.getStatusCode().value()+"");
        metaData.putValue(STATUS_REASON, response.getStatusCode().getReasonPhrase());
        metaData.putValue(ERROR_BODY, response.getBody());
        return ctx.transformMsg(origMsg, origMsg.getType(), origMsg.getOriginator(), metaData, origMsg.getData());
    }

    private TbMsg processException(TbContext ctx, TbMsg origMsg, Throwable e) {
        TbMsgMetaData metaData = origMsg.getMetaData().copy();
        metaData.putValue(ERROR, e.getClass() + ": " + e.getMessage());
        if (e instanceof HttpClientErrorException) {
            HttpClientErrorException httpClientErrorException = (HttpClientErrorException)e;
            metaData.putValue(STATUS, httpClientErrorException.getStatusText());
            metaData.putValue(STATUS_CODE, httpClientErrorException.getRawStatusCode()+"");
            metaData.putValue(ERROR_BODY, httpClientErrorException.getResponseBodyAsString());
        }
        return ctx.transformMsg(origMsg, origMsg.getType(), origMsg.getOriginator(), metaData, origMsg.getData());
    }

    private HttpHeaders prepareHeaders(TbMsgMetaData metaData) {
        HttpHeaders headers = new HttpHeaders();
        config.getHeaders().forEach((k,v) -> {
            headers.add(processPattern(k, metaData), processPattern(v, metaData));
        });
        return headers;
    }

    private String processPattern(String pattern, TbMsgMetaData metaData) {
        String result = new String(pattern);
        for (Map.Entry<String,String> keyVal  : metaData.values().entrySet()) {
            result = processVar(result, keyVal.getKey(), keyVal.getValue());
        }
        return result;
    }

    private String processVar(String pattern, String key, String val) {
        String varPattern = String.format(VARIABLE_TEMPLATE, key);
        return pattern.replace(varPattern, val);
    }
}
