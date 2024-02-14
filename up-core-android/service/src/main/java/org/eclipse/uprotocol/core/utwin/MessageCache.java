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
package org.eclipse.uprotocol.core.utwin;

import static org.eclipse.uprotocol.common.util.log.Formatter.join;
import static org.eclipse.uprotocol.common.util.log.Formatter.stringify;
import static org.eclipse.uprotocol.core.internal.util.CommonUtils.emptyIfNull;
import static org.eclipse.uprotocol.core.internal.util.UMessageUtils.isExpired;
import static org.eclipse.uprotocol.core.utwin.UTwin.TAG;
import static org.eclipse.uprotocol.core.utwin.UTwin.VERBOSE;

import static java.util.Collections.unmodifiableSet;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.eclipse.uprotocol.common.util.log.Key;
import org.eclipse.uprotocol.v1.UMessage;
import org.eclipse.uprotocol.v1.UUri;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

class MessageCache {
    private final ConcurrentHashMap<UUri, UMessage> mMessages = new ConcurrentHashMap<>();

    public boolean addMessage(@NonNull UMessage message) {
        return addMessage(message, null);
    }

    public boolean addMessage(@NonNull UMessage message, Consumer<UMessage> onAdded) {
        var wrapper = new Object() { boolean added = false; };
        mMessages.compute(message.getAttributes().getSource(), (topic, oldMessage) -> {
            if (oldMessage != null && (oldMessage.getAttributes().getId().equals(message.getAttributes().getId()))) {
                return oldMessage;
            }
            if (VERBOSE) {
                Log.v(TAG, join(Key.STATE, "Added", Key.MESSAGE, stringify(message)));
            }
            if (onAdded != null) {
                onAdded.accept(message);
            }
            wrapper.added = true;
            return message;
        });
        return wrapper.added;
    }

    public boolean removeMessage(@NonNull UUri topic) {
        final UMessage oldEvent = mMessages.remove(topic);
        if (oldEvent != null) {
            if (VERBOSE) {
                Log.v(TAG, join(Key.STATE, "Removed", Key.MESSAGE, stringify(oldEvent)));
            }
            return true;
        }
        return false;
    }

    public @Nullable UMessage getMessage(@NonNull UUri topic) {
        return mMessages.computeIfPresent(topic, (key, message) -> {
            if (isExpired(message)) {
                if (VERBOSE) {
                    Log.v(TAG, join(Key.STATE, "Expired", Key.MESSAGE, stringify(message)));
                }
                return null;
            } else {
                return message;
            }
        });
    }

    public @NonNull Set<UUri> getTopics() {
        return unmodifiableSet(emptyIfNull(mMessages.keySet()));
    }

    public int size() {
        return mMessages.size();
    }

    public void clear() {
        mMessages.clear();
    }

    public boolean isEmpty() {
        return mMessages.isEmpty();
    }
}
