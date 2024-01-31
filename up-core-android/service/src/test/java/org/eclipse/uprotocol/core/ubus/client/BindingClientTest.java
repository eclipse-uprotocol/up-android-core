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
package org.eclipse.uprotocol.core.ubus.client;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.os.Binder;
import android.os.IBinder.DeathRecipient;
import android.os.RemoteException;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.eclipse.uprotocol.core.TestBase;
import org.eclipse.uprotocol.core.ubus.IUListener;
import org.eclipse.uprotocol.v1.UCode;
import org.eclipse.uprotocol.v1.UMessage;
import org.eclipse.uprotocol.v1.internal.ParcelableUMessage;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class BindingClientTest extends TestBase {
    private final Credentials mCredentials = new Credentials(PACKAGE_NAME, 0, 0, CLIENT_URI);
    private final IUListener mListener = mock(IUListener.class);
    private final DeathRecipient mDeathRecipient = mock(DeathRecipient.class);
    private final Binder mToken = new Binder();
    private final Client mClient = BindingClient.newClient(mCredentials, mToken, mDeathRecipient, mListener);

    @Test
    public void testNewClient() {
        assertNotNull(BindingClient.newClient(mCredentials, mToken, mDeathRecipient, mListener));
    }

    @Test
    @SuppressWarnings("DataFlowIssue")
    public void testNewClientNegative() {
        assertThrowsStatusException(UCode.INVALID_ARGUMENT, () ->
                BindingClient.newClient(mCredentials, mToken, mDeathRecipient, null));
    }

    @Test
    public void testIsInternal() {
        assertFalse(mClient.isInternal());
    }

    @Test
    public void testGetListener() {
        assertEquals(mListener, mClient.getListener());
    }

    @Test
    public void testSend() throws RemoteException {
        final UMessage message = buildPublishMessage();
        mClient.send(message);
        verify(mListener, times(1)).onReceive(new ParcelableUMessage(message));
    }
}
