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

import static org.eclipse.uprotocol.core.internal.util.UUriUtils.toUriString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.eclipse.uprotocol.core.TestBase;
import org.eclipse.uprotocol.core.usubscription.database.DatabaseHelper;
import org.eclipse.uprotocol.core.usubscription.database.SubscribersRecord;
import org.eclipse.uprotocol.core.usubscription.v3.SubscriberInfo;
import org.eclipse.uprotocol.core.usubscription.v3.Subscription;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class CacheHandlerTest extends SubscriptionTestBase {
    private DatabaseHelper mMockDBHelper;
    private CacheHandler mCacheHandler;

    @Before
    public void setUp() {
        mMockDBHelper = mock(DatabaseHelper.class);
        mCacheHandler = new CacheHandler(mMockDBHelper);
    }

    @Test
    public void testFetchSubscriptionsByTopic() {
        List<SubscribersRecord> subscribersRecords = new ArrayList<>();
        subscribersRecords.add(newSubscribersRecord(RESOURCE_URI, LOCAL_CLIENT_URI, ""));
        when(mMockDBHelper.fetchSubscriptionsByTopic(any())).thenReturn(subscribersRecords);
        final List<Subscription> subscriptions = mCacheHandler.fetchSubscriptionsByTopic(TestBase.RESOURCE_URI);
        assertEquals(subscribersRecords.get(0).getTopicUri(),
                toUriString(subscriptions.get(0).getTopic()));
        assertEquals(subscribersRecords.get(0).getSubscriberUri(),
                toUriString(subscriptions.get(0).getSubscriber().getUri()));
        verify(mMockDBHelper, times(1)).fetchSubscriptionsByTopic(anyString());
    }

    @Test
    public void testFetchSubscriptionsByTopicNotFound() {
        when(mMockDBHelper.fetchSubscriptionsByTopic(any())).thenReturn(Collections.emptyList());
        assertTrue(mCacheHandler.fetchSubscriptionsByTopic(TestBase.RESOURCE_URI).isEmpty());
        verify(mMockDBHelper, times(1)).fetchSubscriptionsByTopic(anyString());
    }

    @Test
    public void testFetchSubscriptionsBySubscriber() {
        List<SubscribersRecord> subscribersRecords = new ArrayList<>();
        subscribersRecords.add(newSubscribersRecord(RESOURCE_URI, LOCAL_CLIENT_URI, ""));
        when(mMockDBHelper.fetchSubscriptionsBySubscriber(any())).thenReturn(subscribersRecords);
        SubscriberInfo subscriber = buildSubscriber(TestBase.LOCAL_CLIENT_URI);
        final List<Subscription> subscriptions = mCacheHandler.fetchSubscriptionsBySubscriber(subscriber);
        assertEquals(subscribersRecords.get(0).getTopicUri(),
                toUriString(subscriptions.get(0).getTopic()));
        assertEquals(subscribersRecords.get(0).getSubscriberUri(),
                toUriString(subscriptions.get(0).getSubscriber().getUri()));
        verify(mMockDBHelper, times(1)).fetchSubscriptionsBySubscriber(anyString());
    }

    @Test
    public void testFetchSubscriptionsBySubscriberNotFound() {
        when(mMockDBHelper.fetchSubscriptionsBySubscriber(any())).thenReturn(Collections.emptyList());
        assertTrue(mCacheHandler.fetchSubscriptionsBySubscriber(buildSubscriber(TestBase.LOCAL_CLIENT_URI)).isEmpty());
        verify(mMockDBHelper, times(1)).fetchSubscriptionsBySubscriber(anyString());
    }

    @After
    public void testInvalidateCache() {
        mCacheHandler.invalidateCache();
    }
}
