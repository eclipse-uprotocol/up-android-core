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
import static org.eclipse.uprotocol.common.util.UStatusUtils.checkArgument;
import static org.eclipse.uprotocol.common.util.log.Formatter.join;
import static org.eclipse.uprotocol.common.util.log.Formatter.stringify;
import static org.eclipse.uprotocol.core.internal.util.CommonUtils.emptyIfNull;
import static org.eclipse.uprotocol.core.internal.util.UUriUtils.checkTopicUriValid;
import static org.eclipse.uprotocol.core.internal.util.UUriUtils.getClientUri;
import static org.eclipse.uprotocol.core.internal.util.UUriUtils.toUri;
import static org.eclipse.uprotocol.core.internal.util.UUriUtils.toUriString;
import static org.eclipse.uprotocol.core.usubscription.SubscriptionUtils.buildNotificationUpdate;
import static org.eclipse.uprotocol.core.usubscription.SubscriptionUtils.buildRequestData;
import static org.eclipse.uprotocol.core.usubscription.SubscriptionUtils.buildSubscriber;
import static org.eclipse.uprotocol.core.usubscription.SubscriptionUtils.buildSubscriptionResponse;
import static org.eclipse.uprotocol.core.usubscription.SubscriptionUtils.buildSubscriptionStatus;
import static org.eclipse.uprotocol.core.usubscription.SubscriptionUtils.convertToString;
import static org.eclipse.uprotocol.core.usubscription.SubscriptionUtils.toStatus;
import static org.eclipse.uprotocol.core.usubscription.USubscription.DEBUG;
import static org.eclipse.uprotocol.core.usubscription.USubscription.TAG;
import static org.eclipse.uprotocol.core.usubscription.USubscription.VERBOSE;
import static org.eclipse.uprotocol.core.usubscription.USubscription.logStatus;
import static org.eclipse.uprotocol.core.usubscription.v3.USubscription.METHOD_CREATE_TOPIC;
import static org.eclipse.uprotocol.core.usubscription.v3.USubscription.METHOD_DEPRECATE_TOPIC;
import static org.eclipse.uprotocol.core.usubscription.v3.USubscription.METHOD_FETCH_SUBSCRIBERS;
import static org.eclipse.uprotocol.core.usubscription.v3.USubscription.METHOD_FETCH_SUBSCRIPTIONS;
import static org.eclipse.uprotocol.core.usubscription.v3.USubscription.METHOD_REGISTER_FOR_NOTIFICATIONS;
import static org.eclipse.uprotocol.core.usubscription.v3.USubscription.METHOD_SUBSCRIBE;
import static org.eclipse.uprotocol.core.usubscription.v3.USubscription.METHOD_UNREGISTER_FOR_NOTIFICATIONS;
import static org.eclipse.uprotocol.core.usubscription.v3.USubscription.METHOD_UNSUBSCRIBE;
import static org.eclipse.uprotocol.transport.builder.UPayloadBuilder.unpack;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import com.google.common.collect.Sets;

