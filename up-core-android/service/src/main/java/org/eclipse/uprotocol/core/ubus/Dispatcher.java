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

import static org.eclipse.uprotocol.common.util.UStatusUtils.STATUS_OK;
import static org.eclipse.uprotocol.common.util.UStatusUtils.buildStatus;
import static org.eclipse.uprotocol.common.util.UStatusUtils.checkArgument;
import static org.eclipse.uprotocol.common.util.UStatusUtils.isOk;
import static org.eclipse.uprotocol.common.util.UStatusUtils.toStatus;
import static org.eclipse.uprotocol.common.util.log.Formatter.join;
import static org.eclipse.uprotocol.common.util.log.Formatter.stringify;
import static org.eclipse.uprotocol.core.internal.util.CommonUtils.emptyIfNull;
import static org.eclipse.uprotocol.core.internal.util.UMessageUtils.addSinkIfEmpty;
import static org.eclipse.uprotocol.core.internal.util.UMessageUtils.isExpired;
import static org.eclipse.uprotocol.core.internal.util.UMessageUtils.removeSink;
import static org.eclipse.uprotocol.core.internal.util.UUriUtils.checkTopicUriValid;
import static org.eclipse.uprotocol.core.internal.util.UUriUtils.isMethodUri;
import static org.eclipse.uprotocol.core.internal.util.UUriUtils.isRemoteUri;
import static org.eclipse.uprotocol.core.internal.util.UUriUtils.isSameClient;
import static org.eclipse.uprotocol.core.internal.util.UUriUtils.toUri;
import static org.eclipse.uprotocol.core.internal.util.log.FormatterExt.stringify;
import static org.eclipse.uprotocol.core.ubus.UBus.EXTRA_BLOCK_AUTO_FETCH;
import static org.eclipse.uprotocol.uri.validator.UriValidator.isEmpty;

import static java.util.Collections.emptyList;

import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import org.eclipse.uprotocol.common.util.log.Key;
import org.eclipse.uprotocol.core.ubus.client.Client;
import org.eclipse.uprotocol.core.ubus.client.ClientManager;
import org.eclipse.uprotocol.core.ubus.client.ClientManager.RegistrationListener;
import org.eclipse.uprotocol.core.usubscription.SubscriptionListener;
import org.eclipse.uprotocol.core.usubscription.USubscription;
import org.eclipse.uprotocol.core.usubscription.v3.SubscriptionStatus;
import org.eclipse.uprotocol.core.usubscription.v3.Update;
import org.eclipse.uprotocol.core.utwin.UTwin;
import org.eclipse.uprotocol.v1.UCode;
import org.eclipse.uprotocol.v1.UMessage;
import org.eclipse.uprotocol.v1.UMessageType;
import org.eclipse.uprotocol.v1.UStatus;
import org.eclipse.uprotocol.v1.UUri;

