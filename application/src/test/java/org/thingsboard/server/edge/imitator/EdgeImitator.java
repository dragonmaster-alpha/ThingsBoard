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
package org.thingsboard.server.edge.imitator;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.protobuf.AbstractMessage;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.thingsboard.edge.rpc.EdgeGrpcClient;
import org.thingsboard.edge.rpc.EdgeRpcClient;
import org.thingsboard.server.gen.edge.AlarmUpdateMsg;
import org.thingsboard.server.gen.edge.AssetUpdateMsg;
import org.thingsboard.server.gen.edge.DashboardUpdateMsg;
import org.thingsboard.server.gen.edge.DeviceUpdateMsg;
import org.thingsboard.server.gen.edge.DownlinkMsg;
import org.thingsboard.server.gen.edge.DownlinkResponseMsg;
import org.thingsboard.server.gen.edge.EdgeConfiguration;
import org.thingsboard.server.gen.edge.EntityDataProto;
import org.thingsboard.server.gen.edge.RelationUpdateMsg;
import org.thingsboard.server.gen.edge.RuleChainUpdateMsg;
import org.thingsboard.server.gen.edge.UplinkMsg;
import org.thingsboard.server.gen.edge.UplinkResponseMsg;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@Slf4j
public class EdgeImitator {

    private String routingKey;
    private String routingSecret;

    private EdgeRpcClient edgeRpcClient;

    private CountDownLatch messagesLatch;
    private CountDownLatch responsesLatch;

    @Getter
    private EdgeConfiguration configuration;
    @Getter
    private List<AbstractMessage> downlinkMsgs;

    public EdgeImitator(String host, int port, String routingKey, String routingSecret) throws NoSuchFieldException, IllegalAccessException {
        edgeRpcClient = new EdgeGrpcClient();
        messagesLatch = new CountDownLatch(0);
        responsesLatch = new CountDownLatch(0);
        downlinkMsgs = new ArrayList<>();
        this.routingKey = routingKey;
        this.routingSecret = routingSecret;
        setEdgeCredentials("rpcHost", host);
        setEdgeCredentials("rpcPort", port);
        setEdgeCredentials("keepAliveTimeSec", 300);
    }

    private void setEdgeCredentials(String fieldName, Object value) throws NoSuchFieldException, IllegalAccessException {
        Field fieldToSet = edgeRpcClient.getClass().getDeclaredField(fieldName);
        fieldToSet.setAccessible(true);
        fieldToSet.set(edgeRpcClient, value);
        fieldToSet.setAccessible(false);
    }

    public void connect() {
        edgeRpcClient.connect(routingKey, routingSecret,
                this::onUplinkResponse,
                this::onEdgeUpdate,
                this::onDownlink,
                this::onClose);

        edgeRpcClient.sendSyncRequestMsg();
    }

    public void disconnect() throws InterruptedException {
        edgeRpcClient.disconnect(false);
    }

    public void sendUplinkMsg(UplinkMsg uplinkMsg) {
        edgeRpcClient.sendUplinkMsg(uplinkMsg);
    }

    private void onUplinkResponse(UplinkResponseMsg msg) {
        log.info("onUplinkResponse: {}", msg);
        responsesLatch.countDown();
    }

    private void onEdgeUpdate(EdgeConfiguration edgeConfiguration) {
        this.configuration = edgeConfiguration;
    }

    private void onDownlink(DownlinkMsg downlinkMsg) {
        ListenableFuture<List<Void>> future = processDownlinkMsg(downlinkMsg);
        Futures.addCallback(future, new FutureCallback<List<Void>>() {
            @Override
            public void onSuccess(@Nullable List<Void> result) {
                DownlinkResponseMsg downlinkResponseMsg = DownlinkResponseMsg.newBuilder().setSuccess(true).build();
                edgeRpcClient.sendDownlinkResponseMsg(downlinkResponseMsg);
            }

            @Override
            public void onFailure(Throwable t) {
                DownlinkResponseMsg downlinkResponseMsg = DownlinkResponseMsg.newBuilder().setSuccess(false).setErrorMsg(t.getMessage()).build();
                edgeRpcClient.sendDownlinkResponseMsg(downlinkResponseMsg);
            }
        }, MoreExecutors.directExecutor());
    }

