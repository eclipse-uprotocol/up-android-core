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

import static org.eclipse.uprotocol.common.util.log.Formatter.stringify;
import static org.eclipse.uprotocol.core.internal.util.UMessageUtils.buildFailedResponseMessage;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static java.util.Objects.requireNonNull;

import android.content.Context;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.eclipse.uprotocol.core.TestBase;
import org.eclipse.uprotocol.core.UCore;
import org.eclipse.uprotocol.core.ubus.client.Client;
import org.eclipse.uprotocol.core.ubus.client.ClientManager;
import org.eclipse.uprotocol.transport.UListener;
import org.eclipse.uprotocol.v1.UCode;
import org.eclipse.uprotocol.v1.UEntity;
import org.eclipse.uprotocol.v1.UMessage;
import org.eclipse.uprotocol.v1.UUri;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.robolectric.RuntimeEnvironment;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Set;

@RunWith(AndroidJUnit4.class)
public class RpcHandlerTest extends TestBase {
    private RpcHandler mRpcHandler;
    private ClientManager mClientManager;
    private Dispatcher mDispatcher;
    private Client mClient;
    private Client mServer;

    @Before
    public void setUp() {
        setLogLevel(Log.VERBOSE);
        final Context context = RuntimeEnvironment.getApplication();
        mRpcHandler = new RpcHandler();
        mClientManager = spy(new ClientManager(context));
        mDispatcher = spy(new Dispatcher(mRpcHandler));
        final UCore uCore = newMockUCoreBuilder(context)
                .setUBus(new UBus(context, mClientManager, mDispatcher))
                .build();
        uCore.init();

        mClient = registerNewClient(CLIENT);
        mServer = registerNewClient(SERVICE);
    }

    private static void setLogLevel(int level) {
        UBus.Component.DEBUG = (level <= Log.DEBUG);
        UBus.Component.VERBOSE = (level <= Log.VERBOSE);
    }

    private @NonNull Client registerNewClient(@NonNull UEntity entity) {
        return registerNewClient(entity, new Binder(), new MockListener());
    }

    private <T> @NonNull Client registerNewClient(@NonNull UEntity entity, @NonNull IBinder clientToken, @NonNull T listener) {
        assertStatus(UCode.OK, mClientManager.registerClient(PACKAGE_NAME, entity, clientToken, listener));
        final Client client = mClientManager.getClient(clientToken);
        assertNotNull(client);
        return client;
    }

    private @NonNull Client registerNewServer(@NonNull UUri methodUri) {
        final Client client = registerNewClient(methodUri.getEntity(), new Binder(), mock(UListener.class));
        assertNotNull(client);
        return registerServer(methodUri, client);
    }

    private @NonNull Client registerServer(@NonNull UUri methodUri, @NonNull Client client) {
        assertStatus(UCode.OK, mRpcHandler.registerServer(methodUri, client));
        final Client server = mRpcHandler.getServer(methodUri);
        assertEquals(client, server);
        return server;
    }

    @SuppressWarnings("UnusedReturnValue")
    private @NonNull Client registerRemoteServer(@NonNull IBinder clientToken) {
        assertStatus(UCode.OK, mClientManager.registerClient(PACKAGE_NAME, REMOTE_SERVER, clientToken, mock(UListener.class)));
        final Client client = mClientManager.getClient(clientToken);
        assertNotNull(client);
        return client;
    }

    @Test
    public void testInit() {
        verify(mClientManager, times(1)).registerListener(mRpcHandler.getClientRegistrationListener());
    }

    @Test
    public void testShutdown() {
        mRpcHandler.shutdown();
        verify(mClientManager, times(1)).unregisterListener(mRpcHandler.getClientRegistrationListener());
    }

    @Test
    public void testRegisterServer() {
        registerServer(METHOD_URI, mServer);
    }

    @Test
    public void testRegisterServerAlreadyRegistered() {
        registerServer(METHOD_URI, mServer);
        registerServer(METHOD_URI, mServer);
    }

    @Test
    public void testRegisterServerOtherRegistered() {
        registerServer(METHOD_URI, mServer);
        final Client newServer = registerNewClient(SERVICE);
        assertStatus(UCode.ALREADY_EXISTS, mRpcHandler.registerServer(METHOD_URI, newServer));
        assertEquals(mServer, mRpcHandler.getServer(METHOD_URI));
    }

    @Test
    public void testRegisterServerReplaceReleased() {
        registerServer(METHOD_URI, mServer);
        mServer.release();
        registerNewServer(METHOD_URI);
    }

