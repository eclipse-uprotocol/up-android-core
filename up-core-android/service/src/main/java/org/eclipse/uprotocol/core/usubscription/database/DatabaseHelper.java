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

import static org.eclipse.uprotocol.core.internal.util.CommonUtils.emptyIfNull;

import android.content.Context;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import java.util.List;

public class DatabaseHelper {
    private final Object mDBLock = new Object();
    @GuardedBy("mDBLock")
    @VisibleForTesting
    SubscriptionDatabase mDatabase;

    public void init(Context context) {
        mDatabase = SubscriptionDatabaseKt.createDbExtension(context);
    }

    public void init(Context ignoredContext, SubscriptionDatabase database) {
        mDatabase = database;
    }

    public boolean isTopicCreated(String topic) {
        synchronized (mDBLock) {
            return topicsDao().isTopicCreated(topic);
        }
    }

    public long addTopic(TopicsRecord topicsRecord) {
        synchronized (mDBLock) {
            return topicsDao().addTopic(topicsRecord);
        }
    }

    public String getPublisher(String topic) {
        synchronized (mDBLock) {
            return topicsDao().getPublisher(topic);
        }
    }

    public void updateTopic(String topic, boolean isRegister) {
        synchronized (mDBLock) {
            topicsDao().updateTopicTable(topic, isRegister);
        }
    }

    public String getPublisherIfRegistered(String topic) {
        synchronized (mDBLock) {
            return topicsDao().getPublisherIfRegistered(topic);
        }
    }

    public boolean isRegisteredForNotification(String topic) {
        synchronized (mDBLock) {
            return topicsDao().isRegisteredForNotification(topic);
        }
    }

    public long addSubscription(SubscriptionsRecord subscriptionsRecord) {
        synchronized (mDBLock) {
            return subscriptionDao().addSubscription(subscriptionsRecord);
        }
    }

    public void deleteTopicFromSubscriptions(String topic) {
        synchronized (mDBLock) {
            subscriptionDao().deleteTopic(topic);
        }
    }

    public @NonNull List<String> getSubscribedTopics() {
        synchronized (mDBLock) {
            return emptyIfNull(subscriptionDao().getSubscribedTopics());
        }
    }

    public @NonNull List<SubscriptionsRecord> getPendingTopics() {
        synchronized (mDBLock) {
            return emptyIfNull(subscriptionDao().getPendingTopics());
        }
    }

    public void updateState(String topic, int state) {
        synchronized (mDBLock) {
            subscriptionDao().updateState(topic, state);
        }
    }

    public int getSubscriptionState(String topicName) {
        synchronized (mDBLock) {
            return subscriptionDao().getSubscriptionState(topicName);
        }
    }

    public long addSubscriber(SubscribersRecord subscribersRecord) {
        synchronized (mDBLock) {
            return subscribersDao().addSubscriber(subscribersRecord);
        }
    }

    public void deleteTopicFromSubscribers(String topic) {
        synchronized (mDBLock) {
            subscribersDao().deleteTopic(topic);
        }
    }

    public void deleteSubscriber(String topic, String subscriber) {
        synchronized (mDBLock) {
            subscribersDao().deleteSubscriber(topic, subscriber);
        }
    }

    public SubscribersRecord getSubscriber(String topic, String subscriber) {
        synchronized (mDBLock) {
            return subscribersDao().getSubscriber(topic, subscriber);
        }
    }

    public SubscribersRecord getFirstSubscriberForTopic(String topic) {
        synchronized (mDBLock) {
            return subscribersDao().getFirstSubscriberForTopic(topic);
        }
    }

    public @NonNull List<String> getSubscribers(String topic) {
        synchronized (mDBLock) {
            return emptyIfNull(subscribersDao().getSubscribers(topic));
        }
    }

    public @NonNull List<SubscribersRecord> getAllSubscriberRecords() {
        synchronized (mDBLock) {
            return emptyIfNull(subscribersDao().getAllSubscriberRecords());
        }
    }

    public @NonNull List<SubscribersRecord> fetchSubscriptionsByTopic(String topicUri) {
        synchronized (mDBLock) {
            return emptyIfNull(subscribersDao().getSubscriptionsByTopic(topicUri));
        }
    }

    public @NonNull List<SubscribersRecord> fetchSubscriptionsBySubscriber(String subscriberInfo) {
        synchronized (mDBLock) {
            return emptyIfNull(subscribersDao().getSubscriptionsBySubscriber(subscriberInfo));
        }
    }

    public TopicsDao topicsDao() {
        return mDatabase.topicsDao();
    }

    public SubscribersDao subscribersDao() {
        return mDatabase.subscribersDao();
    }

    public SubscriptionDao subscriptionDao() {
        return mDatabase.subscriptionDao();
    }

    public boolean shutdown() {
        if (mDatabase.isOpen()) {
            mDatabase.close();
            return true;
        }
        return false;
    }
}
