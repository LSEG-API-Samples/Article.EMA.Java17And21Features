/*|-----------------------------------------------------------------------------
 *|            This source code is provided under the Apache 2.0 license
 *|  and is provided AS IS with no warranty or guarantee of fit for purpose.
 *|                See the project's LICENSE.md for details.
 *|           Copyright (C) 2020-2026 LSEG. All rights reserved.
 *|-----------------------------------------------------------------------------
 */

package com.lseg.ema.example;

import java.util.Objects;
import java.util.function.Consumer;

import com.refinitiv.ema.access.AckMsg;
import com.refinitiv.ema.access.EmaFactory;
import com.refinitiv.ema.access.GenericMsg;
import com.refinitiv.ema.access.Msg;
import com.refinitiv.ema.access.OmmConsumerClient;
import com.refinitiv.ema.access.OmmConsumerEvent;
import com.refinitiv.ema.access.RefreshMsg;
import com.refinitiv.ema.access.StatusMsg;
import com.refinitiv.ema.access.UpdateMsg;
import com.refinitiv.ema.domain.login.Login.LoginRefresh;
import com.refinitiv.ema.domain.login.Login.LoginStatus;
import com.refinitiv.ema.rdm.EmaRdm;

/** Handles EMA callbacks and delegates cloned item messages to the processing pipeline. */
class AppClient implements OmmConsumerClient, AutoCloseable {

    private final LoginRefresh loginRefresh = EmaFactory.Domain.createLoginRefresh();
    private final LoginStatus loginStatus = EmaFactory.Domain.createLoginStatus();
    private final MessageProcessingPipeline pipeline;
    private final Consumer<String> outputConsumer;

    /** Creates the callback client with the example pipeline settings and console output. */
    AppClient() {
        this(System.out::println);
    }

    /** Creates the callback client with explicit pipeline settings for focused tests. */
    AppClient(int workerCount, int queueCapacity, Consumer<String> outputConsumer) {
        this.outputConsumer = Objects.requireNonNull(outputConsumer, "Output consumer must not be null.");
        this.pipeline = new MessageProcessingPipeline(workerCount, queueCapacity, outputConsumer);
    }

    /** Creates the callback client with default pipeline settings and a selected output. */
    private AppClient(Consumer<String> outputConsumer) {
        this.outputConsumer = Objects.requireNonNull(outputConsumer, "Output consumer must not be null.");
        this.pipeline = new MessageProcessingPipeline(outputConsumer);
    }

    /** Decodes login Refreshes directly and queues cloned item Refreshes. */
    @Override
    public void onRefreshMsg(RefreshMsg refreshMsg, OmmConsumerEvent event) {
        if (refreshMsg.domainType() == EmaRdm.MMT_LOGIN) {
            loginRefresh.clear();
            outputConsumer.accept(loginRefresh.message(refreshMsg).toString()+"\n");
            return;
        }

        pipeline.submitRefresh(EmaFactory.createRefreshMsg(refreshMsg), event);
    }

    /** Queues a cloned item Update for asynchronous JSON conversion. */
    @Override
    public void onUpdateMsg(UpdateMsg updateMsg, OmmConsumerEvent event) {
        pipeline.submitUpdate(EmaFactory.createUpdateMsg(updateMsg), event);
    }

    /** Decodes login Statuses directly and queues cloned item Statuses. */
    @Override
    public void onStatusMsg(StatusMsg statusMsg, OmmConsumerEvent event) {
        if (statusMsg.domainType() == EmaRdm.MMT_LOGIN) {
            loginStatus.clear();
            outputConsumer.accept(loginStatus.message(statusMsg).toString()+"\n");
            return;
        }

        pipeline.submitStatus(EmaFactory.createStatusMsg(statusMsg), event);
    }

    /** Receives Generic messages; this example does not process them. */
    @Override
    public void onGenericMsg(GenericMsg genericMsg, OmmConsumerEvent event) {
    }

    /** Receives acknowledgment messages; this example does not process them. */
    @Override
    public void onAckMsg(AckMsg ackMsg, OmmConsumerEvent event) {
    }

    /** Receives every message after its type-specific callback. */
    @Override
    public void onAllMsg(Msg msg, OmmConsumerEvent event) {
    }

    /** Drains queued item messages and closes the virtual-thread pipeline. */
    @Override
    public void close() {
        pipeline.close();
    }
}
