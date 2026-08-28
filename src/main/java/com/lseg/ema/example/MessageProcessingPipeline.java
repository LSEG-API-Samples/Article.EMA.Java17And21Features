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
            workerExecutor.submit(this::processBatches);
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

    /** Adds a message to the queue, or counts it as dropped when no capacity remains. */
    private void enqueue(QueuedMessage queuedMessage) {
        synchronized (admissionLock) {
            if (closing || !messageQueue.offer(queuedMessage))
                droppedMessages.incrementAndGet();
        }
    }

    /** Processes messages in batches until shutdown has drained the queue. */
    private void processBatches() {
        List<QueuedMessage> batch = new ArrayList<>(BATCH_SIZE);
        try {
            while (!closing || !messageQueue.isEmpty()) {
                QueuedMessage queuedMessage = messageQueue.poll(
                        WORKER_POLL_INTERVAL.toMillis(),
                        TimeUnit.MILLISECONDS);
                if (queuedMessage == null)
                    continue;

                batch.add(queuedMessage);
                messageQueue.drainTo(batch, BATCH_SIZE - 1);
                batch.forEach(this::convertAndWrite);
                batch.clear();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    /** Converts and writes one message without allowing a failure to stop its worker. */
    private void convertAndWrite(QueuedMessage queuedMessage) {
        try {
            EventContext context = queuedMessage.eventContext();
            outputConsumer.accept(EmaJsonConverter.toJson(
                    queuedMessage.sequence(),
                    messageType(queuedMessage),
                    context.handle(),
                    context.parentHandle(),
                    context.closure(),
                    queuedMessage.message()));
        } catch (RuntimeException exception) {
            System.err.printf(
                    "Unable to convert EMA message sequence %d to JSON: %s%n",
                    queuedMessage.sequence(),
                    exception.getMessage());
        }
    }

    /** Returns the readable message type included in the JSON output. */
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

    /** Defines the data shared by every queued EMA message. */
    private sealed interface QueuedMessage permits QueuedRefresh, QueuedUpdate {
        long sequence();

        EventContext eventContext();

        Msg message();
    }

    /** Stores a cloned Refresh and its captured callback details. */
    private record QueuedRefresh(
            long sequence,
            EventContext eventContext,
            RefreshMsg message) implements QueuedMessage {
    }

    /** Stores a cloned Update and its captured callback details. */
    private record QueuedUpdate(
            long sequence,
            EventContext eventContext,
            UpdateMsg message) implements QueuedMessage {
    }

    /** Stores callback details that remain safe to use after the callback returns. */
    private record EventContext(long handle, long parentHandle, String closure) {

        /** Copies callback details without retaining the EMA event object. */
        private static EventContext capture(OmmConsumerEvent event) {
            return new EventContext(event.handle(), event.parentHandle(), String.valueOf(event.closure()));
        }
    }
}