import org.eclipse.uprotocol.common.UStatusException;
import org.eclipse.uprotocol.common.util.log.Key;
import org.eclipse.uprotocol.core.internal.util.UUriUtils;
import org.eclipse.uprotocol.core.usubscription.database.DatabaseHelper;
import org.eclipse.uprotocol.core.usubscription.database.SubscribersRecord;
import org.eclipse.uprotocol.core.usubscription.database.SubscriptionsRecord;
import org.eclipse.uprotocol.core.usubscription.database.TopicsRecord;
import org.eclipse.uprotocol.core.usubscription.v3.CreateTopicRequest;
import org.eclipse.uprotocol.core.usubscription.v3.DeprecateTopicRequest;
import org.eclipse.uprotocol.core.usubscription.v3.FetchSubscribersRequest;
import org.eclipse.uprotocol.core.usubscription.v3.FetchSubscribersResponse;
import org.eclipse.uprotocol.core.usubscription.v3.FetchSubscriptionsRequest;
import org.eclipse.uprotocol.core.usubscription.v3.FetchSubscriptionsResponse;
import org.eclipse.uprotocol.core.usubscription.v3.NotificationsRequest;
import org.eclipse.uprotocol.core.usubscription.v3.SubscriberInfo;
import org.eclipse.uprotocol.core.usubscription.v3.Subscription;
import org.eclipse.uprotocol.core.usubscription.v3.SubscriptionRequest;
import org.eclipse.uprotocol.core.usubscription.v3.SubscriptionResponse;
import org.eclipse.uprotocol.core.usubscription.v3.SubscriptionStatus;
import org.eclipse.uprotocol.core.usubscription.v3.SubscriptionStatus.State;
import org.eclipse.uprotocol.core.usubscription.v3.UnsubscribeRequest;
import org.eclipse.uprotocol.uuid.serializer.LongUuidSerializer;
import org.eclipse.uprotocol.v1.UCode;
import org.eclipse.uprotocol.v1.UMessage;
import org.eclipse.uprotocol.v1.UPayload;
import org.eclipse.uprotocol.v1.UStatus;
import org.eclipse.uprotocol.v1.UUri;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@SuppressWarnings("java:S1200")
public class SubscriptionHandler {
    public static final String UNEXPECTED_PAYLOAD = "Unexpected payload";
    private final Context mContext;
    private final DatabaseHelper mDatabaseHelper;
    private final CacheHandler mCacheHandler;
    private USubscription mUSubscription;

    public SubscriptionHandler(@NonNull Context context) {
        mContext = context;
        mDatabaseHelper = new DatabaseHelper();
        mCacheHandler = new CacheHandler(mDatabaseHelper);
    }

    @VisibleForTesting
    public SubscriptionHandler(@NonNull Context context, @NonNull DatabaseHelper databaseHelper,
            @NonNull CacheHandler cacheHandler) {
        mContext = context;
        mDatabaseHelper = databaseHelper;
        mCacheHandler = cacheHandler;
    }

    private static void checkSameEntity(@NonNull UUri uri1, @NonNull UUri uri2) {
        checkArgument(uri1.getEntity().getName().equals(uri2.getEntity().getName()), UCode.PERMISSION_DENIED,
                "'" + stringify(uri1) + "' doesn't match to '" + stringify(uri2) + "'");
    }

    private void notifySubscriptionUpdate(@NonNull Set<String> sinks, @NonNull RequestData data,
            @NonNull SubscriptionStatus status) {

        sinks.forEach(sink -> {
            final UUri sinkUri = toUri(sink);
            mUSubscription.sendSubscriptionUpdate(sinkUri, buildNotificationUpdate(data.topic, data.buildSubscriber(),
                    data.attributes, status));
            if (VERBOSE) {
                Log.v(TAG, join(Key.EVENT, "Notification sent", Key.URI, stringify(data.topic)));
            }
        });
        mUSubscription.notifySubscriptionChanged(
                buildNotificationUpdate(data.topic, data.buildSubscriber(), data.attributes, status));
    }

    public void init(USubscription usubscription) {
        mUSubscription = usubscription;
        mDatabaseHelper.init(mContext);
    }

    /**
     * Fetch subscribers of the topic from subscribers table
     *
     * @param topicUri - topic whose subscribers are to be fetched.
     * @return set of subscribers for the topic if exists. emptySet otherwise
     */
    public @NonNull Set<UUri> getSubscribers(@NonNull UUri topicUri) {
        try {
            final String topic = toUriString(topicUri);
            if (getSubscriptionState(topic) == State.SUBSCRIBED) {
                return getSubscribers(topic).stream()
                        .map(UUriUtils::toUri)
                        .collect(Collectors.toSet());
            }
        } catch (Exception e) {
            logStatus(Log.ERROR, "getSubscribers", toStatus(e));
        }
        return emptySet();
    }

