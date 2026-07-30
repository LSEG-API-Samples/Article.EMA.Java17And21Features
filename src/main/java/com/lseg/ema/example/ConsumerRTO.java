/*|-----------------------------------------------------------------------------
 *|            This source code is provided under the Apache 2.0 license
 *|  and is provided AS IS with no warranty or guarantee of fit for purpose.
 *|                See the project's LICENSE.md for details.
 *|           Copyright (C) 2020-2026 LSEG. All rights reserved.
 *|-----------------------------------------------------------------------------
 */


package com.lseg.ema.example;

import java.time.Duration;

import com.refinitiv.ema.access.EmaFactory;
import com.refinitiv.ema.access.ElementList;
import com.refinitiv.ema.access.OmmArray;
import com.refinitiv.ema.access.OmmConsumer;
import com.refinitiv.ema.access.OmmConsumerConfig;
import com.refinitiv.ema.access.OmmException;
import com.refinitiv.ema.domain.login.Login.LoginReq;
import com.refinitiv.ema.rdm.EmaRdm;

/**
 * EMA consumer example for Real-Time Optimized (RTO) service.
 * Demonstrates single-item and batch requests with field Views over an encrypted connection.
 */
public class ConsumerRTO {

    private static final String CONSUMER_NAME = "Consumer_RTO";
    private static final String CLIENT_ID_ENVIRONMENT_VARIABLE = "RTO_CLIENTID_V2_1";
    private static final String CLIENT_SECRET_ENVIRONMENT_VARIABLE = "RTO_CLIENTSECRET_1";
    private static final Duration RUN_DURATION = Duration.ofMinutes(15);

    // A View Type of 1 means that the View Data contains RDM field IDs.
    private static final int FIELD_ID_VIEW_TYPE = 1;
    private static final int VIEW_ENTRY_WIDTH = 2;
    private static final int[] VIEW_FIELD_IDS = {
            3,    // DSPLY_NAME
            11,   // NETCHNG_1
            22,   // BID
            25,   // ASK
            3386, // QUOTE_DATE
            1025  // QUOTIM
    };


    /** Applies OAuth credentials when their environment variables are defined. */
    private static void applyEnvironmentCredentials(OmmConsumerConfig config) {
        String clientId = System.getenv(CLIENT_ID_ENVIRONMENT_VARIABLE);
        String clientSecret = System.getenv(CLIENT_SECRET_ENVIRONMENT_VARIABLE);

        if (clientId != null && !clientId.isBlank())
            config.clientId(clientId);
        if (clientSecret != null && !clientSecret.isBlank())
            config.clientSecret(clientSecret);
    }

    /** Registers either a single-item request or a batch request, both with the same View. */
    private static void registerItemRequest(
            OmmConsumer consumer,
            AppClient appClient,
            String serviceName,
            String[] itemNames) {

        ElementList payload = createViewPayload();

        switch (itemNames.length) {
            case 0 -> throw new IllegalArgumentException("At least one item name is required.");
            case 1 -> consumer.registerClient(
                    EmaFactory.createReqMsg()
                            .serviceName(serviceName)
                            .name(itemNames[0])
                            .payload(payload),
                    appClient);
            default -> {
                payload.add(EmaFactory.createElementEntry().array(
                        EmaRdm.ENAME_BATCH_ITEM_LIST,
                        createBatchItemArray(itemNames)));
                consumer.registerClient(
                        EmaFactory.createReqMsg()
                                .serviceName(serviceName)
                                .payload(payload),
                        appClient);
            }
        }
    }

    /** Creates the common field View used by both single-item and batch requests. */
    private static ElementList createViewPayload() {
        OmmArray viewData = EmaFactory.createOmmArray();
        viewData.fixedWidth(VIEW_ENTRY_WIDTH);

        for (int fieldId : VIEW_FIELD_IDS)
            viewData.add(EmaFactory.createOmmArrayEntry().intValue(fieldId));

        ElementList payload = EmaFactory.createElementList();
        payload.add(EmaFactory.createElementEntry().uintValue(
                EmaRdm.ENAME_VIEW_TYPE,
                FIELD_ID_VIEW_TYPE));
        payload.add(EmaFactory.createElementEntry().array(
                EmaRdm.ENAME_VIEW_DATA,
                viewData));
        return payload;
    }

    /** Creates the item-name array required by an EMA batch request. */
    private static OmmArray createBatchItemArray(String[] itemNames) {
        OmmArray batchItems = EmaFactory.createOmmArray();
        for (String itemName : itemNames)
            batchItems.add(EmaFactory.createOmmArrayEntry().ascii(itemName));
        return batchItems;
    }

    public static void main(String[] args) {
        OmmConsumer consumer = null;
        try {
            OmmConsumerConfig config = EmaFactory.createOmmConsumerConfig();
            applyEnvironmentCredentials(config);

            if (!Utility.readCommandlineArgs(args, config))
                return;

            AppClient appClient = new AppClient();

            // The "Consumer_RTO" uses EncryptedProtocolType::RSSL_SOCKET predefined in EmaConfig.xml
            consumer = EmaFactory.createOmmConsumer(config.consumerName(CONSUMER_NAME));

            // Register to observe the Login stream.
            LoginReq loginReq = EmaFactory.Domain.createLoginReq();
            consumer.registerClient(loginReq.message(), appClient);

            registerItemRequest(consumer, appClient, Utility.serviceName, Utility.itemNames);

            // Keep listener active; onRefreshMsg(), onUpdateMsg(), and onStatusMsg() callbacks fire asynchronously
            Thread.sleep(RUN_DURATION);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            System.err.println("Consumer interrupted: " + exception.getMessage());
        } catch (OmmException exception) {
            System.err.println(exception.getMessage());
        } finally {
            if (consumer != null)
                consumer.uninitialize();
        }
    }
}


