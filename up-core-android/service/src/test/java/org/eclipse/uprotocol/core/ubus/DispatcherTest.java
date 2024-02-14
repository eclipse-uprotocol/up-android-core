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
import static org.eclipse.uprotocol.core.internal.util.UMessageUtils.replaceSink;
import static org.eclipse.uprotocol.core.internal.util.UUriUtils.addAuthority;
import static org.eclipse.uprotocol.core.ubus.UBus.EXTRA_BLOCK_AUTO_FETCH;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.eclipse.uprotocol.core.TestBase;
import org.eclipse.uprotocol.core.UCore;
import org.eclipse.uprotocol.core.ubus.client.Client;
import org.eclipse.uprotocol.core.ubus.client.ClientManager;
import org.eclipse.uprotocol.core.usubscription.SubscriptionListener;
import org.eclipse.uprotocol.core.usubscription.USubscription;
import org.eclipse.uprotocol.core.usubscription.v3.SubscriptionStatus.State;
import org.eclipse.uprotocol.core.usubscription.v3.Update;
import org.eclipse.uprotocol.core.utwin.UTwin;
import org.eclipse.uprotocol.transport.UListener;
import org.eclipse.uprotocol.v1.UCode;
import org.eclipse.uprotocol.v1.UEntity;
import org.eclipse.uprotocol.v1.UMessage;
import org.eclipse.uprotocol.v1.UUri;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class DispatcherTest extends TestBase {
    private UTwin mUTwin;
    private USubscription mUSubscription;
    private ClientManager mClientManager;
    private RpcHandler mRpcHandler;
    private Dispatcher mDispatcher;
    private SubscriptionListener mSubscriptionListener;
    private SubscriptionCache mSubscriptionCache;
    private Client mClient;
    private Client mServer;

    @Before
    public void setUp() {
        setLogLevel(Log.VERBOSE);
        final Context context = spy(Context.class);
        mUTwin = spy(new UTwin(context));
        mUSubscription = mock(USubscription.class);
        mClientManager = spy(new ClientManager(context));
        mRpcHandler = mock(RpcHandler.class);
        mDispatcher = new Dispatcher(mRpcHandler);

        final UCore uCore = newMockUCoreBuilder(context)
                .setUBus(new UBus(context, mClientManager, mDispatcher))
                .setUTwin(mUTwin)
                .setUSubscription(mUSubscription)
                .build();
        uCore.init();

        mSubscriptionListener = mDispatcher.getSubscriptionListener();
        mSubscriptionCache = mDispatcher.getSubscriptionCache();
        mClient = registerNewClient(CLIENT);
        mServer = registerNewClient(SERVICE);
    }

    private static void setLogLevel(int level) {
        UBus.Component.DEBUG = (level <= Log.DEBUG);
        UBus.Component.VERBOSE = (level <= Log.VERBOSE);
        UBus.Component.TRACE_EVENTS = (level <= Log.VERBOSE);
    }

    private Client registerNewClient(@NonNull UEntity entity) {
        return registerNewClient(entity, new Binder(), mock(UListener.class));
    }

    private <T> Client registerNewClient(@NonNull UEntity entity, @NonNull IBinder clientToken, @NonNull T listener) {
        assertStatus(UCode.OK, mClientManager.registerClient(PACKAGE_NAME, entity, clientToken, listener));
        final Client client = mClientManager.getClient(clientToken);
        assertNotNull(client);
        return client;
    }

    @SuppressWarnings("UnusedReturnValue")
    private @NonNull Client registerRemoteServer(@NonNull IBinder clientToken) {
        assertStatus(UCode.OK, mClientManager.registerClient(PACKAGE_NAME, REMOTE_SERVER, clientToken, mock(UListener.class)));
        final Client client = mClientManager.getClient(clientToken);
        assertNotNull(client);
        return client;
    }

    private @NonNull Client registerReceiver(@NonNull UUri topic, @NonNull final Client client, boolean shouldSubscribe) {
        if (shouldSubscribe) {
            injectSubscription(topic, client.getUri());
        }
        assertStatus(UCode.OK, mDispatcher.enableDispatching(topic, null, client));
        return client;
    }

    private void injectTopic(@NonNull UUri topic, @NonNull UUri publisher) {
        when(mUSubscription.getPublisher(topic)).thenReturn(publisher);
    }

    private void injectSubscription(@NonNull UUri topic, @NonNull UUri subscriber) {
        when(mUSubscription.getSubscribers(topic)).thenReturn(Set.of(subscriber));
    }

    private static @NonNull Update buildUpdate(@NonNull UUri topic, @NonNull UUri clientUri, @NonNull State state) {
        return Update.newBuilder()
                .setTopic(topic)
                .setSubscriber(buildSubscriber(clientUri))
                .setStatus(buildSubscriptionStatus(state))
                .build();
    }

    private void verifyMessageReceived(UMessage message, int times, @NonNull Client client) {
        if (message != null) {
            verify(((UListener) client.getListener()), timeout(DELAY_LONG_MS).times(times)).onReceive(message);
        } else {
            verify(((UListener) client.getListener()), timeout(DELAY_LONG_MS).times(times)).onReceive(any());
        }
    }

    private void verifyMessageNotReceived(UMessage message, @NonNull Client client) {
        verifyMessageReceived(message, 0, client);
    }

    private void verifyNoMessagesReceived(@NonNull Client client) {
        verifyMessageReceived(null, 0, client);
    }

    private void verifyMessageCached(@NonNull UMessage message) {
        assertEquals(message, mUTwin.getMessage(message.getAttributes().getSource()));
    }

    private void verifyMessageNotCached(@NonNull UMessage message) {
        final UMessage cachedMessage = mUTwin.getMessage(message.getAttributes().getSource());
        if (cachedMessage != null) {
            assertNotEquals(message.getAttributes().getId(), cachedMessage.getAttributes().getId());
        }
    }

    @Test
    public void testInit() {
        verify(mRpcHandler, times(1)).init(any());
        verify(mUSubscription, times(1)).registerListener(mDispatcher.getSubscriptionListener());
        verify(mClientManager, times(1)).registerListener(mDispatcher.getClientRegistrationListener());
    }

    @Test
    public void testStartup() {
        mDispatcher.startup();
        verify(mRpcHandler, times(1)).startup();
    }

    @Test
    public void testShutdown() {
        mDispatcher.shutdown();
        verify(mRpcHandler, times(1)).shutdown();
        verify(mUSubscription, times(1)).unregisterListener(mDispatcher.getSubscriptionListener());
        verify(mClientManager, times(1)).unregisterListener(mDispatcher.getClientRegistrationListener());
    }

    @Test
    public void testShutdownTimeout() {
        mDispatcher.getExecutor().schedule(() -> sleep(200), 0, TimeUnit.MILLISECONDS);
        mDispatcher.shutdown();
        verify(mRpcHandler, times(1)).shutdown();
        verify(mUSubscription, times(1)).unregisterListener(any());
        verify(mClientManager, times(1)).unregisterListener(any());
    }

    @Test
    public void testShutdownInterrupted() {
        mDispatcher.getExecutor().schedule(() -> sleep(200), 0, TimeUnit.MILLISECONDS);
        final Thread thread = new Thread(() -> mDispatcher.shutdown());
        thread.start();
        thread.interrupt();
        verify(mRpcHandler, timeout(DELAY_MS).times(1)).shutdown();
        verify(mUSubscription, timeout(DELAY_MS).times(1)).unregisterListener(any());
        verify(mClientManager, timeout(DELAY_MS).times(1)).unregisterListener(any());
    }

    @Test
    public void testClearCache() {
        testDispatchFromPublishMessage();
        assertFalse(mSubscriptionCache.isEmpty());
        mDispatcher.clearCache();
        assertTrue(mSubscriptionCache.isEmpty());
        verify(mRpcHandler, times(1)).clearCache();
    }

    @Test
    public void testOnSubscriptionChanged() {
        setLogLevel(Log.INFO);
        injectSubscription(RESOURCE_URI, CLIENT_URI);
        mSubscriptionListener.onSubscriptionChanged(buildUpdate(RESOURCE_URI, CLIENT_URI, State.SUBSCRIBED));
        assertTrue(mSubscriptionCache.getSubscribers(RESOURCE_URI).contains(CLIENT_URI));
        mSubscriptionListener.onSubscriptionChanged(buildUpdate(RESOURCE_URI, CLIENT_URI, State.UNSUBSCRIBED));
        assertFalse(mSubscriptionCache.getSubscribers(RESOURCE_URI).contains(CLIENT_URI));
    }

    @Test
    public void testOnSubscriptionChangedNegative() {
        injectSubscription(RESOURCE_URI, CLIENT_URI);
        mSubscriptionListener.onSubscriptionChanged(buildUpdate(RESOURCE_URI, CLIENT_URI, State.SUBSCRIBED));
        assertTrue(mSubscriptionCache.getSubscribers(RESOURCE_URI).contains(CLIENT_URI));

        mSubscriptionListener.onSubscriptionChanged(buildUpdate(EMPTY_URI, CLIENT_URI, State.UNSUBSCRIBED));
        assertTrue(mSubscriptionCache.getSubscribers(RESOURCE_URI).contains(CLIENT_URI));

        mSubscriptionListener.onSubscriptionChanged(buildUpdate(RESOURCE_URI, EMPTY_URI, State.UNSUBSCRIBED));
        assertTrue(mSubscriptionCache.getSubscribers(RESOURCE_URI).contains(CLIENT_URI));

        mSubscriptionListener.onSubscriptionChanged(buildUpdate(EMPTY_URI, EMPTY_URI, State.UNSUBSCRIBED));
        assertTrue(mSubscriptionCache.getSubscribers(RESOURCE_URI).contains(CLIENT_URI));
    }

    @Test
    public void testOnTopicChanged() {
        mSubscriptionListener.onTopicCreated(RESOURCE_URI, SERVER_URI);
        assertTrue(mSubscriptionCache.isTopicCreated(RESOURCE_URI, SERVER_URI));
        mSubscriptionListener.onTopicDeprecated(RESOURCE_URI);
        assertFalse(mSubscriptionCache.isTopicCreated(RESOURCE_URI, SERVER_URI));
    }

    @Test
    public void testOnTopicDeleted() {
        mSubscriptionListener.onTopicCreated(RESOURCE_URI, SERVER_URI);
        assertTrue(mSubscriptionCache.isTopicCreated(RESOURCE_URI, SERVER_URI));
        final UMessage message = buildPublishMessage(RESOURCE_URI);
        assertTrue(mUTwin.addMessage(message));
        assertEquals(message, mUTwin.getMessage(RESOURCE_URI));

        mSubscriptionListener.onTopicDeprecated(RESOURCE_URI);
        assertFalse(mSubscriptionCache.isTopicCreated(RESOURCE_URI, SERVER_URI));
        assertNull(mUTwin.getMessage(RESOURCE_URI));
    }

    @Test
    public void testOnClientUnregistered() {
        assertStatus(UCode.OK, mDispatcher.enableDispatching(RESOURCE_URI, null, mClient));
        assertTrue(mDispatcher.getLinkedClients(RESOURCE_URI).contains(mClient));
        mClientManager.unregisterClient(mClient.getToken());
        assertFalse(mDispatcher.getLinkedClients(RESOURCE_URI).contains(mClient));
    }

    @Test
    public void testPull() {
        injectSubscription(RESOURCE_URI, CLIENT_URI);
        final UMessage message = buildPublishMessage(RESOURCE_URI);
        assertTrue(mUTwin.addMessage(message));
        assertTrue(mDispatcher.pull(RESOURCE_URI, 1, mClient).contains(message));
    }

    @Test
    public void testPullNotSubscribed() {
        final UMessage message = buildPublishMessage(RESOURCE_URI);
        assertTrue(mUTwin.addMessage(message));
        assertFalse(mDispatcher.pull(RESOURCE_URI, 1, mClient).contains(message));
        setLogLevel(Log.INFO);
        assertFalse(mDispatcher.pull(RESOURCE_URI, 1, mClient).contains(message));
    }

    @Test
    public void testPullExpired() {
        injectSubscription(RESOURCE_URI, CLIENT_URI);
        final UMessage message = buildPublishMessage(RESOURCE_URI, 100);
        assertTrue(mUTwin.addMessage(message));
        assertTrue(mDispatcher.pull(RESOURCE_URI, 1, mClient).contains(message));
        sleep(200);
        assertFalse(mDispatcher.pull(RESOURCE_URI, 1, mClient).contains(message));
    }

    @Test
    @SuppressWarnings("DataFlowIssue")
    public void testPullNegative() {
        testPull();
        assertTrue(mDispatcher.pull(null, 1, mClient).isEmpty());
        assertTrue(mDispatcher.pull(EMPTY_URI, 1, mClient).isEmpty());
        assertTrue(mDispatcher.pull(RESOURCE_URI, 0, mClient).isEmpty());
        assertTrue(mDispatcher.pull(RESOURCE_URI, 1, null).isEmpty());
    }

    @Test
    public void testShouldAutoFetch() {
        assertTrue(Dispatcher.shouldAutoFetch(null));
        Bundle extras = new Bundle();
        assertTrue(Dispatcher.shouldAutoFetch(extras));
        extras.putString(EXTRA_BLOCK_AUTO_FETCH, "test");
        assertTrue(Dispatcher.shouldAutoFetch(extras));
        extras.putBoolean(EXTRA_BLOCK_AUTO_FETCH, false);
        assertTrue(Dispatcher.shouldAutoFetch(extras));
        extras.putBoolean(EXTRA_BLOCK_AUTO_FETCH, true);
        assertFalse(Dispatcher.shouldAutoFetch(extras));
    }

    @Test
    public void testEnableDispatching() {
        assertStatus(UCode.OK, mDispatcher.enableDispatching(RESOURCE_URI, null, mClient));
        assertTrue(mDispatcher.getLinkedClients(RESOURCE_URI).contains(mClient));
    }

    @Test
    public void testEnableDispatchingAlreadyEnabled() {
        setLogLevel(Log.INFO);
        testEnableDispatching();
        assertStatus(UCode.OK, mDispatcher.enableDispatching(RESOURCE_URI, null, mClient));
        assertTrue(mDispatcher.getLinkedClients(RESOURCE_URI).contains(mClient));
    }

    @Test
    public void testEnableDispatchingAutoFetch() {
        injectSubscription(RESOURCE_URI, mClient.getUri());
        assertStatus(UCode.OK, mDispatcher.enableDispatching(RESOURCE_URI, null, mClient));
        assertTrue(mDispatcher.getLinkedClients(RESOURCE_URI).contains(mClient));
        verify(mUTwin, timeout(DELAY_MS).times(1)).getMessage(RESOURCE_URI);
    }

    @Test
    public void testEnableDispatchingAutoFetchBlocked() {
        injectSubscription(RESOURCE_URI, mClient.getUri());
        Bundle extras = new Bundle();
        extras.putBoolean(EXTRA_BLOCK_AUTO_FETCH, true);
        assertStatus(UCode.OK, mDispatcher.enableDispatching(RESOURCE_URI, extras, mClient));
        assertTrue(mDispatcher.getLinkedClients(RESOURCE_URI).contains(mClient));
        verify(mUTwin, never()).getMessage(RESOURCE_URI);
    }

    @Test
    public void testEnableDispatchingForServer() {
        mDispatcher.enableDispatching(METHOD_URI, null, mServer);
        verify(mRpcHandler, times(1)).registerServer(METHOD_URI, mServer);
    }

    @Test
    @SuppressWarnings("DataFlowIssue")
    public void testEnableDispatchingNegative() {
        assertThrows(NullPointerException.class, () -> mDispatcher.enableDispatching(null, null, mClient));
        assertStatus(UCode.INVALID_ARGUMENT, mDispatcher.enableDispatching(EMPTY_URI, null, mClient));
        assertStatus(UCode.INVALID_ARGUMENT, mDispatcher.enableDispatching(RESOURCE_URI,  null,null));
    }

    @Test
    public void testDisableDispatching() {
        assertStatus(UCode.OK, mDispatcher.disableDispatching(RESOURCE_URI, null, mClient));
        assertFalse(mDispatcher.getLinkedClients(RESOURCE_URI).contains(mClient));
    }

    @Test
    public void testDisableDispatchingAlreadyDisabled() {
        setLogLevel(Log.INFO);
        testDisableDispatching();
        assertStatus(UCode.OK, mDispatcher.disableDispatching(RESOURCE_URI, null, mClient));
        assertFalse(mDispatcher.getLinkedClients(RESOURCE_URI).contains(mClient));
    }

    @Test
    public void testDisableDispatchingForServer() {
        mDispatcher.disableDispatching(METHOD_URI, null, mServer);
        verify(mRpcHandler, times(1)).unregisterServer(METHOD_URI, mServer);
    }

    @Test
    @SuppressWarnings("DataFlowIssue")
    public void testDisableDispatchingNegative() {
        assertThrows(NullPointerException.class, () -> mDispatcher.disableDispatching(null, null, mClient));
        assertStatus(UCode.INVALID_ARGUMENT, mDispatcher.disableDispatching(EMPTY_URI, null, mClient));
        assertStatus(UCode.INVALID_ARGUMENT, mDispatcher.disableDispatching(RESOURCE_URI, null, null));
    }

    @Test
    public void testDispatchFromUnknownMessage() {
        injectTopic(RESOURCE2_URI, mServer.getUri());
        final UMessage message = UMessage.getDefaultInstance();
        assertStatus(UCode.UNIMPLEMENTED, mDispatcher.dispatchFrom(message, mServer));
        verifyMessageNotCached(message);
    }

    @Test
    public void testDispatchFromExpiredMessage() {
        injectTopic(RESOURCE_URI, mServer.getUri());
        final UMessage message = buildPublishMessage(RESOURCE_URI, 100);
        sleep(200);
        assertStatus(UCode.DEADLINE_EXCEEDED, mDispatcher.dispatchFrom(message, mServer));
        verifyMessageNotCached(message);
    }

    @Test
    public void testDispatchFromPublishMessage() {
        injectTopic(RESOURCE_URI, mServer.getUri());
        registerReceiver(RESOURCE_URI, mClient, true);
        final UMessage message = buildPublishMessage(RESOURCE_URI);
        assertStatus(UCode.OK, mDispatcher.dispatchFrom(message, mServer));
        verifyMessageCached(message);
        verifyMessageReceived(message, 1, mClient);
    }

    @Test
    public void testDispatchFromPublishMessageSequence() {
        injectTopic(RESOURCE_URI, mServer.getUri());
        registerReceiver(RESOURCE_URI, mClient, true);
        final UMessage message1 = buildPublishMessage(RESOURCE_URI);
        assertStatus(UCode.OK, mDispatcher.dispatchFrom(message1, mServer));
        verifyMessageCached(message1);
        verifyMessageReceived(message1, 1, mClient);

        final UMessage message2 = buildPublishMessage(RESOURCE_URI);
        assertStatus(UCode.OK, mDispatcher.dispatchFrom(message2, mServer));
        verifyMessageCached(message2);
        verifyMessageReceived(message2, 1, mClient);
    }

    @Test
    public void testDispatchFromPublishMessageDuplicated() {
        injectTopic(RESOURCE_URI, mServer.getUri());
        registerReceiver(RESOURCE_URI, mClient, true);
        final UMessage message = buildPublishMessage(RESOURCE_URI);
        assertStatus(UCode.OK, mDispatcher.dispatchFrom(message, mServer));
        assertStatus(UCode.OK, mDispatcher.dispatchFrom(message, mServer));
        verifyMessageCached(message);
        verifyMessageReceived(message, 1, mClient);
    }

    @Test
    public void testDispatchFromPublishMessageTopicNotCreated() {
        registerReceiver(RESOURCE_URI, mClient, false);
        final UMessage message = buildPublishMessage(RESOURCE_URI);
        assertStatus(UCode.NOT_FOUND, mDispatcher.dispatchFrom(message, mServer));
        assertNull(mUTwin.getMessage(message.getAttributes().getSource()));
        verifyMessageNotReceived(message, mClient);
    }

    @Test
    public void testDispatchFromPublishMessageLinkedButNotSubscribed() {
        injectTopic(RESOURCE_URI, mServer.getUri());
        registerReceiver(RESOURCE_URI, mClient, false);
        final UMessage message = buildPublishMessage(RESOURCE_URI);
        assertStatus(UCode.OK, mDispatcher.dispatchFrom(message, mServer));
        verifyMessageCached(message);
        verifyMessageNotReceived(message, mClient);
    }

    @Test
    public void testDispatchFromPublishMessageSubscribedButNotLinked() {
        injectTopic(RESOURCE_URI, mServer.getUri());
        injectSubscription(RESOURCE_URI, mClient.getUri());
        final UMessage message = buildPublishMessage(RESOURCE_URI);
        assertStatus(UCode.OK, mDispatcher.dispatchFrom(message, mServer));
        verifyMessageCached(message);
        verifyMessageNotReceived(message, mClient);
    }

    @Test
    public void testDispatchFromPublishMessageSubscribedRemotely() {
        injectTopic(RESOURCE_URI, mServer.getUri());
        injectSubscription(RESOURCE_URI, REMOTE_SERVER_URI);
        final Client client = registerRemoteServer(new Binder());
        final UMessage message = buildPublishMessage(RESOURCE_URI);
        assertStatus(UCode.OK, mDispatcher.dispatchFrom(message, mServer));
        final UMessage modifiedMessage = replaceSink(message, REMOTE_SERVER_URI);
        verifyMessageCached(message);
        verifyMessageReceived(modifiedMessage, 1, client);
    }

    @Test
    public void testDispatchFromRemotePublishMessage() {
        final Client server = registerRemoteServer(new Binder());
        registerReceiver(REMOTE_RESOURCE_URI, mClient, true);
        final UMessage message = buildNotificationMessage(REMOTE_RESOURCE_URI, USUBSCRIPTION_URI);
        assertStatus(UCode.OK, mDispatcher.dispatchFrom(message, server));
        final UMessage modifiedMessage = replaceSink(message, EMPTY_URI);
        verifyMessageCached(modifiedMessage);
        verifyMessageReceived(modifiedMessage, 1, mClient);
    }

    @Test
    public void testDispatchFromRemoteSubscriptionNotificationMessage() {
        final UUri topic = addAuthority(USubscription.TOPIC_SUBSCRIPTION_UPDATE, REMOTE_AUTHORITY);
        final Client server = registerRemoteServer(new Binder());
        final Client client = registerReceiver(topic, registerNewClient(USubscription.SERVICE), false);
        final UMessage message = buildNotificationMessage(topic, USUBSCRIPTION_URI);
        assertStatus(UCode.OK, mDispatcher.dispatchFrom(message, server));
        verifyMessageNotCached(message);
        verifyMessageReceived(message, 1, client);
    }

    @Test
    public void testDispatchFromRemoteNotificationMessage() {
        final Client server = registerRemoteServer(new Binder());
        registerReceiver(REMOTE_RESOURCE_URI, mClient, false);
        final UMessage message = buildNotificationMessage(REMOTE_RESOURCE_URI, mClient.getUri());
        assertStatus(UCode.OK, mDispatcher.dispatchFrom(message, server));
        verifyMessageNotCached(message);
        verifyMessageReceived(message, 1, mClient);
    }

    @Test
    public void testDispatchFromNotificationMessage() {
        setLogLevel(Log.INFO);
        registerReceiver(RESOURCE_URI, mClient, false);
        final UMessage message = buildNotificationMessage(RESOURCE_URI, mClient.getUri());
        assertStatus(UCode.OK, mDispatcher.dispatchFrom(message, mServer));
        verifyMessageNotCached(message);
        verifyMessageReceived(message, 1, mClient);
    }

    @Test
    public void testDispatchFromStreamerNotificationMessage() {
        final Client server = registerRemoteServer(new Binder());
        final UUri topic = buildUri(null, REMOTE_SERVER, RESOURCE);
        registerReceiver(topic, mClient, false);
        final UMessage message = buildNotificationMessage(topic, mClient.getUri());
        assertStatus(UCode.OK, mDispatcher.dispatchFrom(message, server));
        verifyMessageNotCached(message);
        verifyMessageReceived(message, 1, mClient);
    }

    @Test
    public void testDispatchFromRequestMessage() {
        final UMessage requestMessage = buildRequestMessage();
        mDispatcher.dispatchFrom(requestMessage, mClient);
        verify(mRpcHandler, times(1)).handleRequestMessage(requestMessage, mClient);
    }

    @Test
    public void testDispatchFromResponseMessage() {
        final UMessage responseMessage = buildResponseMessage(buildRequestMessage());
        mDispatcher.dispatchFrom(responseMessage, mServer);
        verify(mRpcHandler, times(1)).handleResponseMessage(responseMessage, mServer);
    }

    @Test
    public void testDispatchTo() {
        setLogLevel(Log.INFO);
        assertTrue(mDispatcher.dispatchTo(buildPublishMessage(), mClient));
    }

    @Test
    public void testDispatchToExceptionally() {
        doThrow(new RuntimeException()).when((UListener) mClient.getListener()).onReceive(any());
        assertFalse(mDispatcher.dispatchTo(buildPublishMessage(), mClient));
    }

    @Test
    public void testDispatchToRetried() {
        doThrow(new RuntimeException()).doNothing().when((UListener) mClient.getListener()).onReceive(any());
        assertTrue(mDispatcher.dispatchTo(buildPublishMessage(), mClient));
    }

    @Test
    public void testDispatchToRetryInterrupted() {
        doThrow(new RuntimeException()).doNothing().when((UListener) mClient.getListener()).onReceive(any());
        Thread.currentThread().interrupt();
        assertFalse(mDispatcher.dispatchTo(buildPublishMessage(), mClient));
    }

    @Test
    @SuppressWarnings("DataFlowIssue")
    public void testDispatchToNegative() {
        assertFalse(mDispatcher.dispatchTo(null, mClient));
        assertFalse(mDispatcher.dispatchTo(buildPublishMessage(), null));
    }

    @Test
    public void testDispatchNullMessage() {
        registerReceiver(RESOURCE_URI, mClient, false);
        mDispatcher.dispatch(null, EMPTY_URI);
        verifyNoMessagesReceived(mClient);
    }

    @Test
    public void getCachedMessage() {
        final UMessage message = buildPublishMessage(RESOURCE_URI);
        assertTrue(mUTwin.addMessage(message));
        verifyMessageCached(message);
    }

    @Test
    public void getCachedMessageExpired() {
        final UMessage message = buildPublishMessage(RESOURCE_URI, 100);
        assertTrue(mUTwin.addMessage(message));
        verifyMessageCached(message);
        sleep(200);
        assertNull(mUTwin.getMessage(RESOURCE_URI));
    }

    private String dump(@NonNull String... args) {
        final StringWriter out = new StringWriter();
        final PrintWriter writer = new PrintWriter(out);
        mDispatcher.dump(writer, args);
        writer.flush();
        return out.toString();
    }

    @Test
    public void testDump() {
        final UUri topic = RESOURCE_URI;
        mSubscriptionCache.addTopic(topic, SERVER_URI);
        mSubscriptionCache.addSubscriber(topic, mClient.getUri());
        mUTwin.addMessage(buildPublishMessage(topic));
        mDispatcher.enableDispatching(topic, null, mClient);

        final String output = dump();
        assertTrue(output.contains(stringify(topic)));
    }

    @Test
    public void testDumpTopic() {
        final UUri topic1 = RESOURCE_URI;
        final UUri topic2 = RESOURCE2_URI;
        final Client client1 = registerNewClient(CLIENT);
        final Client client2 = registerNewClient(CLIENT2);
        mSubscriptionCache.addTopic(topic1, SERVER_URI);
        mSubscriptionCache.addTopic(topic2, SERVER_URI);
        mSubscriptionCache.addSubscriber(topic1, client1.getUri());

        mDispatcher.enableDispatching(topic1, null, client1);
        mDispatcher.enableDispatching(topic1, null, client2);
        mDispatcher.enableDispatching(topic2, null, client2);

        final String output = dump("-t", stringify(topic1));
        assertTrue(output.contains(stringify(topic1)));
        assertFalse(output.contains(stringify(topic2)));
    }

    @Test
    public void testDumpTopics() {
        final UUri topic1 = RESOURCE_URI;
        final UUri topic2 = RESOURCE2_URI;
        final Client client1 = registerNewClient(CLIENT);
        final Client client2 = registerNewClient(CLIENT2);
        mSubscriptionCache.addTopic(topic1, SERVER_URI);
        mSubscriptionCache.addTopic(topic2, SERVER_URI);
        mSubscriptionCache.addSubscriber(topic1, client1.getUri());

        mDispatcher.enableDispatching(topic1, null, client1);
        mDispatcher.enableDispatching(topic1, null, client2);
        mDispatcher.enableDispatching(topic2, null, client2);

        final String output = dump("-t");
        assertTrue(output.contains(stringify(topic1)));
        assertTrue(output.contains(stringify(topic2)));
    }

    @Test
    public void testDumpUnknownArg() {
        final UUri topic = RESOURCE_URI;
        mSubscriptionCache.addTopic(topic, SERVER_URI);
        mSubscriptionCache.addSubscriber(topic, mClient.getUri());
        mUTwin.addMessage(buildPublishMessage(topic));
        mDispatcher.enableDispatching(topic, null, mClient);

        final String output = dump("-s");
        assertFalse(output.contains(stringify(topic)));
    }
}
