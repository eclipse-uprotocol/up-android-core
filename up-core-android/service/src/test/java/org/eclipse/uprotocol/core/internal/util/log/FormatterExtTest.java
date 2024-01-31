/*
 * Copyright (c) 2024 General Motors GTO LLC
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * SPDX-FileType: SOURCE
 * SPDX-FileCopyrightText: 2023 General Motors GTO LLC
 * SPDX-License-Identifier: Apache-2.0
 */
package org.eclipse.uprotocol.core.internal.util.log;

import static org.junit.Assert.assertEquals;

import android.os.Binder;
import android.os.IBinder;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.eclipse.uprotocol.core.TestBase;
import org.eclipse.uprotocol.core.usubscription.v3.SubscriberInfo;
import org.eclipse.uprotocol.core.usubscription.v3.SubscriptionStatus;
import org.eclipse.uprotocol.core.usubscription.v3.SubscriptionStatus.State;
import org.eclipse.uprotocol.core.usubscription.v3.Update;
import org.eclipse.uprotocol.v1.UCode;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class FormatterExtTest extends TestBase {

    @Test
    public void testStringifyUpdate() {
        final Update update = Update.newBuilder()
                .setTopic(RESOURCE_URI)
                .setSubscriber(SubscriberInfo.newBuilder()
                        .setUri(CLIENT_URI)
                        .build())
                .setStatus(SubscriptionStatus.newBuilder()
                        .setState(State.SUBSCRIBED)
                        .setCode(UCode.OK)
                        .build())
                .build();
        assertEquals("[topic: /test.srv/1/door.front_left#Door, subscriber: /test.app/1, state: SUBSCRIBED]",
                FormatterExt.stringify(update));
    }

    @Test
    public void testStringifyUpdateNull() {
        assertEquals("", FormatterExt.stringify((Update) null));
    }

    @Test
    public void testStringifyIBinder() {
        final IBinder binder = new Binder();
        assertEquals(Integer.toHexString(binder.hashCode()), FormatterExt.stringify(binder));
    }

    @Test
    public void testStringifyIBinderNull() {
        assertEquals("", FormatterExt.stringify((IBinder) null));
    }
}
