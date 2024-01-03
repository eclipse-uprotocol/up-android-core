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

package org.eclipse.uprotocol.core.usubscription.database;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import android.content.Context;

import androidx.room.Room;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.eclipse.uprotocol.core.usubscription.SubscriptionTestBase;
import org.eclipse.uprotocol.core.usubscription.v3.SubscriptionStatus;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@RunWith(AndroidJUnit4.class)
public class DatabaseHelperTest extends SubscriptionTestBase {
    private DatabaseHelper mDbHelper;
    private SubscribersDao mSubscribersDao;
    private SubscriptionDao mSubscriptionDao;
    private TopicsDao mTopicsDao;
    private Context mContext;

    @Before
    public void setUp() {
        mContext = spy(Context.class);
        mDbHelper = new DatabaseHelper();
        final SubscriptionDatabase mockDB = Room.inMemoryDatabaseBuilder(mContext,
                SubscriptionDatabase.class).allowMainThreadQueries().build();
        when(SubscriptionDatabaseKt.createDbExtension(mContext)).thenReturn(mockDB);
        mDbHelper.init(mContext, mockDB);
        mSubscribersDao = mockDB.subscribersDao();
        mSubscriptionDao = mockDB.subscriptionDao();
        mTopicsDao = mockDB.topicsDao();
    }

    @Test
    public void testInit() {
        mDbHelper.init(mContext);
        assertNotNull(mDbHelper.mDatabase);
    }

    @Test
    public void testIsTopicCreatedTrue() {
        mTopicsDao.addTopic(newTopicsRecord(RESOURCE_URI, TOPIC_PUBLISHER_DETAILS, false));
        assertTrue(mDbHelper.isTopicCreated(RESOURCE_URI));
    }

    @Test
    public void testIsTopicCreatedFalse() {
        assertFalse(mDbHelper.isTopicCreated(RESOURCE_URI));
    }

    @Test
    public void testAddTopic() {
        mDbHelper.addTopic(newTopicsRecord(RESOURCE_URI, TOPIC_PUBLISHER_DETAILS, false));
        assertTrue(mTopicsDao.isTopicCreated(RESOURCE_URI));
    }

    @Test
    public void testUpdateTopic() {
        mDbHelper.addTopic(newTopicsRecord(RESOURCE_URI, TOPIC_PUBLISHER_DETAILS, false));
        assertFalse(mTopicsDao.isRegisteredForNotification(RESOURCE_URI));
        mDbHelper.updateTopic(RESOURCE_URI, true);
        assertTrue(mTopicsDao.isRegisteredForNotification(RESOURCE_URI));
    }

    @Test
    public void testGetPublisher() {
        mDbHelper.addTopic(newTopicsRecord(RESOURCE_URI, TOPIC_PUBLISHER_DETAILS, false));
        assertEquals(TOPIC_PUBLISHER_DETAILS, mDbHelper.getPublisher(RESOURCE_URI));
    }

    @Test
    public void testGetPublisherIfRegistered() {
        mDbHelper.addTopic(newTopicsRecord(RESOURCE_URI, TOPIC_PUBLISHER_DETAILS, true));
        assertEquals(TOPIC_PUBLISHER_DETAILS, mDbHelper.getPublisherIfRegistered(RESOURCE_URI));
    }

    @Test
    public void testGetPublisherIfRegisteredFalse() {
        mDbHelper.addTopic(newTopicsRecord(RESOURCE_URI, TOPIC_PUBLISHER_DETAILS, false));
        assertNull(mDbHelper.getPublisherIfRegistered(RESOURCE_URI));
    }

    @Test
    public void testIsRegisteredForNotification() {
        mDbHelper.addTopic(newTopicsRecord(RESOURCE_URI, TOPIC_PUBLISHER_DETAILS, true));
        assertTrue(mDbHelper.isRegisteredForNotification(RESOURCE_URI));
    }

    @Test
    public void testIsRegisteredForNotificationFalse() {
        mDbHelper.addTopic(newTopicsRecord(RESOURCE_URI, TOPIC_PUBLISHER_DETAILS, false));
        assertFalse(mDbHelper.isRegisteredForNotification(RESOURCE_URI));
    }

    @Test
    public void testAddSubscription() {
        mDbHelper.addSubscription(newSubscriptionsRecord(RESOURCE_URI, REQUEST_ID));
        assertEquals(RESOURCE_URI, mSubscriptionDao.getTopic(REQUEST_ID));
    }