    /**
     * Check if the topic exists in Topics table
     *
     * @param topic - Topic to be checked
     * @return true - if found
     */
    public boolean isTopicCreated(@NonNull String topic) {
        try {
            return mDatabaseHelper.isTopicCreated(topic);
        } catch (Exception e) {
            logStatus(Log.ERROR, "isTopicCreated", toStatus(e));
            return false;
        }
    }

    /**
     * Make an entry in the Topics table of db
     *
     * @param message - UMessage having CreateTopicRequest
     * @return Status  - OK if successful
     */
    public @NonNull UStatus createTopic(@NonNull UMessage message) {
        try {
            final UPayload payload = message.getPayload();
            final CreateTopicRequest request = unpack(payload, CreateTopicRequest.class)
                    .orElseThrow(() -> new UStatusException(UCode.INVALID_ARGUMENT, UNEXPECTED_PAYLOAD));
            final UUri topic = request.getTopic();
            final UUri publisher = getClientUri(message.getSource());

            checkArgument(!publisher.hasAuthority() && !topic.hasAuthority(),
                    UCode.PERMISSION_DENIED,
                    "Client '" + stringify(publisher) + "' is not allowed to create topic '" + request.getTopic()
                            + "'");

            checkSameEntity(topic, publisher);
            return createTopic(topic, publisher);
        } catch (Exception e) {
            return logStatus(Log.ERROR, METHOD_CREATE_TOPIC, toStatus(e));
        }
    }

    public @NonNull UStatus createTopic(@NonNull UUri topicUri, @NonNull UUri publisherUri) {
        try {
            checkTopicUriValid(topicUri);
            final String topicDetails = convertToString(emptyList());
            final String topic = toUriString(topicUri);
            final String publisher = toUriString(publisherUri);
            final TopicsRecord topicsRecord = new TopicsRecord(topic, publisher, topicDetails,
                    isRegisteredForNotification(topic));
            if (DEBUG) {
                Log.d(TAG, join(Key.REQUEST, METHOD_CREATE_TOPIC, Key.URI, topicsRecord.getTopic()));
            }
            if (mDatabaseHelper.addTopic(topicsRecord) < 0) {
                throw new UStatusException(
                        UCode.ABORTED,
                        "Failed to add topic to topics table in DB");
            }
            mUSubscription.notifyTopicCreated(topicUri, publisherUri);
            if (VERBOSE) {
                logStatus(Log.VERBOSE, METHOD_CREATE_TOPIC, STATUS_OK, Key.URI, stringify(topicUri));
            }
            return STATUS_OK;
        } catch (Exception e) {
            return logStatus(Log.ERROR, METHOD_CREATE_TOPIC, toStatus(e), Key.URI, stringify(topicUri));
        }
    }

    /**
     * Remove all entries from the tables of db for the corresponding topic
     *
     * @param message - UMessage
     * @return Status - OK - if DB entry removed successfully, NOT_FOUND otherwise.
     */
    public @NonNull UStatus deprecateTopic(@NonNull UMessage message) {
        try {
            final UPayload payload = message.getPayload();
            final DeprecateTopicRequest request = unpack(payload, DeprecateTopicRequest.class)
                    .orElseThrow(() -> new UStatusException(UCode.INVALID_ARGUMENT, UNEXPECTED_PAYLOAD));
            final UUri topic = request.getTopic();
            final UUri publisher = getClientUri(message.getSource());

            checkArgument(!publisher.hasAuthority() && !topic.hasAuthority(),
                    UCode.PERMISSION_DENIED,
                    "Client '" + publisher + "' is not allowed to deprecate topic '" + request.getTopic() + "'");

            checkSameEntity(topic, publisher);
            return deprecateTopic(topic);
        } catch (Exception e) {
            return logStatus(Log.ERROR, METHOD_DEPRECATE_TOPIC, toStatus(e));
        }
    }

