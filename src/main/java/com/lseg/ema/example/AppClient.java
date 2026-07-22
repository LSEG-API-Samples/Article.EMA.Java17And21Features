/*|-----------------------------------------------------------------------------
 *|            This source code is provided under the Apache 2.0 license
 *|  and is provided AS IS with no warranty or guarantee of fit for purpose.
 *|                See the project's LICENSE.md for details.
 *|           Copyright (C) 2020-2025 LSEG. All rights reserved.
 *|-----------------------------------------------------------------------------
 */


package com.lseg.ema.example;



import com.refinitiv.ema.access.AckMsg;
import com.refinitiv.ema.access.GenericMsg;
import com.refinitiv.ema.access.Msg;
import com.refinitiv.ema.access.OmmConsumerClient;
import com.refinitiv.ema.access.OmmConsumerEvent;
import com.refinitiv.ema.access.RefreshMsg;
import com.refinitiv.ema.access.StatusMsg;
import com.refinitiv.ema.access.UpdateMsg;

class AppClient implements OmmConsumerClient
{
    // Shared placeholder for EMA fields that are optional and may be absent in callbacks.
    private static final String NOT_SET = "<not set>";

    /**
     * Resolves an optional EMA field to its value, or a shared placeholder when unset.
     *
     * @param hasValue whether the EMA field is present on the message
     * @param value    the field value to return when {@code hasValue} is {@code true}
     * @return {@code value} if present, otherwise {@link #NOT_SET}
     */
    private static String valueOrNotSet(boolean hasValue, String value)
    {
        return hasValue ? value : NOT_SET;
    }

    /**
     * Prints the item and service name lines shared by refresh/update/status handlers.
     *
     * @param itemName    the resolved item name (or placeholder)
     * @param serviceName the resolved service name (or placeholder)
     */
    private static void printItemAndService(String itemName, String serviceName)
    {
        System.out.printf("Item Name: %s%n", itemName);
        System.out.printf("Service Name: %s%n", serviceName);
    }

    /**
     * Invoked once for the initial image (and any solicited refresh) of the requested item.
     *
     * @param refreshMsg the refresh message containing item state and payload
     * @param event      the consumer event context for this stream
     */
    @Override
    public void onRefreshMsg(RefreshMsg refreshMsg, OmmConsumerEvent event)
    {
        System.out.println("onRefreshMsg");
        printItemAndService(
                valueOrNotSet(refreshMsg.hasName(), refreshMsg.name()),
                valueOrNotSet(refreshMsg.hasServiceName(), refreshMsg.serviceName()));

        System.out.printf("Item State: %s%n", refreshMsg.state());
        System.out.println(refreshMsg);

        System.out.println();
    }
    /**
     * Invoked for streaming field/value changes received after the initial refresh image.
     *
     * @param updateMsg the update message containing changed fields
     * @param event     the consumer event context for this stream
     */
    @Override
    public void onUpdateMsg(UpdateMsg updateMsg, OmmConsumerEvent event)
    {
        System.out.println("onUpdateMsg");
        printItemAndService(
                valueOrNotSet(updateMsg.hasName(), updateMsg.name()),
                valueOrNotSet(updateMsg.hasServiceName(), updateMsg.serviceName()));
        System.out.println(updateMsg);

        System.out.println();
    }
    /**
     * Invoked for stream lifecycle/health changes (e.g., open, suspect, closed).
     *
     * @param statusMsg the status message describing the stream state
     * @param event     the consumer event context for this stream
     */
    @Override
    public void onStatusMsg(StatusMsg statusMsg, OmmConsumerEvent event)
    {
        System.out.println("onStatusMsg");
        printItemAndService(
                valueOrNotSet(statusMsg.hasName(), statusMsg.name()),
                valueOrNotSet(statusMsg.hasServiceName(), statusMsg.serviceName()));

        System.out.println(statusMsg);

        System.out.println();
    }

    public void onGenericMsg(GenericMsg genericMsg, OmmConsumerEvent consumerEvent){}
    public void onAckMsg(AckMsg ackMsg, OmmConsumerEvent consumerEvent){}
    public void onAllMsg(Msg msg, OmmConsumerEvent consumerEvent){}
}
