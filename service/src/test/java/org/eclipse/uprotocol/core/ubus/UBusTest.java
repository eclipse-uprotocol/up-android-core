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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static java.util.Collections.emptyList;

import android.content.Context;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.eclipse.uprotocol.common.UStatusException;
import org.eclipse.uprotocol.core.TestBase;
import org.eclipse.uprotocol.core.UCore;
import org.eclipse.uprotocol.core.ubus.client.BindingClient;
import org.eclipse.uprotocol.core.ubus.client.Client;
import org.eclipse.uprotocol.core.ubus.client.ClientManager;
import org.eclipse.uprotocol.transport.UListener;
import org.eclipse.uprotocol.v1.UAuthority;
import org.eclipse.uprotocol.v1.UCode;
import org.eclipse.uprotocol.v1.UMessage;
import org.eclipse.uprotocol.v1.UStatus;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class UBusTest extends TestBase {
    private final IBinder mClientToken = new Binder();
    private final Client mClient = mock(BindingClient.class);
    private final ClientManager mClientManager = mock(ClientManager.class);
    private final Dispatcher mDispatcher = mock(Dispatcher.class);
    private UBus mUBus;
    private UCore mUCore;

    @Before
    public void setUp() {
        setLogLevel(Log.VERBOSE);
        final Context context = mock(Context.class);
        mUBus = new UBus(context, mClientManager, mDispatcher);
        mUCore = newMockUCoreBuilder(context).setUBus(mUBus).build();
        when(context.getPackageName()).thenReturn(PACKAGE_NAME);
        when(mClientManager.getClientOrThrow(mClientToken)).thenReturn(mClient);
        when(mDispatcher.dispatchFrom(any(), any())).thenReturn(STATUS_OK);
        mUCore.init();
    }

    @After
    public void tearDown() {
        mUCore.shutdown();
    }

    private static void setLogLevel(int level) {
        UBus.Component.DEBUG = (level <= Log.DEBUG);
        UBus.Component.VERBOSE = (level <= Log.VERBOSE);
        UBus.Component.TRACE_EVENTS = (level <= Log.VERBOSE);
    }

    @Test
    public void testInit() {
        mUBus.getComponents().forEach(component -> verify(component, times(1)).init(argThat(components -> {
            assertEquals(mClientManager, components.getClientManager());
            assertNotNull(components.getHandler());
            assertEquals(mDispatcher, components.getDispatcher());
            return true;
        })));
        verify(mClientManager, times(1)).registerClient(any(), eq(UBus.ENTITY), any(), any());
    }

    @Test
    public void testStartup() {
        mUBus.startup();
        mUBus.getComponents().forEach(component -> verify(component, times(1)).startup());
    }

    @Test
    public void testShutdown() {
        mUBus.shutdown();
        mUBus.getComponents().forEach(component -> verify(component, times(1)).shutdown());
    }

    @Test
    public void testClearCache() {
        mUBus.clearCache();
        mUBus.getComponents().forEach(component -> verify(component, times(1)).clearCache());
    }

    @Test
    public void testGetComponents() {
        final List<UBus.Component> components = mUBus.getComponents();
        assertTrue(components.contains(mClientManager));
        assertTrue(components.contains(mDispatcher));
    }

    @Test
    public void testGetDeviceAuthority() {
        assertEquals(UAuthority.getDefaultInstance(), mUBus.getDeviceAuthority());
    }

    @Test
    public void testRegisterClientBinding() {
        final MockListener listener = new MockListener();
        when(mClientManager.registerClient(PACKAGE_NAME, CLIENT2, mClientToken, listener)).thenReturn(STATUS_OK);
        assertEquals(STATUS_OK, mUBus.registerClient(PACKAGE_NAME, CLIENT2, mClientToken, listener));
    }

    @Test
    public void testRegisterClientInternal() {
        final UListener listener = mock(UListener.class);
        when(mClientManager.registerClient(PACKAGE_NAME, CLIENT2, mClientToken, listener)).thenReturn(STATUS_OK);
        assertEquals(STATUS_OK, mUBus.registerClient(CLIENT2, mClientToken, listener));
    }

    @Test
    public void testUnregisterClient() {
        when(mClientManager.unregisterClient(mClientToken)).thenReturn(STATUS_OK);
        assertEquals(STATUS_OK, mUBus.unregisterClient(mClientToken));
    }

    @Test
    public void testSendInvalidMessage() {
        assertStatus(UCode.INVALID_ARGUMENT, mUBus.send(EMPTY_MESSAGE, mClientToken));
    }

    @Test
    public void testSendNotRegisteredClient() {
        final UStatus status = buildStatus(UCode.UNAUTHENTICATED);
        when(mClientManager.getClientOrThrow(mClientToken)).thenThrow(new UStatusException(status));
        assertEquals(status, mUBus.send(buildPublishMessage(), mClientToken));
    }

    @Test
    public void testSendDispatcherFailure() {
        final UStatus status = buildStatus(UCode.UNAUTHENTICATED);
        when(mDispatcher.dispatchFrom(any(), eq(mClient))).thenReturn(status);
        assertEquals(status, mUBus.send(buildPublishMessage(), mClientToken));
    }

    @Test
    public void testSendExceptionally() {
        final UStatus status = buildStatus(UCode.UNKNOWN);
        when(mDispatcher.dispatchFrom(any(), eq(mClient))).thenThrow(new UStatusException(status));
        assertEquals(status, mUBus.send(buildPublishMessage(), mClientToken));
    }

    @Test
    public void testSendPublish() {
        setLogLevel(Log.INFO);
        final UStatus status = STATUS_OK;
        when(mDispatcher.dispatchFrom(any(), eq(mClient))).thenReturn(status);
        assertEquals(status, mUBus.send(buildPublishMessage(), mClientToken));
    }

    @Test
    public void testSendRequestMessage() {
        final UStatus status = STATUS_OK;
        when(mDispatcher.dispatchFrom(any(), eq(mClient))).thenReturn(status);
        assertEquals(status, mUBus.send(buildRequestMessage(), mClientToken));
    }

    @Test
    public void testSendResponseMessage() {
        final UStatus status = STATUS_OK;
        when(mDispatcher.dispatchFrom(any(), eq(mClient))).thenReturn(status);
        assertEquals(status, mUBus.send(buildResponseMessage(buildRequestMessage()), mClientToken));
    }

    @Test
    public void testPullNotRegisteredClient() {
        final UStatus status = buildStatus(UCode.UNAUTHENTICATED);
        when(mClientManager.getClientOrThrow(mClientToken)).thenThrow(new UStatusException(status));
        assertEquals(emptyList(), mUBus.pull(RESOURCE_URI, 1, 0, mClientToken));
    }

    @Test
    public void testPullExceptionally() {
        final UStatus status = buildStatus(UCode.UNKNOWN);
        when(mDispatcher.pull(RESOURCE_URI, 1, mClient)).thenThrow(new UStatusException(status));
        assertEquals(emptyList(), mUBus.pull(RESOURCE_URI, 1, 0, mClientToken));
    }

    @Test
    public void testPull() {
        final List<UMessage> result = List.of(buildPublishMessage());
        when(mDispatcher.pull(RESOURCE_URI, 1, mClient)).thenReturn(result);
        assertEquals(result, mUBus.pull(RESOURCE_URI, 1, 0, mClientToken));
    }

    @Test
    public void testEnableDispatchingNotRegisteredClient() {
        final UStatus status = buildStatus(UCode.UNAUTHENTICATED);
        when(mClientManager.getClientOrThrow(mClientToken)).thenThrow(new UStatusException(status));
        assertEquals(status, mUBus.enableDispatching(RESOURCE_URI, 0, mClientToken));
    }

    @Test
    public void testEnableDispatchingExceptionally() {
        final UStatus status = buildStatus(UCode.UNKNOWN);
        when(mDispatcher.enableDispatching(RESOURCE_URI, 0, mClient)).thenThrow(new UStatusException(status));
        assertEquals(status, mUBus.enableDispatching(RESOURCE_URI, 0, mClientToken));
    }

    @Test
    public void testEnableDispatching() {
        final UStatus status = STATUS_OK;
        when(mDispatcher.enableDispatching(RESOURCE_URI, 0, mClient)).thenReturn(status);
        assertEquals(status, mUBus.enableDispatching(RESOURCE_URI, 0, mClientToken));
    }

    @Test
    public void testDisableDispatchingNotRegisteredClient() {
        final UStatus status = buildStatus(UCode.UNAUTHENTICATED);
        when(mClientManager.getClientOrThrow(mClientToken)).thenThrow(new UStatusException(status));
        assertEquals(status, mUBus.disableDispatching(RESOURCE_URI, 0, mClientToken));
    }

    @Test
    public void testDisableDispatchingExceptionally() {
        final UStatus status = buildStatus(UCode.UNKNOWN);
        when(mDispatcher.disableDispatching(RESOURCE_URI, 0, mClient)).thenThrow(new UStatusException(status));
        assertEquals(status, mUBus.disableDispatching(RESOURCE_URI, 0, mClientToken));
    }

    @Test
    public void testDisableDispatching() {
        final UStatus status = STATUS_OK;
        when(mDispatcher.disableDispatching(RESOURCE_URI, 0, mClient)).thenReturn(status);
        assertEquals(status, mUBus.disableDispatching(RESOURCE_URI, 0, mClientToken));
    }

    @Test
    public void testIsTopicCreated() {
        final SubscriptionCache cache = new SubscriptionCache();
        when(mDispatcher.getSubscriptionCache()).thenReturn(cache);
        assertFalse(mUBus.isTopicCreated(RESOURCE_URI, CLIENT_URI));
        cache.addTopic(RESOURCE_URI, CLIENT_URI);
        assertTrue(mUBus.isTopicCreated(RESOURCE_URI, CLIENT_URI));
    }

    @Test
    public void testDump() {
        final PrintWriter writer = new PrintWriter(new StringWriter());
        final String[] args = {};
        mUBus.dump(writer, args);
        verify(mDispatcher, times(1)).dump(writer, args);
    }

    @Test
    public void testComponentDefaultImplementation() {
        final UBus.Component component = new UBus.Component() {};
        assertNotNull(component);
        component.init(mock(UBus.Components.class));
        component.startup();
        component.shutdown();
        component.clearCache();
    }
}
