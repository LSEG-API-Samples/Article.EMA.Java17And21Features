/*|-----------------------------------------------------------------------------
 *|            This source code is provided under the Apache 2.0 license
 *|  and is provided AS IS with no warranty or guarantee of fit for purpose.
 *|                See the project's LICENSE.md for details.
 *|           Copyright (C) 2020-2026 LSEG. All rights reserved.
 *|-----------------------------------------------------------------------------
 */

package com.lseg.ema.example;

import java.util.List;

import com.refinitiv.ema.access.ChannelInformation;
import com.refinitiv.ema.access.OmmConsumerEvent;

/** Supplies stable callback metadata to focused unit tests. */
record TestConsumerEvent(String closure) implements OmmConsumerEvent {

    /** Returns a fixed item handle. */
    @Override
    public long handle() {
        return 7;
    }

    /** Returns a fixed parent handle. */
    @Override
    public long parentHandle() {
        return 3;
    }

    /** Returns no channel because tests do not open an EMA connection. */
    @Override
    public ChannelInformation channelInformation() {
        return null;
    }

    /** Ignores session channel output because tests do not open an EMA connection. */
    @Override
    public void sessionChannelInfo(List<ChannelInformation> sessionChannelInfoList) {
    }
}

