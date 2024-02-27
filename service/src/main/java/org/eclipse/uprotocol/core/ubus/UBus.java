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

import static org.eclipse.uprotocol.common.util.UStatusUtils.isOk;
import static org.eclipse.uprotocol.common.util.UStatusUtils.toStatus;
import static org.eclipse.uprotocol.common.util.log.Formatter.status;
import static org.eclipse.uprotocol.common.util.log.Formatter.stringify;
import static org.eclipse.uprotocol.common.util.log.Formatter.tag;
import static org.eclipse.uprotocol.core.internal.util.UMessageUtils.checkMessageValid;
import static org.eclipse.uprotocol.core.internal.util.log.FormatterExt.stringify;
import static org.eclipse.uprotocol.core.ubus.UBus.Component.TAG;
import static org.eclipse.uprotocol.core.ubus.UBus.Component.TRACE_EVENTS;
import static org.eclipse.uprotocol.core.ubus.UBus.Component.logStatus;

import static java.lang.String.join;
import static java.util.Collections.emptyList;
import static java.util.Optional.ofNullable;

import android.content.Context;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import org.eclipse.uprotocol.common.util.log.Key;
import org.eclipse.uprotocol.core.UCore;
import org.eclipse.uprotocol.core.internal.handler.MessageHandler;
import org.eclipse.uprotocol.core.ubus.client.Client;
import org.eclipse.uprotocol.core.ubus.client.ClientManager;
import org.eclipse.uprotocol.transport.UListener;
import org.eclipse.uprotocol.v1.UAuthority;
import org.eclipse.uprotocol.v1.UEntity;
import org.eclipse.uprotocol.v1.UMessage;
import org.eclipse.uprotocol.v1.UStatus;
import org.eclipse.uprotocol.v1.UUri;

import java.io.PrintWriter;
import java.util.List;

@SuppressWarnings("java:S3008")
public class UBus extends UCore.Component {
    public static final UEntity ENTITY = UEntity.newBuilder()
            .setName("core.ubus")
            .setVersionMajor(1)
            .build();

    private final IBinder mClientToken = new Binder();
    private final Context mContext;
    private final ClientManager mClientManager;
    private final MessageHandler mMessageHandler;
    private final Dispatcher mDispatcher;
    private final Components mComponents;

    public abstract static class Component {
        protected static final String TAG = tag(ENTITY.getName());
        protected static boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);
        protected static boolean VERBOSE = Log.isLoggable(TAG, Log.VERBOSE);
        protected static boolean TRACE_EVENTS = Log.isLoggable(tag(TAG, "Events"), Log.VERBOSE);

        protected void init(@NonNull Components components) {}
        protected void startup() {}
        protected void shutdown() {}
        protected void clearCache() {}

        protected static @NonNull UStatus logStatus(int priority, @NonNull String method, @NonNull UStatus status,
                Object... args) {
            Log.println(priority, TAG, status(method, status, args));
            return status;
        }

