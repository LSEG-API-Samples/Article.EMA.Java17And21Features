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

public class ConsumerRTO {

    static String clientId ="";
    static String clientSecret ="";

    public static void main(String[] args)
    {
        clientId = System.getenv("RTO_CLIENTID_V2_1");
        clientSecret = System.getenv("RTO_CLIENTSECRET_1");

        OmmConsumer consumer = null;
        try
        {
            OmmConsumerConfig config = EmaFactory.createOmmConsumerConfig();

            // Apply credentials from system properties into config before parsing args
            if (clientId != null && !clientId.isEmpty()) config.clientId(clientId);
            if (clientSecret != null && !clientSecret.isEmpty()) config.clientSecret(clientSecret);

            if (!Utility.readCommandlineArgs(args, config))
                return;

            AppClient appClient = new AppClient();

            // The "Consumer_RTO" uses EncryptedProtocolType::RSSL_SOCKET predefined in EmaConfig.xml
            consumer  = EmaFactory.createOmmConsumer(config.consumerName("Consumer_RTO"));

            consumer.registerClient( EmaFactory.createReqMsg().serviceName(Utility.serviceName).name(Utility.itemName), appClient);

            Thread.sleep(900000);			// API calls onRefreshMsg(), onUpdateMsg() and onStatusMsg()
        }
        catch (InterruptedException | OmmException excp)
        {
            System.out.println(excp.getMessage());
        }
        finally
        {
            if (consumer != null) consumer.uninitialize();
        }
    }
}


