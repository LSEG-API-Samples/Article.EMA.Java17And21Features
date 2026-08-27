/*|-----------------------------------------------------------------------------
 *|            This source code is provided under the Apache 2.0 license
 *|  and is provided AS IS with no warranty or guarantee of fit for purpose.
 *|                See the project's LICENSE.md for details.
 *|           Copyright (C) 2020-2026 LSEG. All rights reserved.
 *|-----------------------------------------------------------------------------
 */

package com.lseg.ema.example;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import com.refinitiv.ema.access.Msg;
import com.refinitiv.ema.access.OmmConsumerEvent;
import com.refinitiv.ema.access.RefreshMsg;
import com.refinitiv.ema.access.StatusMsg;
import com.refinitiv.ema.access.UpdateMsg;

/** Processes cloned EMA item messages on bounded virtual-thread workers. */
final class MessageProcessingPipeline implements AutoCloseable {

    // These example values can be tuned for the expected update rate and available memory.
    private static final int WORKER_COUNT = 5;
    private static final int QUEUE_CAPACITY = 1_000;
    private static final int RESERVED_PRIORITY_CAPACITY = 100;
    private static final int BATCH_SIZE = 50;
    private static final Duration WORKER_POLL_INTERVAL = Duration.ofMillis(100);
    private static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(10);

    private final LinkedBlockingDeque<QueuedMessage> messageQueue;
    private final ExecutorService workerExecutor;
    private final AtomicLong sequence = new AtomicLong();
    private final AtomicLong droppedUpdates = new AtomicLong();
    private final AtomicLong evictedUpdates = new AtomicLong();
    private final AtomicLong droppedPriorityMessages = new AtomicLong();
    private final Object admissionLock = new Object();
    private final Consumer<String> outputConsumer;
    private final int updateCapacity;

    private int queuedUpdateCount;
    private volatile boolean closing;

    /** Creates a pipeline using the documented example tuning values. */
    MessageProcessingPipeline(Consumer<String> outputConsumer) {
        this(WORKER_COUNT, QUEUE_CAPACITY, RESERVED_PRIORITY_CAPACITY, outputConsumer);
    }

    /**
     * Creates a pipeline with explicit worker and queue settings.
     *
     * @param workerCount              number of long-running virtual workers
     * @param queueCapacity            maximum queued message count
     * @param reservedPriorityCapacity positions unavailable to updates
     * @param outputConsumer           destination for complete JSON strings
     */
    MessageProcessingPipeline(
            int workerCount,
            int queueCapacity,
            int reservedPriorityCapacity,
            Consumer<String> outputConsumer) {

        if (workerCount < 1)
            throw new IllegalArgumentException("Worker count must be at least one.");
        if (queueCapacity < 1)
            throw new IllegalArgumentException("Queue capacity must be at least one.");
        if (reservedPriorityCapacity < 0 || reservedPriorityCapacity > queueCapacity)
            throw new IllegalArgumentException("Reserved capacity must be between zero and queue capacity.");

        this.messageQueue = new LinkedBlockingDeque<>(queueCapacity);
        this.updateCapacity = queueCapacity - reservedPriorityCapacity;
        this.outputConsumer = Objects.requireNonNull(outputConsumer, "Output consumer must not be null.");
        this.workerExecutor = Executors.newVirtualThreadPerTaskExecutor();
        for (int workerIndex = 0; workerIndex < workerCount; workerIndex++)
            workerExecutor.submit(this::processMessages);
    }

    /** Submits a cloned Refresh as a priority message. */
    void submitRefresh(RefreshMsg refreshMsg, OmmConsumerEvent event) {
        enqueuePriority(new QueuedRefresh(
                sequence.incrementAndGet(),
                EventContext.capture(event),
                refreshMsg));
    }

    /** Submits a cloned Update while preserving priority queue capacity. */
    void submitUpdate(UpdateMsg updateMsg, OmmConsumerEvent event) {
        enqueueUpdate(new QueuedUpdate(
                sequence.incrementAndGet(),
                EventContext.capture(event),
                updateMsg));
    }

    /** Submits a cloned Status as a priority message. */
    void submitStatus(StatusMsg statusMsg, OmmConsumerEvent event) {
        enqueuePriority(new QueuedStatus(
                sequence.incrementAndGet(),
                EventContext.capture(event),
                statusMsg));
    }

    /** Adds an Update only when non-reserved queue capacity remains. */
    private void enqueueUpdate(QueuedUpdate update) {
        synchronized (admissionLock) {
            if (closing || queuedUpdateCount >= updateCapacity || !messageQueue.offerLast(update)) {
                droppedUpdates.incrementAndGet();
                return;
            }

            queuedUpdateCount++;
        }
    }

