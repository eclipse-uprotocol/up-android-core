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

import static org.eclipse.uprotocol.common.util.UStatusUtils.STATUS_OK;
import static org.eclipse.uprotocol.common.util.UStatusUtils.buildStatus;
import static org.eclipse.uprotocol.core.usubscription.USubscription.Method.CREATE_TOPIC;
import static org.eclipse.uprotocol.core.usubscription.USubscription.Method.DEPRECATE_TOPIC;
import static org.eclipse.uprotocol.core.usubscription.USubscription.Method.FETCH_SUBSCRIBERS;
import static org.eclipse.uprotocol.core.usubscription.USubscription.Method.FETCH_SUBSCRIPTIONS;
import static org.eclipse.uprotocol.core.usubscription.USubscription.Method.REGISTER_FOR_NOTIFICATIONS;
import static org.eclipse.uprotocol.core.usubscription.USubscription.Method.SUBSCRIBE;
import static org.eclipse.uprotocol.core.usubscription.USubscription.Method.UNREGISTER_FOR_NOTIFICATIONS;
import static org.eclipse.uprotocol.core.usubscription.USubscription.Method.UNSUBSCRIBE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.eclipse.uprotocol.core.TestBase;
import org.eclipse.uprotocol.core.UCore;
import org.eclipse.uprotocol.core.ubus.UBus;
import org.eclipse.uprotocol.core.usubscription.v3.FetchSubscribersResponse;
import org.eclipse.uprotocol.core.usubscription.v3.FetchSubscriptionsResponse;
import org.eclipse.uprotocol.core.usubscription.v3.SubscriptionResponse;
import org.eclipse.uprotocol.core.usubscription.v3.SubscriptionStatus.State;
import org.eclipse.uprotocol.core.usubscription.v3.Update;
import org.eclipse.uprotocol.v1.UAuthority;
import org.eclipse.uprotocol.v1.UCode;
import org.eclipse.uprotocol.v1.UMessage;
import org.eclipse.uprotocol.v1.UStatus;
import org.eclipse.uprotocol.v1.UUri;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;

import java.util.Set;

@RunWith(AndroidJUnit4.class)
public class USubscriptionTest extends SubscriptionTestBase {
    private final SubscriptionHandler mSubscriptionHandler = mock(SubscriptionHandler.class);
    private final SubscriptionListener mSubscriptionListener = mock(SubscriptionListener.class);
    private USubscription mUSubscription;
    private UCore mUCore;
    private UBus mUBus = mock(UBus.class);

    @Before
    public void setUp() {
        final Context context = RuntimeEnvironment.getApplication();
        mUSubscription = new USubscription(mSubscriptionHandler);
        mUCore = newMockUCoreBuilder(context).setUBus(mUBus).setUSubscription(mUSubscription).build();
        mUBus = mUCore.getUBus();
        mUSubscription.registerListener(mSubscriptionListener);
        when(mUCore.getUBus().registerClient(any(), any(), any())).thenReturn(STATUS_OK);
        when(mUCore.getUBus().enableDispatching(any(), any(), any())).thenReturn(STATUS_OK);
        mUSubscription.init(mUCore);
        mUSubscription.startup();
    }

    @Test
    public void testInit() {
        verify(mSubscriptionHandler, times(1)).init(any());
        verify(mUBus, times(1)).registerClient(eq(USubscription.SERVICE), any(), any());
        verify(mUBus, times(1)).enableDispatching(eq(SUBSCRIBE.localUri()), any(), any());
        verify(mUBus, times(1)).enableDispatching(eq(UNSUBSCRIBE.localUri()), any(), any());
        verify(mUBus, times(1)).enableDispatching(eq(CREATE_TOPIC.localUri()), any(), any());
        verify(mUBus, times(1)).enableDispatching(eq(DEPRECATE_TOPIC.localUri()), any(), any());
        verify(mUBus, times(1)).enableDispatching(eq(FETCH_SUBSCRIPTIONS.localUri()), any(), any());
        verify(mUBus, times(1)).enableDispatching(eq(FETCH_SUBSCRIBERS.localUri()), any(), any());
        verify(mUBus, times(1)).enableDispatching(eq(REGISTER_FOR_NOTIFICATIONS.localUri()), any(), any());
        verify(mUBus, times(1)).enableDispatching(eq(UNREGISTER_FOR_NOTIFICATIONS.localUri()), any(), any());
    }

    @Test
    public void testRegisterRpcListenerNotOK() {
        when(mUBus.enableDispatching(any(), any(), any())).thenReturn(buildStatus(UCode.INVALID_ARGUMENT));
        mUSubscription.init(mUCore);
        verify(mUBus, atLeast(8)).enableDispatching(any(), any(), any());
    }

