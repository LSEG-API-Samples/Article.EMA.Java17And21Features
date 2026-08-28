/*|-----------------------------------------------------------------------------
 *|            This source code is provided under the Apache 2.0 license
 *|  and is provided AS IS with no warranty or guarantee of fit for purpose.
 *|                See the project's LICENSE.md for details.
 *|           Copyright (C) 2020-2026 LSEG. All rights reserved.
 *|-----------------------------------------------------------------------------
 */

package com.lseg.ema.example;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.json.JSONObject;

import com.refinitiv.ema.access.EmaFactory;
import com.refinitiv.ema.access.Msg;
import com.refinitiv.ema.access.OmmState.DataState;
import com.refinitiv.ema.access.OmmState.StatusCode;
import com.refinitiv.ema.access.OmmState.StreamState;

/** Tests the stateless EMA item-message JSON schema. */
class EmaJsonConverterTest {

    /** Verifies the item-name fallback for every queued message type. */
    @Test
    void usesNotSetItemNameForUnnamedMessages() {
        List<Msg> messages = List.of(
                EmaFactory.createRefreshMsg()
                        .state(StreamState.OPEN, DataState.OK, StatusCode.NONE, "open")
                        .complete(true),
                EmaFactory.createUpdateMsg());

        assertTrue(messages.stream()
                .map(message -> convert("Item", message))
                .allMatch(json -> "<not set>".equals(json.getString("itemName"))));
    }

    /** Verifies common callback metadata and named-item output. */
    @Test
    void convertsCommonMessageMetadata() {
        JSONObject json = convert("Update", EmaFactory.createUpdateMsg().name("TRI.N"));

        assertEquals(11, json.getLong("sequence"));
        assertEquals("Update", json.getString("type"));
        assertEquals(7, json.getLong("handle"));
        assertEquals(3, json.getLong("parentHandle"));
        assertEquals("test\"closure\n", json.getString("closure"));
        assertEquals("TRI.N", json.getString("itemName"));
    }

    /** Converts one test message and parses the generated JSON. */
    private static JSONObject convert(String messageType, Msg message) {
        return new JSONObject(EmaJsonConverter.toJson(
                11,
                messageType,
                7,
                3,
                "test\"closure\n",
                message));
    }
}

