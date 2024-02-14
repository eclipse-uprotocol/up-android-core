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

import static java.util.Collections.emptyList;

import androidx.annotation.NonNull;

import com.google.protobuf.Any;

import org.eclipse.uprotocol.core.usubscription.v3.SubscribeAttributes;
import org.eclipse.uprotocol.core.usubscription.v3.SubscriberInfo;
import org.eclipse.uprotocol.core.usubscription.v3.SubscriptionRequest;
import org.eclipse.uprotocol.core.usubscription.v3.UnsubscribeRequest;
import org.eclipse.uprotocol.v1.UUri;

import java.util.List;

class RequestData {
    final UUri topic;
    final UUri subscriber;
    final List<Any> subscriberDetails;
    final SubscribeAttributes attributes;

    RequestData(@NonNull UUri topic, @NonNull UUri subscriber, @NonNull SubscribeAttributes attributes) {
        this(topic, subscriber, emptyList(), attributes);
    }

    RequestData(@NonNull UUri topic, @NonNull UUri subscriber,
            @NonNull List<Any> subscriberDetails, @NonNull SubscribeAttributes attributes) {
        this.topic = topic;
        this.subscriber = subscriber;
        this.subscriberDetails = subscriberDetails;
        this.attributes = attributes;
    }

    RequestData(@NonNull SubscriptionRequest request) {
        this.topic = request.getTopic();
        this.subscriber = request.getSubscriber().getUri();
        this.subscriberDetails = request.getSubscriber().getDetailsList();
        this.attributes = request.getAttributes();
    }

    RequestData(@NonNull UnsubscribeRequest request) {
        this.topic = request.getTopic();
        this.subscriber = request.getSubscriber().getUri();
        this.subscriberDetails = request.getSubscriber().getDetailsList();
        this.attributes = SubscribeAttributes.getDefaultInstance();
    }

    @NonNull
    SubscriberInfo buildSubscriber() {
        return SubscriberInfo.newBuilder()
                .setUri(subscriber)
                .addAllDetails(subscriberDetails)
                .build();
    }
}
