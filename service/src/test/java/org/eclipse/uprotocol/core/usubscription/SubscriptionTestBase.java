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
import static org.eclipse.uprotocol.transport.builder.UPayloadBuilder.packToAny;

import androidx.annotation.NonNull;

import org.eclipse.uprotocol.core.TestBase;
import org.eclipse.uprotocol.core.usubscription.database.SubscribersRecord;
import org.eclipse.uprotocol.core.usubscription.database.SubscriptionsRecord;
import org.eclipse.uprotocol.core.usubscription.database.TopicsRecord;
import org.eclipse.uprotocol.core.usubscription.v3.CreateTopicRequest;
import org.eclipse.uprotocol.core.usubscription.v3.DeprecateTopicRequest;
import org.eclipse.uprotocol.core.usubscription.v3.FetchSubscribersRequest;
import org.eclipse.uprotocol.core.usubscription.v3.FetchSubscriptionsRequest;
import org.eclipse.uprotocol.core.usubscription.v3.NotificationsRequest;
import org.eclipse.uprotocol.core.usubscription.v3.SubscribeAttributes;
import org.eclipse.uprotocol.core.usubscription.v3.Subscription;
import org.eclipse.uprotocol.core.usubscription.v3.SubscriptionRequest;
import org.eclipse.uprotocol.core.usubscription.v3.SubscriptionStatus;
import org.eclipse.uprotocol.core.usubscription.v3.SubscriptionStatus.State;
import org.eclipse.uprotocol.core.usubscription.v3.UnsubscribeRequest;
import org.eclipse.uprotocol.core.usubscription.v3.Update;
import org.eclipse.uprotocol.v1.UCode;
import org.eclipse.uprotocol.v1.UMessage;
import org.eclipse.uprotocol.v1.UUri;

import java.util.UUID;

@SuppressWarnings({"SameParameterValue", "S2925", "unused"})
public class SubscriptionTestBase extends TestBase {
    protected static final String RESOURCE_URI = toUriString(TestBase.RESOURCE_URI);
    protected static final String REMOTE_RESOURCE_URI = toUriString(TestBase.REMOTE_RESOURCE_URI);
    protected static final String LOCAL_CLIENT_URI = toUriString(TestBase.LOCAL_CLIENT_URI);
    protected static final String LOCAL_CLIENT2_URI = toUriString(TestBase.LOCAL_CLIENT2_URI);
    protected static final String REMOTE_CLIENT_URI = toUriString(TestBase.REMOTE_CLIENT_URI);
    protected static final String LOCAL_SERVER_URI = toUriString(TestBase.LOCAL_SERVER_URI);
    protected static final String REMOTE_SERVER_URI = toUriString(TestBase.REMOTE_SERVER_URI);

    protected static final String TOPIC_DETAILS = "topicDetails";
    protected static final String TOPIC_PUBLISHER_DETAILS = "topicPublisherDetails";
    protected static final String SUBSCRIBERS_DETAILS = "subscribersDetails";
    protected static final String REQUEST_ID = UUID.randomUUID().toString();
    protected static final String SUBSCRIPTION_EXPIRY_TIME = "5000000000";

    protected static @NonNull SubscriptionsRecord newSubscriptionsRecord(@NonNull String resourceUri,
            @NonNull String requestId) {
        return new SubscriptionsRecord(resourceUri, requestId, State.SUBSCRIBED.getNumber());
    }

    protected static @NonNull SubscriptionsRecord newSubscriptionsRecord(@NonNull String resourceUri,
            @NonNull String requestId, @NonNull State state) {
        return new SubscriptionsRecord(resourceUri, requestId, state.getNumber());
    }

    protected static @NonNull TopicsRecord newTopicsRecord(@NonNull String topic, @NonNull String publisherDetails,
            boolean isRegistered) {
        return new TopicsRecord(topic, publisherDetails, TOPIC_DETAILS, isRegistered);
    }

    protected static @NonNull SubscribersRecord newSubscribersRecord(@NonNull String resourceUri,
            @NonNull String clientUri, @NonNull String subscribeDetails) {
        return new SubscribersRecord(resourceUri, clientUri, subscribeDetails, SUBSCRIPTION_EXPIRY_TIME, REQUEST_ID);
    }

    public static @NonNull Subscription buildSubscription() {
        return Subscription.newBuilder()
                .setTopic(TestBase.RESOURCE_URI)
                .setSubscriber(buildSubscriber(TestBase.LOCAL_CLIENT_URI))
                .setStatus(buildSubscriptionStatus(State.SUBSCRIBED, UCode.OK))
                .build();
    }

    protected static @NonNull UMessage buildCreateTopicMessage(@NonNull UUri topicUri, @NonNull UUri clientUri) {
        return buildRequestMessage(buildResponseUri(clientUri), USubscription.Method.CREATE_TOPIC.localUri(),
                packToAny(CreateTopicRequest.newBuilder().setTopic(topicUri).build()));
    }