    @Test
    public void testGetSubscribers() {
        when(mSubscriptionHandler.getSubscribers(TestBase.RESOURCE_URI)).thenReturn(Set.of(TestBase.LOCAL_CLIENT_URI));
        Set<UUri> subscribers = mUSubscription.getSubscribers(TestBase.RESOURCE_URI);
        assertTrue(subscribers.contains(TestBase.LOCAL_CLIENT_URI));
        verify(mSubscriptionHandler, times(1)).getSubscribers(any());
    }

    @Test
    public void testGetDeviceAuthority() {
        when(mUCore.getUBus().getDeviceAuthority()).thenReturn(LOCAL_AUTHORITY);
        assertEquals(LOCAL_AUTHORITY, mUSubscription.getDeviceAuthority());
    }

    @Test
    public void testGetDeviceAuthorityUnknown() {
        when(mUCore.getUBus().getDeviceAuthority()).thenReturn(UAuthority.getDefaultInstance());
        assertThrowsStatusException(UCode.FAILED_PRECONDITION, () -> mUSubscription.getDeviceAuthority());
    }

    @Test
    public void testHandleUnknownMessage() {
        final UMessage message = buildPublishMessage();
        mUSubscription.inject(message);
        sleep(DELAY_MS);
        verify(mSubscriptionHandler, times(1)).init(any());
        verifyNoMoreInteractions(mSubscriptionHandler);
    }

    @Test
    public void testHandleUnknownRequestMessage() {
        final UMessage message = buildRequestMessage();
        mUSubscription.inject(message);
        sleep(DELAY_MS);
        verify(mSubscriptionHandler, times(1)).init(any());
        verifyNoMoreInteractions(mSubscriptionHandler);
    }

    @Test
    public void testHandleRegisterForNotificationsRequestMessage() {
        final UMessage requestMessage = buildRegisterForNotificationsMessage(TestBase.RESOURCE_URI,
                TestBase.LOCAL_CLIENT_URI);
        when(mSubscriptionHandler.registerForNotifications(requestMessage)).thenReturn(STATUS_OK);
        mUSubscription.inject(requestMessage);
        verify(mSubscriptionHandler, timeout(DELAY_MS).times(1)).registerForNotifications(requestMessage);
    }

    @Test
    public void testHandleRegisterForNotificationsRequestFailure() {
        final UMessage requestMessage = buildRegisterForNotificationsMessage(TestBase.RESOURCE_URI,
                TestBase.LOCAL_CLIENT_URI);
        when(mSubscriptionHandler.registerForNotifications(requestMessage))
                .thenReturn(buildStatus(UCode.PERMISSION_DENIED));
        mUSubscription.inject(requestMessage);
        verify(mSubscriptionHandler, timeout(DELAY_MS).times(1)).registerForNotifications(requestMessage);
    }

    @Test
    public void testHandleUnregisterForNotificationsRequestMessage() {
        final UMessage requestMessage = buildUnregisterForNotificationsMessage(TestBase.RESOURCE_URI,
                TestBase.LOCAL_CLIENT_URI);
        when(mSubscriptionHandler.unregisterForNotifications(requestMessage)).thenReturn(STATUS_OK);
        mUSubscription.inject(requestMessage);
        verify(mSubscriptionHandler, timeout(DELAY_MS).times(1)).unregisterForNotifications(requestMessage);
    }

    @Test
    public void testHandleUnregisterForNotificationsRequestFailure() {
        final UMessage requestMessage = buildUnregisterForNotificationsMessage(TestBase.RESOURCE_URI,
                TestBase.LOCAL_CLIENT_URI);
        when(mSubscriptionHandler.unregisterForNotifications(requestMessage))
                .thenReturn(buildStatus(UCode.PERMISSION_DENIED));
        mUSubscription.inject(requestMessage);
        verify(mSubscriptionHandler, timeout(DELAY_MS).times(1)).unregisterForNotifications(requestMessage);
    }

    @Test
    public void testHandleCreateTopicRequestMessage() {
        final UMessage requestMessage = buildCreateTopicMessage(TestBase.RESOURCE_URI, TestBase.LOCAL_CLIENT_URI);
        when(mSubscriptionHandler.createTopic(requestMessage)).thenReturn(STATUS_OK);
        mUSubscription.inject(requestMessage);
        verify(mSubscriptionHandler, timeout(DELAY_MS).times(1)).createTopic(requestMessage);
    }