    @Test
    @SuppressWarnings("DataFlowIssue")
    public void testRegisterServerNegative() {
        assertStatus(UCode.INVALID_ARGUMENT, mRpcHandler.registerServer(null, mServer));
        assertStatus(UCode.INVALID_ARGUMENT, mRpcHandler.registerServer(EMPTY_URI, mServer));
        assertStatus(UCode.INVALID_ARGUMENT, mRpcHandler.registerServer(buildResponseUri(mServer.getUri()), mServer));
        assertStatus(UCode.INVALID_ARGUMENT, mRpcHandler.registerServer(METHOD_URI, null));
        assertStatus(UCode.UNAUTHENTICATED, mRpcHandler.registerServer(REMOTE_METHOD_URI, mServer));
    }

    @Test
    public void testUnregisterServer() {
        registerServer(METHOD_URI, mServer);
        assertStatus(UCode.OK, mRpcHandler.unregisterServer(METHOD_URI, mServer));
        assertNull(mRpcHandler.getServer(METHOD_URI));
    }

    @Test
    public void testUnregisterServerOtherMethod() {
        registerServer(METHOD_URI, mServer);
        registerServer(METHOD2_URI, mServer);
        assertStatus(UCode.OK, mRpcHandler.unregisterServer(METHOD_URI, mServer));
        assertNull(mRpcHandler.getServer(METHOD_URI));
        assertEquals(mServer, mRpcHandler.getServer(METHOD2_URI));
    }

    @Test
    public void testUnregisterServerNotRegistered() {
        assertStatus(UCode.OK, mRpcHandler.unregisterServer(METHOD_URI, mServer));
        assertNull(mRpcHandler.getServer(METHOD_URI));
    }

    @Test
    public void testUnregisterServerOtherRegistered() {
        registerServer(METHOD_URI, mServer);
        final Client newServer = registerNewClient(SERVICE);
        assertStatus(UCode.NOT_FOUND, mRpcHandler.unregisterServer(METHOD_URI, newServer));
        assertEquals(mServer, mRpcHandler.getServer(METHOD_URI));
    }

    @Test
    public void testUnregisterServerDied() {
        registerServer(METHOD_URI, mServer);
        registerServer(METHOD2_URI, mServer);
        requireNonNull(mServer.getDeathRecipient()).binderDied();
        assertNull(mRpcHandler.getServer(METHOD_URI));
        assertNull(mRpcHandler.getServer(METHOD2_URI));
    }

    @Test
    public void testUnregisterServerConcurrent() {
        registerServer(METHOD_URI, mServer);
        mRpcHandler.getServers().remove(METHOD_URI);
        final Client newServer = registerNewServer(METHOD_URI);
        requireNonNull(mServer.getDeathRecipient()).binderDied();
        assertEquals(newServer, mRpcHandler.getServer(METHOD_URI));
    }

    @Test
    @SuppressWarnings("DataFlowIssue")
    public void testUnregisterServerNegative() {
        assertStatus(UCode.INVALID_ARGUMENT, mRpcHandler.unregisterServer(null, mServer));
        assertStatus(UCode.INVALID_ARGUMENT, mRpcHandler.unregisterServer(EMPTY_URI, mServer));
        assertStatus(UCode.INVALID_ARGUMENT, mRpcHandler.unregisterServer(buildResponseUri(mServer.getUri()), mServer));
        assertStatus(UCode.INVALID_ARGUMENT, mRpcHandler.unregisterServer(METHOD_URI, null));
        assertStatus(UCode.UNAUTHENTICATED, mRpcHandler.unregisterServer(REMOTE_METHOD_URI, mServer));
    }

    @Test
    public void testHandleRequestMessage() {
        registerServer(METHOD_URI, mServer);
        final UMessage requestMessage = buildRequestMessage(RESPONSE_URI, METHOD_URI);
        assertStatus(UCode.OK, mRpcHandler.handleRequestMessage(requestMessage, mClient));
    }

    @Test
    public void testHandleRequestMessageRemote() {
        registerRemoteServer(new Binder());
        final UMessage requestMessage = buildRequestMessage(RESPONSE_URI, REMOTE_METHOD_URI);
        assertStatus(UCode.OK, mRpcHandler.handleRequestMessage(requestMessage, mClient));
    }

    @Test
    public void testHandleRequestMessageDuplicated() {
        registerServer(METHOD_URI, mServer);
        final UMessage requestMessage = buildRequestMessage(RESPONSE_URI, METHOD_URI);
        assertStatus(UCode.OK, mRpcHandler.handleRequestMessage(requestMessage, mClient));
        assertStatus(UCode.ABORTED, mRpcHandler.handleRequestMessage(requestMessage, mClient));
    }

    @Test
    public void testHandleRequestMessageUnauthenticated() {
        registerServer(METHOD_URI, mServer);
        final UMessage requestMessage = buildRequestMessage(buildResponseUri(CLIENT2_URI), METHOD_URI);
        assertStatus(UCode.UNAUTHENTICATED, mRpcHandler.handleRequestMessage(requestMessage, mClient));
    }

