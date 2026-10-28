/*|-----------------------------------------------------------------------------
 *|            This source code is provided under the Apache 2.0 license
 *|  and is provided AS IS with no warranty or guarantee of fit for purpose.
 *|                See the project's LICENSE.md for details.
 *|           Copyright (C) 2020-2026 LSEG. All rights reserved.
 *|-----------------------------------------------------------------------------
 */

package com.lseg.ema.example;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.json.JSONObject;

import com.refinitiv.ema.access.EmaFactory;
import com.refinitiv.ema.access.OmmConsumerEvent;
import com.refinitiv.ema.access.OmmState.DataState;
import com.refinitiv.ema.access.OmmState.StatusCode;
import com.refinitiv.ema.access.OmmState.StreamState;

/** Tests bounded admission, overload dropping, and graceful shutdown of the virtual-thread workers. */
class MessageProcessingPipelineTest {

    private static final Duration WAIT_TIMEOUT = Duration.ofSeconds(5);
    private static final OmmConsumerEvent EVENT = new TestConsumerEvent("test\"closure\n");

    /** Verifies constructor guard clauses before worker allocation. */
    @Test
    @SuppressWarnings("resource")
    void rejectsInvalidPipelineConfiguration() {
        assertThrows(IllegalArgumentException.class,
                () -> new MessageProcessingPipeline(0, 10, ignored -> { }));
        assertThrows(IllegalArgumentException.class,
                () -> new MessageProcessingPipeline(1, 0, ignored -> { }));
    }

    /** Verifies that a full queue drops new messages without considering their type. */
    @Test
    void dropsNewestMessagesWithoutTypePriorityWhenQueueIsFull() throws InterruptedException {
        List<String> output = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch firstWorkerOutput = new CountDownLatch(1);
        CountDownLatch releaseWorker = new CountDownLatch(1);

        try (MessageProcessingPipeline pipeline = new MessageProcessingPipeline(1, 3, json -> {
            if (output.isEmpty()) {
                firstWorkerOutput.countDown();
                await(releaseWorker);
            }
            output.add(json);
        })) {
            pipeline.submitUpdate(EmaFactory.createUpdateMsg().name("U1"), EVENT);
            assertTrue(firstWorkerOutput.await(WAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS));

            pipeline.submitUpdate(EmaFactory.createUpdateMsg().name("U2"), EVENT);
            pipeline.submitUpdate(EmaFactory.createUpdateMsg().name("U3"), EVENT);
            pipeline.submitUpdate(EmaFactory.createUpdateMsg().name("U4"), EVENT);
            pipeline.submitUpdate(EmaFactory.createUpdateMsg().name("U5"), EVENT);
            pipeline.submitRefresh(EmaFactory.createRefreshMsg()
                    .name("R1")
                    .state(StreamState.OPEN, DataState.OK, StatusCode.NONE, "open")
                    .complete(true), EVENT);
            releaseWorker.countDown();
        }

        assertEquals(4, output.size());
        assertTrue(output.stream().anyMatch(json -> json.contains("\"itemName\":\"U1\"")));
        assertTrue(output.stream().anyMatch(json -> json.contains("\"itemName\":\"U2\"")));
        assertTrue(output.stream().anyMatch(json -> json.contains("\"itemName\":\"U3\"")));
        assertTrue(output.stream().anyMatch(json -> json.contains("\"itemName\":\"U4\"")));
        assertFalse(output.stream().anyMatch(json -> json.contains("\"itemName\":\"U5\"")));
        assertFalse(output.stream().anyMatch(json -> json.contains("\"type\":\"Refresh\"")));
        assertTrue(output.stream()
                .map(JSONObject::new)
                .allMatch(json -> "test\"closure\n".equals(json.getString("closure"))));
    }

    /** Verifies that close() converts every accepted message before the workers stop. */
    @Test
    void drainsAcceptedMessagesOnClose() {
        List<String> output = Collections.synchronizedList(new ArrayList<>());

        try (MessageProcessingPipeline pipeline = new MessageProcessingPipeline(2, 10, output::add)) {
            for (int index = 1; index <= 5; index++)
                pipeline.submitUpdate(EmaFactory.createUpdateMsg().name("U" + index), EVENT);
        }

        List<Long> sequences = output.stream()
                .map(json -> new JSONObject(json).getLong("sequence"))
                .sorted()
                .toList();
        assertEquals(List.of(1L, 2L, 3L, 4L, 5L), sequences);
    }

    /** Verifies that messages submitted after close() are dropped instead of processed. */
    @Test
    void dropsMessagesSubmittedAfterClose() {
        List<String> output = Collections.synchronizedList(new ArrayList<>());
        MessageProcessingPipeline pipeline = new MessageProcessingPipeline(1, 10, output::add);
        pipeline.close();

        pipeline.submitUpdate(EmaFactory.createUpdateMsg().name("late"), EVENT);

        assertTrue(output.isEmpty());
    }

    /** Waits for the test worker gate while preserving interruption status. */
    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(WAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS))
                throw new IllegalStateException("Timed out waiting to release JSON worker.");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("JSON worker interrupted during test.", exception);
        }
    }
}

