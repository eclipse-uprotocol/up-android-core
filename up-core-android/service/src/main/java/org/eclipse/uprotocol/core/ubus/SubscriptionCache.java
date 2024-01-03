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

import static org.eclipse.uprotocol.core.internal.util.CommonUtils.emptyIfNull;

import androidx.annotation.NonNull;

import org.eclipse.uprotocol.core.usubscription.USubscription;
import org.eclipse.uprotocol.uri.validator.UriValidator;
import org.eclipse.uprotocol.v1.UUri;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

class SubscriptionCache {
    private final Map<UUri, Set<UUri>> mSubscribersByTopic = new ConcurrentHashMap<>();
    private final Map<UUri, UUri> mPublisherByTopic = new ConcurrentHashMap<>();
    private USubscription mService;

    public void setService(USubscription service) {
        mService = service;
    }

    public @NonNull Set<UUri> getSubscribers(@NonNull UUri topic) {
        return mSubscribersByTopic.computeIfAbsent(topic, key -> {
            final USubscription service = mService;
            return (service != null) ?
                    emptyIfNull(service.getSubscribers(topic)).stream()
                            .collect(Collectors.toCollection(ConcurrentHashMap::newKeySet)) :
                    ConcurrentHashMap.newKeySet();
        });
    }

    public boolean addSubscriber(@NonNull UUri topic, @NonNull UUri clientUri) {
        return getSubscribers(topic).add(clientUri);
    }

    public boolean removeSubscriber(@NonNull UUri topic, @NonNull UUri clientUri) {
        return getSubscribers(topic).remove(clientUri);
    }

    public boolean isTopicSubscribed(@NonNull UUri topic, @NonNull UUri clientUri) {
        return getSubscribers(topic).contains(clientUri);
    }

    public @NonNull Set<UUri> getSubscribedTopics() {
        return mSubscribersByTopic.entrySet().stream()
                .filter(entry -> !entry.getValue().isEmpty())
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    public @NonNull UUri getPublisher(@NonNull UUri topic) {
        return mPublisherByTopic.computeIfAbsent(topic, key -> {
            final USubscription service = mService;
            return (service != null) ? service.getPublisher(topic) : UUri.getDefaultInstance();
        });
    }

    public boolean addTopic(@NonNull UUri topic, @NonNull UUri clientUri) {
        final UUri oldClientUri = mPublisherByTopic.put(topic, clientUri);
        return !Objects.equals(oldClientUri, clientUri);
    }

    public boolean removeTopic(@NonNull UUri topic) {
        mSubscribersByTopic.remove(topic);
        final UUri oldClientUri = mPublisherByTopic.put(topic, UUri.getDefaultInstance()); // Do not remove mapping
        return oldClientUri != null && !UriValidator.isEmpty(oldClientUri);
    }

    public boolean isTopicCreated(@NonNull UUri topic, @NonNull UUri clientUri) {
        return getPublisher(topic).equals(clientUri);
    }

    public @NonNull Set<UUri> getCreatedTopics() {
        return mPublisherByTopic.entrySet().stream()
                .filter(entry -> !UriValidator.isEmpty(entry.getValue()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    public void clear() {
        mSubscribersByTopic.clear();
        mPublisherByTopic.clear();
    }

    public boolean isEmpty() {
        return mSubscribersByTopic.isEmpty() && mPublisherByTopic.isEmpty();
    }
}
