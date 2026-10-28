/*|-----------------------------------------------------------------------------
 *|            This source code is provided under the Apache 2.0 license
 *|  and is provided AS IS with no warranty or guarantee of fit for purpose.
 *|                See the project's LICENSE.md for details.
 *|           Copyright (C) 2020-2026 LSEG. All rights reserved.
 *|-----------------------------------------------------------------------------
 */

package com.lseg.ema.example;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.json.JSONArray;
import org.json.JSONObject;

import com.refinitiv.ema.access.EmaFactory;
import com.refinitiv.ema.access.FieldList;
import com.refinitiv.ema.access.Msg;
import com.refinitiv.ema.access.OmmReal;
import com.refinitiv.ema.access.OmmState.DataState;
import com.refinitiv.ema.access.OmmState.StatusCode;
import com.refinitiv.ema.access.OmmState.StreamState;
import com.refinitiv.ema.access.UpdateMsg;
import com.refinitiv.ema.rdm.DataDictionary;
import com.refinitiv.eta.codec.Codec;

/** Tests the stateless EMA item-message JSON schema. */
class EmaJsonConverterTest {

    // Test-only dictionaries in the project root; Surefire runs tests from that directory.
    private static final String FIELD_DICTIONARY_FILE = "etc/RDMFieldDictionary";
    private static final String ENUM_TYPE_DICTIONARY_FILE = "etc/enumtype.def";
    private static final int BID_FID = 22;
    private static final int ASK_FID = 25;

    private static DataDictionary dictionary;

    /** Loads the local field and enumerated-type dictionaries once for FieldList decoding. */
    @BeforeAll
    static void loadDictionary() {
        dictionary = EmaFactory.createDataDictionary();
        dictionary.loadFieldDictionary(FIELD_DICTIONARY_FILE);
        dictionary.loadEnumTypeDictionary(ENUM_TYPE_DICTIONARY_FILE);
    }

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

    /** Verifies that a Refresh includes its state and an Update does not. */
    @Test
    void includesStateOnlyForRefresh() {
        JSONObject refresh = convert("Refresh", EmaFactory.createRefreshMsg()
                .name("TRI.N")
                .state(StreamState.OPEN, DataState.SUSPECT, StatusCode.NONE, "delayed")
                .complete(true));
        JSONObject update = convert("Update", EmaFactory.createUpdateMsg().name("TRI.N"));

        JSONObject state = refresh.getJSONObject("state");
        assertEquals("Open", state.getString("stream"));
        assertEquals("Suspect", state.getString("data"));
        assertEquals("delayed", state.getString("text"));
        assertFalse(update.has("state"));
    }

    /** Verifies that an empty payload reports its data type without a fields array. */
    @Test
    void reportsPayloadTypeWithoutFieldsWhenNoFieldList() {
        JSONObject payload = convert("Update", EmaFactory.createUpdateMsg().name("TRI.N"))
                .getJSONObject("payload");

        assertEquals("NoData", payload.getString("dataType"));
        assertFalse(payload.has("fields"));
    }

    /** Verifies that a blank field is reported as " blank" and a set field keeps its formatted value. */
    @Test
    void reportsBlankFieldAsBlankText() throws ReflectiveOperationException {
        FieldList fieldList = EmaFactory.createFieldList();
        fieldList.add(EmaFactory.createFieldEntry().codeReal(BID_FID));
        fieldList.add(EmaFactory.createFieldEntry().real(ASK_FID, 3332, OmmReal.MagnitudeType.EXPONENT_NEG_2));

        UpdateMsg update = decodeWithDictionary(EmaFactory.createUpdateMsg()
                .name("/THB=")
                .payload(fieldList));
        JSONArray fields = convert("Update", update).getJSONObject("payload").getJSONArray("fields");

        JSONObject bid = fields.getJSONObject(0);
        assertEquals(BID_FID, bid.getInt("FID"));
        assertEquals("BID", bid.getString("FieldName"));
        assertEquals(" blank", bid.getString("value"));

        JSONObject ask = fields.getJSONObject(1);
        assertEquals("ASK", ask.getString("FieldName"));
        assertEquals("33.32", ask.getString("value"));
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

    /**
     * Decodes an encoded message against the local dictionary.
     * EMA has no public API for this, so the test calls the same package-private steps that
     * {@code UpdateMsg.toString(DataDictionary)} uses internally.
     */
    private static UpdateMsg decodeWithDictionary(UpdateMsg encoded) throws ReflectiveOperationException {
        Object encodedBuffer = invokeInternal(encoded, "encodedData");
        Object rsslDictionary = invokeInternal(dictionary, "rsslDataDictionary");

        // Decoding needs an UpdateMsgImpl bound to EMA's global object manager.
        Class<?> objectManagerType = Class.forName("com.refinitiv.ema.access.EmaObjectManager");
        Field globalObjectManager = objectManagerType.getDeclaredField("GlobalObjectManager");
        globalObjectManager.setAccessible(true);
        Constructor<?> constructor = encoded.getClass().getDeclaredConstructor(objectManagerType);
        constructor.setAccessible(true);
        UpdateMsg decoded = (UpdateMsg) constructor.newInstance(globalObjectManager.get(null));

        Method decode = findMethod(decoded.getClass(), "decode",
                com.refinitiv.eta.codec.Buffer.class, int.class, int.class,
                com.refinitiv.eta.codec.DataDictionary.class, Object.class);
        decode.invoke(decoded, encodedBuffer, Codec.majorVersion(), Codec.minorVersion(), rsslDictionary, null);
        return decoded;
    }

    /** Invokes a no-argument, non-public EMA method. */
    private static Object invokeInternal(Object target, String methodName) throws ReflectiveOperationException {
        return findMethod(target.getClass(), methodName).invoke(target);
    }

    /** Finds a declared method in the class hierarchy and makes it accessible. */
    private static Method findMethod(Class<?> type, String name, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                Method method = current.getDeclaredMethod(name, parameterTypes);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException exception) {
                // Continue with the superclass.
            }
        }
        throw new NoSuchMethodException(type.getName() + "." + name);
    }
}
