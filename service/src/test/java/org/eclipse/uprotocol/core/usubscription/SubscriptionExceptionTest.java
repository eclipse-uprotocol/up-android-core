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
package org.eclipse.uprotocol.core.usubscription;

import static org.junit.Assert.assertEquals;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.eclipse.uprotocol.core.usubscription.v3.SubscriptionStatus;
import org.eclipse.uprotocol.v1.UCode;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class SubscriptionExceptionTest extends SubscriptionTestBase {
    @Test
    public void testGetStatus() {
        final SubscriptionStatus status = SubscriptionStatus.newBuilder()
                .setState(SubscriptionStatus.State.UNSUBSCRIBED)
                .setCode(UCode.ABORTED)
                .build();
        final SubscriptionException exception = new SubscriptionException(status);
        assertEquals(status, exception.getStatus());
    }

    @Test
    public void testGetMessage() {
        final SubscriptionStatus status = SubscriptionStatus.newBuilder()
                .setState(SubscriptionStatus.State.SUBSCRIBED)
                .setCode(UCode.OK)
                .setMessage(SUBSCRIBERS_DETAILS)
                .build();
        final SubscriptionException exception = new SubscriptionException(status);
        assertEquals(SUBSCRIBERS_DETAILS, exception.getMessage());
    }

    @Test
    public void testGetMessageWithOutMessage() {
        final SubscriptionStatus status = SubscriptionStatus.newBuilder()
                .setState(SubscriptionStatus.State.SUBSCRIBED)
                .setCode(UCode.OK)
                .setMessage("")
                .build();
        final SubscriptionException exception = new SubscriptionException(status);
        assertEquals("", exception.getMessage());
    }
}
