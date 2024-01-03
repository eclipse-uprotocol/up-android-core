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
package org.eclipse.uprotocol.core;

import static org.eclipse.uprotocol.UPClient.META_DATA_ENTITY_NAME;
import static org.eclipse.uprotocol.UPClient.META_DATA_ENTITY_VERSION;
import static org.eclipse.uprotocol.UPClient.PERMISSION_ACCESS_UBUS;
import static org.eclipse.uprotocol.core.ubus.client.ClientManager.REMOTE_CLIENT_NAME;
import static org.eclipse.uprotocol.transport.builder.UPayloadBuilder.packToAny;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.ServiceInfo;
import android.os.Bundle;

import androidx.annotation.NonNull;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.protobuf.Int32Value;

import org.eclipse.uprotocol.common.UStatusException;
import org.eclipse.uprotocol.core.ubus.IUListener;
import org.eclipse.uprotocol.core.ubus.UBus;
import org.eclipse.uprotocol.core.usubscription.USubscription;
import org.eclipse.uprotocol.core.usubscription.v3.SubscriberInfo;
import org.eclipse.uprotocol.core.usubscription.v3.SubscriptionStatus;
import org.eclipse.uprotocol.core.usubscription.v3.SubscriptionStatus.State;
import org.eclipse.uprotocol.transport.builder.UAttributesBuilder;
import org.eclipse.uprotocol.uri.builder.UResourceBuilder;
import org.eclipse.uprotocol.v1.UAttributes;
import org.eclipse.uprotocol.v1.UAuthority;
import org.eclipse.uprotocol.v1.UCode;
import org.eclipse.uprotocol.v1.UEntity;
import org.eclipse.uprotocol.v1.UMessage;
import org.eclipse.uprotocol.v1.UPayload;
import org.eclipse.uprotocol.v1.UPriority;
import org.eclipse.uprotocol.v1.UResource;
import org.eclipse.uprotocol.v1.UStatus;
import org.eclipse.uprotocol.v1.UUID;
import org.eclipse.uprotocol.v1.UUri;
import org.eclipse.uprotocol.v1.internal.ParcelableUMessage;
import org.junit.function.ThrowingRunnable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@SuppressWarnings({"SameParameterValue", "unused"})
public class TestBase {
    protected static final String PACKAGE_NAME = "org.eclipse.uprotocol.core.test";
    protected static final String PACKAGE_NAME2 = "org.eclipse.uprotocol.core.test2";
    protected static final UEntity SERVICE = buildEntity("test.srv", 1);
    protected static final UEntity SERVICE2 = buildEntity("test.srv", 2);
    protected static final UEntity CLIENT = buildEntity("test.app", 1);
    protected static final UEntity CLIENT2 = buildEntity("test.app", 2);
    protected static final UEntity REMOTE_SERVER = buildEntity(REMOTE_CLIENT_NAME, 1);
    protected static final String VIN = "1GK12D1T2N10339DC";
    protected static final UAuthority LOCAL_AUTHORITY = buildAuthority(VIN + ".veh.uprotocol.eclipse.org");
    protected static final UAuthority REMOTE_AUTHORITY = buildAuthority("bo.uprotocol.eclipse.org");
    protected static final UResource RESOURCE = buildResource("door", "front_left", "Door");
    protected static final UResource RESOURCE2 = buildResource("config", null, "Config");
    protected static final UResource METHOD = UResourceBuilder.forRpcRequest("UpdateDoor");
    protected static final UResource METHOD2 = UResourceBuilder.forRpcRequest("UpdateWindow");
    protected static final UResource METHOD3 = UResourceBuilder.forRpcRequest("UpdateSunroof");
    protected static final UUri LOCAL_SERVER_URI = buildUri(null, SERVICE, null);
    protected static final UUri LOCAL_SERVER2_URI = buildUri(null, SERVICE2, null);
    protected static final UUri LOCAL_CLIENT_URI = buildUri(null, CLIENT, null);
    protected static final UUri LOCAL_CLIENT2_URI = buildUri(null, CLIENT2, null);
    protected static final UUri LOCAL_RESOURCE_URI = buildUri(null, SERVICE, RESOURCE);
    protected static final UUri LOCAL_RESOURCE2_URI = buildUri(null, SERVICE, RESOURCE2);
    protected static final UUri LOCAL_METHOD_URI = buildUri(null, SERVICE, METHOD);
    protected static final UUri LOCAL_METHOD2_URI = buildUri(null, SERVICE, METHOD2);
    protected static final UUri REMOTE_SERVER_URI = buildUri(REMOTE_AUTHORITY, SERVICE, null);
    protected static final UUri REMOTE_CLIENT_URI = buildUri(REMOTE_AUTHORITY, CLIENT, null);
    protected static final UUri REMOTE_RESOURCE_URI = buildUri(REMOTE_AUTHORITY, SERVICE, RESOURCE);
    protected static final UUri REMOTE_METHOD_URI = buildUri(REMOTE_AUTHORITY, SERVICE, METHOD);
    protected static final UUri USUBSCRIPTION_URI = buildUri(null, USubscription.SERVICE, null);
    protected static final UUri SERVER_URI = LOCAL_SERVER_URI;
    protected static final UUri SERVER2_URI = LOCAL_SERVER2_URI;
    protected static final UUri CLIENT_URI = LOCAL_CLIENT_URI;
    protected static final UUri CLIENT2_URI = LOCAL_CLIENT2_URI;
    protected static final UUri RESOURCE_URI = LOCAL_RESOURCE_URI;
    protected static final UUri RESOURCE2_URI = LOCAL_RESOURCE2_URI;
    protected static final UUri METHOD_URI = LOCAL_METHOD_URI;
    protected static final UUri METHOD2_URI = LOCAL_METHOD2_URI;
    protected static final UUri RESPONSE_URI = buildResponseUri(CLIENT_URI);
    protected static final UUri EMPTY_URI = UUri.getDefaultInstance();
    protected static final String TOKEN =
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG" +
            "4gU21pdGgiLCJpYXQiOjE1MTYyMzkwMjJ9.Q_w2AVguPRU2KskCXwR7ZHl09TQXEntfEA8Jj2_Jyew";
    protected static final int TTL = 1000;
    protected static final int DELAY_MS = 100;
    protected static final int DELAY_LONG_MS = 500;
    protected static final Int32Value DATA = Int32Value.newBuilder().setValue(101).build();
    protected static final UPayload PAYLOAD = packToAny(DATA);
    protected static final UMessage EMPTY_MESSAGE = UMessage.getDefaultInstance();

