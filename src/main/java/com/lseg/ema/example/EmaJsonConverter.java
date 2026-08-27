/*|-----------------------------------------------------------------------------
 *|            This source code is provided under the Apache 2.0 license
 *|  and is provided AS IS with no warranty or guarantee of fit for purpose.
 *|                See the project's LICENSE.md for details.
 *|           Copyright (C) 2020-2026 LSEG. All rights reserved.
 *|-----------------------------------------------------------------------------
 */

package com.lseg.ema.example;

import com.refinitiv.ema.access.Data;
import com.refinitiv.ema.access.DataType;
import com.refinitiv.ema.access.DataType.DataTypes;
import com.refinitiv.ema.access.FieldEntry;
import com.refinitiv.ema.access.FieldList;
import com.refinitiv.ema.access.Msg;
import com.refinitiv.ema.access.OmmAscii;
import com.refinitiv.ema.access.OmmDate;
import com.refinitiv.ema.access.OmmEnum;
import com.refinitiv.ema.access.OmmError;
import com.refinitiv.ema.access.OmmInt;
import com.refinitiv.ema.access.OmmReal;
import com.refinitiv.ema.access.OmmRmtes;
import com.refinitiv.ema.access.OmmState;
import com.refinitiv.ema.access.OmmTime;
import com.refinitiv.ema.access.OmmUInt;
import com.refinitiv.ema.access.RefreshMsg;
import com.refinitiv.ema.access.StatusMsg;
import com.refinitiv.ema.access.UpdateMsg;
import org.json.JSONArray;
import org.json.JSONObject;

/** Converts cloned EMA item messages into JSON without retaining mutable state. */
final class EmaJsonConverter {

    private static final String NOT_SET = "<not set>";

    /** Prevents creation of this stateless utility class. */
    private EmaJsonConverter() {
    }

    /**
     * Converts one queued EMA message and its captured callback context to JSON.
     *
     * @param sequence     callback arrival sequence
     * @param messageType  readable EMA message type
     * @param handle       item registration handle
     * @param parentHandle parent registration handle
     * @param closure      captured closure text
     * @param msg          cloned EMA message
     * @return one complete JSON object
     */
    static String toJson(
            long sequence,
            String messageType,
            long handle,
            long parentHandle,
            String closure,
            Msg msg) {

        String serviceName = serviceName(msg);
        JSONObject json = new JSONObject()
                .put("sequence", sequence)
                .put("type", messageType)
                .put("handle", handle)
                .put("parentHandle", parentHandle)
                .put("closure", closure)
                .put("streamId", msg.streamId())
                .put("domainType", msg.domainType())
                .put("itemName", msg.hasName() ? msg.name() : NOT_SET)
                .put("serviceName", serviceName != null ? serviceName : JSONObject.NULL);

        switch (msg) {
            case RefreshMsg refreshMsg -> json.put("state", stateToJson(refreshMsg.state()));
            case StatusMsg statusMsg -> {
                if (statusMsg.hasState())
                    json.put("state", stateToJson(statusMsg.state()));
            }
            default -> {
            }
        }

        json.put("attributes", containerToJson(
                msg.attrib().dataType(),
                msg.attrib().dataType() == DataTypes.FIELD_LIST ? msg.attrib().fieldList() : null));
        json.put("payload", containerToJson(
                msg.payload().dataType(),
                msg.payload().dataType() == DataTypes.FIELD_LIST ? msg.payload().fieldList() : null));
        return json.toString();
    }

    /** Returns the service name exposed by the concrete EMA message type. */
    private static String serviceName(Msg msg) {
        return switch (msg) {
            case RefreshMsg refreshMsg -> refreshMsg.hasServiceName() ? refreshMsg.serviceName() : null;
            case UpdateMsg updateMsg -> updateMsg.hasServiceName() ? updateMsg.serviceName() : null;
            case StatusMsg statusMsg -> statusMsg.hasServiceName() ? statusMsg.serviceName() : null;
            default -> null;
        };
    }

    /** Converts an EMA state to a compact JSON object. */
    private static JSONObject stateToJson(OmmState state) {
        return new JSONObject()
                .put("stream", state.streamStateAsString())
                .put("data", state.dataStateAsString())
                .put("code", state.statusCodeAsString())
                .put("text", state.statusText());
    }

    /** Converts a supported EMA container and identifies unsupported container types. */
    private static JSONObject containerToJson(int dataType, FieldList fieldList) {
        JSONObject container = new JSONObject().put("dataType", DataType.asString(dataType));
        if (fieldList != null) {
            JSONArray fields = new JSONArray();
            for (FieldEntry fieldEntry : fieldList)
                fields.put(fieldToJson(fieldEntry));
            container.put("fields", fields);
        }
        return container;
    }

    /** Converts one EMA field entry to JSON. */
    private static JSONObject fieldToJson(FieldEntry fieldEntry) {
        String value = formatValue(fieldEntry);
        return new JSONObject()
                .put("FID", fieldEntry.fieldId())
                .put("FieldName", fieldEntry.name())
                .put("dataType", DataType.asString(fieldEntry.load().dataType()))
                .put("value", value != null ? value : JSONObject.NULL);
    }

    /** Formats a field value as readable text, or returns null for an EMA blank. */
    private static String formatValue(FieldEntry fieldEntry) {
        if (Data.DataCode.BLANK == fieldEntry.code())
            return null;

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
            default -> fieldEntry.load().toString();
        };
    }
}

