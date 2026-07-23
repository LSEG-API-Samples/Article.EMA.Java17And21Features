/*|-----------------------------------------------------------------------------
 *|            This source code is provided under the Apache 2.0 license
 *|  and is provided AS IS with no warranty or guarantee of fit for purpose.
 *|                See the project's LICENSE.md for details.
 *|           Copyright (C) 2020-2025 LSEG. All rights reserved.
 *|-----------------------------------------------------------------------------
 */


package com.lseg.ema.example;

import com.refinitiv.ema.access.Msg;
import com.refinitiv.ema.access.AckMsg;
import com.refinitiv.ema.access.GenericMsg;
import com.refinitiv.ema.access.RefreshMsg;
import com.refinitiv.ema.access.StatusMsg;
import com.refinitiv.ema.access.UpdateMsg;
import com.refinitiv.ema.access.Data;
import com.refinitiv.ema.access.DataType;
import com.refinitiv.ema.access.DataType.DataTypes;
import com.refinitiv.ema.access.FieldEntry;
import com.refinitiv.ema.access.FieldList;
import com.refinitiv.ema.access.OmmConsumerClient;
import com.refinitiv.ema.access.OmmConsumerEvent;
import com.refinitiv.ema.access.Payload;


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
     * Prints common event context metadata shared by EMA callback handlers.
     *
     * @param messageType the callback/message type label (for example, Refresh or Status)
     * @param event       the consumer event providing handle and closure information
     */
    private static void printEventContext(String messageType, OmmConsumerEvent event)
    {
        System.out.printf(
                "Received %s. Item Handle: %s Closure: %s%n",
                messageType,
                event.handle(),
                event.closure());
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
        printEventContext("onRefreshMsg", event);

        printItemAndService(
                valueOrNotSet(refreshMsg.hasName(), refreshMsg.name()),
                valueOrNotSet(refreshMsg.hasServiceName(), refreshMsg.serviceName()));

        System.out.printf("Item State: %s%n", refreshMsg.state());
        //System.out.println(refreshMsg);
        decodePayloadIfFieldList(refreshMsg.payload());

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

        printEventContext("onUpdateMsg", event);

        printItemAndService(
                valueOrNotSet(updateMsg.hasName(), updateMsg.name()),
                valueOrNotSet(updateMsg.hasServiceName(), updateMsg.serviceName()));
        //System.out.println(updateMsg);
        decodePayloadIfFieldList(updateMsg.payload());

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
        printEventContext("onStatusMsg", event);

        printItemAndService(
                valueOrNotSet(statusMsg.hasName(), statusMsg.name()),
                valueOrNotSet(statusMsg.hasServiceName(), statusMsg.serviceName()));

        if (statusMsg.hasState())
            System.out.println("Item State: " +statusMsg.state());

        System.out.println();
    }

    public void onGenericMsg(GenericMsg genericMsg, OmmConsumerEvent consumerEvent){}
    public void onAckMsg(AckMsg ackMsg, OmmConsumerEvent consumerEvent){}
    public void onAllMsg(Msg msg, OmmConsumerEvent consumerEvent){}

    private void decodePayloadIfFieldList(Payload payload)
    {
        switch (payload.dataType())
        {
            case DataTypes.FIELD_LIST -> decode(payload.fieldList());
            default -> {
                // Ignore non-field-list payloads in this sample app.
            }
        }
    }

    void decode(FieldList fieldList)
    {
        fieldList.forEach(fieldEntry -> System.out.printf(
                "Fid: %d Name = %s DataType: %s Value: %s%n",
                fieldEntry.fieldId(),
                fieldEntry.name(),
                DataType.asString(fieldEntry.load().dataType()),
                formatValue(fieldEntry)));
    }

    /**
     * Formats a field entry's load into a human-readable value string.
     *
     * @param fieldEntry the field entry whose value should be formatted
     * @return the formatted value, "blank" for blank loads, or an empty string for unsupported loads
     */
    private static String formatValue(FieldEntry fieldEntry)
    {
        if (Data.DataCode.BLANK == fieldEntry.code())
            return "blank";

        return switch (fieldEntry.loadType())
        {
            case DataTypes.REAL  -> Double.toString(fieldEntry.real().asDouble());
            case DataTypes.DATE  -> "%d / %d / %d".formatted(
                    fieldEntry.date().day(), fieldEntry.date().month(), fieldEntry.date().year());
            case DataTypes.TIME  -> "%d:%d:%d:%d".formatted(
                    fieldEntry.time().hour(), fieldEntry.time().minute(),
                    fieldEntry.time().second(), fieldEntry.time().millisecond());
            case DataTypes.INT   -> Long.toString(fieldEntry.intValue());
            case DataTypes.UINT  -> Long.toString(fieldEntry.uintValue());
            case DataTypes.ASCII -> fieldEntry.ascii().toString();
            case DataTypes.ENUM  -> fieldEntry.hasEnumDisplay()
                    ? fieldEntry.enumDisplay()
                    : Integer.toString(fieldEntry.enumValue());
            case DataTypes.RMTES -> fieldEntry.rmtes().toString();
            case DataTypes.ERROR -> "(" + fieldEntry.error().errorCodeAsString() + ")";
            default              -> "";
        };
    }
}
