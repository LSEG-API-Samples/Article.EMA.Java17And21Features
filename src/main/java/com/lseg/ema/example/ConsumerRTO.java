/*|-----------------------------------------------------------------------------
 *|            This source code is provided under the Apache 2.0 license
 *|  and is provided AS IS with no warranty or guarantee of fit for purpose.
 *|                See the project's LICENSE.md for details.
 *|           Copyright (C) 2020-2025 LSEG. All rights reserved.
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

public class ConsumerRTO {

    static String clientId = "";
    static String clientSecret = "";

    public static void main(String[] args) {
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

            //View

            OmmArray view_array = EmaFactory.createOmmArray();

            // Set a fixed size for each array entry to minimize bandwidth, since we know their sizes
            view_array.fixedWidth(2);

            // Set the array entries
            view_array.add(EmaFactory.createOmmArrayEntry().intValue(3)); //DSPLY_NAME
            view_array.add(EmaFactory.createOmmArrayEntry().intValue(11)); //NETCHNG_1
            view_array.add(EmaFactory.createOmmArrayEntry().intValue(22)); //BID
            view_array.add(EmaFactory.createOmmArrayEntry().intValue(25)); //ASK
            view_array.add(EmaFactory.createOmmArrayEntry().intValue(3386)); //QUOTE_DATE
            view_array.add(EmaFactory.createOmmArrayEntry().intValue(1025)); //QUOTIM

            //Batch
            OmmArray batch_array = EmaFactory.createOmmArray();
            //batch_array.add(EmaFactory.createOmmArrayEntry().ascii("THB="));
            //batch_array.add(EmaFactory.createOmmArrayEntry().ascii("MYR="));
            for (String item: Utility.itemNames) {
                batch_array.add(EmaFactory.createOmmArrayEntry().ascii(item));
            }

            ElementList payload = EmaFactory.createElementList();
            payload.add(EmaFactory.createElementEntry().array(EmaRdm.ENAME_BATCH_ITEM_LIST, batch_array));
            payload.add(EmaFactory.createElementEntry().uintValue(EmaRdm.ENAME_VIEW_TYPE, 1));
            payload.add(EmaFactory.createElementEntry().array(EmaRdm.ENAME_VIEW_DATA, view_array));

            //consumer.registerClient(EmaFactory.createReqMsg().serviceName(Utility.serviceName).name(Utility.itemName).payload(payload), appClient);
            consumer.registerClient(EmaFactory.createReqMsg().serviceName(Utility.serviceName).payload(payload), appClient);

            Thread.sleep(900000);            // API calls onRefreshMsg(), onUpdateMsg() and onStatusMsg()
        } catch (InterruptedException | OmmException excp) {
            System.out.println(excp.getMessage());
        } finally {
            if (consumer != null) consumer.uninitialize();
        }
    }
}


