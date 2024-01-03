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

import static org.eclipse.uprotocol.core.usubscription.v3.SubscriptionStatus.State.UNSUBSCRIBED;
import static org.junit.Assert.assertEquals;

import static java.util.Collections.emptyList;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.google.protobuf.Any;
import com.google.protobuf.Int32Value;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.StringValue;

import org.eclipse.uprotocol.common.UStatusException;
import org.eclipse.uprotocol.core.TestBase;
import org.eclipse.uprotocol.core.usubscription.v3.SubscribeAttributes;
import org.eclipse.uprotocol.core.usubscription.v3.SubscriptionRequest;
import org.eclipse.uprotocol.core.usubscription.v3.SubscriptionResponse;
import org.eclipse.uprotocol.core.usubscription.v3.SubscriptionStatus;
import org.eclipse.uprotocol.core.usubscription.v3.UnsubscribeRequest;
import org.eclipse.uprotocol.v1.UCode;
import org.eclipse.uprotocol.v1.UStatus;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class SubscriptionUtilsTest extends SubscriptionTestBase {

    @Test
    public void testBuildUUri() {
        assertEquals(TestBase.RESOURCE_URI, SubscriptionUtils.buildUri(null, SERVICE, RESOURCE));
        assertEquals(TestBase.REMOTE_RESOURCE_URI, SubscriptionUtils.buildUri(REMOTE_AUTHORITY, SERVICE, RESOURCE));
        assertEquals(TestBase.RESOURCE_URI.getResource(),
                SubscriptionUtils.buildUri(null, null, RESOURCE).getResource());
        assertEquals(TestBase.RESOURCE_URI.getEntity(),
                SubscriptionUtils.buildUri(null, SERVICE, null).getEntity());
    }

    @Test
    public void testBuildSubscriber() {
        assertEquals(buildSubscriber(TestBase.LOCAL_CLIENT_URI), SubscriptionUtils.buildSubscriber(LOCAL_CLIENT_URI));
    }

    @Test
    public void testBuildNotificationUpdate() {
        assertEquals(buildNotificationUpdate(TestBase.RESOURCE_URI, TestBase.LOCAL_CLIENT_URI,
                        buildSubscriptionStatus(SubscriptionStatus.State.SUBSCRIBED, UCode.OK)),
                SubscriptionUtils.buildNotificationUpdate(TestBase.RESOURCE_URI,
                        buildSubscriber(TestBase.LOCAL_CLIENT_URI), SubscribeAttributes.getDefaultInstance(),
                        buildSubscriptionStatus(SubscriptionStatus.State.SUBSCRIBED, UCode.OK)));
    }

    @Test
    public void testBuildSubscriptionStatus() {
        assertEquals(buildSubscriptionStatus(SubscriptionStatus.State.SUBSCRIBE_PENDING, UCode.OK),
                SubscriptionUtils.buildSubscriptionStatus(UCode.OK, SubscriptionStatus.State.SUBSCRIBE_PENDING, ""));
    }

    @Test
    public void testBuildSubscriptionResponse() {
        assertEquals(buildSubscriptionStatus(SubscriptionStatus.State.SUBSCRIBED),
                SubscriptionUtils.buildSubscriptionResponse(
                        buildSubscriptionStatus(SubscriptionStatus.State.SUBSCRIBED)).getStatus());
    }

    @Test
    public void testBuildRequestDataWithSubscriptionRequest() {
        assertEquals(buildSubscriber(TestBase.LOCAL_CLIENT_URI).getUri(),
                SubscriptionUtils.buildRequestData(
                        SubscriptionRequest.newBuilder()
                                .setTopic(TestBase.RESOURCE_URI)
                                .setSubscriber(buildSubscriber(TestBase.LOCAL_CLIENT_URI))
                                .build(),
                        false).subscriber);
        assertEquals(buildSubscriber(TestBase.REMOTE_CLIENT_URI).getUri(),
                SubscriptionUtils.buildRequestData(
                        SubscriptionRequest.newBuilder()
                                .setTopic(TestBase.REMOTE_RESOURCE_URI)
                                .setSubscriber(buildSubscriber(TestBase.REMOTE_CLIENT_URI))
                                .build(),
                        true).subscriber);
    }

    @Test
    public void testBuildRequestDataWithSubscriptionRequestExceptionally() {
        assertThrowsStatusException(UCode.INVALID_ARGUMENT, () -> SubscriptionUtils.buildRequestData(
                SubscriptionRequest.getDefaultInstance(), false));
        assertThrowsStatusException(UCode.INVALID_ARGUMENT, () -> SubscriptionUtils.buildRequestData(
                SubscriptionRequest.newBuilder().setTopic(TestBase.RESOURCE_URI).build(), false));
    }

    @Test
    public void testBuildRequestDataWithUnsubscribeRequest() {
        assertEquals(buildSubscriber(TestBase.LOCAL_CLIENT_URI).getUri(),
                SubscriptionUtils.buildRequestData(
                        UnsubscribeRequest.newBuilder()
                                .setTopic(TestBase.RESOURCE_URI)
                                .setSubscriber(buildSubscriber(TestBase.LOCAL_CLIENT_URI))
                                .build(),
                        false).subscriber);
        assertEquals(buildSubscriber(TestBase.REMOTE_CLIENT_URI).getUri(),
                SubscriptionUtils.buildRequestData(
                        UnsubscribeRequest.newBuilder()
                                .setTopic(TestBase.REMOTE_RESOURCE_URI)
                                .setSubscriber(buildSubscriber(TestBase.REMOTE_CLIENT_URI))
                                .build(),
                        true).subscriber);
    }

    @Test
    public void testBuildRequestDataWithUnsubscribeRequestExceptionally() {
        assertThrowsStatusException(UCode.INVALID_ARGUMENT, () -> SubscriptionUtils.buildRequestData(
                UnsubscribeRequest.getDefaultInstance(), false));
        assertThrowsStatusException(UCode.INVALID_ARGUMENT, () -> SubscriptionUtils.buildRequestData(
                UnsubscribeRequest.newBuilder().setTopic(TestBase.RESOURCE_URI).build(), false));
    }

    @Test
    public void testConvertToString() {
        final String expectedString = "[\"\\n/type.googleapis.com/google.protobuf"
                + ".StringValue\\u0012\\b\\n\\u0006detail\","
                + "\"\\n.type.googleapis.com/google.protobuf.Int32Value\\u0012\\u0002\\b\\u0007\"]";
        List<Any> details = new ArrayList<>();
        details.add(Any.pack(StringValue.newBuilder().setValue("detail").build()));
        details.add(Any.pack(Int32Value.newBuilder().setValue(7).build()));
        final String string = SubscriptionUtils.convertToString(details);
        assertEquals(expectedString, string);
        final List<Any> restoredDetails = SubscriptionUtils.convertToAnyList(string);
        assertEquals(details, restoredDetails);
    }

    @Test
    public void testConvertToStringEmptyList() {
        assertEquals("[]", SubscriptionUtils.convertToString(emptyList()));
    }

    @Test
    public void testConvertToStringListWithNull() {
        final String expectedString = "[\"\\n/type.googleapis.com/google.protobuf"
                + ".StringValue\\u0012\\b\\n\\u0006detail\"]";
        List<Any> details = new ArrayList<>();
        details.add(Any.pack(StringValue.newBuilder().setValue("detail").build()));
        details.add(null);
        final String string = SubscriptionUtils.convertToString(details);
        assertEquals(expectedString, string);
        final List<Any> restoredDetails = SubscriptionUtils.convertToAnyList(string);
        assertEquals(1, restoredDetails.size());
        assertEquals(details.get(0), restoredDetails.get(0));
    }

    @Test
    public void testConvertToAnyList() {
        final String actualString = "[\"\\n/type.googleapis.com/google.protobuf"
                + ".StringValue\\u0012\\b\\n\\u0006detail\","
                + "\"\\n.type.googleapis.com/google.protobuf.Int32Value\\u0012\\u0002\\b\\u0007\"]";
        List<Any> expectedDetails = new ArrayList<>();
        expectedDetails.add(Any.pack(StringValue.newBuilder().setValue("detail").build()));
        expectedDetails.add(Any.pack(Int32Value.newBuilder().setValue(7).build()));
        final List<Any> restoredDetails = SubscriptionUtils.convertToAnyList(actualString);
        assertEquals(expectedDetails, restoredDetails);
    }

    @Test
    public void testConvertToAnyListEmptyString() {
        assertEquals(emptyList(), SubscriptionUtils.convertToAnyList(""));
    }

    @Test
    public void testConvertToAnyListEmptyJsonString() {
        assertEquals(emptyList(), SubscriptionUtils.convertToAnyList("[]"));
    }

    @Test
    public void testConvertToAnyListCorruptedString() {
        final String actualString = "[\"\\n/type.googleapis.com/google.protobuf"
                + ".StringValue\\u0012\\b\\n\\u0006detail\","
                + "\"\\n.type.googleapis.com/google.protobuf.Unknown\\u0012\\u0002\\b\\u0007\"]";
        List<Any> expectedDetails = new ArrayList<>();
        expectedDetails.add(Any.pack(StringValue.newBuilder().setValue("detail").build()));
        final List<Any> restoredDetails = SubscriptionUtils.convertToAnyList(actualString);
        assertEquals(expectedDetails, restoredDetails);
    }

    @Test
    public void testThrowableToSubscriptionResponseWithThrowable() {
        final UStatus status = UStatus.newBuilder().getDefaultInstanceForType();
        SubscriptionResponse response = SubscriptionUtils.toSubscriptionResponse(
                new Throwable(new InvalidProtocolBufferException("invalid")));
        assertEquals(UNSUBSCRIBED, response.getStatus().getState());

        response = SubscriptionUtils.toSubscriptionResponse(new Throwable(new UStatusException(status)));
        assertEquals(UNSUBSCRIBED, response.getStatus().getState());

        response = SubscriptionUtils.toSubscriptionResponse(new Throwable(new IllegalAccessException()));
        assertEquals(UNSUBSCRIBED, response.getStatus().getState());
    }
}
