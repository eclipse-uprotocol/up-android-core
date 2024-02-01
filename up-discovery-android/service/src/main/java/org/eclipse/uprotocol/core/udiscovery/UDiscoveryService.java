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
 * SPDX-FileCopyrightText: 2024 General Motors GTO LLC
 * SPDX-License-Identifier: Apache-2.0
 */

package org.eclipse.uprotocol.core.udiscovery;

import static org.eclipse.uprotocol.common.util.UStatusUtils.buildStatus;
import static org.eclipse.uprotocol.common.util.UStatusUtils.checkArgument;
import static org.eclipse.uprotocol.common.util.UStatusUtils.isOk;
import static org.eclipse.uprotocol.common.util.UStatusUtils.toStatus;
import static org.eclipse.uprotocol.common.util.log.Formatter.join;
import static org.eclipse.uprotocol.common.util.log.Formatter.quote;
import static org.eclipse.uprotocol.common.util.log.Formatter.stringify;
import static org.eclipse.uprotocol.common.util.log.Formatter.tag;
import static org.eclipse.uprotocol.core.udiscovery.common.Constants.TOPIC_NODE_NOTIFICATION;
import static org.eclipse.uprotocol.core.udiscovery.internal.Utils.logStatus;
import static org.eclipse.uprotocol.core.udiscovery.v3.UDiscovery.METHOD_ADD_NODES;
import static org.eclipse.uprotocol.core.udiscovery.v3.UDiscovery.METHOD_DELETE_NODES;
import static org.eclipse.uprotocol.core.udiscovery.v3.UDiscovery.METHOD_FIND_NODES;
import static org.eclipse.uprotocol.core.udiscovery.v3.UDiscovery.METHOD_FIND_NODE_PROPERTIES;
import static org.eclipse.uprotocol.core.udiscovery.v3.UDiscovery.METHOD_LOOKUP_URI;
import static org.eclipse.uprotocol.core.udiscovery.v3.UDiscovery.METHOD_REGISTER_FOR_NOTIFICATIONS;
import static org.eclipse.uprotocol.core.udiscovery.v3.UDiscovery.METHOD_UNREGISTER_FOR_NOTIFICATIONS;
import static org.eclipse.uprotocol.core.udiscovery.v3.UDiscovery.METHOD_UPDATE_NODE;
import static org.eclipse.uprotocol.core.udiscovery.v3.UDiscovery.METHOD_UPDATE_PROPERTY;
import static org.eclipse.uprotocol.core.udiscovery.v3.UDiscovery.SERVICE;
import static org.eclipse.uprotocol.transport.builder.UPayloadBuilder.packToAny;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import org.eclipse.uprotocol.UPClient;
import org.eclipse.uprotocol.common.UStatusException;
import org.eclipse.uprotocol.common.util.log.Key;
import org.eclipse.uprotocol.core.udiscovery.db.DiscoveryManager;
import org.eclipse.uprotocol.core.udiscovery.v3.FindNodePropertiesResponse;
import org.eclipse.uprotocol.core.udiscovery.v3.FindNodesResponse;
import org.eclipse.uprotocol.core.udiscovery.v3.LookupUriResponse;
import org.eclipse.uprotocol.rpc.CallOptions;
import org.eclipse.uprotocol.rpc.URpcListener;
import org.eclipse.uprotocol.uri.builder.UResourceBuilder;
import org.eclipse.uprotocol.v1.UCode;
import org.eclipse.uprotocol.v1.UMessage;
import org.eclipse.uprotocol.v1.UPayload;
import org.eclipse.uprotocol.v1.UStatus;
import org.eclipse.uprotocol.v1.UUri;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

/**
 * UDiscoveryService is a class that extends the Android Service class and implements the UPClient.ServiceLifecycleListener interface.
 * This service is responsible for handling various methods related to the UProtocol discovery process.
 * It manages the lifecycle of the UPClient and handles RPC requests.
 * <p>
 * The service is also responsible for managing the database initialization, registering and unregistering methods,
 * and handling request events. It also manages the lifecycle of the UPClient and handles the connection status.
 * <p>
 * The service is started in the foreground to ensure it keeps running even when the app is not in the foreground.
 * <p>
 * This class is part of the UProtocol core discovery package.
 */
