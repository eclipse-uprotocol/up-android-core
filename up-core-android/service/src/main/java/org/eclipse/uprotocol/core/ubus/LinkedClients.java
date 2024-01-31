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

import org.eclipse.uprotocol.core.ubus.client.Client;
import org.eclipse.uprotocol.v1.UUri;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collector;

class LinkedClients {
    private final Map<UUri, Set<Client>> mClientsByTopic = new ConcurrentHashMap<>();
    private final Map<Client, Set<UUri>> mTopicsByClient = new ConcurrentHashMap<>();

    public void linkToDispatch(@NonNull UUri topic, @NonNull Client client) {
        mTopicsByClient.compute(client, (key, topics) -> {
            if (client.isReleased()) {
                return topics;
            }
            if (topics == null) {
                topics = ConcurrentHashMap.newKeySet();
            }
            if (topics.add(topic)) {
                mClientsByTopic.computeIfAbsent(topic, k -> ConcurrentHashMap.newKeySet()).add(client);
            }
            return topics;
        });
    }

    public void unlinkFromDispatch(@NonNull UUri topic, @NonNull Client client) {
        mTopicsByClient.computeIfPresent(client, (key, topics) -> {
            if (topics.remove(topic)) {
                mClientsByTopic.computeIfPresent(topic, (k, clients) -> {
                    clients.remove(client);
                    return clients.isEmpty() ? null : clients;
                });
            }
            return topics.isEmpty() ? null : topics;
        });
    }

    public void unlinkFromDispatch(@NonNull Client client) {
        mTopicsByClient.computeIfPresent(client, (key, topics) -> {
            topics.forEach(topic -> mClientsByTopic.computeIfPresent(topic, (k, clients) -> {
                clients.remove(client);
                return clients.isEmpty() ? null : clients;
            }));
            return null;
        });
    }

    public @NonNull Set<Client> getClients(@NonNull UUri topic) {
        return emptyIfNull(mClientsByTopic.get(topic));
    }

    public @NonNull Set<UUri> getTopics() {
        return mClientsByTopic.keySet();
    }

    public Collection<Client> getClients(@NonNull UUri topic, @NonNull UUri clientUri,
            @NonNull Collector<Client, ?, Collection<Client>> collector) {
        return getClients(topic).stream()
                .filter(client -> client.getUri().equals(clientUri))
                .collect(collector);
    }

    public void clear() {
        mClientsByTopic.clear();
        mTopicsByClient.clear();
    }

    public boolean isEmpty() {
        return mTopicsByClient.isEmpty();
    }
}