    @Test
    public void testHandleCreateTopicRequestFailure() {
        final UMessage requestMessage = buildCreateTopicMessage(TestBase.RESOURCE_URI, TestBase.LOCAL_CLIENT_URI);
        when(mSubscriptionHandler.createTopic(requestMessage))
                .thenReturn(buildStatus(UCode.PERMISSION_DENIED));
        mUSubscription.inject(requestMessage);
        verify(mSubscriptionHandler, timeout(DELAY_MS).times(1)).createTopic(requestMessage);
    }

    @Test
    public void testHandleDeprecateTopicRequestMessage() {
        final UMessage requestMessage = buildDeprecateTopicMessage(TestBase.RESOURCE_URI, TestBase.LOCAL_CLIENT_URI);
        when(mSubscriptionHandler.deprecateTopic(requestMessage)).thenReturn(STATUS_OK);
        mUSubscription.inject(requestMessage);
        verify(mSubscriptionHandler, timeout(DELAY_MS).times(1)).deprecateTopic(requestMessage);
    }

    @Test
    public void testHandleDeprecateTopicRequestFailure() {
        final UMessage requestMessage = buildDeprecateTopicMessage(TestBase.RESOURCE_URI, TestBase.LOCAL_CLIENT_URI);
        when(mSubscriptionHandler.deprecateTopic(requestMessage))
                .thenReturn(buildStatus(UCode.PERMISSION_DENIED));
        mUSubscription.inject(requestMessage);
        verify(mSubscriptionHandler, timeout(DELAY_MS).times(1)).deprecateTopic(requestMessage);
    }

    @Test
    public void testHandleSubscribeRequestMessage() {
        final UMessage requestMessage = buildLocalSubscriptionRequestMessage(TestBase.RESOURCE_URI,
                TestBase.LOCAL_CLIENT_URI);
        when(mSubscriptionHandler.subscribe(requestMessage)).thenReturn(SubscriptionResponse.getDefaultInstance());
        mUSubscription.inject(requestMessage);
        verify(mSubscriptionHandler, timeout(DELAY_LONG_MS).times(1)).subscribe(requestMessage);
    }

    @Test
    public void testHandleSubscribeRequestFailure() {
        final UMessage requestMessage = buildLocalSubscriptionRequestMessage(TestBase.RESOURCE_URI,
                TestBase.LOCAL_CLIENT_URI);
        when(mSubscriptionHandler.subscribe(requestMessage))
                .thenReturn(SubscriptionResponse.newBuilder()
                        .setStatus(buildSubscriptionStatus(State.UNSUBSCRIBED, UCode.NOT_FOUND))
                        .build());
        mUSubscription.inject(requestMessage);
        verify(mSubscriptionHandler, timeout(DELAY_MS).times(1)).subscribe(requestMessage);
    }

    @Test
    public void testHandleUnsubscribeRequestMessage() {
        final UMessage requestMessage = buildUnsubscribeMessage(TestBase.RESOURCE_URI, TestBase.LOCAL_CLIENT_URI);
        when(mSubscriptionHandler.unsubscribe(requestMessage)).thenReturn(STATUS_OK);
        mUSubscription.inject(requestMessage);
        verify(mSubscriptionHandler, timeout(DELAY_MS).times(1)).unsubscribe(requestMessage);
    }

    @Test
    public void testHandleUnsubscribeRequestFailure() {
        final UMessage requestMessage = buildUnsubscribeMessage(TestBase.RESOURCE_URI, TestBase.LOCAL_CLIENT_URI);
        when(mSubscriptionHandler.unsubscribe(requestMessage)).thenReturn(buildStatus(UCode.PERMISSION_DENIED));
        mUSubscription.inject(requestMessage);
        verify(mSubscriptionHandler, timeout(DELAY_MS).times(1)).unsubscribe(requestMessage);
    }

    @Test
    public void testHandleFetchSubscriptionsRequestMessage() {
        final UMessage requestMessage = buildFetchSubscriptionsByTopicMessage(TestBase.RESOURCE_URI,
                TestBase.LOCAL_CLIENT_URI);
        when(mSubscriptionHandler.fetchSubscriptions(requestMessage)).thenReturn(
                FetchSubscriptionsResponse.getDefaultInstance());
        mUSubscription.inject(requestMessage);
        verify(mSubscriptionHandler, timeout(DELAY_MS).times(1)).fetchSubscriptions(requestMessage);
    }

    @Test
    public void testHandleFetchSubscriptionsRequestFailure() {
        final UMessage requestMessage = buildFetchSubscriptionsByTopicMessage(TestBase.RESOURCE_URI,
                TestBase.LOCAL_CLIENT_URI);
        when(mSubscriptionHandler.fetchSubscriptions(requestMessage)).thenReturn(
                FetchSubscriptionsResponse.newBuilder().setStatus(buildStatus(UCode.PERMISSION_DENIED)).build());
        mUSubscription.inject(requestMessage);
        verify(mSubscriptionHandler, timeout(DELAY_MS).times(1)).fetchSubscriptions(requestMessage);
    }