    @Test
    public void testHandleRequestMessageExpired() {
        registerServer(METHOD_URI, mServer);
        final UMessage requestMessage = buildRequestMessage(RESPONSE_URI, METHOD_URI, 1);
        sleep(DELAY_MS);
        assertStatus(UCode.DEADLINE_EXCEEDED, mRpcHandler.handleRequestMessage(requestMessage, mClient));
    }

    @Test
    public void testHandleRequestMessageTimeoutNotSet() {
        registerRemoteServer(new Binder());
        final UMessage requestMessage = buildRequestMessage(RESPONSE_URI, REMOTE_METHOD_URI, -1);
        assertStatus(UCode.DEADLINE_EXCEEDED, mRpcHandler.handleRequestMessage(requestMessage, mClient));
    }

    @Test
    public void testHandleRequestMessageNoServer() {
        final UMessage requestMessage = buildRequestMessage(RESPONSE_URI, METHOD_URI);
        sleep(DELAY_MS);
        assertStatus(UCode.UNAVAILABLE, mRpcHandler.handleRequestMessage(requestMessage, mClient));
    }

    @Test
    public void testHandleRequestMessageRetriedAfterDispatchFailure() {
        registerServer(METHOD_URI, mServer);
        final UMessage requestMessage = buildRequestMessage(RESPONSE_URI, METHOD_URI);
        when(mDispatcher.dispatchTo(requestMessage, mServer)).thenReturn(false);
        assertStatus(UCode.OK, mRpcHandler.handleRequestMessage(requestMessage, mClient));
        verify(mDispatcher, times(1)).dispatchTo(requestMessage, mServer);
        // Retried once
        verify(mDispatcher, timeout(DELAY_LONG_MS + DELAY_MS).times(2)).dispatchTo(requestMessage, mServer);
    }

    @Test
    public void testHandleRequestMessageSkipRetryWhenDispatched() {
        registerServer(METHOD_URI, mServer);
        final UMessage requestMessage = buildRequestMessage(RESPONSE_URI, METHOD_URI);
        when(mDispatcher.dispatchTo(requestMessage, mServer)).thenReturn(false);
        assertStatus(UCode.OK, mRpcHandler.handleRequestMessage(requestMessage, mClient));
        verify(mDispatcher, times(1)).dispatchTo(requestMessage, mServer);
        assertStatus(UCode.OK, mRpcHandler.unregisterServer(METHOD_URI, mServer));
        // Server registered and ready to receive request
        when(mDispatcher.dispatchTo(requestMessage, mServer)).thenReturn(true);
        registerServer(METHOD_URI, mServer);
        verify(mDispatcher, timeout(DELAY_LONG_MS + DELAY_MS).times(2)).dispatchTo(requestMessage, mServer);
    }

    @Test
    public void testHandleRequestMessageRetriedAfterServerRegistration() {
        registerServer(METHOD_URI, mServer);
        final UMessage requestMessage = buildRequestMessage(RESPONSE_URI, METHOD_URI, 5000);
        when(mDispatcher.dispatchTo(requestMessage, mServer)).thenReturn(false);
        assertStatus(UCode.OK, mRpcHandler.handleRequestMessage(requestMessage, mClient));
        verify(mDispatcher, timeout(DELAY_LONG_MS + DELAY_MS).times(2)).dispatchTo(requestMessage, mServer);
        assertStatus(UCode.OK, mRpcHandler.unregisterServer(METHOD_URI, mServer));
        // Server registered and immediately unregistered
        registerServer(METHOD_URI, mServer);
        assertStatus(UCode.OK, mRpcHandler.unregisterServer(METHOD_URI, mServer));
        verify(mDispatcher, timeout(DELAY_LONG_MS).atLeast(2)).dispatchTo(requestMessage, mServer);
        // Server registered and unregistered after retry attempt
        registerServer(METHOD_URI, mServer);
        verify(mDispatcher, timeout(DELAY_LONG_MS).atLeast(3)).dispatchTo(requestMessage, mServer);
    }

    @Test
    public void testHandleResponseMessage() {
        registerServer(METHOD_URI, mServer);
        final UMessage requestMessage = buildRequestMessage(RESPONSE_URI, METHOD_URI);
        assertStatus(UCode.OK, mRpcHandler.handleRequestMessage(requestMessage, mClient));
        final UMessage responseMessage = buildResponseMessage(requestMessage);
        assertStatus(UCode.OK, mRpcHandler.handleResponseMessage(responseMessage, mServer));
    }

    @Test
    public void testHandleResponseMessageCommunicationFailure() {
        setLogLevel(Log.INFO);
        registerServer(METHOD_URI, mServer);
        final UMessage requestMessage = buildRequestMessage(RESPONSE_URI, METHOD_URI);
        assertStatus(UCode.OK, mRpcHandler.handleRequestMessage(requestMessage, mClient));
        final UMessage responseMessage = buildFailedResponseMessage(requestMessage, UCode.UNKNOWN);
        assertStatus(UCode.OK, mRpcHandler.handleResponseMessage(responseMessage, mServer));
    }