    public @NonNull UStatus deprecateTopic(@NonNull UUri topicUri) {
        return logStatus(Log.WARN, METHOD_DEPRECATE_TOPIC, buildStatus(UCode.UNIMPLEMENTED), Key.TOPIC, topicUri);
    }

    /**
     * subscribe API: Extract SubscriptionRequest from UMessage to fetch topic ,
     * SubscriberInfo and Subscriber Attribute
     * <p>
     * Check if the incoming topic is local and is created.
     * Add entry to subscription table if the topic is not subscribed ,
     * update subscription state as SUBSCRIBED and set Status to OK.
     * Notify SubscriptionUpdate to subscriber and tracker to indicate the topic is SUBSCRIBED.
     * <p>
     * If topic is remote and not subscribed, add entry to subscription table
     * with subscriptionState as SUBSCRIBE_PENDING and set Status to OK.
     * Notify SubscriptionUpdate to subscriber indicating the topic is SUBSCRIBE_PENDING,
     * <p>
     * Add to subscribers table if status is OK, else return NOT_FOUND
     *
     * @param message - UMessage of req type, containing SubscriptionRequest
     * @return SubscriptionResponse - Having the status, or null if request is postponed
     */
    @SuppressWarnings("java:S3776")
    public @NonNull SubscriptionResponse subscribe(@NonNull UMessage message) {
        if (DEBUG) {
            Log.d(TAG, join(Key.REQUEST, METHOD_SUBSCRIBE, Key.EVENT, stringify(message)));
        }
        SubscriptionResponse response;
        final String id = LongUuidSerializer.instance().serialize(message.getAttributes().getId());

        try {
            final UPayload payload = message.getPayload();
            final SubscriptionRequest request = unpack(payload, SubscriptionRequest.class)
                    .orElseThrow(() -> new UStatusException(UCode.INVALID_ARGUMENT, UNEXPECTED_PAYLOAD));
            final UUri source = message.getSource();

            final RequestData data = buildRequestData(request, source.hasAuthority());
            checkSameEntity(source, data.subscriber);

            if (!data.topic.hasAuthority()) {
                if (isTopicCreated(toUriString(data.topic))) {
                    response = subscribeLocal(id, data);
                } else {
                    response = buildSubscriptionResponse(buildSubscriptionStatus(
                            UCode.NOT_FOUND, State.UNSUBSCRIBED, "Topic is not created"));
                }
            } else {
                response = buildSubscriptionResponse(buildSubscriptionStatus(
                        UCode.UNIMPLEMENTED, State.UNSUBSCRIBED, "Remote requests not supported currently"));
            }
        } catch (Exception e) {
            logStatus(Log.ERROR, METHOD_SUBSCRIBE, toStatus(e));
            response = SubscriptionUtils.toSubscriptionResponse(e);
        }
        if (DEBUG) {
            logStatus(Log.DEBUG, METHOD_SUBSCRIBE, buildStatus(response.getStatus().getCode()));
        }
        return response;
    }

    private @NonNull SubscriptionResponse subscribeLocal(String id, @NonNull RequestData data) {
        final String topic = toUriString(data.topic);
        if (VERBOSE) {
            Log.v(TAG, join(Key.MESSAGE, "Topic is created", Key.URI, topic));
        }
        if (!isTopicSubscribed(topic)) {
            if (VERBOSE) {
                Log.v(TAG, join(Key.MESSAGE, "Topic not subscribed, add to subscriptions"));
            }
            addTopicToSubscriptionTable(topic, id, State.SUBSCRIBED);
        }
        final SubscriptionStatus status = addSubscriber(id, data);
        return buildSubscriptionResponse(status);
    }

    private void addTopicToSubscriptionTable(String topicUri, String id, @NonNull State state) {
        final SubscriptionsRecord subscriptionsRecord = new SubscriptionsRecord(topicUri, id, state.getNumber());
        if (mDatabaseHelper.addSubscription(subscriptionsRecord) < 0) {
            throw new SubscriptionException(buildSubscriptionStatus(
                    UCode.ABORTED, getSubscriptionState(topicUri),
                    "Failed to add topic to subscription table in DB"));
        }
    }

