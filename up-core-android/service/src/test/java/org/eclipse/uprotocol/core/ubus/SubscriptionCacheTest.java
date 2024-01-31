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

import static org.eclipse.uprotocol.core.internal.util.UUriUtils.getClientUri;
import static org.eclipse.uprotocol.uri.validator.UriValidator.isEmpty;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import static java.util.Collections.emptySet;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.eclipse.uprotocol.core.TestBase;
import org.eclipse.uprotocol.core.usubscription.USubscription;
import org.eclipse.uprotocol.v1.UUri;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Set;

@RunWith(AndroidJUnit4.class)
public class SubscriptionCacheTest extends TestBase {
    private final USubscription mUSubscription = mock(USubscription.class);
    private SubscriptionCache mSubscriptionCache;

    @Before
    public void setUp() {
        when(mUSubscription.getPublisher(any())).thenReturn(EMPTY_URI);
        when(mUSubscription.getSubscribers(any())).thenReturn(emptySet());
        mSubscriptionCache = new SubscriptionCache();
        mSubscriptionCache.setService(mUSubscription);
    }

    private void injectTopic(@NonNull UUri topic) {
        when(mUSubscription.getPublisher(topic)).thenReturn(getClientUri(topic));
    }

    private void injectSubscriptions(@NonNull UUri topic, @NonNull Set<UUri> subscribers) {
        when(mUSubscription.getSubscribers(topic)).thenReturn(subscribers);
    }

    private void clear() {
        mSubscriptionCache.clear();
        assertTrue(mSubscriptionCache.isEmpty());
    }

    @Test
    public void testGetSubscribers() {
        final Set<UUri> subscribers = Set.of(CLIENT_URI, CLIENT2_URI);
        injectSubscriptions(RESOURCE_URI, subscribers);
        assertEquals(subscribers, mSubscriptionCache.getSubscribers(RESOURCE_URI));
    }

    @Test
    public void testGetSubscribersNotAvailable() {
        final Set<UUri> subscribers = Set.of(CLIENT_URI, CLIENT2_URI);
        injectSubscriptions(RESOURCE2_URI, subscribers);
        assertTrue(mSubscriptionCache.getSubscribers(RESOURCE_URI).isEmpty());
    }

    @Test
    public void testGetSubscribersNoService() {
        mSubscriptionCache.setService(null);
        assertTrue(mSubscriptionCache.getSubscribers(RESOURCE_URI).isEmpty());
    }

    @Test
    public void testAddSubscriber() {
        assertTrue(mSubscriptionCache.addSubscriber(RESOURCE_URI, CLIENT_URI));
        assertTrue(mSubscriptionCache.getSubscribers(RESOURCE_URI).contains(CLIENT_URI));
    }

    @Test
    public void testAddSubscriberSame() {
        testAddSubscriber();
        assertFalse(mSubscriptionCache.addSubscriber(RESOURCE_URI, CLIENT_URI));
        assertTrue(mSubscriptionCache.getSubscribers(RESOURCE_URI).contains(CLIENT_URI));
    }

    @Test
    public void testRemoveSubscriber() {
        assertFalse(mSubscriptionCache.removeSubscriber(RESOURCE_URI, CLIENT_URI));
        assertFalse(mSubscriptionCache.getSubscribers(RESOURCE_URI).contains(CLIENT_URI));
    }

    @Test
    public void testRemoveSubscriberNotEmpty() {
        testAddSubscriber();
        assertTrue(mSubscriptionCache.removeSubscriber(RESOURCE_URI, CLIENT_URI));
        assertFalse(mSubscriptionCache.getSubscribers(RESOURCE_URI).contains(CLIENT_URI));
    }

    @Test
    public void testRemoveSubscriberAlreadyRemoved() {
        testRemoveSubscriberNotEmpty();
        assertFalse(mSubscriptionCache.removeSubscriber(RESOURCE_URI, CLIENT_URI));
        assertFalse(mSubscriptionCache.getSubscribers(RESOURCE_URI).contains(CLIENT_URI));
    }

    @Test
    public void testIsTopicSubscribed() {
        assertTrue(mSubscriptionCache.addSubscriber(RESOURCE_URI, CLIENT_URI));
        assertTrue(mSubscriptionCache.isTopicSubscribed(RESOURCE_URI, CLIENT_URI));

        assertTrue(mSubscriptionCache.removeSubscriber(RESOURCE_URI, CLIENT_URI));
        assertFalse(mSubscriptionCache.isTopicSubscribed(RESOURCE_URI, CLIENT_URI));
    }

