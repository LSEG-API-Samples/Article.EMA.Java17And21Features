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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.refinitiv.ema.access.EmaFactory;
import com.refinitiv.ema.access.OmmConsumerEvent;
import com.refinitiv.ema.access.OmmState.DataState;
import com.refinitiv.ema.access.OmmState.StatusCode;
import com.refinitiv.ema.access.OmmState.StreamState;
import com.refinitiv.ema.access.RefreshMsg;
import com.refinitiv.ema.access.StatusMsg;
import com.refinitiv.ema.rdm.EmaRdm;

/** Tests direct login handling performed by the EMA callback client. */
class AppClientTest {

    private static final OmmConsumerEvent EVENT = new TestConsumerEvent("login-test");

    /** Verifies that Login Refresh bypasses JSON conversion. */
    @Test
    void printsLoginRefreshWithoutJsonConversion() {
        List<String> output = Collections.synchronizedList(new ArrayList<>());
        RefreshMsg loginRefresh = EmaFactory.createRefreshMsg()
                .domainType(EmaRdm.MMT_LOGIN)
                .state(StreamState.OPEN, DataState.OK, StatusCode.NONE, "login accepted")
                .complete(true);
        String expectedOutput = EmaFactory.Domain.createLoginRefresh()
                .message(loginRefresh)
                .toString();

        try (AppClient client = new AppClient(1, 10, 1, output::add)) {
            client.onRefreshMsg(loginRefresh, EVENT);
        }

        assertEquals(List.of(expectedOutput), output);
        assertFalse(output.getFirst().contains("\"sequence\""));
    }

    /** Verifies that Login Status bypasses JSON conversion. */
    @Test
    void printsLoginStatusWithoutJsonConversion() {
        List<String> output = Collections.synchronizedList(new ArrayList<>());
        StatusMsg loginStatus = EmaFactory.createStatusMsg()
                .domainType(EmaRdm.MMT_LOGIN)
                .state(StreamState.OPEN, DataState.SUSPECT, StatusCode.NONE, "login suspect");
        String expectedOutput = EmaFactory.Domain.createLoginStatus()
                .message(loginStatus)
                .toString();

        try (AppClient client = new AppClient(1, 10, 1, output::add)) {
            client.onStatusMsg(loginStatus, EVENT);
        }

        assertEquals(List.of(expectedOutput), output);
        assertFalse(output.getFirst().contains("\"sequence\""));
    }
}