    private SubscriptionStatus addSubscriber(String id, @NonNull RequestData data) {
        final String subscriber = toUriString(data.subscriber);
        final String topic = toUriString(data.topic);
        final Set<String> subscribers = getSubscribers(topic);

        if (!subscribers.contains(subscriber)) {
            return addSubscriberToSubscribersTable(id, data);
        } else {
            Log.i(TAG, join(Key.MESSAGE, "Subscriber already exists", Key.SUBSCRIBER, stringify(data.subscriber)));
            return buildSubscriptionStatus(UCode.OK, getSubscriptionState(topic), "Subscriber already exists");
        }
    }

    private @NonNull SubscriptionStatus addSubscriberToSubscribersTable(@NonNull String id, @NonNull RequestData data) {
        final String expiryTime = data.attributes.getExpire().toString();
        final String topic = toUriString(data.topic);
        final String subscriber = toUriString(data.subscriber);
        final String subscriberDetails = convertToString(data.subscriberDetails);
        final SubscribersRecord subscribersRecord =
                new SubscribersRecord(topic, subscriber, subscriberDetails, expiryTime, id);
        if (mDatabaseHelper.addSubscriber(subscribersRecord) < 0) {
            throw new SubscriptionException(buildSubscriptionStatus(
                    UCode.ABORTED,
                    getSubscriptionState(topic),
                    "Failed to add subscriber to DB"));
        } else {
            if (VERBOSE) {
                Log.v(TAG, join(Key.MESSAGE, "Subscriber added to db", Key.SUBSCRIBER, stringify(data.subscriber)));
            }
            final SubscriptionStatus status = buildSubscriptionStatus(UCode.OK, getSubscriptionState(topic), "");
            final String tracker = getPublisherIfRegistered(topic);
            final Set<String> notifiers = tracker.isEmpty() ? emptySet() : Sets.newHashSet(tracker);
            notifySubscriptionUpdate(notifiers, data, status);
            return status;
        }
    }

    private @NonNull String getPublisherIfRegistered(String topic) {
        return emptyIfNull(mDatabaseHelper.getPublisherIfRegistered(topic));
    }

    /**
     * unsubscribe API : Unpack the UMessage for UnsubscribeRequest
     * <p>
     * if topic is subscribed, remove entry from subscribers table.
     * Check if this subscriber was the last subscriber in the table.
     * if yes , and if topic is local, remove from subscription table and
     * notify subscription update indicating the topic was UNSUBSCRIBED.
     * If remote , generate UMessage for remote request, update status in db to
     * UNSUBSCRIBE_PENDING and notify subscription state update as UNSUBSCRIBED.
     *
     * @param message - UMessage having UnsubscribeRequest
     * @return Status - OK
     */
    public @NonNull UStatus unsubscribe(@NonNull UMessage message) {
        if (DEBUG) {
            Log.d(TAG, join(Key.REQUEST, METHOD_UNSUBSCRIBE, Key.EVENT, stringify(message)));
        }
        UCode code = UCode.OK;
        try {
            final UPayload payload = message.getPayload();
            final UnsubscribeRequest request = unpack(payload, UnsubscribeRequest.class)
                    .orElseThrow(() -> new UStatusException(UCode.INVALID_ARGUMENT, UNEXPECTED_PAYLOAD));

            final UUri source = message.getSource();

            final RequestData data = buildRequestData(request, source.hasAuthority());
            checkSameEntity(source, data.subscriber);

            if (isTopicSubscribed(toUriString(data.topic))) {
                code = deleteSubscriberFromDB(data);
            }
            // TODO: check if NOT_FOUND should be returned if not subscribed ?
        } catch (Exception e) {
            return logStatus(Log.ERROR, METHOD_UNSUBSCRIBE, toStatus(e));
        }
        final UStatus status = buildStatus(code);
        if (DEBUG) {
            logStatus(Log.DEBUG, METHOD_UNSUBSCRIBE, status);
        }
        return status;
    }