    @Test
    public void testGetSubscribedTopics() {
        assertTrue(mSubscriptionCache.addSubscriber(RESOURCE_URI, CLIENT_URI));
        assertTrue(mSubscriptionCache.getSubscribedTopics().contains(RESOURCE_URI));

        assertTrue(mSubscriptionCache.removeSubscriber(RESOURCE_URI, CLIENT_URI));
        assertFalse(mSubscriptionCache.getSubscribedTopics().contains(RESOURCE_URI));
    }

    @Test
    public void testGetPublisher() {
        injectTopic(RESOURCE_URI);
        assertEquals(SERVER_URI, mSubscriptionCache.getPublisher(RESOURCE_URI));
    }

    @Test
    public void testGetPublisherNotAvailable() {
        injectTopic(RESOURCE2_URI);
        assertTrue(isEmpty(mSubscriptionCache.getPublisher(RESOURCE_URI)));
    }

    @Test
    public void testGetPublisherNoService() {
        mSubscriptionCache.setService(null);
        assertTrue(isEmpty(mSubscriptionCache.getPublisher(RESOURCE_URI)));
    }

    @Test
    public void testAddTopic() {
        assertTrue(mSubscriptionCache.addTopic(RESOURCE_URI, SERVER_URI));
        assertEquals(SERVER_URI, mSubscriptionCache.getPublisher(RESOURCE_URI));
    }

    @Test
    public void testAddTopicSame() {
        testAddTopic();
        assertFalse(mSubscriptionCache.addTopic(RESOURCE_URI, SERVER_URI));
        assertEquals(SERVER_URI, mSubscriptionCache.getPublisher(RESOURCE_URI));
    }

    @Test
    public void testRemoveTopic() {
        assertFalse(mSubscriptionCache.removeTopic(RESOURCE_URI));
    }

    @Test
    public void testRemoveTopicNotEmpty() {
        testAddTopic();
        assertEquals(SERVER_URI, mSubscriptionCache.getPublisher(RESOURCE_URI));
        assertTrue(mSubscriptionCache.addSubscriber(RESOURCE_URI, CLIENT_URI));

        assertTrue(mSubscriptionCache.removeTopic(RESOURCE_URI));
        assertTrue(isEmpty(mSubscriptionCache.getPublisher(RESOURCE_URI)));
        assertFalse(mSubscriptionCache.getSubscribedTopics().contains(RESOURCE_URI));
    }

    @Test
    public void testRemoveTopicAlreadyRemoved() {
        testRemoveTopicNotEmpty();
        assertFalse(mSubscriptionCache.removeTopic(RESOURCE_URI));
        assertTrue(isEmpty(mSubscriptionCache.getPublisher(RESOURCE_URI)));
        assertFalse(mSubscriptionCache.getSubscribedTopics().contains(RESOURCE_URI));
    }

    @Test
    public void testIsTopicCreated() {
        assertTrue(mSubscriptionCache.addTopic(RESOURCE_URI, SERVER_URI));
        assertTrue(mSubscriptionCache.isTopicCreated(RESOURCE_URI, SERVER_URI));

        assertTrue(mSubscriptionCache.removeTopic(RESOURCE_URI));
        assertFalse(mSubscriptionCache.isTopicCreated(RESOURCE_URI, SERVER_URI));
    }

    @Test
    public void testGetCreatedTopics() {
        assertTrue(mSubscriptionCache.addTopic(RESOURCE_URI, SERVER_URI));
        assertTrue(mSubscriptionCache.addTopic(RESOURCE2_URI, SERVER_URI));
        assertEquals(Set.of(RESOURCE_URI, RESOURCE2_URI), mSubscriptionCache.getCreatedTopics());

        assertTrue(mSubscriptionCache.removeTopic(RESOURCE_URI));
        assertEquals(Set.of(RESOURCE2_URI), mSubscriptionCache.getCreatedTopics());

        assertTrue(mSubscriptionCache.removeTopic(RESOURCE2_URI));
        assertTrue(mSubscriptionCache.getCreatedTopics().isEmpty());
    }

    @Test
    public void testIsEmpty() {
        assertTrue(mSubscriptionCache.isEmpty());
        testAddTopic();
        assertFalse(mSubscriptionCache.isEmpty());
    }

    @Test
    public void testClear() {
        clear();

        assertTrue(mSubscriptionCache.addSubscriber(RESOURCE_URI, CLIENT_URI));
        assertTrue(mSubscriptionCache.addTopic(RESOURCE_URI, SERVER_URI));
        assertFalse(mSubscriptionCache.isEmpty());
        clear();

        assertTrue(mSubscriptionCache.addSubscriber(RESOURCE_URI, CLIENT_URI));
        assertFalse(mSubscriptionCache.isEmpty());
        clear();

        assertTrue(mSubscriptionCache.addTopic(RESOURCE_URI, SERVER_URI));
        assertFalse(mSubscriptionCache.isEmpty());
        clear();
    }
}