import java.io.PrintWriter;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class Dispatcher extends UBus.Component {
    private static final int DISPATCH_RETRY_DELAY_MS = 50;
    private static final UUri EMPTY_URI = UUri.getDefaultInstance();

    private final RpcHandler mRpcHandler;
    private final ScheduledExecutorService mExecutor = Executors.newScheduledThreadPool(1);
    private final SubscriptionCache mSubscriptionCache = new SubscriptionCache();
    private final LinkedClients mLinkedClients = new LinkedClients();
    private UTwin mUTwin;
    private USubscription mUSubscription;
    private ClientManager mClientManager;

    private final SubscriptionListener mSubscriptionListener = new SubscriptionListener() {
        @Override
        public void onSubscriptionChanged(@NonNull Update update) {
            handleSubscriptionChange(update);
        }

        @Override
        public void onTopicCreated(@NonNull UUri topic, @NonNull UUri publisher) {
            mSubscriptionCache.addTopic(topic, publisher);
        }

        @Override
        public void onTopicDeprecated(@NonNull UUri topic) {
            mSubscriptionCache.removeTopic(topic);
            mUTwin.removeMessage(topic);
        }
    };

    private final RegistrationListener mClientRegistrationListener = new RegistrationListener() {
        @Override
        public void onClientUnregistered(@NonNull Client client) {
            mLinkedClients.unlinkFromDispatch(client);
        }
    };

    @VisibleForTesting
    @NonNull SubscriptionListener getSubscriptionListener() {
        return mSubscriptionListener;
    }

    @VisibleForTesting
    @NonNull RegistrationListener getClientRegistrationListener() {
        return mClientRegistrationListener;
    }

    public Dispatcher() {
        mRpcHandler = new RpcHandler();
    }

    @VisibleForTesting
    Dispatcher(@NonNull RpcHandler rpcHandler) {
        mRpcHandler = rpcHandler;
    }

    @Override
    public void init(@NonNull UBus.Components components) {
        mUTwin = components.getUCore().getUTwin();
        mUSubscription = components.getUCore().getUSubscription();
        mSubscriptionCache.setService(mUSubscription);
        mClientManager = components.getClientManager();

        mRpcHandler.init(components);
        mUSubscription.registerListener(mSubscriptionListener);
        mClientManager.registerListener(mClientRegistrationListener);
    }

    @Override
    public void startup() {
        mRpcHandler.startup();
    }

    @Override
    @SuppressWarnings("BlockingMethodInNonBlockingContext")
    public void shutdown() {
        mRpcHandler.shutdown();
        mExecutor.shutdown();
        try {
            if (!mExecutor.awaitTermination(100, TimeUnit.MILLISECONDS)) {
                Log.w(TAG, join(Key.EVENT, "Timeout while waiting for executor termination"));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        mUSubscription.unregisterListener(mSubscriptionListener);
        mClientManager.unregisterListener(mClientRegistrationListener);
        mSubscriptionCache.clear();
        mLinkedClients.clear();
    }

    @Override
    public void clearCache() {
        mRpcHandler.clearCache();
        mSubscriptionCache.clear();
    }

    @NonNull SubscriptionCache getSubscriptionCache() {
        return mSubscriptionCache;
    }

    public @NonNull List<UMessage> pull(@NonNull UUri uri, int count, @NonNull Client client) {
        try {
            checkTopicUriValid(uri);
            checkArgument(count > 0, "Count is negative");
            if (mSubscriptionCache.isTopicSubscribed(uri, client.getUri())) {
                final UMessage message = mUTwin.getMessage(uri);
                return (message != null) ? List.of(message) : emptyList();
            }
        } catch (Exception e) {
            logStatus(Log.ERROR, "pull", toStatus(e), Key.URI, stringify(uri), Key.CLIENT, client);
        }
        return emptyList();
    }

    public @NonNull UStatus enableDispatching(@NonNull UUri uri, Bundle extras, @NonNull Client client) {
        if (isMethodUri(uri)) {
            return mRpcHandler.registerServer(uri, client);
        } else {
            return enableGenericDispatching(uri, extras, client);
        }
    }

    public @NonNull UStatus disableDispatching(@NonNull UUri uri, Bundle extras, @NonNull Client client) {
        if (isMethodUri(uri)) {
            return mRpcHandler.unregisterServer(uri, client);
        } else {
            return disableGenericDispatching(uri, extras, client);
        }
    }

    static boolean shouldAutoFetch(Bundle extras) {
        return (extras == null) || !extras.getBoolean(EXTRA_BLOCK_AUTO_FETCH, false);
    }

    private @NonNull UStatus enableGenericDispatching(@NonNull UUri topic, Bundle extras, @NonNull Client client) {
        try {
            checkTopicUriValid(topic);
            mLinkedClients.linkToDispatch(topic, client);
            if (VERBOSE) {
                logStatus(Log.VERBOSE, "enableDispatching", STATUS_OK, Key.URI, stringify(topic), Key.CLIENT, client);
            }
            if (shouldAutoFetch(extras) && mSubscriptionCache.isTopicSubscribed(topic, client.getUri())) {
                dispatchToAsync(mUTwin.getMessage(topic), client);
            }
            return STATUS_OK;
        } catch (Exception e) {
            return logStatus(Log.ERROR, "enableDispatching", toStatus(e), Key.URI, stringify(topic), Key.CLIENT, client);
        }
    }

    private @NonNull UStatus disableGenericDispatching(@NonNull UUri topic, Bundle ignored, @NonNull Client client) {
        try {
            checkTopicUriValid(topic);
            mLinkedClients.unlinkFromDispatch(topic, client);
            if (VERBOSE) {
                logStatus(Log.VERBOSE, "disableDispatching", STATUS_OK, Key.URI, stringify(topic), Key.CLIENT, client);
            }
            return STATUS_OK;
        } catch (Exception e) {
            return logStatus(Log.ERROR, "disableDispatching", toStatus(e), Key.URI, stringify(topic), Key.CLIENT, client);
        }
    }

    public @NonNull UStatus dispatchFrom(@NonNull UMessage message, @NonNull Client client) {
        final UMessageType type = message.getAttributes().getType();
        return switch (type) {
            case UMESSAGE_TYPE_REQUEST -> mRpcHandler.handleRequestMessage(message, client);
            case UMESSAGE_TYPE_RESPONSE -> mRpcHandler.handleResponseMessage(message, client);
            case UMESSAGE_TYPE_PUBLISH -> handleGenericMessage(message, client);
            default -> buildStatus(UCode.UNIMPLEMENTED, "Message type '" + type + "' is not supported");
        };
    }

    @SuppressWarnings("BlockingMethodInNonBlockingContext")
    public boolean dispatchTo(UMessage message, @NonNull Client client) {
        if (message == null) {
            return false;
        }
        UStatus status = STATUS_OK;
        try {
            client.send(message);
        } catch (Exception ignored) {
            // Pause and retry
            try {
                Thread.sleep(DISPATCH_RETRY_DELAY_MS);
                client.send(message);
            } catch (Exception e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                status = toStatus(e);
            }
        }
        if (isOk(status)) {
            if (TRACE_EVENTS) {
                logStatus(Log.VERBOSE, "dispatch", status, Key.MESSAGE, stringify(message), Key.CLIENT, client);
            }
            return true;
        } else {
            logStatus(Log.WARN, "dispatch", status, Key.MESSAGE, stringify(message), Key.CLIENT, client);
            return false;
        }
    }

    private void dispatchToAsync(UMessage message, @NonNull Client client) {
        if (message != null) {
            mExecutor.execute(() -> dispatchTo(message, client));
        }
    }

    @VisibleForTesting
    void dispatch(UMessage message, @NonNull UUri sinkOrEmpty) {
        if (message == null) {
            return;
        }
        final UUri source = message.getSource();
        final Client remoteClient = mClientManager.getRemoteClient();
        final LinkedList<Client> clients = new LinkedList<>();
        final LinkedList<UUri> remoteSinks = new LinkedList<>();
        final Set<UUri> sinks = isEmpty(sinkOrEmpty) ?
                mSubscriptionCache.getSubscribers(source) : Set.of(sinkOrEmpty);
        sinks.forEach(sink -> {
            if (isRemoteUri(sink)) {
                remoteSinks.add(sink);
            } else {
                mLinkedClients.getClients(source, sink, Collectors.toCollection(() -> clients));
            }
        });
        clients.forEach(client -> dispatchTo(message, client));
        if (remoteClient != null) {
            remoteSinks.forEach(sink -> dispatchTo(addSinkIfEmpty(message, sink), remoteClient));
        }
    }

    private void dispatchAsync(UMessage message, @NonNull UUri sinkOrEmpty) {
        if (message != null) {
            mExecutor.execute(() -> dispatch(message, sinkOrEmpty));
        }
    }

    public static void checkAuthority(@NonNull UUri uri, @NonNull Client client) {
        if (client.isLocal()) {
            checkArgument(isSameClient(uri, client.getUri()), UCode.UNAUTHENTICATED,
                    "'" + stringify(uri) + "' doesn't match to '" + stringify(client.getUri()) + "'");
        } else {
            if (!isSameClient(uri, client.getUri())) { // uStreamer on behalf of remote clients
                checkArgument(isRemoteUri(uri), UCode.UNAUTHENTICATED, "URI authority is not remote");
            }
        }
    }

    private static boolean isRemoteBroadcast(@NonNull UUri source, @NonNull UUri sink) {
        return isRemoteUri(source) &&
                !source.getEntity().getName().equals(USubscription.SERVICE.getName()) &&
                sink.getEntity().getName().equals(USubscription.SERVICE.getName());
    }

    private @NonNull UStatus handleGenericMessage(@NonNull UMessage message, @NonNull Client client) {
        final UUri topic = message.getSource();
        try {
            checkAuthority(topic, client);
            checkArgument(!isExpired(message), UCode.DEADLINE_EXCEEDED, "Event expired");

            UUri sink = message.getAttributes().getSink();
            if (isRemoteBroadcast(topic, sink)) {
                message = removeSink(message);
                sink = EMPTY_URI;
            }
            if (!isEmpty(sink)) {
                dispatchAsync(message, sink);
                return STATUS_OK;
            }
            if (client.isLocal()) {
                checkArgument(client.getUri().equals(mSubscriptionCache.getPublisher(topic)), UCode.NOT_FOUND,
                        "Topic was not created by this client");
            }
            if (mUTwin.addMessage(message)) {
                dispatch(message, EMPTY_URI);
            }
            return STATUS_OK;
        } catch (Exception e) {
            return toStatus(e);
        }
    }

    @SuppressWarnings("java:S3398")
    private void handleSubscriptionChange(@NonNull Update update) {
        if (DEBUG) {
            Log.d(TAG, join(Key.EVENT, "Subscription changed", Key.SUBSCRIPTION, stringify(update)));
        }
        final UUri topic = update.getTopic();
        final UUri clientUri = update.getSubscriber().getUri();
        if (isEmpty(topic) || isEmpty(clientUri)) {
            return;
        }
        if (update.getStatus().getState() == SubscriptionStatus.State.SUBSCRIBED) {
            mSubscriptionCache.addSubscriber(topic, clientUri);
            dispatchAsync(mUTwin.getMessage(topic), clientUri);
        } else {
            mSubscriptionCache.removeSubscriber(topic, clientUri);
        }
    }

    public @NonNull ScheduledExecutorService getExecutor() {
        return mExecutor;
    }

    @VisibleForTesting
    @NonNull Set<Client> getLinkedClients(@NonNull UUri topic) {
        return mLinkedClients.getClients(topic);
    }

    protected void dump(@NonNull PrintWriter writer, String[] args) {
        args = emptyIfNull(args);
        if (args.length > 0) {
            if ("-t".equals(args[0])) {
                if (args.length > 1) {
                    dumpTopic(writer, toUri(args[1]));
                    return;
                }
            } else {
                mRpcHandler.dump(writer, args);
                return;
            }
        }
        dumpSummary(writer);
        mRpcHandler.dump(writer, args);
    }

    private void dumpSummary(@NonNull PrintWriter writer) {
        writer.println("  ========");
        final Set<Client> clients = mClientManager.getClients();
        writer.println("  There are " + mUTwin.getMessageCount() + " topic(s) with published data, " +
                clients.size() + " registered client(s)");
        clients.forEach(client -> writer.println("    " + client));

        dumpAllTopics(writer);
    }

    private void dumpAllTopics(@NonNull PrintWriter writer) {
        final Set<UUri> publishedTopics = mUTwin.getTopics();
        publishedTopics.forEach(topic -> dumpTopic(writer, topic));
        mLinkedClients.getTopics().stream()
                .filter(topic -> !publishedTopics.contains(topic))
                .forEach(topic -> dumpTopic(writer, topic));
    }

    private void dumpTopic(PrintWriter writer, UUri topic) {
        final UMessage message = mUTwin.getMessage(topic);
        final StringBuilder sb = new StringBuilder("{");
        getLinkedClients(topic).forEach(client -> {
            if (sb.length() > 1) {
                sb.append(", ");
            }
            boolean subscribed = mSubscriptionCache.isTopicSubscribed(topic, client.getUri());
            sb.append(client).append(": ").append((subscribed) ? "SUBSCRIBED" : "NOT SUBSCRIBED");
        });
        final String formattedSubscribers = sb.append("}").toString();

        writer.println("  --------");
        writer.println("    Topic: " + stringify(topic));
        writer.println("  Message: " + stringify(message));
        writer.println("  Clients: " + formattedSubscribers);
    }
}