    private @NonNull UCode deleteSubscriberFromDB(@NonNull RequestData data) {
        final String topic = toUriString(data.topic);
        final String subscriber = toUriString(data.subscriber);
        final int subscribersCount = getSubscribers(topic).size();
        final SubscribersRecord subscriberRecord = mDatabaseHelper.getSubscriber(topic, subscriber);

        if (subscriberRecord != null) {
            mDatabaseHelper.deleteSubscriber(topic, subscriber);
            if (subscribersCount == 1) {
                if (VERBOSE) {
                    Log.v(TAG, join(Key.MESSAGE, "Delete last subscriber for topic", Key.URI, topic));
                }
                mDatabaseHelper.deleteTopicFromSubscriptions(topic);
            }
            final String tracker = getPublisherIfRegistered(topic);
            final Set<String> notifiers = tracker.isEmpty() ? emptySet() : Sets.newHashSet(tracker);
            notifySubscriptionUpdate(notifiers, data,
                    buildSubscriptionStatus(UCode.NOT_FOUND, State.UNSUBSCRIBED, ""));
        }
        return UCode.OK;
    }

    private @NonNull Set<String> getSubscribers(@NonNull String topic) {
        return new HashSet<>(mDatabaseHelper.getSubscribers(topic));
    }

    /**
     * fetchSubscriptions : API to fetchSubscriptions by Topic or by Subscriber
     * <p>
     * Based on the request type, list of subscriptions is fetched from the db.
     * if subscriptions are not empty, build and return FetchSubscriptionResponse.
     * Else response with NOT_FOUND is returned.
     *
     * @param message - UMessage having FetchSubscriptionRequest
     * @return FetchSubscriptionsResponse - containing subscriptions and status
     */
    public @NonNull FetchSubscriptionsResponse fetchSubscriptions(@NonNull UMessage message) {
        if (DEBUG) {
            Log.d(TAG, join(Key.REQUEST, METHOD_FETCH_SUBSCRIPTIONS, Key.EVENT, stringify(message)));
        }
        UCode code = UCode.NOT_FOUND;
        FetchSubscriptionsResponse response = FetchSubscriptionsResponse.newBuilder()
                .setStatus(buildStatus(code)).build();
        try {
            final UPayload payload = message.getPayload();
            final FetchSubscriptionsRequest request = unpack(payload, FetchSubscriptionsRequest.class)
                    .orElseThrow(() -> new UStatusException(UCode.INVALID_ARGUMENT, UNEXPECTED_PAYLOAD));
            final FetchSubscriptionsRequest.RequestCase requestCase = request.getRequestCase();
            List<Subscription> subscriptions = emptyList();
            if (requestCase == FetchSubscriptionsRequest.RequestCase.TOPIC) {
                final UUri topicUri = request.getTopic();
                subscriptions = mCacheHandler.fetchSubscriptionsByTopic(topicUri);
            } else if (requestCase == FetchSubscriptionsRequest.RequestCase.SUBSCRIBER) {
                final SubscriberInfo subscriber = request.getSubscriber();
                subscriptions = mCacheHandler.fetchSubscriptionsBySubscriber(subscriber);
            }
            if (!subscriptions.isEmpty()) {
                code = UCode.OK;
                response = FetchSubscriptionsResponse.newBuilder()
                        .addAllSubscriptions(subscriptions)
                        .setHasMoreRecords(false)
                        .setStatus(buildStatus(code))
                        .build();
            }
            if (VERBOSE) {
                logStatus(Log.VERBOSE, METHOD_FETCH_SUBSCRIPTIONS, buildStatus(code));
            }
        } catch (Exception e) {
            code = UCode.ABORTED;
            logStatus(Log.ERROR, METHOD_FETCH_SUBSCRIPTIONS, buildStatus(code, e.getMessage()));
            response = FetchSubscriptionsResponse.newBuilder()
                    .setStatus(buildStatus(code))
                    .build();
        }
        return response;
    }