    protected static @NonNull UAuthority buildAuthority(@NonNull String name) {
        return UAuthority.newBuilder()
                .setName(name)
                .build();
    }

    protected static @NonNull UEntity buildEntity(String name, int version) {
        final UEntity.Builder builder = UEntity.newBuilder();
        if (name != null) {
            builder.setName(name);
        }
        if (version > 0) {
            builder.setVersionMajor(version);
        }
        return builder.build();
    }

    protected static @NonNull UResource buildResource(String name, String instance, String message) {
        final UResource.Builder builder = UResource.newBuilder();
        if (name != null) {
            builder.setName(name);
        }
        if (instance != null) {
            builder.setInstance(instance);
        }
        if (message != null) {
            builder.setMessage(message);
        }
        return builder.build();
    }

    protected static @NonNull UUri buildUri(UAuthority authority, UEntity entity, UResource resource) {
        final UUri.Builder builder = UUri.newBuilder();
        if (authority != null) {
            builder.setAuthority(authority);
        }
        if (entity != null) {
            builder.setEntity(entity);
        }
        if (resource != null) {
            builder.setResource(resource);
        }
        return builder.build();
    }
    
    protected static UUri buildResponseUri(UUri clientUri) {
        return UUri.newBuilder(clientUri)
                .setResource(UResourceBuilder.forRpcResponse())
                .build();
    }

    protected static @NonNull UAttributes buildPublishAttributes() {
        return newPublishAttributesBuilder().build();
    }

    protected static @NonNull UAttributes buildRequestAttributes(@NonNull UUri methodUri) {
        return newRequestAttributesBuilder(methodUri).build();
    }

    protected static @NonNull UAttributes buildResponseAttributes(@NonNull UUri responseUri, @NonNull UUID requestId) {
        return newResponseAttributesBuilder(responseUri, requestId).build();
    }

    protected static @NonNull UAttributesBuilder newPublishAttributesBuilder() {
        return UAttributesBuilder.publish(UPriority.UPRIORITY_CS0);
    }

    protected static @NonNull UAttributesBuilder newNotificationAttributesBuilder(@NonNull UUri sink) {
        return UAttributesBuilder.notification(UPriority.UPRIORITY_CS0, sink);
    }

    protected static @NonNull UAttributesBuilder newRequestAttributesBuilder(@NonNull UUri methodUri) {
        return UAttributesBuilder.request(UPriority.UPRIORITY_CS4, methodUri, TTL);
    }