    @Test
    public void testHandleFetchSubscribersRequestMessage() {
        final UMessage requestMessage = buildFetchSubscribersMessage(TestBase.RESOURCE_URI, TestBase.LOCAL_CLIENT_URI);
        when(mSubscriptionHandler.fetchSubscribers(requestMessage)).thenReturn(
                FetchSubscribersResponse.getDefaultInstance());
        mUSubscription.inject(requestMessage);
        verify(mSubscriptionHandler, timeout(DELAY_MS).times(1)).fetchSubscribers(requestMessage);
    }

    @Test
    public void testHandleFetchSubscribersRequestFailure() {
        final UMessage requestMessage = buildFetchSubscribersMessage(TestBase.RESOURCE_URI, TestBase.LOCAL_CLIENT_URI);
        when(mSubscriptionHandler.fetchSubscribers(requestMessage)).thenReturn(
                FetchSubscribersResponse.newBuilder().setStatus(buildStatus(UCode.PERMISSION_DENIED)).build());
        mUSubscription.inject(requestMessage);
        verify(mSubscriptionHandler, timeout(DELAY_MS).times(1)).fetchSubscribers(requestMessage);
    }

    @Test
    public void testGetPublisher() {
        when(mSubscriptionHandler.getPublisher(any())).thenReturn(TestBase.RESOURCE_URI);
        final UUri value = mUSubscription.getPublisher(TestBase.RESOURCE_URI);
        assertEquals(TestBase.RESOURCE_URI, value);
        verify(mSubscriptionHandler, times(1)).getPublisher(any());
    }

    @Test
    public void testNotifySubscriptionChangedAndTopicCreatedAndTopicDeleted() {
        final UStatus status = UStatus.newBuilder().setCode(UCode.OK).build();
        when(mUCore.getUBus().send(any(), any())).thenReturn(status);
        mUSubscription.sendSubscriptionUpdate(TestBase.LOCAL_CLIENT_URI, Update.getDefaultInstance());
        mUSubscription.notifySubscriptionChanged(Update.getDefaultInstance());
        mUSubscription.notifyTopicCreated(TestBase.RESOURCE_URI, TestBase.LOCAL_CLIENT_URI);
        mUSubscription.notifyTopicDeprecated(TestBase.RESOURCE_URI);
        //Notify subscription change verified
        verify(mUCore.getUBus(), times(1)).send(any(), any());
    }

    @Test
    public void testLogStatus() {
        assertEquals(UCode.OK, USubscription.logStatus(Log.DEBUG, SUBSCRIBE.name(), STATUS_OK).getCode());
    }

    @Test
    public void testShutdownTimeout() {
        mUSubscription.getExecutor().execute(() -> sleep(200));
        mUSubscription.shutdown();
        verify(mUBus, atLeastOnce()).disableDispatching(any(), any(), any());
    }

    @Test
    public void testShutdownInterrupted() {
        mUSubscription.getExecutor().execute(() -> sleep(200));
        final Thread thread = new Thread(() -> mUSubscription.shutdown());
        thread.start();
        thread.interrupt();
        verify(mUBus, times(0)).disableDispatching(any(), any(), any());
    }

    @After
    public void testShutdown() {
        mUSubscription.unregisterListener(mSubscriptionListener);
        mUSubscription.shutdown();
        verify(mUBus, times(1)).disableDispatching(eq(SUBSCRIBE.localUri()), any(), any());
        verify(mUBus, times(1)).disableDispatching(eq(UNSUBSCRIBE.localUri()), any(), any());
        verify(mUBus, times(1)).disableDispatching(eq(CREATE_TOPIC.localUri()), any(), any());
        verify(mUBus, times(1)).disableDispatching(eq(DEPRECATE_TOPIC.localUri()), any(), any());
        verify(mUBus, times(1)).disableDispatching(eq(FETCH_SUBSCRIPTIONS.localUri()), any(), any());
        verify(mUBus, times(1)).disableDispatching(eq(FETCH_SUBSCRIBERS.localUri()), any(), any());
        verify(mUBus, times(1)).disableDispatching(eq(REGISTER_FOR_NOTIFICATIONS.localUri()), any(), any());
        verify(mUBus, times(1)).disableDispatching(eq(UNREGISTER_FOR_NOTIFICATIONS.localUri()), any(), any());
    }
}
