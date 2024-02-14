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
package org.eclipse.uprotocol.core.ubus;

import static org.eclipse.uprotocol.common.util.UStatusUtils.STATUS_OK;
import static org.eclipse.uprotocol.common.util.UStatusUtils.buildStatus;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import static java.util.Collections.emptyList;

import android.os.Binder;
import android.os.IBinder;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.eclipse.uprotocol.common.UStatusException;
import org.eclipse.uprotocol.core.TestBase;
import org.eclipse.uprotocol.v1.UCode;
import org.eclipse.uprotocol.v1.UMessage;
import org.eclipse.uprotocol.v1.UStatus;
import org.eclipse.uprotocol.v1.internal.ParcelableUEntity;
import org.eclipse.uprotocol.v1.internal.ParcelableUMessage;
import org.eclipse.uprotocol.v1.internal.ParcelableUStatus;
import org.eclipse.uprotocol.v1.internal.ParcelableUUri;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@RunWith(AndroidJUnit4.class)
public class UBusAdapterTest extends TestBase {
    private final IBinder mClientToken = new Binder();
    private final MockListener mListener = new MockListener();
    private UBus mUBus;
    private UBusAdapter mUBusAdapter;

    @Before
    public void setUp() {
        mUBus = mock(UBus.class);
        mUBusAdapter = new UBusAdapter(mUBus);
    }

    @Test
    public void testRegisterClient() {
        final UStatus status = STATUS_OK;
        when(mUBus.registerClient(PACKAGE_NAME, CLIENT, mClientToken, mListener)).thenReturn(status);
        assertEquals(new ParcelableUStatus(status),
                mUBusAdapter.registerClient(PACKAGE_NAME, new ParcelableUEntity(CLIENT), mClientToken, 0, mListener));
    }

    @Test
    public void testRegisterClientExceptionally() {
        final UStatus status = buildStatus(UCode.UNKNOWN);
        when(mUBus.registerClient(PACKAGE_NAME, CLIENT, mClientToken, mListener))
                .thenThrow(new UStatusException(status));
        assertEquals(new ParcelableUStatus(status),
                mUBusAdapter.registerClient(PACKAGE_NAME, new ParcelableUEntity(CLIENT), mClientToken, 0, mListener));
    }

    @Test
    public void testUnregisterClient() {
        final UStatus status = STATUS_OK;
        when(mUBus.unregisterClient(mClientToken)).thenReturn(status);
        assertEquals(new ParcelableUStatus(status), mUBusAdapter.unregisterClient(mClientToken));
    }

    @Test
    public void testUnregisterClientExceptionally() {
        final UStatus status = buildStatus(UCode.UNKNOWN);
        when(mUBus.unregisterClient(mClientToken)).thenThrow(new UStatusException(status));
        assertEquals(new ParcelableUStatus(status), mUBusAdapter.unregisterClient(mClientToken));
    }

    @Test
    public void testSend() {
        final UMessage message = buildPublishMessage();
        final UStatus status = STATUS_OK;
        when(mUBus.send(message, mClientToken)).thenReturn(status);
        assertEquals(new ParcelableUStatus(status), mUBusAdapter.send(new ParcelableUMessage(message), mClientToken));
    }

    @Test
    public void testSendExceptionally() {
        final UMessage message = buildPublishMessage();
        final UStatus status = buildStatus(UCode.UNKNOWN);
        when(mUBus.send(message, mClientToken)).thenThrow(new UStatusException(status));
        assertEquals(new ParcelableUStatus(status), mUBusAdapter.send(new ParcelableUMessage(message), mClientToken));
    }

    @Test
    public void testPull() {
        final UMessage message = buildPublishMessage();
        when(mUBus.pull(RESOURCE_URI, 1, 0, mClientToken)).thenReturn(List.of(message));
        ParcelableUMessage[] messages = mUBusAdapter.pull(new ParcelableUUri(RESOURCE_URI), 1, 0, mClientToken);
        assertArrayEquals(new ParcelableUMessage[] { new ParcelableUMessage(message) }, messages);
    }

    @Test
    public void testPullExceptionally() {
        final UStatus status = buildStatus(UCode.UNKNOWN);
        when(mUBus.pull(RESOURCE_URI, 1, 0, mClientToken)).thenThrow(new UStatusException(status));
        assertNull(mUBusAdapter.pull(new ParcelableUUri(RESOURCE_URI), 1, 0, mClientToken));
    }

    @Test
    public void testPullNotPublished() {
        when(mUBus.pull(RESOURCE_URI, 1, 0, mClientToken)).thenReturn(emptyList());
        assertNull(mUBusAdapter.pull(new ParcelableUUri(RESOURCE_URI), 1, 0, mClientToken));
    }

    @Test
    public void testEnableDispatchingExceptionally() {
        final UStatus status = buildStatus(UCode.UNKNOWN);
        when(mUBus.enableDispatching(RESOURCE_URI, 0, mClientToken)).thenThrow(new UStatusException(status));
        assertEquals(new ParcelableUStatus(status),
                mUBusAdapter.enableDispatching(new ParcelableUUri(RESOURCE_URI), 0, mClientToken));
    }

    @Test
    public void testEnableDispatching() {
        final UStatus status = STATUS_OK;
        when(mUBus.enableDispatching(RESOURCE_URI, 0, mClientToken)).thenReturn(status);
        assertEquals(new ParcelableUStatus(status),
                mUBusAdapter.enableDispatching(new ParcelableUUri(RESOURCE_URI), 0, mClientToken));
    }

    @Test
    public void testDisableDispatchingExceptionally() {
        final UStatus status = buildStatus(UCode.UNKNOWN);
        when(mUBus.disableDispatching(RESOURCE_URI, 0, mClientToken)).thenThrow(new UStatusException(status));
        assertEquals(new ParcelableUStatus(status),
                mUBusAdapter.disableDispatching(new ParcelableUUri(RESOURCE_URI), 0, mClientToken));
    }

    @Test
    public void testDisableDispatching() {
        final UStatus status = STATUS_OK;
        when(mUBus.disableDispatching(RESOURCE_URI, 0, mClientToken)).thenReturn(status);
        assertEquals(new ParcelableUStatus(status),
                mUBusAdapter.disableDispatching(new ParcelableUUri(RESOURCE_URI), 0, mClientToken));
    }
}
