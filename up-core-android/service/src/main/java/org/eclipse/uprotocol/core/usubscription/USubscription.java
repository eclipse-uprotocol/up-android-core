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

import static org.eclipse.uprotocol.common.util.UStatusUtils.checkArgument;
import static org.eclipse.uprotocol.common.util.log.Formatter.join;
import static org.eclipse.uprotocol.common.util.log.Formatter.status;
import static org.eclipse.uprotocol.common.util.log.Formatter.tag;
import static org.eclipse.uprotocol.core.usubscription.v3.USubscription.METHOD_CREATE_TOPIC;
import static org.eclipse.uprotocol.core.usubscription.v3.USubscription.METHOD_DEPRECATE_TOPIC;
import static org.eclipse.uprotocol.core.usubscription.v3.USubscription.METHOD_FETCH_SUBSCRIBERS;
import static org.eclipse.uprotocol.core.usubscription.v3.USubscription.METHOD_FETCH_SUBSCRIPTIONS;
import static org.eclipse.uprotocol.core.usubscription.v3.USubscription.METHOD_REGISTER_FOR_NOTIFICATIONS;
import static org.eclipse.uprotocol.core.usubscription.v3.USubscription.METHOD_SUBSCRIBE;
import static org.eclipse.uprotocol.core.usubscription.v3.USubscription.METHOD_UNREGISTER_FOR_NOTIFICATIONS;
import static org.eclipse.uprotocol.core.usubscription.v3.USubscription.METHOD_UNSUBSCRIBE;
import static org.eclipse.uprotocol.transport.builder.UPayloadBuilder.packToAny;
import static org.eclipse.uprotocol.uri.validator.UriValidator.isRemote;

import static java.util.Optional.ofNullable;

import android.content.Context;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import org.eclipse.uprotocol.common.util.log.Key;
import org.eclipse.uprotocol.core.UCore;
import org.eclipse.uprotocol.core.internal.handler.MessageHandler;
import org.eclipse.uprotocol.core.ubus.UBus;
import org.eclipse.uprotocol.core.usubscription.v3.Update;
import org.eclipse.uprotocol.transport.builder.UAttributesBuilder;
import org.eclipse.uprotocol.uri.builder.UResourceBuilder;
import org.eclipse.uprotocol.v1.UAuthority;
import org.eclipse.uprotocol.v1.UCode;
import org.eclipse.uprotocol.v1.UEntity;
import org.eclipse.uprotocol.v1.UMessage;
import org.eclipse.uprotocol.v1.UPayload;
import org.eclipse.uprotocol.v1.UPriority;
import org.eclipse.uprotocol.v1.UResource;
import org.eclipse.uprotocol.v1.UStatus;
import org.eclipse.uprotocol.v1.UUri;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@SuppressWarnings({"java:S1200", "java:S3008"})
public class USubscription extends UCore.Component {
    public static final UEntity SERVICE = org.eclipse.uprotocol.core.usubscription.v3.USubscription.SERVICE;

