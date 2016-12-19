/*
 * Copyright (c) 2008-2016, Hazelcast, Inc. All Rights Reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.hazelcast.quorum.queue;

import com.hazelcast.instance.HazelcastInstanceFactory;
import com.hazelcast.quorum.QuorumException;
import com.hazelcast.quorum.QuorumType;
import com.hazelcast.test.HazelcastSerialClassRunner;
import com.hazelcast.test.annotation.QuickTest;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import java.io.IOException;

import static com.hazelcast.test.HazelcastTestSupport.assertOpenEventually;

@RunWith(HazelcastSerialClassRunner.class)
@Category(QuickTest.class)
public class QueueReadWriteQuorumTest extends AbstractQueueQuorumTest {
    @BeforeClass
    public static void initialize() throws InterruptedException {
        initializeFiveMemberCluster(QuorumType.READ_WRITE, 3);
        addQueueData(q4);
        cluster.splitFiveMembersThreeAndTwo();
    }

    @AfterClass
    public static void killAllHazelcastInstances() throws IOException {
        HazelcastInstanceFactory.terminateAll();
    }

    @Test(expected = QuorumException.class)
    public void testPutOperationThrowsExceptionWhenQuorumSizeNotMet() throws Exception {
        q4.put("foo");
    }

    @Test(expected = QuorumException.class)
    public void testOfferOperationThrowsExceptionWhenQuorumSizeNotMet() throws Exception {
        q4.offer("foo");
    }

    @Test(expected = QuorumException.class)
    public void testAddOperationThrowsExceptionWhenQuorumSizeNotMet() throws Exception {
        q4.add("foo");
    }

    @Test(expected = QuorumException.class)
    public void testTakeOperationThrowsExceptionWhenQuorumSizeNotMet() throws Exception {
        q4.take();
    }

    @Test(expected = QuorumException.class)
    public void testRemoveOperationThrowsExceptionWhenQuorumSizeNotMet() throws Exception {
        q4.remove();
    }

    @Test(expected = QuorumException.class)
    public void testPollOperationThrowsExceptionWhenQuorumSizeNotMet() throws Exception {
        q4.poll();
    }

    @Test(expected = QuorumException.class)
    public void testElementOperationThrowsExceptionWhenQuorumSizeNotMet() throws Exception {
        q4.element();
    }

    @Test(expected = QuorumException.class)
    public void testPeekOperationThrowsExceptionWhenQuorumSizeNotMet() throws Exception {
        q4.peek();
    }

    @Test
    public void testGetLocalQueueStatsOperationSuccessfulWhenQuorumSizeNotMet() throws Exception {
        q4.getLocalQueueStats();
    }
}