    @Test
    @SuppressWarnings("DataFlowIssue")
    public void testDeleteTopicFromSubscriptions() {
        mDbHelper.addSubscription(newSubscriptionsRecord(RESOURCE_URI, REQUEST_ID));
        assertNotNull(mSubscriptionDao.getTopic(REQUEST_ID));
        mDbHelper.deleteTopicFromSubscriptions(RESOURCE_URI);
        assertNull(mSubscriptionDao.getTopic(REQUEST_ID));
    }

    @Test
    public void testGetSubscribedTopics() {
        mSubscriptionDao.addSubscription(newSubscriptionsRecord(RESOURCE_URI, REQUEST_ID));
        assertEquals(mSubscriptionDao.getSubscribedTopics(), mDbHelper.getSubscribedTopics());
    }

    @Test
    public void testGetPendingTopics() {
        mSubscriptionDao.addSubscription(
                newSubscriptionsRecord(RESOURCE_URI, REQUEST_ID, SubscriptionStatus.State.SUBSCRIBE_PENDING));
        assertFalse(mDbHelper.getPendingTopics().isEmpty());
    }

    @Test
    public void testUpdateState() {
        mSubscriptionDao.addSubscription(newSubscriptionsRecord(RESOURCE_URI, REQUEST_ID));
        assertEquals(SubscriptionStatus.State.SUBSCRIBED.getNumber(),
                mDbHelper.getSubscriptionState(RESOURCE_URI));
        mDbHelper.updateState(RESOURCE_URI, SubscriptionStatus.State.UNSUBSCRIBED.getNumber());
        assertEquals(SubscriptionStatus.State.UNSUBSCRIBED.getNumber(),
                mDbHelper.getSubscriptionState(RESOURCE_URI));
        mDbHelper.updateState(RESOURCE_URI, SubscriptionStatus.State.SUBSCRIBE_PENDING.getNumber());
        assertEquals(SubscriptionStatus.State.SUBSCRIBE_PENDING.getNumber(),
                mDbHelper.getSubscriptionState(RESOURCE_URI));
    }

    @Test
    public void testGetSubscriptionState() {
        mDbHelper.addSubscription(newSubscriptionsRecord(RESOURCE_URI, REQUEST_ID));
        assertEquals(SubscriptionStatus.State.SUBSCRIBED.getNumber(),
                mDbHelper.getSubscriptionState(RESOURCE_URI));
    }

    @Test
    public void testAddSubscriber() {
        mDbHelper.addSubscriber(newSubscribersRecord(RESOURCE_URI, LOCAL_CLIENT_URI, SUBSCRIBERS_DETAILS));
        assertEquals(LOCAL_CLIENT_URI, mDbHelper.getFirstSubscriberForTopic(RESOURCE_URI).getSubscriberUri());
    }

    @Test
    public void testDeleteTopicFromSubscribers() {
        mDbHelper.addSubscriber(newSubscribersRecord(RESOURCE_URI, LOCAL_CLIENT_URI, SUBSCRIBERS_DETAILS));
        assertFalse(mSubscribersDao.getSubscribers(RESOURCE_URI).isEmpty());
        mDbHelper.deleteTopicFromSubscribers(RESOURCE_URI);
        assertTrue(mSubscribersDao.getSubscribers(RESOURCE_URI).isEmpty());
    }

    @Test
    public void testDeleteSubscriber() {
        mDbHelper.addSubscriber(newSubscribersRecord(RESOURCE_URI, LOCAL_CLIENT_URI, SUBSCRIBERS_DETAILS));
        mDbHelper.addSubscriber(newSubscribersRecord(RESOURCE_URI, LOCAL_CLIENT2_URI, SUBSCRIBERS_DETAILS));
        assertEquals(List.of(LOCAL_CLIENT_URI, LOCAL_CLIENT2_URI), mSubscribersDao.getSubscribers(RESOURCE_URI));
        mDbHelper.deleteSubscriber(RESOURCE_URI, LOCAL_CLIENT_URI);
        assertFalse(mSubscribersDao.getSubscribers(RESOURCE_URI).contains(LOCAL_CLIENT_URI));
    }

