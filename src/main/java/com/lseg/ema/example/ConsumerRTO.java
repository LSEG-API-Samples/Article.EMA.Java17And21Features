/*|-----------------------------------------------------------------------------
 *|            This source code is provided under the Apache 2.0 license
 *|  and is provided AS IS with no warranty or guarantee of fit for purpose.
 *|                See the project's LICENSE.md for details.
 *|           Copyright (C) 2020-2026 LSEG. All rights reserved.
 *|-----------------------------------------------------------------------------
 */


package com.lseg.ema.example;

import com.refinitiv.ema.access.EmaFactory;
import com.refinitiv.ema.access.OmmConsumer;
import com.refinitiv.ema.access.OmmConsumerConfig;
import com.refinitiv.ema.access.OmmException;
import com.refinitiv.ema.access.ElementList;
import com.refinitiv.ema.access.OmmArray;
import com.refinitiv.ema.rdm.EmaRdm;

/**
 * EMA consumer example for Real-Time Optimized (RTO) service.
 * Demonstrates batch item requests with field views over encrypted connection.
 */
public class ConsumerRTO {

    // RTO OAuth 2.0 credentials (loaded from environment variables)
    static String clientId = "";
    static String clientSecret = "";

    public static void main(String[] args) {
        // Load credentials from system environment (RTO_CLIENTID_V2_1, RTO_CLIENTSECRET_1)
        clientId = System.getenv("RTO_CLIENTID_V2_1");
        clientSecret = System.getenv("RTO_CLIENTSECRET_1");

        OmmConsumer consumer = null;
        try {
            OmmConsumerConfig config = EmaFactory.createOmmConsumerConfig();

            // Apply credentials from system properties into config before parsing args
            if (clientId != null && !clientId.isEmpty()) config.clientId(clientId);
            if (clientSecret != null && !clientSecret.isEmpty()) config.clientSecret(clientSecret);

            if (!Utility.readCommandlineArgs(args, config))
                return;

            AppClient appClient = new AppClient();

            // The "Consumer_RTO" uses EncryptedProtocolType::RSSL_SOCKET predefined in EmaConfig.xml
            consumer = EmaFactory.createOmmConsumer(config.consumerName("Consumer_RTO"));

            //Create View
            OmmArray view_array = EmaFactory.createOmmArray();
            // Set a fixed size for each array entry to minimize bandwidth, since we know their sizes
            view_array.fixedWidth(2);

            // Set the array entries (RDM field IDs to request)
            view_array.add(EmaFactory.createOmmArrayEntry().intValue(3)); //DSPLY_NAME
            view_array.add(EmaFactory.createOmmArrayEntry().intValue(11)); //NETCHNG_1
            view_array.add(EmaFactory.createOmmArrayEntry().intValue(22)); //BID
            view_array.add(EmaFactory.createOmmArrayEntry().intValue(25)); //ASK
            view_array.add(EmaFactory.createOmmArrayEntry().intValue(3386)); //QUOTE_DATE
            view_array.add(EmaFactory.createOmmArrayEntry().intValue(1025)); //QUOTIM

            // Create batch request with item names (from -itemNames arg or default)
            OmmArray batch_array = EmaFactory.createOmmArray();
            for (String item: Utility.itemNames) {
                batch_array.add(EmaFactory.createOmmArrayEntry().ascii(item));
            }

            // Combine View (which fields) and Batch (which items) into a single payload
            ElementList payload = EmaFactory.createElementList();
            payload.add(EmaFactory.createElementEntry().array(EmaRdm.ENAME_BATCH_ITEM_LIST, batch_array));
            payload.add(EmaFactory.createElementEntry().uintValue(EmaRdm.ENAME_VIEW_TYPE, 1));
            payload.add(EmaFactory.createElementEntry().array(EmaRdm.ENAME_VIEW_DATA, view_array));

            // Send batch request with view filter to RTO service
            consumer.registerClient(EmaFactory.createReqMsg().serviceName(Utility.serviceName).payload(payload), appClient);

            // Keep listener active; onRefreshMsg(), onUpdateMsg(), and onStatusMsg() callbacks fire asynchronously
            Thread.sleep(900000);
        } catch (InterruptedException | OmmException excp) {
            System.out.println(excp.getMessage());
        } finally {
            // Clean up consumer connection
            if (consumer != null) consumer.uninitialize();
        }
    }
}


