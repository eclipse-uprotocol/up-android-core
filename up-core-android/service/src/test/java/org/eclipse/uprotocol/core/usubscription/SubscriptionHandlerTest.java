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
import static org.eclipse.uprotocol.core.internal.util.UUriUtils.getClientUri;
import static org.eclipse.uprotocol.core.internal.util.UUriUtils.toUriString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static java.util.Collections.emptyList;

import android.content.Context;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.eclipse.uprotocol.core.TestBase;
import org.eclipse.uprotocol.core.usubscription.database.DatabaseHelper;
import org.eclipse.uprotocol.core.usubscription.database.SubscribersRecord;
import org.eclipse.uprotocol.core.usubscription.v3.FetchSubscriptionsResponse;
import org.eclipse.uprotocol.core.usubscription.v3.Subscription;
import org.eclipse.uprotocol.core.usubscription.v3.SubscriptionRequest;
import org.eclipse.uprotocol.core.usubscription.v3.SubscriptionResponse;
import org.eclipse.uprotocol.core.usubscription.v3.SubscriptionStatus.State;
import org.eclipse.uprotocol.v1.UCode;
import org.eclipse.uprotocol.v1.UMessage;
import org.eclipse.uprotocol.v1.UStatus;
import org.eclipse.uprotocol.v1.UUri;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.stubbing.Answer;
import org.robolectric.RuntimeEnvironment;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class SubscriptionHandlerTest extends SubscriptionTestBase {
    private final ScheduledExecutorService mExecutor = mock(ScheduledExecutorService.class);
    private SubscriptionHandler mSubscriptionHandler;
    private DatabaseHelper mDbHelper;
    private USubscription mUSubscription;
    private CacheHandler mCacheHandler;
    private Context mContext;

    private static void setLogLevel(int level) {
        USubscription.DEBUG = (level <= Log.DEBUG);
        USubscription.VERBOSE = (level <= Log.VERBOSE);
    }

    @Before
    public void setUp() {
        setLogLevel(Log.VERBOSE);
        mContext = RuntimeEnvironment.getApplication();
        mUSubscription = mock(USubscription.class);
        mDbHelper = mock(DatabaseHelper.class);
        mCacheHandler = mock(CacheHandler.class);
        mSubscriptionHandler = new SubscriptionHandler(mContext, mDbHelper, mCacheHandler);
        prepareExecuteOnSameThread();
        when(mDbHelper.getPendingTopics()).thenReturn(emptyList());
        mSubscriptionHandler.init(mUSubscription);
    }

    @SuppressWarnings({"ResultOfMethodCallIgnored", "BlockingMethodInNonBlockingContext","unused"})
    private void prepareExecuteOnOtherThread(CountDownLatch latch) {
        Executor executor = Executors.newSingleThreadExecutor();
        doAnswer((Answer<Object>) invocationOnMock -> {
            final Runnable task = invocationOnMock.getArgument(0);
            executor.execute(task);
            return null;
        }).when(mExecutor).execute(any(Runnable.class));

        doAnswer((Answer<Object>) invocationOnMock -> {
            final Runnable task = invocationOnMock.getArgument(0);
            executor.execute(() -> {
                try {
                    latch.await(DELAY_LONG_MS, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                task.run();
            });
            return mock(ScheduledFuture.class);
        }).when(mExecutor).schedule(any(Runnable.class), anyLong(), any());
    }

    private void prepareExecuteOnSameThread() {
        doAnswer((Answer<Object>) invocationOnMock -> {
            ((Runnable) invocationOnMock.getArgument(0)).run();
            return null;
        }).when(mExecutor).execute(any(Runnable.class));

        doAnswer((Answer<Object>) invocationOnMock -> {
            ((Runnable) invocationOnMock.getArgument(0)).run();
            return null;
        }).when(mExecutor).schedule(any(Runnable.class), anyLong(), any());
    }

    @Test
    public void testInit() {
        verify(mDbHelper, times(1)).init(mContext);
    }

    @Test
    public void testGetSubscribersEmpty() {
        when(mDbHelper.getSubscriptionState(any())).thenReturn(State.SUBSCRIBED.getNumber());
        when(mDbHelper.getSubscribers(any())).thenReturn(emptyList());
        final Set<UUri> result = mSubscriptionHandler.getSubscribers(TestBase.RESOURCE_URI);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testGetSubscribers() {
        final List<String> sinks = List.of(LOCAL_CLIENT_URI, LOCAL_CLIENT2_URI);
        final List<UUri> sinkUris = List.of(TestBase.LOCAL_CLIENT_URI, TestBase.LOCAL_CLIENT2_URI);
        when(mDbHelper.getSubscribers(any())).thenReturn(sinks);
        when(mDbHelper.getSubscriptionState(any())).thenReturn(State.SUBSCRIBED.getNumber());
        assertEquals(new HashSet<>(sinkUris), mSubscriptionHandler.getSubscribers(TestBase.RESOURCE_URI));
        verify(mDbHelper, times(1)).getSubscribers(any());
    }

    @Test
    public void testGetSubscribersExceptionally() {
        when(mDbHelper.getSubscriptionState(any())).thenThrow(NullPointerException.class).thenReturn(
                State.SUBSCRIBED_VALUE);
        Set<UUri> result = mSubscriptionHandler.getSubscribers(TestBase.RESOURCE_URI);
        assertTrue(result.isEmpty());

        when(mDbHelper.getSubscribers(any())).thenThrow(NullPointerException.class);
        result = mSubscriptionHandler.getSubscribers(TestBase.RESOURCE_URI);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testCreateTopic() {
        when(mDbHelper.addTopic(any())).thenReturn(1L);
        when(mDbHelper.isRegisteredForNotification(any())).thenReturn(false);
        final UStatus status = mSubscriptionHandler.createTopic(buildCreateTopicMessage(TestBase.RESOURCE_URI,
                TestBase.LOCAL_SERVER_URI));
        assertStatus(UCode.OK, status);
        verify(mDbHelper, times(1)).isRegisteredForNotification(any());
        verify(mDbHelper, times(1)).addTopic(any());
        verify(mUSubscription, times(1)).notifyTopicCreated(any(), any());
    }

    @Test
    public void testCreateTopicNegative() {
        assertStatus(UCode.PERMISSION_DENIED,
                mSubscriptionHandler.createTopic(buildCreateTopicMessage(TestBase.RESOURCE_URI,
                        TestBase.LOCAL_CLIENT_URI)));
        assertStatus(UCode.PERMISSION_DENIED,
                mSubscriptionHandler.createTopic(buildCreateTopicMessage(TestBase.REMOTE_RESOURCE_URI,
                        TestBase.LOCAL_CLIENT_URI)));
        assertStatus(UCode.PERMISSION_DENIED,
                mSubscriptionHandler.createTopic(buildCreateTopicMessage(TestBase.RESOURCE_URI,
                        TestBase.REMOTE_SERVER_URI)));
        assertStatus(UCode.PERMISSION_DENIED,
                mSubscriptionHandler.createTopic(buildCreateTopicMessage(TestBase.REMOTE_RESOURCE_URI,
                        TestBase.REMOTE_SERVER_URI)));
    }

    @Test
    public void testCreateTopicInvalidRequest() {
        final UStatus status = mSubscriptionHandler.createTopic(buildDeprecateTopicMessage(TestBase.RESOURCE_URI,
                TestBase.LOCAL_SERVER_URI));
        assertStatus(UCode.INVALID_ARGUMENT, status);
    }

    @Test
    public void testCreateTopicExceptionally() {
        when(mDbHelper.addTopic(any())).thenReturn(-1L);
        final UStatus status = mSubscriptionHandler.createTopic(TestBase.RESOURCE_URI,
                getClientUri(TestBase.LOCAL_SERVER_URI));
        assertStatus(UCode.ABORTED, status);
        verify(mDbHelper, times(1)).isRegisteredForNotification(any());
        verify(mDbHelper, times(1)).addTopic(any());
    }

    @Test
    public void testCreateTopicInfo() {
        setLogLevel(Log.INFO);
        final UStatus status = mSubscriptionHandler.createTopic(TestBase.RESOURCE_URI,
                getClientUri(TestBase.LOCAL_SERVER_URI));
        assertStatus(UCode.OK, status);
        verify(mDbHelper, times(1)).isRegisteredForNotification(any());
        verify(mDbHelper, times(1)).addTopic(any());
    }

    @Test
    public void testIsTopicCreated() {
        when(mDbHelper.isTopicCreated(any())).thenReturn(true);
        assertTrue(mSubscriptionHandler.isTopicCreated(RESOURCE_URI));
    }

    @Test
    public void testIsTopicCreatedFalse() {
        when(mDbHelper.isTopicCreated(any())).thenReturn(false);
        assertFalse(mSubscriptionHandler.isTopicCreated(RESOURCE_URI));
    }

    @Test
    public void testIsTopicCreatedExceptionally() {
        when(mDbHelper.isTopicCreated(any())).thenThrow(NullPointerException.class);
        assertFalse(mSubscriptionHandler.isTopicCreated(RESOURCE_URI));
    }

    @Test
    public void testDeprecateTopicUnimplemented() {
        final UStatus status = mSubscriptionHandler.deprecateTopic(buildDeprecateTopicMessage(TestBase.RESOURCE_URI,
                TestBase.LOCAL_SERVER_URI));
        assertStatus(UCode.UNIMPLEMENTED, status);
    }

    @Test
    public void testDeprecateTopicExceptionally() {
        UStatus status = mSubscriptionHandler.deprecateTopic(buildPublishMessage());
        assertStatus(UCode.INVALID_ARGUMENT, status);

        status = mSubscriptionHandler.deprecateTopic(buildDeprecateTopicMessage(TestBase.REMOTE_RESOURCE_URI,
                TestBase.LOCAL_SERVER_URI));
        assertStatus(UCode.PERMISSION_DENIED, status);

        status = mSubscriptionHandler.deprecateTopic(buildDeprecateTopicMessage(TestBase.RESOURCE_URI,
                TestBase.REMOTE_SERVER_URI));
        assertStatus(UCode.PERMISSION_DENIED, status);
    }

    @Test
    public void testDeprecateTopic() {
        setLogLevel(Log.DEBUG);
        assertStatus(UCode.UNIMPLEMENTED, mSubscriptionHandler.deprecateTopic(TestBase.RESOURCE_URI));
    }

    @Test
    public void testRequestDataSubscriptionRequest() {
        final SubscriptionRequest request = SubscriptionRequest.newBuilder().setTopic(TestBase.RESOURCE_URI).build();
        final RequestData requestData = new RequestData(request);
        assertEquals(TestBase.RESOURCE_URI, requestData.topic);
    }

    @Test
    public void testSubscribeTopicNotCreated() {
        final UMessage message = buildLocalSubscriptionRequestMessage(TestBase.RESOURCE_URI, TestBase.LOCAL_CLIENT_URI);
        assertNotNull(mSubscriptionHandler.subscribe(message));
        assertEquals(State.UNSUBSCRIBED, mSubscriptionHandler.getSubscriptionState(RESOURCE_URI));
        verify(mUSubscription, times(0)).notifySubscriptionChanged(any());
    }

    @Test
    public void testSubscribe() {
        setLogLevel(Log.INFO);
        when(mDbHelper.isTopicCreated(any())).thenReturn(true);
        when(mDbHelper.getSubscriptionState(any()))
                .thenReturn(State.UNSUBSCRIBED.getNumber())
                .thenReturn(State.SUBSCRIBED.getNumber());
        when(mDbHelper.addSubscription(any())).thenReturn(1L);
        when(mDbHelper.getPublisherIfRegistered(any())).thenReturn(LOCAL_SERVER_URI);
        final SubscriptionResponse response = mSubscriptionHandler.subscribe(
                buildLocalSubscriptionRequestMessage(TestBase.RESOURCE_URI, TestBase.LOCAL_CLIENT_URI));
        assertNotNull(response);
        assertEquals(State.SUBSCRIBED, response.getStatus().getState());
        verify(mDbHelper, times(1)).addSubscription(any());
        verify(mDbHelper, times(1)).addSubscriber(any());
        verify(mUSubscription, times(1)).sendSubscriptionUpdate(any(), any());
        verify(mUSubscription, times(1)).notifySubscriptionChanged(any());
    }

    @Test
    public void testSubscriberAlreadyExists() {
        when(mDbHelper.isTopicCreated(any())).thenReturn(true);
        when(mDbHelper.getSubscriptionState(any())).thenReturn(State.SUBSCRIBED.getNumber());
        when(mDbHelper.getSubscribers(any())).thenReturn(List.of(LOCAL_CLIENT_URI));
        final SubscriptionResponse response = mSubscriptionHandler.subscribe(
                buildLocalSubscriptionRequestMessage(TestBase.RESOURCE_URI, TestBase.LOCAL_CLIENT_URI));
        assertNotNull(response);
        assertEquals(UCode.OK, response.getStatus().getCode());
        verify(mDbHelper, times(0)).addSubscriber(any());
    }

    //Future use case
    @Test
    public void testSubscribeFromRemote() {
        when(mDbHelper.isTopicCreated(any())).thenReturn(true);
        when(mDbHelper.getSubscriptionState(any())).thenReturn(State.SUBSCRIBED.getNumber());
        when(mDbHelper.addSubscription(newSubscriptionsRecord(REMOTE_CLIENT_URI, REQUEST_ID))).thenReturn(1L);
        SubscriptionResponse response = mSubscriptionHandler.subscribe(
                buildLocalSubscriptionRequestMessage(TestBase.RESOURCE_URI, TestBase.REMOTE_CLIENT_URI));
        assertNotNull(response);
        assertEquals(State.SUBSCRIBED, response.getStatus().getState());
    }

    @Test
    public void testSubscribeExceptionally() {
        //pass wrong message type to trigger InvalidProtocolBufferException
        SubscriptionResponse response = mSubscriptionHandler.subscribe(
                buildCreateTopicMessage(TestBase.RESOURCE_URI,
                        TestBase.LOCAL_SERVER_URI));
        assertNotNull(response);
        assertEquals(State.UNSUBSCRIBED, response.getStatus().getState());

        doThrow(SubscriptionException.class).when(mDbHelper).addSubscription(any());
        response = mSubscriptionHandler.subscribe(buildLocalSubscriptionRequestMessage(TestBase.RESOURCE_URI,
                TestBase.LOCAL_SERVER_URI));
        assertNotNull(response);
        assertNotSame(UCode.OK, response.getStatus().getCode());

        doThrow(NullPointerException.class).when(mDbHelper).addSubscription(any());
        response = mSubscriptionHandler.subscribe(buildLocalSubscriptionRequestMessage(TestBase.RESOURCE_URI,
                TestBase.LOCAL_SERVER_URI));
        assertNotNull(response);
        assertNotSame(UCode.OK, response.getStatus().getCode());

        doThrow(IllegalStateException.class).when(mDbHelper).addSubscription(any());
        response = mSubscriptionHandler.subscribe(buildLocalSubscriptionRequestMessage(TestBase.RESOURCE_URI,
                TestBase.LOCAL_SERVER_URI));
        assertNotNull(response);
        assertNotSame(UCode.OK, response.getStatus().getCode());
    }

    @Test
    public void testSubscribeRemote() {
        final UMessage requestMessage = buildRemoteSubscriptionRequestMessage(TestBase.REMOTE_RESOURCE_URI,
                TestBase.LOCAL_CLIENT_URI);
        final SubscriptionResponse response = mSubscriptionHandler.subscribe(requestMessage);
        assertNotNull(response);
        assertEquals(UCode.UNIMPLEMENTED, response.getStatus().getCode());
        verify(mDbHelper, times(0)).addSubscription(any());
        verify(mDbHelper, times(0)).addSubscriber(any());
    }

    @Test
    public void testAddSubscriptionFailure() {
        final UMessage requestMessage = buildRemoteSubscriptionRequestMessage(TestBase.LOCAL_RESOURCE_URI,
                TestBase.LOCAL_CLIENT_URI);
        when(mDbHelper.isTopicCreated(any())).thenReturn(true);
        when(mDbHelper.getSubscriptionState(any())).thenReturn(State.UNSUBSCRIBED.getNumber());
        when(mDbHelper.addSubscription(any())).thenReturn(-1L);
        final SubscriptionResponse response = mSubscriptionHandler.subscribe(requestMessage);
        assertNotNull(response);
        assertEquals(UCode.ABORTED, response.getStatus().getCode());
        verify(mDbHelper, times(1)).addSubscription(any());
        verify(mDbHelper, times(0)).addSubscriber(any());
    }

    @Test
    public void testAddSubscriberFailure() {
        final UMessage requestMessage = buildRemoteSubscriptionRequestMessage(TestBase.LOCAL_RESOURCE_URI,
                TestBase.LOCAL_CLIENT_URI);
        when(mDbHelper.isTopicCreated(any())).thenReturn(true);
        when(mDbHelper.getSubscriptionState(any())).thenReturn(State.SUBSCRIBED.getNumber());
        when(mDbHelper.addSubscriber(any())).thenReturn(-1L);
        final SubscriptionResponse response = mSubscriptionHandler.subscribe(requestMessage);
        assertNotNull(response);
        assertEquals(UCode.ABORTED, response.getStatus().getCode());
        verify(mDbHelper, times(0)).addSubscription(any());
        verify(mDbHelper, times(1)).addSubscriber(any());
    }

    @Test
    public void testUnsubscribeInvalidRequest() {
        final UStatus status = mSubscriptionHandler.unsubscribe(
                buildLocalSubscriptionRequestMessage(TestBase.RESOURCE_URI, TestBase.LOCAL_CLIENT_URI));
        assertStatus(UCode.INVALID_ARGUMENT, status);
    }

    @Test
    public void testUnsubscribeLocalFoundSubscription() {
        when(mDbHelper.getSubscriptionState(any()))
                .thenReturn(State.SUBSCRIBED.getNumber())
                .thenReturn(State.SUBSCRIBED.getNumber());
        when(mDbHelper.getSubscribers(any())).thenReturn(List.of(LOCAL_CLIENT_URI));
        when(mDbHelper.getSubscriber(any(), any())).thenReturn(
                newSubscribersRecord(RESOURCE_URI, LOCAL_CLIENT_URI, SUBSCRIBERS_DETAILS)).thenReturn(null);
        when(mDbHelper.getPublisherIfRegistered(any())).thenReturn(LOCAL_SERVER_URI);
        UStatus status = mSubscriptionHandler.unsubscribe(
                buildUnsubscribeMessage(TestBase.RESOURCE_URI, TestBase.LOCAL_CLIENT_URI));
        assertStatus(UCode.OK, status);
        setLogLevel(Log.INFO);
        status = mSubscriptionHandler.unsubscribe(
                buildUnsubscribeMessage(TestBase.RESOURCE_URI, TestBase.LOCAL_CLIENT_URI));
        assertStatus(UCode.OK, status);
        verify(mDbHelper, times(1)).deleteSubscriber(any(), any());
        verify(mDbHelper, times(1)).deleteTopicFromSubscriptions(any());
    }

    @Test
    public void testUnsubscribeSubscriptionNotFound() {
        when(mDbHelper.getSubscriptionState(any())).thenReturn(State.UNSUBSCRIBED.getNumber());
        final UStatus status = mSubscriptionHandler.unsubscribe(buildUnsubscribeMessage(TestBase.RESOURCE_URI,
                TestBase.LOCAL_CLIENT_URI));
        assertStatus(UCode.OK, status);
    }

    @Test
    public void testUnsubscribeFromRemote() {
        setLogLevel(Log.INFO);
        when(mDbHelper.getSubscriptionState(any())).thenReturn(State.SUBSCRIBED.getNumber());
        when(mDbHelper.getSubscriber(any(), any())).thenReturn(
                newSubscribersRecord(RESOURCE_URI, REMOTE_CLIENT_URI, SUBSCRIBERS_DETAILS));
        when(mDbHelper.getSubscribers(any())).thenReturn(List.of(REMOTE_CLIENT_URI));
        final UStatus status = mSubscriptionHandler.unsubscribe(buildUnsubscribeMessage(TestBase.RESOURCE_URI,
                TestBase.REMOTE_CLIENT_URI));
        assertStatus(UCode.OK, status);
        verify(mDbHelper, times(1)).deleteSubscriber(any(), any());
    }

    @Test
    public void testUnsubscribeWithMultipleSubscribersForSameTopic() {
        setLogLevel(Log.INFO);
        when(mDbHelper.getSubscriptionState(any())).thenReturn(State.SUBSCRIBED.getNumber());
        when(mDbHelper.getSubscriber(any(), any())).thenReturn(
                newSubscribersRecord(RESOURCE_URI, REMOTE_CLIENT_URI, SUBSCRIBERS_DETAILS));
        when(mDbHelper.getSubscribers(any())).thenReturn(
                List.of(REMOTE_CLIENT_URI, LOCAL_CLIENT_URI, LOCAL_CLIENT2_URI));
        final UStatus status = mSubscriptionHandler.unsubscribe(buildUnsubscribeMessage(TestBase.RESOURCE_URI,
                TestBase.REMOTE_CLIENT_URI));
        assertStatus(UCode.OK, status);
        verify(mDbHelper, times(1)).deleteSubscriber(any(), any());
    }

    @Test
    public void testFetchSubscriptionsByTopic() {
        final Subscription subscription = Subscription.newBuilder().setTopic(TestBase.RESOURCE_URI).build();
        List<Subscription> subscriptions = new ArrayList<>();
        subscriptions.add(subscription);
        when(mCacheHandler.fetchSubscriptionsByTopic((UUri) any())).thenReturn(subscriptions);
        final FetchSubscriptionsResponse response = mSubscriptionHandler.fetchSubscriptions(
                buildFetchSubscriptionsByTopicMessage(TestBase.RESOURCE_URI, TestBase.LOCAL_CLIENT_URI));
        assertEquals(STATUS_OK, response.getStatus());
        verify(mCacheHandler, times(1)).fetchSubscriptionsByTopic((UUri) any());
    }

    @Test
    public void testFetchSubscriptionsBySubscriber() {
        final Subscription subscription =
                Subscription.newBuilder().setSubscriber(buildSubscriber(TestBase.LOCAL_CLIENT_URI)).build();
        List<Subscription> subscriptions = new ArrayList<>();
        subscriptions.add(subscription);
        when(mCacheHandler.fetchSubscriptionsBySubscriber(any())).thenReturn(subscriptions);
        final FetchSubscriptionsResponse response = mSubscriptionHandler.fetchSubscriptions(
                buildFetchSubscriptionsBySubscriberMessage(TestBase.LOCAL_CLIENT_URI));
        assertEquals(STATUS_OK, response.getStatus());
        verify(mCacheHandler, times(1)).fetchSubscriptionsBySubscriber(any());
    }

    @Test
    public void testFetchSubscriptions() {
        setLogLevel(Log.INFO);
        final FetchSubscriptionsResponse response =
                mSubscriptionHandler.fetchSubscriptions(buildFetchSubscriptionsRequestMessage());
        assertEquals(UCode.NOT_FOUND, response.getStatus().getCode());
    }

    @Test
    public void testFetchSubscriptionsExceptionally() {
        final FetchSubscriptionsResponse response = mSubscriptionHandler.fetchSubscriptions(
                buildFetchSubscribersMessage(TestBase.RESOURCE_URI, TestBase.LOCAL_CLIENT_URI));
        assertNotEquals(STATUS_OK, response.getStatus());
    }

    @Test
    public void testFetchSubscribersValid() {
        final UMessage message = buildFetchSubscribersMessage(TestBase.RESOURCE_URI, TestBase.LOCAL_CLIENT_URI);
        List<SubscribersRecord> subscribersRecords = new ArrayList<>();
        subscribersRecords.add(newSubscribersRecord(RESOURCE_URI, LOCAL_CLIENT_URI, ""));
        when(mDbHelper.fetchSubscriptionsByTopic(any())).thenReturn(subscribersRecords);
        assertEquals(UCode.OK, mSubscriptionHandler.fetchSubscribers(message).getStatus().getCode());
    }

    @Test
    public void testFetchSubscribersResponseNotFound() {
        setLogLevel(Log.INFO);
        final UMessage message = buildFetchSubscribersMessage(TestBase.RESOURCE_URI, TestBase.LOCAL_CLIENT_URI);
        assertEquals(UCode.NOT_FOUND, mSubscriptionHandler.fetchSubscribers(message).getStatus().getCode());
    }

    @Test
    public void testFetchSubscribersInvalid() {
        final UMessage message = buildCreateTopicMessage(TestBase.RESOURCE_URI, TestBase.LOCAL_CLIENT_URI);
        assertEquals(UCode.ABORTED, mSubscriptionHandler.fetchSubscribers(message).getStatus().getCode());
    }

    @Test
    public void testRegisterForNotifications() {
        when(mDbHelper.isTopicCreated(any())).thenReturn(true);
        assertStatus(UCode.OK, mSubscriptionHandler.registerForNotifications(
                buildRegisterForNotificationsMessage(TestBase.RESOURCE_URI,
                        TestBase.LOCAL_SERVER_URI)));
        verify(mDbHelper, times(1)).isTopicCreated(any());
    }

    @Test
    public void testRegisterForNotificationsNotFound() {
        setLogLevel(Log.INFO);
        when(mDbHelper.isTopicCreated(any())).thenReturn(false);
        assertStatus(UCode.NOT_FOUND, mSubscriptionHandler.registerForNotifications(
                buildRegisterForNotificationsMessage(TestBase.RESOURCE_URI,
                        TestBase.LOCAL_SERVER_URI)));
        verify(mDbHelper, times(1)).isTopicCreated(any());
    }

    @Test
    public void testRegisterForNotificationsExceptionally() {
        when(mDbHelper.isTopicCreated(any())).thenReturn(true);
        doThrow(NullPointerException.class).when(mDbHelper).updateTopic(RESOURCE_URI, true);
        assertStatus(UCode.INVALID_ARGUMENT, mSubscriptionHandler.registerForNotifications(
                buildRegisterForNotificationsMessage(TestBase.RESOURCE_URI,
                        TestBase.LOCAL_SERVER_URI)));
        verify(mDbHelper, times(1)).isTopicCreated(any());
    }

    @Test
    public void testRegisterForNotificationsInvalid() {
        //pass wrong message type to trigger Exception
        final UMessage message = buildCreateTopicMessage(TestBase.RESOURCE_URI, TestBase.LOCAL_CLIENT_URI);
        assertEquals(UCode.INVALID_ARGUMENT, mSubscriptionHandler.registerForNotifications(message).getCode());
    }

    @Test
    public void testUnregisterForNotificationsAndIsSubscriberTrue() {
        when(mDbHelper.isRegisteredForNotification(any())).thenReturn(true);
        doNothing().when(mDbHelper).updateTopic(RESOURCE_URI, false);
        assertStatus(UCode.OK, mSubscriptionHandler.unregisterForNotifications(
                buildUnregisterForNotificationsMessage(TestBase.RESOURCE_URI,
                        TestBase.LOCAL_SERVER_URI)));
        verify(mDbHelper, times(1)).isRegisteredForNotification(any());
    }

    @Test
    public void testUnregisterForNotificationsAndIsSubscriberFalse() {
        setLogLevel(Log.INFO);
        when(mDbHelper.isRegisteredForNotification(any())).thenReturn(false);
        assertStatus(UCode.NOT_FOUND, mSubscriptionHandler.unregisterForNotifications(
                buildUnregisterForNotificationsMessage(TestBase.RESOURCE_URI,
                        TestBase.LOCAL_SERVER_URI)));
        verify(mDbHelper, times(1)).isRegisteredForNotification(any());
    }

    @Test
    public void testUnregisterForNotificationsExceptionally() {
        when(mDbHelper.isRegisteredForNotification(any())).thenThrow(NullPointerException.class);
        assertStatus(UCode.INVALID_ARGUMENT, mSubscriptionHandler.unregisterForNotifications(
                buildUnregisterForNotificationsMessage(TestBase.RESOURCE_URI,
                        TestBase.LOCAL_SERVER_URI)));
        verify(mDbHelper, times(1)).isRegisteredForNotification(any());
    }

    @Test
    public void tesUnregisterForNotificationsInvalid() {
        //pass wrong message type to trigger Exception
        final UMessage message = buildCreateTopicMessage(TestBase.RESOURCE_URI, TestBase.LOCAL_CLIENT_URI);
        assertEquals(UCode.INVALID_ARGUMENT, mSubscriptionHandler.unregisterForNotifications(message).getCode());
    }

    @Test
    public void testIsTopicSubscribed() {
        when(mDbHelper.getSubscriptionState(any())).thenReturn(State.SUBSCRIBE_PENDING.getNumber());
        assertTrue(mSubscriptionHandler.isTopicSubscribed(RESOURCE_URI));
        verify(mDbHelper, times(1)).getSubscriptionState(any());
    }

    @Test
    public void testIsTopicSubscribedEmpty() {
        assertFalse(mSubscriptionHandler.isTopicSubscribed(RESOURCE_URI));
    }

    @Test
    public void testIsTopicSubscribedExceptionally() {
        when(mDbHelper.getSubscriptionState(any())).thenReturn(-1);
        when(mSubscriptionHandler.isTopicSubscribed(any())).thenThrow(IllegalStateException.class);
        assertFalse(mSubscriptionHandler.isTopicSubscribed(RESOURCE_URI));
        verify(mDbHelper, times(1)).getSubscriptionState(any());
    }

    @Test
    public void testGetSubscriptionState() {
        when(mDbHelper.getSubscriptionState(any())).thenReturn(State.SUBSCRIBED.getNumber());
        assertEquals(State.SUBSCRIBED, mSubscriptionHandler.getSubscriptionState(RESOURCE_URI));
        verify(mDbHelper, times(1)).getSubscriptionState(any());
    }

    @Test
    public void testGetSubscriptionStateExceptionally() {
        when(mDbHelper.getSubscriptionState(any())).thenThrow(NullPointerException.class);
        assertEquals(State.UNSUBSCRIBED, mSubscriptionHandler.getSubscriptionState(RESOURCE_URI));
    }

    @Test
    public void testGetPublisher() {
        when(mDbHelper.getPublisher(any())).thenReturn(LOCAL_SERVER_URI);
        assertEquals(TestBase.LOCAL_SERVER_URI, mSubscriptionHandler.getPublisher(TestBase.RESOURCE_URI));
        verify(mDbHelper, times(1)).getPublisher(any());
    }

    @Test
    public void testGetPublisherExceptionally() {
        when(mDbHelper.getPublisher(any())).thenThrow(NullPointerException.class);
        assertEquals("", toUriString(mSubscriptionHandler.getPublisher(any())));
        verify(mDbHelper, times(1)).getPublisher(any());
    }
}
