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
package org.eclipse.uprotocol.core.internal.handler;

import static org.eclipse.uprotocol.common.util.UStatusUtils.isOk;
import static org.eclipse.uprotocol.common.util.UStatusUtils.toStatus;
import static org.eclipse.uprotocol.core.internal.util.UMessageUtils.buildFailedResponseMessage;
import static org.eclipse.uprotocol.core.internal.util.UMessageUtils.buildResponseMessage;
import static org.eclipse.uprotocol.core.ubus.UBus.EXTRA_BLOCK_AUTO_FETCH;

import android.os.Bundle;
import android.os.IBinder;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import org.eclipse.uprotocol.core.internal.rpc.RpcExecutor;
import org.eclipse.uprotocol.core.ubus.UBus;
import org.eclipse.uprotocol.rpc.URpcListener;
import org.eclipse.uprotocol.transport.UListener;
import org.eclipse.uprotocol.v1.UAttributes;
import org.eclipse.uprotocol.v1.UEntity;
import org.eclipse.uprotocol.v1.UMessage;
import org.eclipse.uprotocol.v1.UPayload;
import org.eclipse.uprotocol.v1.UStatus;
import org.eclipse.uprotocol.v1.UUri;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

public class MessageHandler implements UListener {
    private final UBus mUBus;
    private final IBinder mClientToken;
    private final RpcExecutor mRpcExecutor;
    private final Executor mExecutor;
    private final Map<UUri, URpcListener> mRequestListeners = new ConcurrentHashMap<>();
    private final Map<UUri, Set<UListener>> mListeners = new ConcurrentHashMap<>();

    public MessageHandler(@NonNull UBus uBus, @NonNull UEntity entity, @NonNull IBinder clientToken) {
        this(uBus, entity, clientToken, Runnable::run);
    }

    public MessageHandler(@NonNull UBus uBus, @NonNull UEntity entity, @NonNull IBinder clientToken,
            @NonNull Executor executor) {
        mUBus = uBus;
        mClientToken = clientToken;
        mRpcExecutor = new RpcExecutor(uBus, entity, clientToken);
        mExecutor = executor;
    }

    public @NonNull RpcExecutor getRpcExecutor() {
        return mRpcExecutor;
    }

    public boolean registerListener(@NonNull UUri uri, @NonNull UListener listener) {
        final Set<UListener> registeredListeners = mListeners.compute(uri, (it, listeners) -> {
            if (listeners == null) {
                listeners = ConcurrentHashMap.newKeySet();
                final Bundle extras = new Bundle();
                extras.putBoolean(EXTRA_BLOCK_AUTO_FETCH, true);
                final UStatus status = mUBus.enableDispatching(uri, extras, mClientToken);
                if (!isOk(status)) {
                    return null;
                }
            }
            listeners.add(listener);
            return listeners;
        });
        return (registeredListeners != null);
    }

    public boolean unregisterListener(@NonNull UUri uri, @NonNull UListener listener) {
        final var wrapper = new Object() { boolean removed = false; };
        mListeners.computeIfPresent(uri, (it, listeners) -> {
            wrapper.removed = listeners.remove(listener);
            if (listeners.isEmpty()) {
                mUBus.disableDispatching(uri, null, mClientToken);
                return null;
            }
            return listeners;
        });
        return wrapper.removed;
    }

    public boolean registerRpcListener(@NonNull UUri uri, @NonNull URpcListener listener) {
        final URpcListener registeredListener = mRequestListeners.computeIfAbsent(uri, it -> {
            final UStatus status = mUBus.enableDispatching(uri, null, mClientToken);
            return isOk(status) ? listener : null;
        });
        return (registeredListener == listener);
    }

    public boolean unregisterRpcListener(@NonNull UUri uri, @NonNull URpcListener listener) {
        final var wrapper = new Object() { boolean removed = false; };
        mRequestListeners.computeIfPresent(uri, (it, registeredListener) -> {
            if (registeredListener != listener) {
                return registeredListener;
            }
            mUBus.disableDispatching(uri, null, mClientToken);
            wrapper.removed = true;
            return null;
        });
        return wrapper.removed;
    }

    public void unregisterAllListeners() {
        mRequestListeners.forEach(this::unregisterRpcListener);
        mListeners.forEach((uri, listeners) ->
                listeners.forEach(listener -> unregisterListener(uri, listener)));
    }

    @VisibleForTesting
    boolean isRegistered(@NonNull UUri uri, @NonNull UListener listener) {
        final Set<UListener> listeners = mListeners.get(uri);
        return listeners != null && listeners.contains(listener);
    }

    @VisibleForTesting
    boolean isRegistered(@NonNull UUri uri, @NonNull URpcListener listener) {
        return mRequestListeners.get(uri) == listener;
    }

    private @NonNull CompletableFuture<UPayload> buildResponseFuture(@NonNull UMessage requestMessage) {
        final CompletableFuture<UPayload> responseFuture = new CompletableFuture<>();
        responseFuture.whenComplete((responsePayload, exception) -> {
            final UMessage responseMessage;
            if (exception != null) {
                responseMessage = buildFailedResponseMessage(requestMessage, toStatus(exception).getCode());
            } else if (responsePayload != null) {
                responseMessage = buildResponseMessage(requestMessage, responsePayload);
            } else {
                return;
            }
            mUBus.send(responseMessage, mClientToken);
        });
        return responseFuture;
    }

    @Override
    public void onReceive(@NonNull UUri source, @NonNull UPayload payload, @NonNull UAttributes attributes) {
        onReceive(UMessage.newBuilder()
                .setSource(source)
                .setPayload(payload)
                .setAttributes(attributes)
                .build());
    }

    @Override
    public void onReceive(@NonNull UMessage message) {
        switch (message.getAttributes().getType()) {
            case UMESSAGE_TYPE_PUBLISH -> handleGenericMessage(message);
            case UMESSAGE_TYPE_REQUEST -> handleRequestMessage(message);
            case UMESSAGE_TYPE_RESPONSE -> handleResponseMessage(message);
            default -> {
                // Nothing to do
            }
        }
    }

    private void handleGenericMessage(@NonNull UMessage message) {
        final UUri uri = message.getSource();
        final Set<UListener> listeners = mListeners.get(uri);
        if (listeners != null) {
            mExecutor.execute(() -> listeners.forEach(listener -> listener.onReceive(message)));
        }
    }

    private void handleRequestMessage(@NonNull UMessage message) {
        final UUri uri = message.getAttributes().getSink();
        final URpcListener listener = mRequestListeners.get(uri);
        if (listener != null) {
            mExecutor.execute(() -> listener.onReceive(message, buildResponseFuture(message)));
        }
    }

    private void handleResponseMessage(@NonNull UMessage message) {
        mRpcExecutor.onReceive(message);
    }
}
