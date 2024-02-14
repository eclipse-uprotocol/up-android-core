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

import static org.eclipse.uprotocol.core.ubus.client.ClientManager.REMOTE_CLIENT_NAME;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.os.Binder;
import android.os.IBinder;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.eclipse.uprotocol.core.TestBase;
import org.eclipse.uprotocol.transport.UListener;
import org.eclipse.uprotocol.v1.UCode;
import org.eclipse.uprotocol.v1.UMessage;
import org.eclipse.uprotocol.v1.UUri;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class InternalClientTest extends TestBase {
    private final Credentials mCredentials = new Credentials(PACKAGE_NAME, 0, 0, CLIENT_URI);
    private final IBinder mToken = new Binder();
    private final UListener mListener = mock(UListener.class);
    private final InternalClient mClient = new InternalClient(mCredentials, mToken, mListener);

    @Test
    @SuppressWarnings("DataFlowIssue")
    public void testConstructorNegative() {
        assertThrowsStatusException(UCode.INVALID_ARGUMENT, () ->
                new InternalClient(mCredentials, mToken, null));
    }

    @Test
    public void testIsRemote() {
        final UUri clientUri = buildUri(null, buildEntity(REMOTE_CLIENT_NAME, 1), null);
        final Client client = new InternalClient(new Credentials(PACKAGE_NAME, 0, 0, clientUri), mToken, mListener);
        assertTrue(client.isRemote());
    }

    @Test
    public void testIsInternal() {
        assertTrue(mClient.isInternal());
    }

    @Test
    public void testGetListener() {
        assertEquals(mListener, mClient.getListener());
    }

    @Test
    public void testSend() {
        final UMessage message = buildPublishMessage();
        mClient.send(message);
        verify(mListener, times(1)).onReceive(message);
    }
}