    private void onClose(Exception e) {
        log.info("onClose: {}", e.getMessage());
    }

    private ListenableFuture<List<Void>> processDownlinkMsg(DownlinkMsg downlinkMsg) {
        List<ListenableFuture<Void>> result = new ArrayList<>();
        if (downlinkMsg.getDeviceUpdateMsgList() != null && !downlinkMsg.getDeviceUpdateMsgList().isEmpty()) {
            for (DeviceUpdateMsg deviceUpdateMsg: downlinkMsg.getDeviceUpdateMsgList()) {
                saveDownlinkMsg(deviceUpdateMsg);
            }
        }
        if (downlinkMsg.getAssetUpdateMsgList() != null && !downlinkMsg.getAssetUpdateMsgList().isEmpty()) {
            for (AssetUpdateMsg assetUpdateMsg: downlinkMsg.getAssetUpdateMsgList()) {
                saveDownlinkMsg(assetUpdateMsg);
            }
        }
        if (downlinkMsg.getRuleChainUpdateMsgList() != null && !downlinkMsg.getRuleChainUpdateMsgList().isEmpty()) {
            for (RuleChainUpdateMsg ruleChainUpdateMsg: downlinkMsg.getRuleChainUpdateMsgList()) {
                saveDownlinkMsg(ruleChainUpdateMsg);
            }
        }
        if (downlinkMsg.getDashboardUpdateMsgList() != null && !downlinkMsg.getDashboardUpdateMsgList().isEmpty()) {
            for (DashboardUpdateMsg dashboardUpdateMsg: downlinkMsg.getDashboardUpdateMsgList()) {
                saveDownlinkMsg(dashboardUpdateMsg);
            }
        }
        if (downlinkMsg.getRelationUpdateMsgList() != null && !downlinkMsg.getRelationUpdateMsgList().isEmpty()) {
            for (RelationUpdateMsg relationUpdateMsg: downlinkMsg.getRelationUpdateMsgList()) {
                saveDownlinkMsg(relationUpdateMsg);
            }
        }
        if (downlinkMsg.getAlarmUpdateMsgList() != null && !downlinkMsg.getAlarmUpdateMsgList().isEmpty()) {
            for (AlarmUpdateMsg alarmUpdateMsg: downlinkMsg.getAlarmUpdateMsgList()) {
                saveDownlinkMsg(alarmUpdateMsg);
            }
        }
        if (downlinkMsg.getEntityDataList() != null && !downlinkMsg.getEntityDataList().isEmpty()) {
            for (EntityDataProto entityData: downlinkMsg.getEntityDataList()) {
                saveDownlinkMsg(entityData);
            }
        }
        return Futures.allAsList(result);
    }

    private ListenableFuture<Void> saveDownlinkMsg(AbstractMessage message) {
        downlinkMsgs.add(message);
        messagesLatch.countDown();
        return Futures.immediateFuture(null);
    }

    public void waitForMessages() throws InterruptedException {
        messagesLatch.await(5, TimeUnit.SECONDS);
    }

    public void expectMessageAmount(int messageAmount) {
        messagesLatch = new CountDownLatch(messageAmount);
    }

    public void waitForResponses() throws InterruptedException { responsesLatch.await(5, TimeUnit.SECONDS); }

    public void expectResponsesAmount(int messageAmount) {
        responsesLatch = new CountDownLatch(messageAmount);
    }

    public <T> Optional<T> findMessageByType(Class<T> tClass) {
        return (Optional<T>) downlinkMsgs.stream().filter(downlinkMsg -> downlinkMsg.getClass().isAssignableFrom(tClass)).findAny();
    }

    public AbstractMessage getLatestMessage() {
        return downlinkMsgs.get(downlinkMsgs.size() - 1);
    }

}