    /** Adds priority traffic, evicting the oldest queued Update when necessary. */
    private void enqueuePriority(QueuedMessage priorityMessage) {
        synchronized (admissionLock) {
            if (closing) {
                droppedPriorityMessages.incrementAndGet();
                return;
            }

            if (messageQueue.offerLast(priorityMessage))
                return;

            QueuedUpdate oldestUpdate = messageQueue.stream()
                    .filter(QueuedUpdate.class::isInstance)
                    .map(QueuedUpdate.class::cast)
                    .findFirst()
                    .orElse(null);

            if (oldestUpdate != null && messageQueue.remove(oldestUpdate)) {
                queuedUpdateCount--;
                evictedUpdates.incrementAndGet();
                if (messageQueue.offerLast(priorityMessage))
                    return;
            }

            // A worker may have removed the selected update between the scan and removal.
            if (messageQueue.offerLast(priorityMessage))
                return;

            droppedPriorityMessages.incrementAndGet();
        }
    }

    /** Polls and converts message batches until shutdown has drained the queue. */
    private void processMessages() {
        List<QueuedMessage> batch = new ArrayList<>(BATCH_SIZE);
        try {
            while (!closing || !messageQueue.isEmpty()) {
                QueuedMessage firstMessage = messageQueue.pollFirst(
                        WORKER_POLL_INTERVAL.toMillis(),
                        TimeUnit.MILLISECONDS);
                if (firstMessage == null)
                    continue;

                batch.add(firstMessage);
                messageQueue.drainTo(batch, BATCH_SIZE - 1);
                updateQueueAccounting(batch);
                batch.forEach(this::writeJson);
                batch.clear();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    /** Updates admission accounting after workers claim a batch. */
    private void updateQueueAccounting(List<QueuedMessage> batch) {
        long removedUpdates = batch.stream().filter(QueuedUpdate.class::isInstance).count();
        if (removedUpdates == 0)
            return;

        synchronized (admissionLock) {
            queuedUpdateCount -= Math.toIntExact(removedUpdates);
        }
    }

    /** Converts and writes one queued message without terminating its worker on failure. */
    private void writeJson(QueuedMessage queuedMessage) {
        try {
            EventContext event = queuedMessage.eventContext();
            outputConsumer.accept(EmaJsonConverter.toJson(
                    queuedMessage.sequence(),
                    messageType(queuedMessage),
                    event.handle(),
                    event.parentHandle(),
                    event.closure(),
                    queuedMessage.message()));
        } catch (RuntimeException exception) {
            System.err.printf(
                    "Unable to convert EMA message sequence %d to JSON: %s%n",
                    queuedMessage.sequence(),
                    exception.getMessage());
        }
    }

    /** Returns the readable type of a queued message. */
    private static String messageType(QueuedMessage queuedMessage) {
        return switch (queuedMessage) {
            case QueuedRefresh ignored -> "Refresh";
            case QueuedUpdate ignored -> "Update";
            case QueuedStatus ignored -> "Status";
        };
    }

    /** Drains accepted messages, stops workers, and reports overload totals. */
    @Override
    public void close() {
        synchronized (admissionLock) {
            closing = true;
        }
        workerExecutor.shutdown();

        try {
            if (!workerExecutor.awaitTermination(SHUTDOWN_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                List<Runnable> abandonedWorkers = workerExecutor.shutdownNow();
                System.err.printf("JSON workers did not stop cleanly; %d worker tasks were abandoned.%n",
                        abandonedWorkers.size());
            }
        } catch (InterruptedException exception) {
            workerExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        System.err.printf(
                "JSON queue summary: dropped updates=%d, evicted updates=%d, dropped refresh/status=%d%n",
                droppedUpdates.get(),
                evictedUpdates.get(),
                droppedPriorityMessages.get());
    }

    /** Common data exposed by every queued EMA message. */
    private sealed interface QueuedMessage permits QueuedRefresh, QueuedUpdate, QueuedStatus {

        /** Returns the callback arrival sequence. */
        long sequence();

        /** Returns immutable callback metadata. */
        EventContext eventContext();

        /** Returns the cloned EMA message. */
        Msg message();
    }

    /** A queued item Refresh. */
    private record QueuedRefresh(
            long sequence,
            EventContext eventContext,
            RefreshMsg message) implements QueuedMessage {
    }

    /** A queued item Update. */
    private record QueuedUpdate(
            long sequence,
            EventContext eventContext,
            UpdateMsg message) implements QueuedMessage {
    }

    /** A queued item Status. */
    private record QueuedStatus(
            long sequence,
            EventContext eventContext,
            StatusMsg message) implements QueuedMessage {
    }

    /** Immutable callback metadata that is safe to use after callback return. */
    private record EventContext(long handle, long parentHandle, String closure) {

        /** Captures event values without retaining the EMA callback object. */
        private static EventContext capture(OmmConsumerEvent event) {
            return new EventContext(event.handle(), event.parentHandle(), String.valueOf(event.closure()));
        }
    }
}

