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
import static org.eclipse.uprotocol.core.ubus.UBusManager.FLAG_BLOCK_AUTO_FETCH;
import static org.eclipse.uprotocol.uri.validator.UriValidator.isRpcMethod;

import android.os.IBinder;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import org.eclipse.uprotocol.core.internal.rpc.RpcExecutor;
import org.eclipse.uprotocol.core.ubus.UBus;
import org.eclipse.uprotocol.transport.UListener;
import org.eclipse.uprotocol.v1.UEntity;
import org.eclipse.uprotocol.v1.UMessage;
import org.eclipse.uprotocol.v1.UStatus;
import org.eclipse.uprotocol.v1.UUri;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

public class MessageHandler implements UListener {
    private final UBus mUBus;
    private final IBinder mClientToken;
    private final RpcExecutor mRpcExecutor;
    private final Executor mExecutor;
    private final Map<UUri, Set<UListener>> mGenericListeners = new ConcurrentHashMap<>();
    private final Map<UUri, UListener> mRequestListeners = new ConcurrentHashMap<>();

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
        return isRpcMethod(uri) ? registerRequestListener(uri, listener) : registerGenericListener(uri, listener);
    }

    public boolean unregisterListener(@NonNull UUri uri, @NonNull UListener listener) {
        return isRpcMethod(uri) ? unregisterRequestListener(uri, listener) : unregisterGenericListener(uri, listener);
    }

    public void unregisterAllListeners() {
        mGenericListeners.forEach((uri, listeners) ->
                listeners.forEach(listener -> unregisterGenericListener(uri, listener)));
        mRequestListeners.forEach(this::unregisterRequestListener);
    }

    private boolean registerGenericListener(@NonNull UUri uri, @NonNull UListener listener) {
        final Set<UListener> registeredListeners = mGenericListeners.compute(uri, (it, listeners) -> {
            if (listeners == null) {
                listeners = ConcurrentHashMap.newKeySet();
                final UStatus status = mUBus.enableDispatching(uri, FLAG_BLOCK_AUTO_FETCH, mClientToken);
                if (!isOk(status)) {
                    return null;
                }
            }
            listeners.add(listener);
            return listeners;
        });
        return (registeredListeners != null);
    }

    private boolean unregisterGenericListener(@NonNull UUri uri, @NonNull UListener listener) {
        final var wrapper = new Object() { boolean removed = false; };
        mGenericListeners.computeIfPresent(uri, (it, listeners) -> {
            wrapper.removed = listeners.remove(listener);
            if (listeners.isEmpty()) {
                mUBus.disableDispatching(uri, 0, mClientToken);
                return null;
            }
            return listeners;
        });
        return wrapper.removed;
    }

    private boolean registerRequestListener(@NonNull UUri uri, @NonNull UListener listener) {
        final UListener registeredListener = mRequestListeners.computeIfAbsent(uri, it -> {
            final UStatus status = mUBus.enableDispatching(uri, 0, mClientToken);
            return isOk(status) ? listener : null;
        });
        return (registeredListener == listener);
    }

    private boolean unregisterRequestListener(@NonNull UUri uri, @NonNull UListener listener) {
        final var wrapper = new Object() { boolean removed = false; };
        mRequestListeners.computeIfPresent(uri, (it, registeredListener) -> {
            if (registeredListener != listener) {
                return registeredListener;
            }
            mUBus.disableDispatching(uri, 0, mClientToken);
            wrapper.removed = true;
            return null;
        });
        return wrapper.removed;
    }

    @VisibleForTesting
    boolean isRegistered(@NonNull UUri uri, @NonNull UListener listener) {
        if (isRpcMethod(uri)) {
            return mRequestListeners.get(uri) == listener;
        } else {
            final Set<UListener> listeners = mGenericListeners.get(uri);
            return listeners != null && listeners.contains(listener);
        }
    }

    @Override
    public void onReceive(@NonNull UMessage message) {
        switch (message.getAttributes().getType()) {
            case UMESSAGE_TYPE_PUBLISH, UMESSAGE_TYPE_NOTIFICATION -> handleGenericMessage(message);
            case UMESSAGE_TYPE_REQUEST -> handleRequestMessage(message);
            case UMESSAGE_TYPE_RESPONSE -> handleResponseMessage(message);
            default -> { /* Nothing to do */ }
        }
    }

    private void handleGenericMessage(@NonNull UMessage message) {
        final UUri uri = message.getAttributes().getSource();
        final Set<UListener> listeners = mGenericListeners.get(uri);
        if (listeners != null) {
            mExecutor.execute(() -> listeners.forEach(listener -> listener.onReceive(message)));
        }
    }

    private void handleRequestMessage(@NonNull UMessage message) {
        final UUri uri = message.getAttributes().getSink();
        final UListener listener = mRequestListeners.get(uri);
        if (listener != null) {
            mExecutor.execute(() -> listener.onReceive(message));
        }
    }

    private void handleResponseMessage(@NonNull UMessage message) {
        mRpcExecutor.onReceive(message);
    }
}