    /**
     * fetchSubscribers : API to fetchSubscribers for a topic
     * <p>
     * List of subscribers is fetched from the db.
     * if subscribers are not empty, build and return FetchSubscriberResponse.
     * Else response with NOT_FOUND is returned.
     *
     * @param message - UMessage having FetchSubscribersRequest
     * @return FetchSubscribersResponse - containing subscribers and status
     */
    public @NonNull FetchSubscribersResponse fetchSubscribers(@NonNull UMessage message) {
        if (DEBUG) {
            Log.d(TAG, join(Key.REQUEST, METHOD_FETCH_SUBSCRIBERS, Key.EVENT, stringify(message)));
        }
        UCode code = UCode.NOT_FOUND;
        FetchSubscribersResponse response = FetchSubscribersResponse.newBuilder().setStatus(buildStatus(code)).build();
        try {
            final UPayload payload = message.getPayload();
            final FetchSubscribersRequest request = unpack(payload, FetchSubscribersRequest.class)
                    .orElseThrow(() -> new UStatusException(UCode.INVALID_ARGUMENT, UNEXPECTED_PAYLOAD));
            final List<SubscribersRecord> subscribersRecords = mDatabaseHelper.fetchSubscriptionsByTopic(
                    toUriString(request.getTopic()));

            if (!subscribersRecords.isEmpty()) {
                final List<SubscriberInfo> subscriberInfoList = new ArrayList<>();
                for (SubscribersRecord subscribersRecord : subscribersRecords) {
                    String subscriberUri = emptyIfNull(subscribersRecord.getSubscriberUri());
                    String subscriberDetails = emptyIfNull(subscribersRecord.getSubscriberDetails());
                    subscriberInfoList.add(buildSubscriber(subscriberUri, subscriberDetails));
                }
                code = UCode.OK;
                response = FetchSubscribersResponse.newBuilder()
                        .addAllSubscribers(subscriberInfoList)
                        .setHasMoreRecords(false)
                        .setStatus(buildStatus(code))
                        .build();
            }
            if (VERBOSE) {
                logStatus(Log.VERBOSE, METHOD_FETCH_SUBSCRIBERS, buildStatus(code));
            }
        } catch (Exception e) {
            final UStatus status = logStatus(Log.ERROR, METHOD_FETCH_SUBSCRIBERS,
                    buildStatus(UCode.ABORTED, e.getMessage()));
            response = FetchSubscribersResponse.newBuilder().setStatus(status).build();
        }
        return response;
    }

    /**
     * registerForNotifications : API called by Publisher to register notifications on the topic
     * <p>
     * Check if caller uEntity matches to Topic, else return PERMISSION_DENIED.
     * If topic is created, update topics table registered flag.
     * Else return NOT_FOUND status
     *
     * @param message - UMessage having NotificationRequest
     * @return Status - OK if successful
     */
    public @NonNull UStatus registerForNotifications(@NonNull UMessage message) {
        if (DEBUG) {
            Log.d(TAG, join(Key.REQUEST, METHOD_REGISTER_FOR_NOTIFICATIONS, Key.EVENT, stringify(message)));
        }
        try {
            final UPayload payload = message.getPayload();
            final NotificationsRequest request = unpack(payload, NotificationsRequest.class)
                    .orElseThrow(() -> new UStatusException(UCode.INVALID_ARGUMENT, UNEXPECTED_PAYLOAD));

            final UUri responseUri = message.getSource();
            final UUri topicUri = request.getTopic();
            final String topic = toUriString(topicUri);
            checkSameEntity(topicUri, responseUri);
            if (isTopicCreated(topic)) {
                mDatabaseHelper.updateTopic(topic, true);
                return STATUS_OK;
            }
            return logStatus(Log.WARN, METHOD_REGISTER_FOR_NOTIFICATIONS,
                    buildStatus(UCode.NOT_FOUND, "Topic is not created"), Key.URI, stringify(topicUri));
        } catch (Exception e) {
            return logStatus(Log.ERROR, METHOD_REGISTER_FOR_NOTIFICATIONS, toStatus(e));
        }
    }

