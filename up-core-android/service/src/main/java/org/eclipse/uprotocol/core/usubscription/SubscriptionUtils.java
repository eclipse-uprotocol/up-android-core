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

package org.eclipse.uprotocol.core.usubscription;

import static org.eclipse.uprotocol.common.util.UStatusUtils.buildStatus;
import static org.eclipse.uprotocol.common.util.UStatusUtils.checkArgument;
import static org.eclipse.uprotocol.core.internal.util.CommonUtils.emptyIfNull;
import static org.eclipse.uprotocol.core.internal.util.UUriUtils.removeAuthority;
import static org.eclipse.uprotocol.core.usubscription.USubscription.logStatus;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.TypeConverter;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import org.eclipse.uprotocol.common.UStatusException;
import org.eclipse.uprotocol.core.usubscription.v3.SubscribeAttributes;
import org.eclipse.uprotocol.core.usubscription.v3.SubscriberInfo;
import org.eclipse.uprotocol.core.usubscription.v3.SubscriptionRequest;
import org.eclipse.uprotocol.core.usubscription.v3.SubscriptionResponse;
import org.eclipse.uprotocol.core.usubscription.v3.SubscriptionStatus;
import org.eclipse.uprotocol.core.usubscription.v3.UnsubscribeRequest;
import org.eclipse.uprotocol.core.usubscription.v3.Update;
import org.eclipse.uprotocol.uri.serializer.LongUriSerializer;
import org.eclipse.uprotocol.v1.UAuthority;
import org.eclipse.uprotocol.v1.UCode;
import org.eclipse.uprotocol.v1.UEntity;
import org.eclipse.uprotocol.v1.UResource;
import org.eclipse.uprotocol.v1.UStatus;
import org.eclipse.uprotocol.v1.UUri;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@SuppressWarnings("java:S1200")
public class SubscriptionUtils {
    private static final Type GSON_TYPE_STRING_LIST = new TypeToken<List<Any>>() {}.getType();
    private static final Gson sGson = new GsonBuilder()
            .registerTypeAdapter(Any.class, new AnyDeserializerFromJson())
            .create();

    private static final LongUriSerializer serializer = LongUriSerializer.instance();

    static class AnyDeserializerFromJson implements JsonDeserializer<Any> {
        @Override
        public Any deserialize(@NonNull JsonElement jElement, Type typeOfT, JsonDeserializationContext context)
                throws JsonParseException {
            try {
                return Any.parseFrom(ByteString.copyFromUtf8(jElement.getAsString()));
            } catch (InvalidProtocolBufferException e) {
                logStatus(Log.ERROR, "deserialize", toStatus(e));
                return null;
            }
        }
    }

    private SubscriptionUtils() {}

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

    public static @NonNull SubscriberInfo buildSubscriber(@NonNull String subscriber) {
        return SubscriberInfo.newBuilder().setUri(serializer.deserialize(subscriber)).build();
    }

    public static @NonNull SubscriberInfo buildSubscriber(@NonNull String subscriber,
                                                          @NonNull String subscriberDetails) {
        final List<Any> details = convertToAnyList(subscriberDetails);
        return SubscriberInfo.newBuilder().setUri(serializer.deserialize(subscriber)).addAllDetails(details).build();
    }

    public static @NonNull Update buildNotificationUpdate(@NonNull UUri topic, @NonNull SubscriberInfo subscriber,
            @NonNull SubscribeAttributes attributes, @NonNull SubscriptionStatus subscriptionStatus) {
        return Update.newBuilder()
                .setTopic(topic)
                .setSubscriber(subscriber)
                .setAttributes(attributes)
                .setStatus(subscriptionStatus)
                .build();
    }

    public static @NonNull SubscriptionStatus buildSubscriptionStatus(@NonNull UCode code,
                                                                      @NonNull SubscriptionStatus.State state,
                                                                      @Nullable String message) {
        return SubscriptionStatus.newBuilder()
                .setCode(code)
                .setState(state)
                .setMessage(emptyIfNull(message))
                .build();
    }

    public static @NonNull SubscriptionResponse buildSubscriptionResponse(
            @NonNull SubscriptionStatus subscriptionStatus) {
        return SubscriptionResponse.newBuilder().setStatus(subscriptionStatus).build();
    }

    public static @NonNull RequestData buildRequestData(@NonNull SubscriptionRequest request,
                                                        boolean removeTopicAuthority) {
        final UUri topic = request.getTopic();
        final UUri subscriber = request.getSubscriber().getUri();

        checkArgument(!serializer.serialize(topic).isEmpty(), UCode.INVALID_ARGUMENT, "Topic is empty");
        checkArgument(!serializer.serialize(subscriber).isEmpty(), UCode.INVALID_ARGUMENT, "Subscriber is empty");

        if (removeTopicAuthority) {
            return new RequestData(removeAuthority(topic), subscriber, request.getAttributes());
        } else {
            return new RequestData(request);
        }
    }

    public static @NonNull RequestData buildRequestData(@NonNull UnsubscribeRequest request,
                                                        boolean removeTopicAuthority) {
        final UUri topic = request.getTopic();
        final UUri subscriber = request.getSubscriber().getUri();

        checkArgument(!serializer.serialize(topic).isEmpty(), UCode.INVALID_ARGUMENT, "Topic is empty");
        checkArgument(!serializer.serialize(subscriber).isEmpty(), UCode.INVALID_ARGUMENT, "Subscriber is empty");

        if (removeTopicAuthority) {
            return new RequestData(removeAuthority(topic), subscriber, SubscribeAttributes.getDefaultInstance());
        } else {
            return new RequestData(request);
        }
    }

    @TypeConverter
    public static @NonNull String convertToString(@NonNull List<Any> details) {
        final List<String> list = details.stream()
                .filter(Objects::nonNull)
                .map(it -> it.toByteString().toStringUtf8())
                .collect(Collectors.toList());
        return sGson.toJson(list, GSON_TYPE_STRING_LIST);
    }

    @TypeConverter
    public static @NonNull List<Any> convertToAnyList(@NonNull String details) {
        final List<Any> list = sGson.fromJson(details, GSON_TYPE_STRING_LIST);
        return emptyIfNull(list).stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    @NonNull
    public static UStatus toStatus(@NonNull Throwable throwable) {
        if (throwable instanceof UStatusException statusException) {
            return statusException.getStatus();
        }
        return buildStatus(UCode.INVALID_ARGUMENT, throwable.getMessage());
    }

    public static @NonNull SubscriptionResponse toSubscriptionResponse(Throwable throwable) {
        if (throwable instanceof SubscriptionException subscriptionException) {
            return buildSubscriptionResponse(subscriptionException.getStatus());
        } else if (throwable instanceof UStatusException statusException) {
            return buildSubscriptionResponse(buildSubscriptionStatus(
                    statusException.getCode(),
                    SubscriptionStatus.State.UNSUBSCRIBED,
                    emptyIfNull(throwable.getMessage())));

        } else {
            return buildSubscriptionResponse(buildSubscriptionStatus(
                    UCode.ABORTED,
                    SubscriptionStatus.State.UNSUBSCRIBED,
                    emptyIfNull(throwable.getMessage())));
        }
    }
}