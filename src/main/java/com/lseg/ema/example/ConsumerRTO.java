/*|-----------------------------------------------------------------------------
 *|            This source code is provided under the Apache 2.0 license
 *|  and is provided AS IS with no warranty or guarantee of fit for purpose.
 *|                See the project's LICENSE.md for details.
 *|           Copyright (C) 2020-2026 LSEG. All rights reserved.
 *|-----------------------------------------------------------------------------
 */


package com.lseg.ema.example;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

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
    private static final int EXIT_SUCCESS = 0;
    private static final int EXIT_FAILURE = 1;

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

    /** Closed request model that distinguishes single-item requests from batch requests. */
    private sealed interface ItemRequest permits SingleItemRequest, BatchItemRequest {

        /**
         * Validates and normalizes request values, then selects the record matching the item count.
         *
         * @param serviceName service from which to request the items
         * @param itemNames   item names to request
         * @return a single-item record for one item, otherwise a batch record
         * @throws NullPointerException     if {@code itemNames} is {@code null}
         * @throws IllegalArgumentException if no items are provided or a service or item name is blank
         */
        static ItemRequest create(String serviceName, String[] itemNames) {
            Objects.requireNonNull(itemNames, "Item names must not be null.");

            List<String> validatedItemNames = Arrays.stream(itemNames)
                    .map(itemName -> requireNonBlank(itemName, "Item name"))
                    .toList();

            return switch (validatedItemNames.size()) {
                case 0 -> throw new IllegalArgumentException("At least one item name is required.");
                case 1 -> new SingleItemRequest(serviceName, validatedItemNames.getFirst());
                default -> new BatchItemRequest(serviceName, validatedItemNames);
            };
        }
    }

    /**
     * Immutable request for exactly one item.
     *
     * @param serviceName validated service name
     * @param itemName    validated item name
     */
    private record SingleItemRequest(String serviceName, String itemName) implements ItemRequest {

        private SingleItemRequest {
            serviceName = requireNonBlank(serviceName, "Service name");
            itemName = requireNonBlank(itemName, "Item name");
        }
    }

    /**
     * Immutable request for two or more items.
     *
     * @param serviceName validated service name
     * @param itemNames   validated, immutable item names
     */
    private record BatchItemRequest(String serviceName, List<String> itemNames) implements ItemRequest {

        private BatchItemRequest {
            serviceName = requireNonBlank(serviceName, "Service name");
            Objects.requireNonNull(itemNames, "Item names must not be null.");

            // Rebuild the list so this record never retains caller-owned mutable state.
            itemNames = itemNames.stream()
                    .map(itemName -> requireNonBlank(itemName, "Item name"))
                    .toList();

            if (itemNames.size() < 2)
                throw new IllegalArgumentException("A batch request requires at least two item names.");
        }
    }

    /** Returns a stripped value after rejecting null and whitespace-only input. */
    private static String requireNonBlank(String value, String description) {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException(description + " must not be blank.");
        return value.strip();
    }

    /** Applies OAuth credentials when their environment variables are defined. */
    private static boolean applyEnvironmentCredentials(OmmConsumerConfig config) {
        // Treat unset and blank environment variables consistently during validation.
        String clientId = Objects.requireNonNullElse(
                System.getenv(CLIENT_ID_ENVIRONMENT_VARIABLE),
                "");
        String clientSecret = Objects.requireNonNullElse(
                System.getenv(CLIENT_SECRET_ENVIRONMENT_VARIABLE),
                "");

        boolean hasClientId = !clientId.isBlank();
        boolean hasClientSecret = !clientSecret.isBlank();
        boolean hasClientJwk = Utility.hasJWK();

        // Both client-secret and JWK authentication require the application client ID.
        if (!hasClientId) {
            System.err.printf(
                    "No client ID found; exiting. Set the %s environment variable to a valid value.%n",
                    CLIENT_ID_ENVIRONMENT_VARIABLE);
            return false;
        }

        // Either a client secret or the JWK loaded from -jwkFile can authenticate the session.
        if (!hasClientSecret && !hasClientJwk) {
            System.err.printf(
                    "No client secret or JWK found; exiting. Set the %s environment variable or provide -jwkFile.%n",
                    CLIENT_SECRET_ENVIRONMENT_VARIABLE);
            return false;
        }

        config.clientId(clientId);
        // Prefer JWK authentication when configured; command-line parsing already applied it to config.
        if (!hasClientJwk)
            config.clientSecret(clientSecret);

        return true;
    }

    /** Registers either a single-item request or a batch request, both with the same View. */
    private static void registerItemRequest(
            OmmConsumer consumer,
            AppClient appClient,
            ItemRequest itemRequest) {

        ElementList payload = createViewPayload();

        // The sealed hierarchy makes this exhaustive; a new request type requires a new branch.
        switch (itemRequest) {
            case SingleItemRequest(String serviceName, String itemName) -> consumer.registerClient(
                    EmaFactory.createReqMsg()
                            .serviceName(serviceName)
                            .name(itemName)
                            .payload(payload),
                    appClient);
            case BatchItemRequest(String serviceName, List<String> itemNames) -> {
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
    private static OmmArray createBatchItemArray(List<String> itemNames) {
        OmmArray batchItems = EmaFactory.createOmmArray();
        for (String itemName : itemNames)
            batchItems.add(EmaFactory.createOmmArrayEntry().ascii(itemName));
        return batchItems;
    }

    /**
     * Starts the consumer and exits with code 1 if startup or the session fails.
     *
     * @param args command-line options; use {@code -?} to list them
     */
    public static void main(String[] args) {
        int exitCode = run(args);
        // A successful run ends normally once EMA and worker threads have stopped.
        if (exitCode != EXIT_SUCCESS)
            System.exit(exitCode);
    }

    /**
     * Requests the configured items for {@code RUN_DURATION}, then cleans up.
     *
     * @param args command-line options
     * @return {@link #EXIT_SUCCESS} after a normal run or help, otherwise {@link #EXIT_FAILURE}
     */
    private static int run(String[] args) {
        OmmConsumer consumer = null;
        AppClient appClient = null;
        try {
            OmmConsumerConfig config = EmaFactory.createOmmConsumerConfig();

            switch (Utility.readCommandlineArgs(args, config)) {
                case CONTINUE -> { }
                case HELP_SHOWN -> { return EXIT_SUCCESS; }
                case INVALID -> { return EXIT_FAILURE; }
            }

            if (!applyEnvironmentCredentials(config))
                return EXIT_FAILURE;

            appClient = new AppClient();

            // Validate request data before creating the consumer and opening the RTO session.
            ItemRequest itemRequest = ItemRequest.create(Utility.serviceName, Utility.itemNames);

            // The "Consumer_RTO" uses EncryptedProtocolType::RSSL_SOCKET predefined in EmaConfig.xml
            consumer = EmaFactory.createOmmConsumer(config.consumerName(CONSUMER_NAME));

            // Register to observe the Login stream.
            LoginReq loginReq = EmaFactory.Domain.createLoginReq();
            consumer.registerClient(loginReq.message(), appClient);

            registerItemRequest(consumer, appClient, itemRequest);

            // Keep listener active; onRefreshMsg(), onUpdateMsg(), and onStatusMsg() callbacks fire asynchronously
            Thread.sleep(RUN_DURATION);
            return EXIT_SUCCESS;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            System.err.println("Consumer interrupted: " + exception.getMessage());
            return EXIT_FAILURE;
        } catch (IllegalArgumentException exception) {
            System.err.println("Invalid item request: " + exception.getMessage());
            return EXIT_FAILURE;
        } catch (OmmException exception) {
            System.err.println(exception.getMessage());
            return EXIT_FAILURE;
        } finally {
            if (consumer != null)
                consumer.uninitialize();
            if (appClient != null)
                appClient.close();
        }
    }
}