    /**
     * unregisterForNotifications : API called by Publisher to unregister notifications on the topic
     * <p>
     * Check if caller uEntity matches to Topic, else return PERMISSION_DENIED.
     * If topic is registered, update topics table registered flag.
     * Else return NOT_FOUND status
     *
     * @param message - UMessage having NotificationRequest
     * @return Status - OK if successful
     */
    public @NonNull UStatus unregisterForNotifications(@NonNull UMessage message) {
        if (DEBUG) {
            Log.d(TAG, join(Key.REQUEST, METHOD_UNREGISTER_FOR_NOTIFICATIONS, Key.EVENT, stringify(message)));
        }
        try {
            final UPayload payload = message.getPayload();
            final NotificationsRequest request = unpack(payload, NotificationsRequest.class)
                    .orElseThrow(() -> new UStatusException(UCode.INVALID_ARGUMENT, UNEXPECTED_PAYLOAD));

            final UUri responseUri = message.getSource();
            final UUri topicUri = request.getTopic();
            final String topic = toUriString(topicUri);
            checkSameEntity(topicUri, responseUri);
            if (isRegisteredForNotification(topic)) {
                mDatabaseHelper.updateTopic(topic, false);
                return STATUS_OK;
            }
            return logStatus(Log.WARN, METHOD_UNREGISTER_FOR_NOTIFICATIONS,
                    buildStatus(UCode.NOT_FOUND, "Topic was not registered"), Key.URI, stringify(topicUri));
        } catch (Exception e) {
            return logStatus(Log.ERROR, METHOD_UNREGISTER_FOR_NOTIFICATIONS, toStatus(e));
        }
    }

    private boolean isRegisteredForNotification(@NonNull String topic) {
        return mDatabaseHelper.isRegisteredForNotification(topic);
    }

    /**
     * Check if the SubscriptionState of a topic is SUBSCRIBED.
     *
     * @param topic - topic to be checked
     * @return true - if topic is SUBSCRIBED or SUBSCRIBE_PENDING
     */
    public boolean isTopicSubscribed(@NonNull String topic) {
        try {
            int state = getSubscriptionState(topic).getNumber();
            return (state == State.SUBSCRIBED.getNumber()) || (state == State.SUBSCRIBE_PENDING.getNumber());
        } catch (Exception e) {
            logStatus(Log.ERROR, "isTopicSubscribed", toStatus(e));
            return false;
        }
    }

    /**
     * Function to get subscription state of the given topic
     *
     * @param topicName - given topic
     * @return - SubscriptionState
     */
    public @NonNull State getSubscriptionState(@NonNull String topicName) {
        try {
            int subscriptionState = mDatabaseHelper.getSubscriptionState(topicName);
            return (State.forNumber(subscriptionState));
        } catch (Exception e) {
            logStatus(Log.ERROR, "getSubscriptionState", toStatus(e));
            return State.UNSUBSCRIBED;
        }
    }

    /**
     * Get the publisher of a topic from TOPICS table.
     *
     * @param topic - topic to be used to fetch the producer
     * @return clientId who registered the topic if exists,
     * empty string otherwise.
     */
    public @NonNull UUri getPublisher(@NonNull UUri topic) {
        try {
            return toUri(mDatabaseHelper.getPublisher(toUriString(topic)));
        } catch (Exception e) {
            logStatus(Log.ERROR, "getPublisher", toStatus(e));
            return UUri.getDefaultInstance();
        }
    }
}