        protected static @NonNull UStatus logStatus(int priority, @NonNull String tag, @NonNull String method,
                @NonNull UStatus status, Object... args) {
            Log.println(priority, tag, status(method, status, args));
            return status;
        }
    }

    public class Components {
        private final List<Component> mDependentComponents;
        private UCore mUCore;

        Components() {
            mDependentComponents = List.of(mClientManager, mDispatcher);
        }

        public UCore getUCore() {
            return mUCore;
        }

        public @NonNull ClientManager getClientManager() {
            return mClientManager;
        }

        public @NonNull MessageHandler getHandler() {
            return mMessageHandler;
        }

        public @NonNull Dispatcher getDispatcher() {
            return mDispatcher;
        }

        void init(@NonNull UCore uCore) {
            mUCore = uCore;
            mDependentComponents.forEach(component -> component.init(this));
        }

        void startup() {
            mDependentComponents.forEach(Component::startup);
        }

        void shutdown() {
            mDependentComponents.forEach(Component::shutdown);
        }

        void clearCache() {
            mDependentComponents.forEach(Component::clearCache);
        }
    }

    public UBus(@NonNull Context context) {
        this(context, null, null);
    }

    @VisibleForTesting
    public UBus(@NonNull Context context, ClientManager clientManager, Dispatcher dispatcher) {
        mContext = context;
        mMessageHandler = new MessageHandler(this, ENTITY, mClientToken);
        mClientManager = ofNullable(clientManager).orElseGet(() -> new ClientManager(context));
        mDispatcher = ofNullable(dispatcher).orElseGet(Dispatcher::new);
        mComponents = new Components();
    }

    @Override
    protected void init(@NonNull UCore uCore) {
        Log.i(TAG, join(Key.EVENT, "Service init"));
        mComponents.init(uCore);
        registerClient(ENTITY, mClientToken, mMessageHandler);
    }

    @Override
    protected void startup() {
        Log.i(TAG, join(Key.EVENT, "Service start"));
        mComponents.startup();
    }

    @Override
    protected void shutdown() {
        Log.i(TAG, join(Key.EVENT, "Service shutdown"));
        mComponents.shutdown();
    }

    @Override
    protected void clearCache() {
        Log.w(TAG, join(Key.EVENT, "Clear cache"));
        mComponents.clearCache();
    }

    @VisibleForTesting
    @NonNull List<Component> getComponents() {
        return mComponents.mDependentComponents;
    }

    public @NonNull UAuthority getDeviceAuthority() {
        // TODO: Get VIN...
        return UAuthority.getDefaultInstance();
    }

    public @NonNull UStatus registerClient(@NonNull UEntity entity, @NonNull IBinder clientToken,
            @NonNull UListener listener) {
        return mClientManager.registerClient(mContext.getPackageName(), entity, clientToken, listener);
    }

    public @NonNull <T> UStatus registerClient(@NonNull String packageName, @NonNull UEntity entity,
            @NonNull IBinder clientToken, @NonNull T listener) {
        // TODO: PELE - Remove this logging eventually
        Log.d(TAG, "ParcelableUEntity: " + entity);

        return mClientManager.registerClient(packageName, entity, clientToken, listener);
    }

    public @NonNull UStatus unregisterClient(@NonNull IBinder clientToken) {
        return mClientManager.unregisterClient(clientToken);
    }

    public @NonNull UStatus send(@NonNull UMessage message, @NonNull IBinder clientToken) {
        try {
            checkMessageValid(message);
            final Client client = mClientManager.getClientOrThrow(clientToken);
            final UStatus status = mDispatcher.dispatchFrom(message, client);
            if (!isOk(status)) {
                logStatus(Log.ERROR, "send", status, Key.MESSAGE, stringify(message), Key.CLIENT, client);
            } else if (TRACE_EVENTS) {
                logStatus(Log.VERBOSE, "send", status, Key.MESSAGE, stringify(message), Key.CLIENT, client);
            }
            return status;
        } catch (Exception e) {
            return logStatus(Log.ERROR, "send", toStatus(e), Key.MESSAGE, stringify(message), Key.TOKEN, stringify(clientToken));
        }
    }

    public @NonNull List<UMessage> pull(@NonNull UUri uri, int count, int ignored, @NonNull IBinder clientToken) {
        try {
            return mDispatcher.pull(uri, count, mClientManager.getClientOrThrow(clientToken));
        } catch (Exception e) {
            logStatus(Log.ERROR, "pull", toStatus(e), Key.URI, stringify(uri), Key.TOKEN, stringify(clientToken));
            return emptyList();
        }
    }

    public @NonNull UStatus enableDispatching(@NonNull UUri uri, int flags, @NonNull IBinder clientToken) {
        try {
            return mDispatcher.enableDispatching(uri, flags, mClientManager.getClientOrThrow(clientToken));
        } catch (Exception e) {
            return toStatus(e);
        }
    }

    public @NonNull UStatus disableDispatching(@NonNull UUri uri, int flags, @NonNull IBinder clientToken) {
        try {
            return mDispatcher.disableDispatching(uri, flags, mClientManager.getClientOrThrow(clientToken));
        } catch (Exception e) {
            return toStatus(e);
        }
    }

    public boolean isTopicCreated(@NonNull UUri topic, @NonNull UUri clientUri) {
        return mDispatcher.getSubscriptionCache().isTopicCreated(topic, clientUri);
    }

    @Override
    protected void dump(@NonNull PrintWriter writer, String[] args) {
        mDispatcher.dump(writer, args);
    }
}
