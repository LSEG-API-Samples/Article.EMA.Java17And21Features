/*|-----------------------------------------------------------------------------
 *|            This source code is provided under the Apache 2.0 license
 *|  and is provided AS IS with no warranty or guarantee of fit for purpose.
 *|                See the project's LICENSE.md for details.
 *|           Copyright (C) 2020-2026 LSEG. All rights reserved.
 *|-----------------------------------------------------------------------------
 */

package com.lseg.ema.example;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.refinitiv.ema.access.EmaFactory;
import com.refinitiv.ema.access.OmmConsumerConfig;

import com.lseg.ema.example.Utility.CommandLineResult;

/** Tests command-line parsing and validation. */
class UtilityTest {

    private String originalServiceName;
    private String[] originalItemNames;
    private OmmConsumerConfig config;

    /** Saves the shared defaults because Utility stores parsed options in static fields. */
    @BeforeEach
    void saveDefaults() {
        originalServiceName = Utility.serviceName;
        originalItemNames = Utility.itemNames;
        config = EmaFactory.createOmmConsumerConfig();
    }

    /** Restores the shared defaults so each test starts from the same state. */
    @AfterEach
    void restoreDefaults() {
        Utility.serviceName = originalServiceName;
        Utility.itemNames = originalItemNames;
    }

    /** Verifies that the service and a trimmed batch of item names are read. */
    @Test
    void readsServiceAndTrimmedItemNames() {
        String[] args = {"-serviceName", "MY_SERVICE", "-itemNames", " AUD= , ,HKD= "};

        assertEquals(CommandLineResult.CONTINUE, Utility.readCommandlineArgs(args, config));

        assertEquals("MY_SERVICE", Utility.serviceName);
        assertArrayEquals(new String[]{"AUD=", "HKD="}, Utility.itemNames);
    }

    /** Verifies that the defaults are kept when no options are given. */
    @Test
    void keepsDefaultsWithoutOptions() {
        assertEquals(CommandLineResult.CONTINUE, Utility.readCommandlineArgs(new String[0], config));

        assertEquals("ELEKTRON_DD", Utility.serviceName);
        assertArrayEquals(new String[]{"/THB="}, Utility.itemNames);
    }

    /** Verifies that help stops startup without being treated as an error. */
    @Test
    void stopsAfterHelp() {
        assertEquals(CommandLineResult.HELP_SHOWN, Utility.readCommandlineArgs(new String[]{"-?"}, config));
    }

    /** Verifies that an option without a value is rejected. */
    @Test
    void rejectsOptionWithoutValue() {
        assertEquals(CommandLineResult.INVALID, Utility.readCommandlineArgs(new String[]{"-serviceName"}, config));
    }

    /** Verifies that an item list containing only separators is rejected. */
    @Test
    void rejectsItemListWithoutNames() {
        assertEquals(CommandLineResult.INVALID,
                Utility.readCommandlineArgs(new String[]{"-itemNames", " , "}, config));
    }

    /** Verifies that a non-numeric or out-of-range proxy port is rejected. */
    @Test
    void rejectsInvalidProxyPort() {
        assertEquals(CommandLineResult.INVALID, Utility.readCommandlineArgs(new String[]{"-pp", "abc"}, config));
        assertEquals(CommandLineResult.INVALID, Utility.readCommandlineArgs(new String[]{"-pp", "70000"}, config));
    }

    /** Verifies that an unknown option is rejected. */
    @Test
    void rejectsUnknownOption() {
        assertEquals(CommandLineResult.INVALID, Utility.readCommandlineArgs(new String[]{"-unknown"}, config));
    }
}