    protected static @NonNull UAttributesBuilder newResponseAttributesBuilder(@NonNull UUri responseUri,
            @NonNull UUID requestId) {
        return UAttributesBuilder.response(UPriority.UPRIORITY_CS4, responseUri, requestId);
    }

    protected static @NonNull UMessage buildMessage(UUri source, UPayload payload, UAttributes attributes) {
        final UMessage.Builder builder = UMessage.newBuilder();
        if (source != null) {
            builder.setSource(source);
        }
        if (payload != null) {
            builder.setPayload(payload);
        }
        if (attributes != null) {
            builder.setAttributes(attributes);
        }
        return builder.build();
    }

    protected static @NonNull UMessage buildPublishMessage() {
        return buildMessage(RESOURCE_URI, PAYLOAD, newPublishAttributesBuilder().build());
    }

    protected static @NonNull UMessage buildPublishMessage(@NonNull UUri topic) {
        return buildMessage(topic, PAYLOAD, newPublishAttributesBuilder().build());
    }

    protected static @NonNull UMessage buildPublishMessage(@NonNull UUri topic, int ttl) {
        return buildMessage(RESOURCE_URI, PAYLOAD, newPublishAttributesBuilder().withTtl(ttl).build());
    }

    protected static @NonNull UMessage buildNotificationMessage() {
        return buildMessage(RESOURCE_URI, PAYLOAD, newNotificationAttributesBuilder(CLIENT_URI).build());
    }

    protected static @NonNull UMessage buildNotificationMessage(@NonNull UUri topic, @NonNull UUri sink) {
        return buildMessage(topic, PAYLOAD, newNotificationAttributesBuilder(sink).build());
    }

    protected static @NonNull UMessage buildNotificationMessage(@NonNull UUri topic, @NonNull UUri sink,
            @NonNull UPayload payload) {
        return buildMessage(topic, PAYLOAD, newNotificationAttributesBuilder(sink).build());
    }

    protected static @NonNull UMessage buildRequestMessage() {
        final UUri responseUri = buildResponseUri(CLIENT_URI);
        return buildMessage(responseUri, PAYLOAD, newRequestAttributesBuilder(METHOD_URI).withTtl(TTL).build());
    }

    protected static @NonNull UMessage buildRequestMessage(@NonNull UUri responseUri, @NonNull UUri methodUri) {
        return buildMessage(responseUri, PAYLOAD, newRequestAttributesBuilder(methodUri).withTtl(TTL).build());
    }

    protected static @NonNull UMessage buildRequestMessage(@NonNull UUri responseUri, @NonNull UUri methodUri,
            int timeout) {
        return buildMessage(responseUri, PAYLOAD, newRequestAttributesBuilder(methodUri).withTtl(timeout).build());
    }

    protected static @NonNull UMessage buildRequestMessage(@NonNull UUri responseUri, @NonNull UUri methodUri,
            @NonNull UPayload payload, int timeout){
        return buildMessage(responseUri, payload, newRequestAttributesBuilder(methodUri).withTtl(timeout).build());
    }

    protected static @NonNull UMessage buildResponseMessage(@NonNull UMessage requestMessage) {
        return buildMessage(requestMessage.getAttributes().getSink(), PAYLOAD,
                newResponseAttributesBuilder(requestMessage.getSource(), requestMessage.getAttributes().getId())
                        .build());
    }

    protected static @NonNull UMessage buildResponseMessage(@NonNull UMessage requestMessage, int timeout) {
        return buildMessage(requestMessage.getAttributes().getSink(), PAYLOAD,
                newResponseAttributesBuilder(requestMessage.getSource(), requestMessage.getAttributes().getId())
                        .withTtl(timeout)
                        .build());
    }

    protected static @NonNull UMessage buildResponseMessage(@NonNull UMessage requestMessage,
            @NonNull UPayload payload, int timeout) {
        return buildMessage(requestMessage.getAttributes().getSink(), payload,
                newResponseAttributesBuilder(requestMessage.getSource(), requestMessage.getAttributes().getId())
                        .withTtl(timeout)
                        .build());
    }

    protected static @NonNull UMessage buildFailureResponseMessage(@NonNull UMessage requestMessage,
            @NonNull UCode code) {
        return buildMessage(requestMessage.getAttributes().getSink(), null,
                newResponseAttributesBuilder(requestMessage.getSource(), requestMessage.getAttributes().getId())
                        .withCommStatus(code.getNumber())
                        .build());
    }

