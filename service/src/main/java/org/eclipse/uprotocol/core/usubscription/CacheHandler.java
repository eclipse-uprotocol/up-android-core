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

import static org.eclipse.uprotocol.core.internal.util.CommonUtils.emptyIfNull;
import static org.eclipse.uprotocol.core.usubscription.SubscriptionUtils.buildSubscriber;

import static java.util.Collections.emptyList;

import androidx.annotation.NonNull;

import org.eclipse.uprotocol.core.usubscription.database.DatabaseHelper;
import org.eclipse.uprotocol.core.usubscription.database.SubscribersRecord;
import org.eclipse.uprotocol.core.usubscription.v3.SubscriberInfo;
import org.eclipse.uprotocol.core.usubscription.v3.Subscription;
import org.eclipse.uprotocol.uri.serializer.LongUriSerializer;
import org.eclipse.uprotocol.v1.UUri;

import java.util.ArrayList;
import java.util.List;

public class CacheHandler {
    //TODO : Build caching logic in this class
    private final DatabaseHelper mDatabaseHelper;
    private final LongUriSerializer serializer = LongUriSerializer.instance();

    public CacheHandler(@NonNull DatabaseHelper helper) {
        mDatabaseHelper = helper;
    }

    public void invalidateCache() {
        //TODO: Clear cache data.
    }

    //TODO: Add Caching logic. Currently fetching from DB
    public @NonNull List<Subscription> fetchSubscriptionsByTopic(@NonNull UUri topic) {
        return fetchSubscriptionsByTopic(serializer.serialize(topic));
    }

    public @NonNull List<Subscription> fetchSubscriptionsByTopic(@NonNull String topic) {
        return buildSubscriptions(mDatabaseHelper.fetchSubscriptionsByTopic(topic));
    }

    //TODO: Add Caching logic. Currently fetching from DB
    public @NonNull List<Subscription> fetchSubscriptionsBySubscriber(@NonNull SubscriberInfo subscriber) {
        return buildSubscriptions(mDatabaseHelper.fetchSubscriptionsBySubscriber(serializer.serialize(subscriber.getUri())));
    }

    private @NonNull List<Subscription> buildSubscriptions(@NonNull List<SubscribersRecord> records) {
        if (records.isEmpty()) {
            return emptyList();
        }
        final List<Subscription> subscriptions = new ArrayList<>();
        for (SubscribersRecord subscribersRecord : records) {
            final String topicUri = emptyIfNull(subscribersRecord.getTopicUri());
            final String subscriberUri = emptyIfNull(subscribersRecord.getSubscriberUri());
            final String subscriberDetails = emptyIfNull(subscribersRecord.getSubscriberDetails());
            subscriptions.add(Subscription.newBuilder()
                    .setTopic(serializer.deserialize(topicUri))
                    .setSubscriber(buildSubscriber(subscriberUri, subscriberDetails))
                    .build());
        }
        return subscriptions;
    }
}
