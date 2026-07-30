/*|-----------------------------------------------------------------------------
 *|            This source code is provided under the Apache 2.0 license
 *|  and is provided AS IS with no warranty or guarantee of fit for purpose.
 *|                See the project's LICENSE.md for details.
 *|           Copyright (C) 2020-2026 LSEG. All rights reserved.
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
import com.refinitiv.ema.access.OmmAscii;
import com.refinitiv.ema.access.OmmConsumerClient;
import com.refinitiv.ema.access.OmmConsumerEvent;
import com.refinitiv.ema.access.OmmDate;
import com.refinitiv.ema.access.OmmEnum;
import com.refinitiv.ema.access.OmmError;
import com.refinitiv.ema.access.OmmInt;
import com.refinitiv.ema.access.OmmReal;
import com.refinitiv.ema.access.OmmRmtes;
import com.refinitiv.ema.access.OmmTime;
import com.refinitiv.ema.access.OmmUInt;
import java.util.function.Supplier;
import com.refinitiv.ema.domain.login.Login.LoginRefresh;
import com.refinitiv.ema.domain.login.Login.LoginStatus;
import com.refinitiv.ema.access.EmaFactory;
import com.refinitiv.ema.rdm.EmaRdm;


class AppClient implements OmmConsumerClient {

    LoginRefresh _loginRefresh = EmaFactory.Domain.createLoginRefresh();
    LoginStatus _loginStatus = EmaFactory.Domain.createLoginStatus();

    // Shared placeholder for EMA fields that are optional and may be absent in callbacks.
    private static final String NOT_SET = "<not set>";

    /**
     * Resolves an optional EMA field to its value, or a shared placeholder when unset.
     *
     * @param hasValue whether the EMA field is present on the message
     * @param valueSupplier    the field value to return when {@code hasValue} is {@code true}
     * @return {@code value} if present, otherwise {@link #NOT_SET}
     */
    private static String valueOrNotSet(boolean hasValue, Supplier<String> valueSupplier) {
        return hasValue ? valueSupplier.get() : NOT_SET;
    }

    /**
     * Prints the item and service name lines shared by refresh/update/status handlers.
     *
     * @param itemName    the resolved item name (or placeholder)
     * @param serviceName the resolved service name (or placeholder)
     */
    private static void printItemAndService(String itemName, String serviceName) {
        System.out.printf("Item Name: %s%n", itemName);
        System.out.printf("Service Name: %s%n", serviceName);
    }

    /**
     * Prints common event context metadata shared by EMA callback handlers.
     *
     * @param messageType the callback/message type label (for example, Refresh or Status)
     * @param event       the consumer event providing handle and closure information
     */
    private static void printEventContext(String messageType, OmmConsumerEvent event) {
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
    public void onRefreshMsg(RefreshMsg refreshMsg, OmmConsumerEvent event) {
        printEventContext("onRefreshMsg", event);

        printItemAndService(
                valueOrNotSet(refreshMsg.hasName(), refreshMsg::name),
                valueOrNotSet(refreshMsg.hasServiceName(), refreshMsg::serviceName));

        System.out.printf("Item State: %s%n", refreshMsg.state());

        if (refreshMsg.domainType() == EmaRdm.MMT_LOGIN)
        {
            _loginRefresh.clear();
            System.out.println(_loginRefresh.message(refreshMsg).toString());
        }
        else
        {
            decode( refreshMsg );
        }

        System.out.println();
    }

    /**
     * Invoked for streaming field/value changes received after the initial refresh image.
     *
     * @param updateMsg the update message containing changed fields
     * @param event     the consumer event context for this stream
     */
    @Override
    public void onUpdateMsg(UpdateMsg updateMsg, OmmConsumerEvent event) {

        printEventContext("onUpdateMsg", event);

        printItemAndService(
                valueOrNotSet(updateMsg.hasName(), updateMsg::name),
                valueOrNotSet(updateMsg.hasServiceName(), updateMsg::serviceName));
        decode( updateMsg );

        System.out.println();
    }

    /**
     * Invoked for stream lifecycle/health changes (e.g., open, suspect, closed).
     *
     * @param statusMsg the status message describing the stream state
     * @param event     the consumer event context for this stream
     */
    @Override
    public void onStatusMsg(StatusMsg statusMsg, OmmConsumerEvent event) {
        printEventContext("onStatusMsg", event);

        printItemAndService(
                valueOrNotSet(statusMsg.hasName(), statusMsg::name),
                valueOrNotSet(statusMsg.hasServiceName(), statusMsg::serviceName));

        if (statusMsg.hasState())
            System.out.println("Item State: " + statusMsg.state());

        if (statusMsg.domainType() == EmaRdm.MMT_LOGIN)
        {
            _loginStatus.clear();
            System.out.println(_loginStatus.message(statusMsg).toString());
        }

        System.out.println();
    }

    @Override
    public void onGenericMsg(GenericMsg genericMsg, OmmConsumerEvent consumerEvent) {
    }

    @Override
    public void onAckMsg(AckMsg ackMsg, OmmConsumerEvent consumerEvent) {
    }
    @Override
    public void onAllMsg(Msg msg, OmmConsumerEvent consumerEvent) {
    }

    void decode(Msg msg) {

        // A message can contain data in two places: its attributes and its payload.
        // Only request a FieldList when EMA reports that the container is a FIELD_LIST;
        // calling fieldList() for another data type would be invalid.
        if(msg.attrib().dataType() == DataTypes.FIELD_LIST)
            decodeFieldList(msg.attrib().fieldList());

        // The payload usually contains the item's field values. Decode it separately
        // because a message may have a payload FieldList even when its attributes do not.
        if(msg.payload().dataType() == DataTypes.FIELD_LIST)
            decodeFieldList(msg.payload().fieldList());
    }

    void decodeFieldList(FieldList fieldList) {
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
    private static String formatValue(FieldEntry fieldEntry) {
        if (Data.DataCode.BLANK == fieldEntry.code())
            return "blank";

        return switch (fieldEntry.load()) {
            case OmmReal real -> Double.toString(real.asDouble());
            case OmmDate date -> "%d / %d / %d".formatted(date.day(), date.month(), date.year());
            case OmmTime time -> "%d:%d:%d:%d".formatted(
                    time.hour(), time.minute(), time.second(), time.millisecond());
            case OmmInt intValue -> Long.toString(intValue.intValue());
            case OmmUInt uintValue -> Long.toString(uintValue.longValue());
            case OmmAscii ascii -> ascii.ascii();
            case OmmEnum enumValue -> fieldEntry.hasEnumDisplay()
                    ? fieldEntry.enumDisplay()
                    : Integer.toString(enumValue.enumValue());
            case OmmRmtes rmtes -> rmtes.rmtes().toString();
            case OmmError error -> "(" + error.errorCodeAsString() + ")";
            default -> "";
        };
    }
}