    protected static final String TAG = tag(SERVICE.getName());
    protected static boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);
    protected static boolean VERBOSE = Log.isLoggable(TAG, Log.VERBOSE);

    public static final UUri TOPIC_SUBSCRIPTION_UPDATE = UUri.newBuilder()
            .setEntity(SERVICE)
            .setResource(UResource.newBuilder()
                    .setName("subscriptions")
                    .setMessage("Update")
                    .build())
            .build();

    public enum Method {
        CREATE_TOPIC(METHOD_CREATE_TOPIC),
        DEPRECATE_TOPIC(METHOD_DEPRECATE_TOPIC),
        SUBSCRIBE(METHOD_SUBSCRIBE),
        UNSUBSCRIBE(METHOD_UNSUBSCRIBE),
        FETCH_SUBSCRIPTIONS(METHOD_FETCH_SUBSCRIPTIONS),
        FETCH_SUBSCRIBERS(METHOD_FETCH_SUBSCRIBERS),
        REGISTER_FOR_NOTIFICATIONS(METHOD_REGISTER_FOR_NOTIFICATIONS),
        UNREGISTER_FOR_NOTIFICATIONS(METHOD_UNREGISTER_FOR_NOTIFICATIONS);

        private final String methodName;

        Method(@NonNull String methodName) {
            this.methodName = methodName;
        }

        public @NonNull UUri localUri() {
            return UUri.newBuilder()
                    .setEntity(SERVICE)
                    .setResource(UResourceBuilder.forRpcRequest(methodName))
                    .build();
        }

        public @NonNull UUri remoteUri(@NonNull UAuthority remoteAuthority) {
            return UUri.newBuilder()
                    .setAuthority(remoteAuthority)
                    .setEntity(SERVICE)
                    .setResource(UResourceBuilder.forRpcRequest(methodName))
                    .build();
        }
    }

    @VisibleForTesting
    final IBinder mClientToken = new Binder();
    private final SubscriptionHandler mSubscriptionHandler;
    private final Set<SubscriptionListener> mSubscriptionListeners = ConcurrentHashMap.newKeySet();
    private final ScheduledExecutorService mExecutor = Executors.newSingleThreadScheduledExecutor();
    private UBus mUBus;
    private MessageHandler mMessageHandler;

    public USubscription(@NonNull Context context) {
        mSubscriptionHandler = new SubscriptionHandler(context);
    }

    @VisibleForTesting
    public USubscription(@NonNull SubscriptionHandler subscriptionHandler) {
        mSubscriptionHandler = subscriptionHandler;
    }

    @Override
    protected void init(@NonNull UCore uCore) {
        Log.i(TAG, join(Key.EVENT, "Service init"));
        mUBus = uCore.getUBus();
        mMessageHandler = ofNullable(mMessageHandler).orElse(
                new MessageHandler(mUBus, SERVICE, mClientToken, mExecutor));
        mSubscriptionHandler.init(this);

        mUBus.registerClient(SERVICE, mClientToken, mMessageHandler);
        mMessageHandler.registerRpcListener(Method.CREATE_TOPIC.localUri(), this::createTopic);
        mMessageHandler.registerRpcListener(Method.DEPRECATE_TOPIC.localUri(), this::deprecateTopic);
        mMessageHandler.registerRpcListener(Method.SUBSCRIBE.localUri(), this::subscribe);
        mMessageHandler.registerRpcListener(Method.UNSUBSCRIBE.localUri(), this::unsubscribe);
        mMessageHandler.registerRpcListener(Method.FETCH_SUBSCRIPTIONS.localUri(), this::fetchSubscriptions);
        mMessageHandler.registerRpcListener(Method.FETCH_SUBSCRIBERS.localUri(), this::fetchSubscribers);
        mMessageHandler.registerRpcListener(Method.REGISTER_FOR_NOTIFICATIONS.localUri(),
                this::registerForNotifications);
        mMessageHandler.registerRpcListener(Method.UNREGISTER_FOR_NOTIFICATIONS.localUri(),
                this::unregisterForNotifications);
    }

    @Override
    protected void startup() {
        Log.i(TAG, join(Key.EVENT, "Service start"));
    }

    @Override
    @SuppressWarnings("BlockingMethodInNonBlockingContext")
    protected void shutdown() {
        Log.i(TAG, join(Key.EVENT, "Service shutdown"));
        mMessageHandler.unregisterAllListeners();
        mExecutor.shutdown();
        try {
            if (!mExecutor.awaitTermination(100, TimeUnit.MILLISECONDS)) {
                Log.w(TAG, join(Key.EVENT, "Executor hasn't been terminated after timeout"));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        mSubscriptionListeners.clear();
    }

    @VisibleForTesting
    @NonNull ScheduledExecutorService getExecutor() {
        return mExecutor;
    }

    private void createTopic(@NonNull UMessage requestMessage, @NonNull CompletableFuture<UPayload> responseFuture) {
        responseFuture.complete(packToAny(mSubscriptionHandler.createTopic(requestMessage)));
    }

    private void deprecateTopic(@NonNull UMessage requestMessage, @NonNull CompletableFuture<UPayload> responseFuture) {
        responseFuture.complete(packToAny(mSubscriptionHandler.deprecateTopic(requestMessage)));
    }

    private void subscribe(@NonNull UMessage requestMessage, @NonNull CompletableFuture<UPayload> responseFuture) {
        responseFuture.complete(packToAny(mSubscriptionHandler.subscribe(requestMessage)));
    }

    private void unsubscribe(@NonNull UMessage requestMessage, @NonNull CompletableFuture<UPayload> responseFuture) {
        responseFuture.complete(packToAny(mSubscriptionHandler.unsubscribe(requestMessage)));
    }

    private void fetchSubscriptions(@NonNull UMessage requestMessage,
            @NonNull CompletableFuture<UPayload> responseFuture) {
        responseFuture.complete(packToAny(mSubscriptionHandler.fetchSubscriptions(requestMessage)));
    }

    private void fetchSubscribers(@NonNull UMessage requestMessage,
            @NonNull CompletableFuture<UPayload> responseFuture) {
        responseFuture.complete(packToAny(mSubscriptionHandler.fetchSubscribers(requestMessage)));
    }

    private void registerForNotifications(@NonNull UMessage requestMessage,
            @NonNull CompletableFuture<UPayload> responseFuture) {
        responseFuture.complete(packToAny(mSubscriptionHandler.registerForNotifications(requestMessage)));
    }

    private void unregisterForNotifications(@NonNull UMessage requestMessage,
            @NonNull CompletableFuture<UPayload> responseFuture) {
        responseFuture.complete(packToAny(mSubscriptionHandler.unregisterForNotifications(requestMessage)));
    }

    public @NonNull UAuthority getDeviceAuthority() {
        final UAuthority authority = mUBus.getDeviceAuthority();
        checkArgument(isRemote(authority), UCode.FAILED_PRECONDITION, "Device authority is unknown");
        return authority;
    }

    public @NonNull Set<UUri> getSubscribers(@NonNull UUri topic) {
        return mSubscriptionHandler.getSubscribers(topic);
    }

    public @NonNull UUri getPublisher(@NonNull UUri topic) {
        return mSubscriptionHandler.getPublisher(topic);
    }

    public void registerListener(@NonNull SubscriptionListener listener) {
        mSubscriptionListeners.add(listener);
    }

    public void unregisterListener(@NonNull SubscriptionListener listener) {
        mSubscriptionListeners.remove(listener);
    }

    protected void sendSubscriptionUpdate(@NonNull UUri sink, @NonNull Update updatedSubscription) {
        final UMessage message = UMessage.newBuilder()
                .setSource(TOPIC_SUBSCRIPTION_UPDATE)
                .setAttributes(UAttributesBuilder.notification(UPriority.UPRIORITY_CS0, sink).build())
                .setPayload(packToAny(updatedSubscription))
                .build();
        mUBus.send(message, mClientToken);
    }

    protected void notifySubscriptionChanged(@NonNull Update updatedSubscription) {
        mSubscriptionListeners.forEach((listener -> listener.onSubscriptionChanged(updatedSubscription)));
    }

    protected void notifyTopicCreated(@NonNull UUri topic, @NonNull UUri publisher) {
        mSubscriptionListeners.forEach((listener -> listener.onTopicCreated(topic, publisher)));
    }

    protected void notifyTopicDeprecated(@NonNull UUri topic) {
        mSubscriptionListeners.forEach((listener -> listener.onTopicDeprecated(topic)));
    }

    protected static @NonNull UStatus logStatus(int priority, @NonNull String method, @NonNull UStatus status,
            Object... args) {
        Log.println(priority, TAG, status(method, status, args));
        return status;
    }

    @VisibleForTesting
    void inject(@NonNull UMessage message) {
        mMessageHandler.onReceive(message);
    }
}
