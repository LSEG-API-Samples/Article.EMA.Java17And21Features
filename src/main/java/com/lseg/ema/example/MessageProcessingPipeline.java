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
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import com.refinitiv.ema.access.Msg;
import com.refinitiv.ema.access.OmmConsumerEvent;
import com.refinitiv.ema.access.RefreshMsg;
import com.refinitiv.ema.access.UpdateMsg;

/** Processes cloned EMA item messages on bounded virtual-thread workers. */
final class MessageProcessingPipeline implements AutoCloseable {

    // These example values can be tuned for the expected message rate and available memory.
    private static final int WORKER_COUNT = 5;
    private static final int QUEUE_CAPACITY = 1_000;
    private static final int BATCH_SIZE = 50;
    private static final Duration WORKER_POLL_INTERVAL = Duration.ofMillis(100);
    private static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(10);

    private final LinkedBlockingQueue<QueuedMessage> messageQueue;
    private final ExecutorService workerExecutor;
    private final AtomicLong sequence = new AtomicLong();
    private final AtomicLong droppedMessages = new AtomicLong();
    private final Object admissionLock = new Object();
    private final Consumer<String> outputConsumer;

    private volatile boolean closing;

    /** Creates a pipeline using the documented example tuning values. */
    MessageProcessingPipeline(Consumer<String> outputConsumer) {
        this(WORKER_COUNT, QUEUE_CAPACITY, outputConsumer);
    }

    /**
     * Creates a pipeline with explicit worker and queue settings.
     *
     * @param workerCount    number of long-running virtual workers
     * @param queueCapacity  maximum queued message count
     * @param outputConsumer destination for complete JSON strings
     */
    MessageProcessingPipeline(
            int workerCount,
            int queueCapacity,
            Consumer<String> outputConsumer) {

        if (workerCount < 1)
            throw new IllegalArgumentException("Worker count must be at least one.");
        if (queueCapacity < 1)
            throw new IllegalArgumentException("Queue capacity must be at least one.");

        this.messageQueue = new LinkedBlockingQueue<>(queueCapacity);
        this.outputConsumer = Objects.requireNonNull(outputConsumer, "Output consumer must not be null.");
        this.workerExecutor = Executors.newVirtualThreadPerTaskExecutor();
        for (int workerIndex = 0; workerIndex < workerCount; workerIndex++)
            workerExecutor.submit(this::processMessages);
    }

    /** Submits a cloned Refresh for asynchronous processing. */
    void submitRefresh(RefreshMsg refreshMsg, OmmConsumerEvent event) {
        enqueue(new QueuedRefresh(
                sequence.incrementAndGet(),
                EventContext.capture(event),
                refreshMsg));
    }

    /** Submits a cloned Update for asynchronous processing. */
    void submitUpdate(UpdateMsg updateMsg, OmmConsumerEvent event) {
        enqueue(new QueuedUpdate(
                sequence.incrementAndGet(),
                EventContext.capture(event),
                updateMsg));
    }

    /** Adds a message when queue capacity remains, regardless of its type. */
    private void enqueue(QueuedMessage queuedMessage) {
        synchronized (admissionLock) {
            if (closing || !messageQueue.offer(queuedMessage))
                droppedMessages.incrementAndGet();
        }
    }

    /** Polls and converts message batches until shutdown has drained the queue. */
    private void processMessages() {
        List<QueuedMessage> batch = new ArrayList<>(BATCH_SIZE);
        try {
            while (!closing || !messageQueue.isEmpty()) {
                QueuedMessage firstMessage = messageQueue.poll(
                        WORKER_POLL_INTERVAL.toMillis(),
                        TimeUnit.MILLISECONDS);
                if (firstMessage == null)
                    continue;

                batch.add(firstMessage);
                messageQueue.drainTo(batch, BATCH_SIZE - 1);
                batch.forEach(this::writeJson);
                batch.clear();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
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
        };
    }

    /** Drains accepted messages, stops workers, and reports the overload total. */
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

        System.err.printf("JSON queue summary: dropped messages=%d%n", droppedMessages.get());
    }

    /** Common data exposed by every queued EMA message. */
    private sealed interface QueuedMessage permits QueuedRefresh, QueuedUpdate {

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


    /** Immutable callback metadata that is safe to use after callback return. */
    private record EventContext(long handle, long parentHandle, String closure) {

        /** Captures event values without retaining the EMA callback object. */
        private static EventContext capture(OmmConsumerEvent event) {
            return new EventContext(event.handle(), event.parentHandle(), String.valueOf(event.closure()));
        }
    }
}