    @Test
    public void testHandleResponseMessageExpired() {
        registerServer(METHOD_URI, mServer);
        final UMessage requestMessage = buildRequestMessage(RESPONSE_URI, METHOD_URI);
        assertStatus(UCode.OK, mRpcHandler.handleRequestMessage(requestMessage, mClient));
        final UMessage responseMessage = buildResponseMessage(requestMessage, 1);
        sleep(DELAY_MS);
        assertStatus(UCode.DEADLINE_EXCEEDED, mRpcHandler.handleResponseMessage(responseMessage, mServer));
    }

    @Test
    public void testHandleResponseMessageWrongServer() {
        registerServer(METHOD_URI, mServer);
        final UMessage requestMessage = buildRequestMessage(RESPONSE_URI, METHOD_URI);
        assertStatus(UCode.OK, mRpcHandler.handleRequestMessage(requestMessage, mClient));
        final Client newServer = registerNewClient(SERVICE2, new Binder(), new MockListener());
        final UMessage responseMessage = buildResponseMessage(requestMessage);
        assertStatus(UCode.UNAUTHENTICATED, mRpcHandler.handleResponseMessage(responseMessage, newServer));
    }

    @Test
    public void testHandleResponseMessageTimeout() {
        registerServer(METHOD_URI, mServer);
        final UListener listener = mock(UListener.class);
        final Client client = registerNewClient(CLIENT, new Binder(), listener);

        final UMessage requestMessage = buildRequestMessage(buildResponseUri(client.getUri()), METHOD_URI, 100);
        assertStatus(UCode.OK, mRpcHandler.handleRequestMessage(requestMessage, client));
        // Timeout response
        final ArgumentCaptor<UMessage> captor = ArgumentCaptor.forClass(UMessage.class);
        verify(listener, timeout(200).times(1)).onReceive(captor.capture());
        final UMessage responseMessage = captor.getValue();
        assertNotNull(responseMessage);
        assertEquals(requestMessage.getAttributes().getId(), responseMessage.getAttributes().getReqid());
        assertEquals(requestMessage.getAttributes().getSink(), responseMessage.getSource());
        assertEquals(requestMessage.getSource(), responseMessage.getAttributes().getSink());
        assertEquals(UCode.DEADLINE_EXCEEDED_VALUE, responseMessage.getAttributes().getCommstatus());
    }

    @Test
    public void testHandleResponseMessageAfterTimeout() {
        registerServer(METHOD_URI, mServer);
        final UListener listener = mock(UListener.class);
        final Client client = registerNewClient(CLIENT, new Binder(), listener);

        final UMessage requestMessage = buildRequestMessage(buildResponseUri(client.getUri()), METHOD_URI, 100);
        assertStatus(UCode.OK, mRpcHandler.handleRequestMessage(requestMessage, client));
        // Timeout response
        verify(listener, timeout(200).times(1)).onReceive(any());

        final UMessage responseMessage = buildResponseMessage(requestMessage);
        assertStatus(UCode.CANCELLED, mRpcHandler.handleResponseMessage(responseMessage, mServer));
    }

    @Test
    public void testGetMethods() {
        registerServer(METHOD_URI, mServer);
        registerServer(METHOD2_URI, mServer);
        assertEquals(Set.of(METHOD_URI, METHOD2_URI), mRpcHandler.getMethods(mServer));
    }

    private String dump(String... args) {
        final StringWriter out = new StringWriter();
        final PrintWriter writer = new PrintWriter(out);
        mRpcHandler.dump(writer, args);
        writer.flush();
        return out.toString();
    }

    @Test
    public void testDump() {
        registerNewServer(METHOD_URI);
        final String output = dump();
        assertTrue(output.contains(stringify(METHOD_URI)));
    }

    @Test
    public void testDumpServer() {
        final Client server = registerNewServer(METHOD_URI);
        registerServer(METHOD2_URI, server);
        final String output = dump("-s", stringify(server.getEntity()));
        assertTrue(output.contains(stringify(METHOD_URI)));
        assertTrue(output.contains(stringify(METHOD2_URI)));
    }

    @Test
    public void testDumpServers() {
        registerNewServer(METHOD_URI);
        registerNewServer(METHOD2_URI);
        final String output = dump("-s");
        assertTrue(output.contains(stringify(METHOD_URI)));
        assertTrue(output.contains(stringify(METHOD2_URI)));
    }

    @Test
    public void testDumpUnknownArg() {
        registerNewServer(METHOD_URI);
        final String output = dump("-t");
        assertFalse(output.contains(stringify(METHOD_URI)));
    }
}