    protected static void assertStatus(@NonNull UCode code, @NonNull UStatus status) {
        assertEquals(code, status.getCode());
    }

    protected static @NonNull SubscriberInfo buildSubscriber(@NonNull UUri uri) {
        return SubscriberInfo.newBuilder().setUri(uri).build();
    }

    protected static @NonNull SubscriptionStatus buildSubscriptionStatus(@NonNull State state) {
        return buildSubscriptionStatus(state, (state == State.SUBSCRIBED) ? UCode.OK : UCode.NOT_FOUND);
    }

    protected static @NonNull SubscriptionStatus buildSubscriptionStatus(@NonNull State state, @NonNull UCode code) {
        return SubscriptionStatus.newBuilder()
                .setState(state)
                .setCode(code)
                .build();
    }

    @CanIgnoreReturnValue
    protected static UStatusException assertThrowsStatusException(UCode code, ThrowingRunnable runnable) {
        final UStatusException exception = assertThrows(UStatusException.class, runnable);
        assertEquals(code, exception.getCode());
        return exception;
    }

    protected @NonNull UCore.Builder newMockUCoreBuilder() {
        return newMockUCoreBuilder(mock(Context.class));
    }

    protected @NonNull UCore.Builder newMockUCoreBuilder(@NonNull Context context) {
        return new UCore.Builder(context)
                .setUBus(mock(UBus.class))
                .setUSubscription(mock(USubscription.class));
    }

    public static class MetaDataBuilder {
        private UEntity mEntity;

        public @NonNull MetaDataBuilder setEntity(UEntity entity) {
            mEntity = entity;
            return this;
        }

        public Bundle build() {
            final Bundle bundle = new Bundle();
            if (mEntity != null) {
                final String name = mEntity.getName();
                if (!name.isEmpty()) {
                    bundle.putString(META_DATA_ENTITY_NAME, name);
                }
                if (mEntity.hasVersionMajor()) {
                    bundle.putInt(META_DATA_ENTITY_VERSION, mEntity.getVersionMajor());
                }
            }
            return bundle;
        }
    }

    protected static @NonNull PackageInfo buildPackageInfo(@NonNull String packageName, Bundle metaData) {
        final ApplicationInfo appInfo = buildApplicationInfo(packageName, metaData);
        final PackageInfo packageInfo = new PackageInfo();
        packageInfo.applicationInfo = appInfo;
        packageInfo.packageName = packageName;
        packageInfo.requestedPermissions = new String[] { PERMISSION_ACCESS_UBUS };
        return packageInfo;
    }

    protected static @NonNull PackageInfo buildPackageInfo(@NonNull String packageName, ServiceInfo... services) {
        final ApplicationInfo appInfo = buildApplicationInfo(packageName, null);
        final PackageInfo packageInfo = new PackageInfo();
        packageInfo.applicationInfo = appInfo;
        packageInfo.packageName = packageName;
        packageInfo.services = services;
        for (ServiceInfo service : services) {
            service.packageName = packageName;
            service.applicationInfo = appInfo;
        }
        packageInfo.requestedPermissions = new String[] { PERMISSION_ACCESS_UBUS };
        return packageInfo;
    }

    protected static @NonNull ApplicationInfo buildApplicationInfo(@NonNull String packageName, Bundle metaData) {
        final ApplicationInfo appInfo = new ApplicationInfo();
        appInfo.flags = ApplicationInfo.FLAG_INSTALLED;
        appInfo.packageName = packageName;
        if (metaData != null && !metaData.isEmpty()) {
            appInfo.metaData = metaData;
        }
        return appInfo;
    }

    protected static @NonNull ServiceInfo buildServiceInfo(ComponentName component, Bundle metaData) {
        final ServiceInfo serviceInfo = new ServiceInfo();
        if (component != null) {
            serviceInfo.packageName = component.getPackageName();
            serviceInfo.name = component.getClassName();
        }
        if (metaData != null && !metaData.isEmpty()) {
            serviceInfo.metaData = metaData;
        }
        return serviceInfo;
    }

    @SuppressWarnings("BlockingMethodInNonBlockingContext")
    protected void sleep(long timeout) {
        try {
            new CompletableFuture<>().get(timeout, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception ignored) {}
    }

    protected static class MockListener extends IUListener.Stub {
        public MockListener() {}

        @Override
        public void onReceive(ParcelableUMessage parcelableUMessage){}
    }
}
