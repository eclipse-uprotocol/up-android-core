/*
 * Copyright (c) 2023 General Motors GTO LLC
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
package org.eclipse.uprotocol.example.service;

import static org.eclipse.uprotocol.common.util.UStatusUtils.STATUS_OK;
import static org.eclipse.uprotocol.common.util.UStatusUtils.checkArgument;
import static org.eclipse.uprotocol.common.util.UStatusUtils.isOk;
import static org.eclipse.uprotocol.common.util.UStatusUtils.toStatus;
import static org.eclipse.uprotocol.common.util.log.Formatter.join;
import static org.eclipse.uprotocol.common.util.log.Formatter.status;
import static org.eclipse.uprotocol.common.util.log.Formatter.stringify;
import static org.eclipse.uprotocol.transport.builder.UPayloadBuilder.packToAny;
import static org.eclipse.uprotocol.transport.builder.UPayloadBuilder.unpack;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.eclipse.uprotocol.UPClient;
import org.eclipse.uprotocol.common.UStatusException;
import org.eclipse.uprotocol.common.util.log.Key;
import org.eclipse.uprotocol.core.usubscription.v3.CreateTopicRequest;
import org.eclipse.uprotocol.core.usubscription.v3.USubscription;
import org.eclipse.uprotocol.example.v1.Door;
import org.eclipse.uprotocol.example.v1.DoorCommand;
import org.eclipse.uprotocol.example.v1.Example;
import org.eclipse.uprotocol.rpc.URpcListener;
import org.eclipse.uprotocol.transport.builder.UAttributesBuilder;
import org.eclipse.uprotocol.uri.factory.UResourceBuilder;
import org.eclipse.uprotocol.v1.UCode;
import org.eclipse.uprotocol.v1.UMessage;
import org.eclipse.uprotocol.v1.UPayload;
import org.eclipse.uprotocol.v1.UPriority;
import org.eclipse.uprotocol.v1.UResource;
import org.eclipse.uprotocol.v1.UStatus;
import org.eclipse.uprotocol.v1.UUri;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;

@SuppressWarnings("SameParameterValue")
public class ExampleService extends Service {
    private static final String TAG = Example.SERVICE.getName();
    private static final UUri SERVICE_URI = UUri.newBuilder()
            .setEntity(Example.SERVICE)
            .build();
    private static final UResource DOOR_FRONT_LEFT = UResource.newBuilder()
            .setName("doors")
            .setInstance("front_left")
            .setMessage("Doors")
            .build();
    private static final UResource DOOR_FRONT_RIGHT = UResource.newBuilder()
            .setName("doors")
            .setInstance("front_right")
            .setMessage("Doors")
            .build();
    private static final Map<String, UUri> sDoorTopics = new HashMap<>();
    static {
        List.of(DOOR_FRONT_LEFT, DOOR_FRONT_RIGHT)
                .forEach(resource -> sDoorTopics.put(
                        resource.getInstance(), UUri.newBuilder(SERVICE_URI)
                                .setResource(resource)
                                .build()));
    }

    private static final Map<String, UUri> sMethodUris = new HashMap<>();
    static {
        List.of(Example.METHOD_EXECUTE_DOOR_COMMAND)
                .forEach(method -> sMethodUris.put(
                        method, UUri.newBuilder(SERVICE_URI)
                                .setResource(UResourceBuilder.forRpcRequest(method))
                                .build()));
    }

    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private final Map<UUri, BiConsumer<UPayload, CompletableFuture<UPayload>>> mMethodHandlers = new HashMap<>();
    private final URpcListener mURpcListener = this::handleRequestMessage;
    private UPClient mUPClient;
    private USubscription.Stub mUSubscriptionStub;

    private static @NonNull UUri mapDoorTopic(@NonNull String instance) {
        final UUri topic = sDoorTopics.get(instance);
        return (topic != null) ? topic : UUri.getDefaultInstance();
    }

    private static @NonNull UUri mapMethodUri(@NonNull String method) {
        final UUri uri = sMethodUris.get(method);
        return (uri != null) ? uri : UUri.getDefaultInstance();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mUPClient = UPClient.create(getApplicationContext(), Example.SERVICE, mExecutor, (client, ready) -> {
            if (ready) {
                Log.i(TAG, join(Key.EVENT, "uPClient connected"));
            } else {
                Log.w(TAG, join(Key.EVENT, "uPClient unexpectedly disconnected"));
            }
        });
        mUSubscriptionStub = USubscription.newStub(mUPClient);

        mUPClient.connect()
                .thenCompose(status -> {
                    logStatus("connect", status);
                    return isOk(status) ?
                            CompletableFuture.completedFuture(status) :
                            CompletableFuture.failedFuture(new UStatusException(status));
                })
                .thenCompose(it -> CompletableFuture.allOf(
                        createTopic(mapDoorTopic(DOOR_FRONT_LEFT.getInstance())),
                        createTopic(mapDoorTopic(DOOR_FRONT_RIGHT.getInstance())),
                        registerMethod(mapMethodUri(Example.METHOD_EXECUTE_DOOR_COMMAND), this::executeDoorCommand)));
    }

    @Override
    public @Nullable IBinder onBind(@NonNull Intent intent) {
        return new Binder();
    }

    @Override
    public void onDestroy() {
        mExecutor.shutdown();

        CompletableFuture.allOf(
                        unregisterMethod(mapMethodUri(Example.METHOD_EXECUTE_DOOR_COMMAND)))
                .exceptionally(exception -> null)
                .thenCompose(it -> mUPClient.disconnect())
                .whenComplete((status, exception) -> logStatus("disconnect", status));
        super.onDestroy();
    }

    private CompletableFuture<UStatus> registerMethod(@NonNull UUri methodUri,
            @NonNull BiConsumer<UPayload, CompletableFuture<UPayload>> handler) {
        return CompletableFuture.supplyAsync(() -> {
            final UStatus status = mUPClient.registerRpcListener(methodUri, mURpcListener);
            if (isOk(status)) {
                mMethodHandlers.put(methodUri, handler);
            }
            return logStatus("registerMethod", status, Key.URI, stringify(methodUri));
        });
    }

    private CompletableFuture<UStatus> unregisterMethod(@NonNull UUri methodUri) {
        return CompletableFuture.supplyAsync(() -> {
            final UStatus status = mUPClient.unregisterRpcListener(methodUri, mURpcListener);
            mMethodHandlers.remove(methodUri);
            return logStatus("unregisterMethod", status, Key.URI, stringify(methodUri));
        });
    }

    private CompletableFuture<UStatus> createTopic(@NonNull UUri topic) {
        return mUSubscriptionStub.createTopic(CreateTopicRequest.newBuilder()
                        .setTopic(topic)
                        .build())
                .toCompletableFuture()
                .whenComplete((status, exception) -> {
                    if (exception != null) { // Communication failure
                        status = toStatus(exception);
                    }
                    logStatus("createTopic", status, Key.TOPIC, stringify(topic));
                });
    }

    private void publish(@NonNull UMessage message) {
        final UStatus status = mUPClient.send(message);
        logStatus("publish", status, Key.TOPIC, stringify(message.getSource()));
    }

    private void handleRequestMessage(@NonNull UMessage requestMessage, @NonNull CompletableFuture<UPayload> responseFuture) {
        final UUri methodUri = requestMessage.getAttributes().getSink();
        final BiConsumer<UPayload, CompletableFuture<UPayload>> handler = mMethodHandlers.get(methodUri);
        if (handler != null) {
            handler.accept(requestMessage.getPayload(), responseFuture);
        }
    }

    private void executeDoorCommand(@NonNull UPayload requestPayload,
            @NonNull CompletableFuture<UPayload> responseFuture) {
        UStatus status;
        try {
            final DoorCommand request = unpack(requestPayload, DoorCommand.class).orElseThrow(IllegalArgumentException::new);
            final String instance = request.getDoor().getInstance();
            final DoorCommand.Action action = request.getAction();
            Log.i(TAG, join(Key.REQUEST, "executeDoorCommand", "instance", instance, "action", action));
            checkArgument(sDoorTopics.containsKey(instance), "Unknown door: " + instance);
            final boolean locked = switch (action) {
                case LOCK -> true;
                case UNLOCK -> false;
                default -> throw new UStatusException(UCode.INVALID_ARGUMENT, "Unknown action: " + action);
            };
            // Pretend that all required CAN signals were sent successfully.
            // Simulate a received signal below.
            mExecutor.execute(() -> publish(UMessage.newBuilder()
                    .setSource(mapDoorTopic(instance))
                    .setPayload(packToAny(Door.newBuilder()
                            .setInstance(instance)
                            .setLocked(locked)
                            .build()))
                    .setAttributes(UAttributesBuilder.publish(UPriority.UPRIORITY_CS0).build())
                    .build()));
            status = STATUS_OK;
        } catch (Exception e) {
            status = toStatus(e);
        }
        logStatus("executeDoorCommand", status);
        responseFuture.complete(packToAny(status));
    }

    private @NonNull UStatus logStatus(@NonNull String method, @NonNull UStatus status, Object... args) {
        Log.println(isOk(status) ? Log.INFO : Log.ERROR, TAG, status(method, status, args));
        return status;
    }
}
