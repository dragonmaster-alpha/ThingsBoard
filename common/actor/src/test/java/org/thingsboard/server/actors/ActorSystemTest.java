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
package org.thingsboard.server.actors;

import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;
import org.thingsboard.server.common.data.id.DeviceId;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@RunWith(MockitoJUnitRunner.class)
public class ActorSystemTest {

    public static final String ROOT_DISPATCHER = "root-dispatcher";
    private static final int _1M = 1024 * 1024;

    private TbActorSystem actorSystem;
    private ExecutorService submitPool;

    @Before
    public void initActorSystem() {
        int cores = Runtime.getRuntime().availableProcessors();
        int parallelism = Math.max(1, cores / 2);
        TbActorSystemSettings settings = new TbActorSystemSettings(5, parallelism, 42);
        actorSystem = new DefaultTbActorSystem(settings);
        submitPool = Executors.newWorkStealingPool(parallelism);
        actorSystem.createDispatcher(ROOT_DISPATCHER, Executors.newWorkStealingPool(parallelism));
    }

    @After
    public void shutdownActorSystem() {
        actorSystem.stop();
        submitPool.shutdownNow();
    }

    @Test
    public void test10actorsAnd1MMessages() throws InterruptedException {
        testActorsAndMessages(10, _1M);
    }

    @Test
    public void test1MActorsAnd10Messages() throws InterruptedException {
        testActorsAndMessages(_1M, 10);
    }

    @Test
    public void test1KActorsAnd1KMessages() throws InterruptedException {
        testActorsAndMessages(1000, 1000);
    }

    @Test
    public void testNoMessagesAfterDestroy() throws InterruptedException {
        ActorTestCtx testCtx1 = getActorTestCtx(1);
        ActorTestCtx testCtx2 = getActorTestCtx(1);

        TbActorId actorId1 = actorSystem.createRootActor(ROOT_DISPATCHER, new SlowInitActor.SlowInitActorCreator(
                new TbActorId(new DeviceId(UUID.randomUUID())), testCtx1));
        TbActorId actorId2 = actorSystem.createRootActor(ROOT_DISPATCHER, new SlowInitActor.SlowInitActorCreator(
                new TbActorId(new DeviceId(UUID.randomUUID())), testCtx2));

        actorSystem.tell(actorId1, new IntTbActorMsg(42));
        actorSystem.tell(actorId2, new IntTbActorMsg(42));
        actorSystem.stop(actorId1);

        Assert.assertTrue(testCtx2.getLatch().await(1, TimeUnit.SECONDS));
        Assert.assertFalse(testCtx1.getLatch().await(2, TimeUnit.SECONDS));
    }

    @Test
    public void testOneActorCreated() throws InterruptedException {
        ActorTestCtx testCtx1 = getActorTestCtx(1);
        ActorTestCtx testCtx2 = getActorTestCtx(1);
        TbActorId actorId = new TbActorId(new DeviceId(UUID.randomUUID()));
        submitPool.submit(() -> actorSystem.createRootActor(ROOT_DISPATCHER, new SlowCreateActor.SlowCreateActorCreator(actorId, testCtx1)));
        submitPool.submit(() -> actorSystem.createRootActor(ROOT_DISPATCHER, new SlowCreateActor.SlowCreateActorCreator(actorId, testCtx2)));

        Thread.sleep(1000);
        actorSystem.tell(actorId, new IntTbActorMsg(42));

        Assert.assertTrue(testCtx1.getLatch().await(3, TimeUnit.SECONDS));
        Assert.assertFalse(testCtx2.getLatch().await(3, TimeUnit.SECONDS));
    }

    @Test
    public void testActorCreatorCalledOnce() throws InterruptedException {
        ActorTestCtx testCtx = getActorTestCtx(1);
        TbActorId actorId = new TbActorId(new DeviceId(UUID.randomUUID()));
        for(int i =0; i < 1000; i++) {
            submitPool.submit(() -> actorSystem.createRootActor(ROOT_DISPATCHER, new SlowCreateActor.SlowCreateActorCreator(actorId, testCtx)));
        }
        Thread.sleep(1000);
        actorSystem.tell(actorId, new IntTbActorMsg(42));

        Assert.assertTrue(testCtx.getLatch().await(1, TimeUnit.SECONDS));
        //One for creation and one for message
        Assert.assertEquals(2, testCtx.getInvocationCount().get());
    }


    public void testActorsAndMessages(int actorsCount, int msgNumber) throws InterruptedException {
        Random random = new Random();
        int[] randomIntegers = new int[msgNumber];
        long sumTmp = 0;
        for (int i = 0; i < msgNumber; i++) {
            int tmp = random.nextInt();
            randomIntegers[i] = tmp;
            sumTmp += tmp;
        }
        long expected = sumTmp;

        List<ActorTestCtx> testCtxes = new ArrayList<>();

        List<TbActorId> actorIds = new ArrayList<>();
        for (int actorIdx = 0; actorIdx < actorsCount; actorIdx++) {
            ActorTestCtx testCtx = getActorTestCtx(msgNumber);

            actorIds.add(actorSystem.createRootActor(ROOT_DISPATCHER, new TestRootActor.TestRootActorCreator(
                    new TbActorId(new DeviceId(UUID.randomUUID())), testCtx)));
            testCtxes.add(testCtx);
        }

        long start = System.nanoTime();

        for (int i = 0; i < msgNumber; i++) {
            int tmp = randomIntegers[i];
            submitPool.execute(() -> actorIds.forEach(actorId -> actorSystem.tell(actorId, new IntTbActorMsg(tmp))));
        }
        log.info("Submitted all messages");

        testCtxes.forEach(ctx -> {
            try {
                Assert.assertTrue(ctx.getLatch().await(1, TimeUnit.MINUTES));
                Assert.assertEquals(expected, ctx.getActual().get());
                Assert.assertEquals(msgNumber, ctx.getInvocationCount().get());
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });

        long duration = System.nanoTime() - start;
        log.info("Time spend: {}ns ({} ms)", duration, TimeUnit.NANOSECONDS.toMillis(duration));
    }

    private ActorTestCtx getActorTestCtx(int i) {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        AtomicLong actual = new AtomicLong();
        AtomicInteger invocations = new AtomicInteger();
        return new ActorTestCtx(countDownLatch, invocations, i, actual);
    }
}