@SuppressWarnings({"java:S1200", "java:S3008", "java:S1134"})
public class UDiscoveryService extends Service implements UPClient.ServiceLifecycleListener {
    public static final String TAG = tag(SERVICE.getName());
    private static final CharSequence NOTIFICATION_CHANNEL_NAME = TAG;
    private static final int NOTIFICATION_ID = 1;
    private static final String DATABASE_NOT_INITIALIZED = "Database not initialized";
    private static final String NOTIFICATION_CHANNEL_ID = TAG;
    protected static boolean VERBOSE = Log.isLoggable(TAG, Log.VERBOSE);
    protected static boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);
    private final ScheduledExecutorService mExecutor = Executors.newScheduledThreadPool(1);
    private final Map<UUri, BiConsumer<UMessage, CompletableFuture<UPayload>>> mMethodHandlers = new HashMap<>();
    private final URpcListener mRequestEventListener = this::handleRequestEvent;
    private final Binder mBinder = new Binder() {
    };
    private final AtomicBoolean mDatabaseInitialized = new AtomicBoolean(false);
    private RPCHandler mRpcHandler;
    private ResourceLoader mResourceLoader;
    private UPClient mUpClient;

    // This constructor is for service initialization
    // without this constructor service won't start.
    @SuppressWarnings("unused")
    public UDiscoveryService() {
    }

    @VisibleForTesting
    UDiscoveryService(Context context, RPCHandler rpcHandler, UPClient upClient,
                      ResourceLoader resourceLoader) {
        mRpcHandler = rpcHandler;
        mUpClient = upClient;
        mResourceLoader = resourceLoader;
        upClientInit().join();
    }

    @Nullable
    @Override
    public IBinder onBind(@NonNull Intent intent) {
        if (DEBUG) {
            Log.d(TAG, join(Key.EVENT, "onBind"));
        }
        return mBinder;
    }

    @SuppressWarnings("ConstantConditions")
    @Override
    public void onCreate() {
        super.onCreate();
        if (DEBUG) {
            Log.d(TAG, join(Key.EVENT, "onCreate - Starting uDiscovery"));
        }

        mUpClient = UPClient.create(getApplicationContext(), SERVICE, mExecutor, this);
        ObserverManager observerManager = new ObserverManager(this);
        Notifier notifier = new Notifier(observerManager, mUpClient);
        DiscoveryManager discoveryManager = new DiscoveryManager(notifier);
        AssetManager assetManager = new AssetManager();
        mResourceLoader = new ResourceLoader(this, assetManager, discoveryManager);
        mRpcHandler = new RPCHandler(this, assetManager, discoveryManager, observerManager);
        upClientInit();
        startForegroundService();
    }

    private void startForegroundService() {
        if (DEBUG) {
            Log.d(TAG, join(Key.EVENT, "startForegroundService"));
        }
        final NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID,
                NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_NONE);
        final NotificationManager manager =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        manager.createNotificationChannel(channel);
        android.app.Notification.Builder notificationBuilder = new android.app.Notification.Builder(this,
                NOTIFICATION_CHANNEL_ID);
        android.app.Notification notification = notificationBuilder.setOngoing(true)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(getApplicationContext().getResources().getString(
                        R.string.notification_title))
                .setCategory(android.app.Notification.CATEGORY_SERVICE)
                .build();
        startForeground(NOTIFICATION_ID, notification);
    }

    private synchronized CompletableFuture<Void> upClientInit() {
        if (DEBUG) {
            Log.d(TAG, join(Key.EVENT, "upClientInit"));
        }
        return (CompletableFuture<Void>) mUpClient.connect()
                .thenCompose(status -> {
                    Log.i(TAG, join(Key.MESSAGE, "upClient.isConnected()", Key.CONNECTION, mUpClient.isConnected()));
                    return isOk(status) ?
                            CompletableFuture.completedFuture(status) :
                            CompletableFuture.failedFuture(new UStatusException(status));
                }).thenRunAsync(() -> {
                    ResourceLoader.InitLDSCode code = mResourceLoader.initializeLDS();
                    boolean isInitialized = (code != ResourceLoader.InitLDSCode.FAILURE);
                    mDatabaseInitialized.set(isInitialized);
                    if (mUpClient.isConnected()) {
                        registerAllMethods();
                        createNotificationTopic();
                    }
                });
    }

    private void registerAllMethods() {
        if (DEBUG) {
            Log.d(TAG, join(Key.EVENT, "registerAllMethods, upClient Connect", Key.STATUS, mUpClient.isConnected()));
        }
        CompletableFuture.allOf(
                        registerMethod(METHOD_LOOKUP_URI, this::executeLookupUri),
                        registerMethod(METHOD_FIND_NODES, this::executeFindNodes),
                        registerMethod(METHOD_UPDATE_NODE, this::executeUpdateNode),
                        registerMethod(METHOD_FIND_NODE_PROPERTIES, this::executeFindNodesProperty),
                        registerMethod(METHOD_ADD_NODES, this::executeAddNodes),
                        registerMethod(METHOD_DELETE_NODES, this::executeDeleteNodes),
                        registerMethod(METHOD_UPDATE_PROPERTY, this::executeUpdateProperty),
                        registerMethod(METHOD_REGISTER_FOR_NOTIFICATIONS, this::executeRegisterNotification),
                        registerMethod(METHOD_UNREGISTER_FOR_NOTIFICATIONS, this::executeUnregisterNotification))
                .exceptionally(e -> {
                    logStatus(TAG, "registerAllMethods", toStatus(e));
                    return null;
                });
    }

    private void executeLookupUri(@NonNull UMessage requestEvent, @NonNull CompletableFuture<UPayload> responseFuture) {
        if (DEBUG) {
            Log.d(TAG, join(Key.EVENT, "executeLookupUri"));
        }
        try {
            checkArgument(mDatabaseInitialized.get(), UCode.FAILED_PRECONDITION, DATABASE_NOT_INITIALIZED);
            if (DEBUG) {
                Log.d(TAG, join(Key.EVENT, "LookupUri", Key.REQUEST, stringify(requestEvent)));
            }
            responseFuture.complete(mRpcHandler.processLookupUriFromLDS(requestEvent));
        } catch (Exception e) {
            UStatus status = logStatus(TAG, "executeLookupUri", toStatus(e));
            LookupUriResponse response = LookupUriResponse.newBuilder().setStatus(status).build();
            responseFuture.complete(packToAny(response));
        }
    }

    private void executeFindNodes(@NonNull UMessage requestEvent, @NonNull CompletableFuture<UPayload> responseFuture) {
        if (DEBUG) {
            Log.d(TAG, join(Key.EVENT, "executeFindNodes"));
        }
        try {
            checkArgument(mDatabaseInitialized.get(), UCode.FAILED_PRECONDITION, DATABASE_NOT_INITIALIZED);
            if (DEBUG) {
                Log.d(TAG, join(Key.EVENT, "FindNodes", Key.REQUEST, stringify(requestEvent)));
            }
            responseFuture.complete(mRpcHandler.processFindNodesFromLDS(requestEvent));
        } catch (Exception e) {
            UStatus status = logStatus(TAG, "executeFindNodes", toStatus(e));
            FindNodesResponse response = FindNodesResponse.newBuilder().setStatus(status).build();
            responseFuture.complete(packToAny(response));
        }
    }

    private void executeUpdateNode(@NonNull UMessage requestEvent, @NonNull CompletableFuture<UPayload> future) {
        if (DEBUG) {
            Log.d(TAG, join(Key.EVENT, "executeUpdateNode"));
        }
        try {
            checkArgument(mDatabaseInitialized.get(), UCode.FAILED_PRECONDITION, DATABASE_NOT_INITIALIZED);
            if (DEBUG) {
                Log.d(TAG, join(Key.EVENT, "received for UpdateNode",
                        Key.REQUEST, stringify(requestEvent)));
            }
            future.complete(mRpcHandler.processLDSUpdateNode(requestEvent));
        } catch (Exception e) {
            UStatus status = logStatus(TAG, "executeUpdateNode", toStatus(e));
            future.complete(packToAny(status));
        }
    }

    private void executeFindNodesProperty(@NonNull UMessage requestEvent, @NonNull CompletableFuture<UPayload> future) {
        if (DEBUG) {
            Log.d(TAG, join(Key.EVENT, "executeFindNodesProperty"));
        }
        try {
            checkArgument(mDatabaseInitialized.get(), UCode.FAILED_PRECONDITION, DATABASE_NOT_INITIALIZED);
            future.complete(mRpcHandler.processFindNodeProperties(requestEvent));
        } catch (Exception e) {
            UStatus status = logStatus(TAG, "executeFindNodesProperty", toStatus(e));
            FindNodePropertiesResponse response = FindNodePropertiesResponse.newBuilder().setStatus(status).build();
            future.complete(packToAny(response));
        }
    }

    private void executeAddNodes(@NonNull UMessage requestEvent, @NonNull CompletableFuture<UPayload> future) {
        if (DEBUG) {
            Log.d(TAG, join(Key.EVENT, "executeAddNodes"));
        }
        try {
            checkArgument(mDatabaseInitialized.get(), UCode.FAILED_PRECONDITION, DATABASE_NOT_INITIALIZED);
            if (DEBUG) {
                Log.d(TAG, join(Key.EVENT, "received for AddNodes", Key.REQUEST, stringify(requestEvent)));
            }
            future.complete(mRpcHandler.processAddNodesLDS(requestEvent));
        } catch (Exception e) {
            UStatus status = logStatus(TAG, "executeAddNodes", toStatus(e));
            future.complete(packToAny(status));
        }
    }

    private void executeDeleteNodes(@NonNull UMessage requestEvent, @NonNull CompletableFuture<UPayload> future) {
        if (DEBUG) {
            Log.d(TAG, join(Key.EVENT, "executeDeleteNodes"));
        }
        try {
            checkArgument(mDatabaseInitialized.get(), UCode.FAILED_PRECONDITION, DATABASE_NOT_INITIALIZED);
            Log.i(TAG, join(Key.EVENT, "received for DeleteNodes", Key.REQUEST, stringify(requestEvent)));
            future.complete(mRpcHandler.processDeleteNodes(requestEvent));
        } catch (Exception e) {
            UStatus status = logStatus(TAG, "executeDeleteNodes", toStatus(e));
            future.complete(packToAny(status));
        }
    }

    private void executeUpdateProperty(@NonNull UMessage requestEvent, @NonNull CompletableFuture<UPayload> future) {
        if (DEBUG) {
            Log.d(TAG, join(Key.EVENT, "executeUpdateProperty"));
        }
        try {
            checkArgument(mDatabaseInitialized.get(), UCode.FAILED_PRECONDITION, DATABASE_NOT_INITIALIZED);
            future.complete(mRpcHandler.processLDSUpdateProperty(requestEvent));
        } catch (Exception e) {
            UStatus status = logStatus(TAG, "executeUpdateProperty", toStatus(e));
            future.complete(packToAny(status));
        }
    }

    private void executeRegisterNotification(@NonNull UMessage requestEvent,
                                             @NonNull CompletableFuture<UPayload> future) {
        if (DEBUG) {
            Log.d(TAG, join(Key.EVENT, "executeRegisterNotification"));
        }
        try {
            checkArgument(mDatabaseInitialized.get(), UCode.FAILED_PRECONDITION, DATABASE_NOT_INITIALIZED);
            Log.i(TAG, join(Key.EVENT, "received for Register Notification", Key.REQUEST, stringify(requestEvent)));
            UPayload uPayload = mRpcHandler.processNotificationRegistration(requestEvent, METHOD_REGISTER_FOR_NOTIFICATIONS);
            future.complete(uPayload);
        } catch (Exception e) {
            UStatus status = logStatus(TAG, "executeRegisterNotification", toStatus(e));
            future.complete(packToAny(status));
        }
    }

    private void executeUnregisterNotification(@NonNull UMessage requestEvent,
                                               @NonNull CompletableFuture<UPayload> future) {
        if (DEBUG) {
            Log.d(TAG, join(Key.EVENT, "executeUnregisterNotification"));
        }
        try {
            checkArgument(mDatabaseInitialized.get(), UCode.FAILED_PRECONDITION, DATABASE_NOT_INITIALIZED);
            Log.i(TAG, join(Key.EVENT, "received for Unregister Notification", Key.REQUEST, stringify(requestEvent)));
            UPayload uPayload = mRpcHandler.processNotificationRegistration(requestEvent, METHOD_UNREGISTER_FOR_NOTIFICATIONS);
            future.complete(uPayload);
        } catch (Exception e) {
            UStatus status = logStatus(TAG, "executeUnregisterNotification", toStatus(e));
            future.complete(packToAny(status));
        }
    }

    private CompletableFuture<UStatus> registerMethod(@NonNull String methodName,
                                                      @NonNull BiConsumer<UMessage, CompletableFuture<UPayload>> handler) {
        if (DEBUG) {
            Log.d(TAG, join(Key.EVENT, "registerMethod"));
        }
        final UUri methodUri = UUri.newBuilder().setEntity(SERVICE).
                setResource(UResourceBuilder.forRpcRequest(methodName)).build();
        return CompletableFuture.supplyAsync(() -> {
            final UStatus status = mUpClient.registerRpcListener(methodUri, mRequestEventListener);
            logStatus(TAG, "Register listener for '" + methodUri + "'", status);
            if (isOk(status)) {
                mMethodHandlers.put(methodUri, handler);
            } else {
                throw new UStatusException(UCode.INVALID_ARGUMENT, "URI not implemented or invalid argument");
            }
            return status;
        });
    }

    private CompletableFuture<UStatus> unregisterMethod(@NonNull String methodName) {
        if (DEBUG) {
            Log.d(TAG, join(Key.EVENT, "unregisterMethod"));
        }
        final UUri methodUri = UUri.newBuilder().setEntity(SERVICE).
                setResource(UResourceBuilder.forRpcRequest(methodName)).build();
        return CompletableFuture.supplyAsync(() -> {
            final UStatus status = mUpClient.unregisterRpcListener(methodUri, mRequestEventListener);
            logStatus(TAG, "Unregister listener for '" + methodUri + "'", status);
            mMethodHandlers.remove(methodUri);
            if (!isOk(status)) {
                throw new UStatusException(status.getCode(), status.getMessage());
            }
            return status;
        });
    }

    @Override
    public void onDestroy() {
        if (DEBUG) {
            Log.d(TAG, join(Key.EVENT, "onDestroy"));
        }
        CompletableFuture.allOf(
                        unregisterMethod(METHOD_LOOKUP_URI),
                        unregisterMethod(METHOD_FIND_NODES),
                        unregisterMethod(METHOD_UPDATE_NODE),
                        unregisterMethod(METHOD_FIND_NODE_PROPERTIES),
                        unregisterMethod(METHOD_ADD_NODES),
                        unregisterMethod(METHOD_DELETE_NODES),
                        unregisterMethod(METHOD_UPDATE_PROPERTY),
                        unregisterMethod(METHOD_REGISTER_FOR_NOTIFICATIONS),
                        unregisterMethod(METHOD_UNREGISTER_FOR_NOTIFICATIONS))
                .exceptionally(e -> {
                    logStatus(TAG, "onDestroy", toStatus(e));
                    return null;
                })
                .thenCompose(it -> mUpClient.disconnect())
                .whenComplete((status, exception) -> logStatus(TAG, "upClient disconnect", status));
        mRpcHandler.shutdown();
        super.onDestroy();
    }

    private void handleRequestEvent(@NonNull UMessage requestEvent, @NonNull CompletableFuture<UPayload> future) {
        if (DEBUG) {
            Log.d(TAG, join(Key.EVENT, "handleRequestEvent"));
        }
        final UUri uUri = requestEvent.getAttributes().getSink();
        final boolean isSinkAvailable = requestEvent.getAttributes().hasSink();
        if (isSinkAvailable) {
            final BiConsumer<UMessage, CompletableFuture<UPayload>> handler = mMethodHandlers.get(uUri);
            if (handler == null) {
                UStatus sts = buildStatus(UCode.INVALID_ARGUMENT, "unregistered method " + uUri);
                future.completeExceptionally(new UStatusException(sts.getCode(), sts.getMessage()));
            } else {
                handler.accept(requestEvent, future);
            }
        }
    }

    private void createNotificationTopic() {
        if (DEBUG) {
            Log.d(TAG, join(Key.REQUEST, "CreateTopic", Key.URI, quote(TOPIC_NODE_NOTIFICATION.toString())));
        }
        mUpClient.invokeMethod(TOPIC_NODE_NOTIFICATION, UPayload.getDefaultInstance(), CallOptions.DEFAULT)
                .exceptionally(e -> {
                    Log.e(TAG, join("registerAllMethods", toStatus(e)));
                    return null;
                }).thenAccept(status -> Log.i(TAG, join("createNotificationTopic", status)));
    }

    @Override
    public void onLifecycleChanged(@NonNull UPClient upClient, boolean ready) {
        if (DEBUG) {
            Log.d(TAG, join(Key.EVENT, "onLifecycleChanged"));
        }
        if (ready) {
            Log.i(TAG, join(Key.EVENT, "upClient is connected"));
        } else {
            Log.i(TAG, join(Key.EVENT, "upClient is disconnected"));
        }
    }
}