    @Test
    public void testGetSubscriber() {
        final SubscribersRecord record = newSubscribersRecord(RESOURCE_URI, LOCAL_CLIENT_URI, SUBSCRIBERS_DETAILS);
        mDbHelper.addSubscriber(record);
        assertEquals(record.getTopicUri(), mDbHelper.getSubscriber(RESOURCE_URI, LOCAL_CLIENT_URI).getTopicUri());
    }

    @Test
    public void testGetFirstSubscriberForTopic() {
        mDbHelper.addSubscriber(newSubscribersRecord(RESOURCE_URI, LOCAL_CLIENT_URI, SUBSCRIBERS_DETAILS));
        mDbHelper.addSubscriber(newSubscribersRecord(RESOURCE_URI, LOCAL_CLIENT2_URI, SUBSCRIBERS_DETAILS));
        assertEquals(LOCAL_CLIENT_URI, mDbHelper.getFirstSubscriberForTopic(RESOURCE_URI).getSubscriberUri());
    }

    @Test
    public void testGetSubscribersEmpty() {
        assertTrue(mDbHelper.getSubscribers(RESOURCE_URI).isEmpty());
    }

    @Test
    public void testGetSubscribers() {
        mSubscribersDao.addSubscriber(newSubscribersRecord(RESOURCE_URI, LOCAL_CLIENT_URI, SUBSCRIBERS_DETAILS));
        assertEquals(List.of(LOCAL_CLIENT_URI), mDbHelper.getSubscribers(RESOURCE_URI));
    }

    @Test
    public void testGetAllSubscriberRecords() {
        mSubscribersDao.addSubscriber(newSubscribersRecord(RESOURCE_URI, LOCAL_CLIENT_URI, SUBSCRIBERS_DETAILS));
        mSubscribersDao.addSubscriber(newSubscribersRecord(REMOTE_RESOURCE_URI, LOCAL_CLIENT_URI, SUBSCRIBERS_DETAILS));
        mSubscribersDao.addSubscriber(newSubscribersRecord(RESOURCE_URI, LOCAL_CLIENT2_URI, SUBSCRIBERS_DETAILS));
        mSubscribersDao.addSubscriber(newSubscribersRecord(RESOURCE_URI, REMOTE_CLIENT_URI, SUBSCRIBERS_DETAILS));
        mSubscribersDao.addSubscriber(newSubscribersRecord(REMOTE_RESOURCE_URI, REMOTE_CLIENT_URI,
                SUBSCRIBERS_DETAILS));
        assertEquals(5, mDbHelper.getAllSubscriberRecords().size());
    }

    @Test
    public void testFetchSubscriptionsByTopic() {
        mSubscribersDao.addSubscriber(newSubscribersRecord(RESOURCE_URI, LOCAL_CLIENT_URI, SUBSCRIBERS_DETAILS));
        assertEquals(RESOURCE_URI, mDbHelper.fetchSubscriptionsByTopic(RESOURCE_URI).get(0).getTopicUri());
        assertEquals(SUBSCRIBERS_DETAILS, mDbHelper.fetchSubscriptionsByTopic(RESOURCE_URI).get(
                0).getSubscriberDetails());
    }

    @Test
    public void testFetchSubscriptionsBySubscriber() {
        mSubscribersDao.addSubscriber(newSubscribersRecord(RESOURCE_URI, LOCAL_CLIENT_URI, SUBSCRIBERS_DETAILS));
        mSubscribersDao.addSubscriber(newSubscribersRecord(RESOURCE_URI, LOCAL_CLIENT2_URI, SUBSCRIBERS_DETAILS));
        assertEquals(LOCAL_CLIENT_URI,
                mDbHelper.fetchSubscriptionsBySubscriber(LOCAL_CLIENT_URI).get(0).getSubscriberUri());
        assertEquals(LOCAL_CLIENT2_URI,
                mDbHelper.fetchSubscriptionsBySubscriber(LOCAL_CLIENT2_URI).get(0).getSubscriberUri());
    }

    @Test
    public void topicsDaoValid() {
        assertNotNull(mDbHelper.topicsDao());
    }

    @Test
    public void subscribersDaoValid() {
        assertNotNull(mDbHelper.subscribersDao());
    }

    @Test
    public void subscriptionDaoValid() {
        assertNotNull(mDbHelper.subscriptionDao());
    }

    @After
    public void tearDown() {
        mDbHelper.shutdown();
    }
}