    protected static @NonNull UMessage buildDeprecateTopicMessage(@NonNull UUri topicUri, @NonNull UUri clientUri) {
        return buildRequestMessage(buildResponseUri(clientUri), USubscription.Method.DEPRECATE_TOPIC.localUri(),
                packToAny(DeprecateTopicRequest.newBuilder().setTopic(topicUri).build()));
    }

    protected static @NonNull SubscriptionRequest buildSubscriptionRequest(@NonNull UUri topicUri,
            @NonNull UUri clientUri) {
        return SubscriptionRequest.newBuilder()
                .setTopic(topicUri)
                .setSubscriber(buildSubscriber(clientUri))
                .build();
    }

    protected static @NonNull UMessage buildLocalSubscriptionRequestMessage(@NonNull UUri topicUri,
            @NonNull UUri clientUri) {
        return buildRequestMessage(buildResponseUri(clientUri), USubscription.Method.SUBSCRIBE.localUri(),
                packToAny(buildSubscriptionRequest(topicUri, clientUri)));
    }

    protected static @NonNull UMessage buildRemoteSubscriptionRequestMessage(@NonNull UUri topicUri,
            @NonNull UUri clientUri) {
        return buildRequestMessage(buildResponseUri(clientUri), USubscription.Method.SUBSCRIBE.remoteUri(REMOTE_AUTHORITY),
                packToAny(buildSubscriptionRequest(topicUri, clientUri)));
    }

    protected static @NonNull UMessage buildUnsubscribeMessage(@NonNull UUri topicUri, @NonNull UUri clientUri) {
        return buildRequestMessage(buildResponseUri(clientUri), USubscription.Method.UNSUBSCRIBE.localUri(),
                packToAny(UnsubscribeRequest.newBuilder()
                        .setTopic(topicUri)
                        .setSubscriber(buildSubscriber(clientUri))
                        .build()));
    }

    protected static @NonNull UMessage buildFetchSubscriptionsRequestMessage() {
        return buildRequestMessage(buildResponseUri(TestBase.LOCAL_CLIENT_URI),
                USubscription.Method.FETCH_SUBSCRIPTIONS.localUri(),
                packToAny(FetchSubscriptionsRequest.getDefaultInstance()));
    }

    protected static @NonNull UMessage buildFetchSubscriptionsByTopicMessage(@NonNull UUri topicUri,
            @NonNull UUri clientUri) {
        return buildRequestMessage(buildResponseUri(clientUri), USubscription.Method.FETCH_SUBSCRIPTIONS.localUri(),
                packToAny(FetchSubscriptionsRequest.newBuilder()
                        .setTopic(topicUri)
                        .build()));
    }

    protected static @NonNull UMessage buildFetchSubscriptionsBySubscriberMessage(@NonNull UUri subscriber) {
        return buildRequestMessage(buildResponseUri(subscriber), USubscription.Method.FETCH_SUBSCRIPTIONS.localUri(),
                packToAny(FetchSubscriptionsRequest.newBuilder()
                        .setSubscriber(buildSubscriber(subscriber))
                        .build()));
    }

    protected static @NonNull UMessage buildRegisterForNotificationsMessage(@NonNull UUri topicUri,
            @NonNull UUri clientUri) {
        return buildRequestMessage(buildResponseUri(clientUri), USubscription.Method.REGISTER_FOR_NOTIFICATIONS.localUri(),
                packToAny(NotificationsRequest.newBuilder()
                        .setTopic(topicUri)
                        .setSubscriber(buildSubscriber(clientUri))
                        .build()));
    }

    protected static @NonNull UMessage buildUnregisterForNotificationsMessage(@NonNull UUri topicUri,
            @NonNull UUri clientUri) {
        return buildRequestMessage(buildResponseUri(clientUri),
                USubscription.Method.UNREGISTER_FOR_NOTIFICATIONS.localUri(),
                packToAny(NotificationsRequest.newBuilder()
                        .setTopic(topicUri)
                        .setSubscriber(buildSubscriber(clientUri))
                        .build()));
    }

    protected static @NonNull Update buildNotificationUpdate(@NonNull UUri topic, @NonNull UUri sink,
            @NonNull SubscriptionStatus status) {
        return Update.newBuilder()
                .setTopic(topic)
                .setSubscriber(buildSubscriber(sink))
                .setAttributes(SubscribeAttributes.getDefaultInstance())
                .setStatus(status)
                .build();
    }

    protected static @NonNull UMessage buildFetchSubscribersMessage(@NonNull UUri topicUri, @NonNull UUri clientUri) {
        return buildRequestMessage(buildResponseUri(clientUri),
                USubscription.Method.FETCH_SUBSCRIBERS.localUri(),
                packToAny(FetchSubscribersRequest.newBuilder()
                        .setTopic(topicUri)
                        .build()));
    }
}
