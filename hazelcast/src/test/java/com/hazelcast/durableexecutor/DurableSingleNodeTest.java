/*
 * Copyright (c) 2008-2016, Hazelcast, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hazelcast.durableexecutor;

import com.hazelcast.core.ExecutionCallback;
import com.hazelcast.core.Member;
import com.hazelcast.executor.ExecutorServiceTestSupport;
import com.hazelcast.test.HazelcastParallelClassRunner;
import com.hazelcast.test.annotation.ParallelTest;
import com.hazelcast.test.annotation.QuickTest;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import java.io.Serializable;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(HazelcastParallelClassRunner.class)
@Category({QuickTest.class, ParallelTest.class})
public class DurableSingleNodeTest extends ExecutorServiceTestSupport {

    private DurableExecutorService executor;

    @Before
    public void setUp() {
        executor = createSingleNodeDurableExecutorService("test", 1);
    }

    @Test
    public void hazelcastInstanceAware_expectInjection() throws Throwable {
        HazelcastInstanceAwareRunnable task = new HazelcastInstanceAwareRunnable();
        try {
            executor.submit(task).get();
        } catch (ExecutionException e) {
            throw e.getCause();
        }
    }

    @Test(expected = NullPointerException.class)
    public void submitNullTask_expectFailure() throws Exception {
        executor.submit((Callable<?>) null);
    }

    @Test
    public void submitBasicTask() throws Exception {
        Callable<String> task = new BasicTestCallable();
        Future future = executor.submit(task);
        assertEquals(future.get(), BasicTestCallable.RESULT);
    }

    @Test
    public void executionCallback_notifiedOnSuccess() throws Exception {
        final Callable<String> task = new BasicTestCallable();
        final CountDownLatch latch = new CountDownLatch(1);
        final ExecutionCallback<String> executionCallback = new ExecutionCallback<String>() {
            public void onResponse(String response) {
                latch.countDown();
            }

            public void onFailure(Throwable t) {
            }
        };
        executor.submit(task).andThen(executionCallback);
        assertOpenEventually(latch);
    }

    @Test
    public void executionCallback_notifiedOnFailure() throws Exception {
        final FailingTestTask task = new FailingTestTask();
        final CountDownLatch latch = new CountDownLatch(1);
        final ExecutionCallback<String> executionCallback = new ExecutionCallback<String>() {
            public void onResponse(String response) {
            }

            public void onFailure(Throwable t) {
                latch.countDown();
            }
        };
        executor.submit(task).andThen(executionCallback);
        assertOpenEventually(latch);
    }

    @Test
    public void isDoneAfterGet() throws Exception {
        Callable<String> task = new BasicTestCallable();
        Future future = executor.submit(task);
        assertEquals(future.get(), BasicTestCallable.RESULT);
        assertTrue(future.isDone());
    }

    @Test
    public void issue129() throws Exception {
        for (int i = 0; i < 1000; i++) {
            Callable<String>
                    task1 = new BasicTestCallable(),
                    task2 = new BasicTestCallable();
            Future<String>
                    future1 = executor.submit(task1),
                    future2 = executor.submit(task2);
            assertEquals(future2.get(), BasicTestCallable.RESULT);
            assertTrue(future2.isDone());
            assertEquals(future1.get(), BasicTestCallable.RESULT);
            assertTrue(future1.isDone());
        }
    }

    @Test
    public void issue292() throws Exception {
        final BlockingQueue<Member> qResponse = new ArrayBlockingQueue<Member>(1);
        executor.submit(new MemberCheck()).andThen(new ExecutionCallback<Member>() {
            public void onResponse(Member response) {
                qResponse.offer(response);
            }

            public void onFailure(Throwable t) {
            }
        });
        assertNotNull(qResponse.poll(10, TimeUnit.SECONDS));
    }

    @Test(timeout = 10000)
    public void taskSubmitsNestedTask() throws Exception {
        Callable<String> task = new NestedExecutorTask();
        executor.submit(task).get();
    }

    @Test
    public void getManyTimesFromSameFuture() throws Exception {
        Callable<String> task = new BasicTestCallable();
        Future<String> future = executor.submit(task);
        for (int i = 0; i < 4; i++) {
            assertEquals(future.get(), BasicTestCallable.RESULT);
            assertTrue(future.isDone());
        }
    }

    /**
     * Shutdown-related method behaviour when the cluster is running
     */
    @Test
    public void shutdownBehaviour() throws Exception {
        // Fresh instance, is not shutting down
        assertFalse(executor.isShutdown());
        assertFalse(executor.isTerminated());
        executor.shutdown();
        assertTrue(executor.isShutdown());
        assertTrue(executor.isTerminated());
        // shutdownNow() should return an empty list and be ignored
        List<Runnable> pending = executor.shutdownNow();
        assertTrue(pending.isEmpty());
        assertTrue(executor.isShutdown());
        assertTrue(executor.isTerminated());
        // awaitTermination() should return immediately false
        try {
            boolean terminated = executor.awaitTermination(60L, TimeUnit.SECONDS);
            assertFalse(terminated);
        } catch (InterruptedException ie) {
            fail("InterruptedException");
        }
        assertTrue(executor.isShutdown());
        assertTrue(executor.isTerminated());
    }

    /**
     * Shutting down the cluster should act as the ExecutorService shutdown
     */
    @Test(expected = RejectedExecutionException.class)
    public void clusterShutdown() throws Exception {
        shutdownNodeFactory();
        Thread.sleep(2000);

        assertNotNull(executor);
        assertTrue(executor.isShutdown());
        assertTrue(executor.isTerminated());

        // New tasks must be rejected
        Callable<String> task = new BasicTestCallable();
        executor.submit(task);
    }

//    @Test
//    public void executorServiceStats() throws InterruptedException, ExecutionException {
//        final int k = 10;
//        LatchRunnable.latch = new CountDownLatch(k);
//        final LatchRunnable r = new LatchRunnable();
//        for (int i = 0; i < k; i++) {
//            executor.execute(r);
//        }
//        assertOpenEventually(LatchRunnable.latch);
//        final Future<Boolean> f = executor.submit(new SleepingTask(10));
//        f.cancel(true);
//        try {
//            f.get();
//        } catch (CancellationException ignored) {
//        }
//
//        assertTrueEventually(new AssertTask() {
//            @Override
//            public void run()
//                    throws Exception {
//                final LocalExecutorStats stats = executor.getLocalExecutorStats();
//                assertEquals(k + 1, stats.getStartedTaskCount());
//                assertEquals(k, stats.getCompletedTaskCount());
//                assertEquals(0, stats.getPendingTaskCount());
//                assertEquals(1, stats.getCancelledTaskCount());
//            }
//        });
//    }

    static class LatchRunnable implements Runnable, Serializable {
        static CountDownLatch latch;

        @Override
        public void run() {
            latch.countDown();
        }
    }
}