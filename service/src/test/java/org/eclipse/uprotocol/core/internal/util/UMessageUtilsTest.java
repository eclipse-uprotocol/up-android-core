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
package org.eclipse.uprotocol.core.internal.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mockStatic;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.eclipse.uprotocol.core.TestBase;
import org.eclipse.uprotocol.uuid.factory.UuidUtils;
import org.eclipse.uprotocol.v1.UAttributes;
import org.eclipse.uprotocol.v1.UCode;
import org.eclipse.uprotocol.v1.UMessage;
import org.eclipse.uprotocol.v1.UMessageType;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;

import java.util.Optional;

@RunWith(AndroidJUnit4.class)
public class UMessageUtilsTest extends TestBase {
    private static final int DELTA = 30;

    @Test
    public void testCheckMessageValid() {
        UMessage message = buildPublishMessage();
        assertEquals(message, UMessageUtils.checkMessageValid(message));
        message = buildRequestMessage();
        assertEquals(message, UMessageUtils.checkMessageValid(message));
        message = buildResponseMessage(message);
        assertEquals(message, UMessageUtils.checkMessageValid(message));
    }

    @Test
    public void testCheckMessageValidNegative() {
        assertThrowsStatusException(UCode.INVALID_ARGUMENT, () -> UMessageUtils.checkMessageValid(EMPTY_MESSAGE));
        assertThrowsStatusException(UCode.INVALID_ARGUMENT, () ->
                UMessageUtils.checkMessageValid(UMessage.newBuilder()
                        .setAttributes(UAttributes.newBuilder().setType(UMessageType.UMESSAGE_TYPE_REQUEST))
                        .build()));
    }

    @Test
    public void testReplaceSource() {
        final UMessage message = buildPublishMessage(RESOURCE_URI);
        assertEquals(RESOURCE_URI, message.getAttributes().getSource());
        final UMessage newMessage = UMessageUtils.replaceSource(message, RESOURCE2_URI);
        assertEquals(RESOURCE2_URI, newMessage.getAttributes().getSource());
    }

    @Test
    public void testReplaceSink() {
        final UMessage message = buildNotificationMessage(RESOURCE_URI, CLIENT_URI);
        assertEquals(CLIENT_URI, message.getAttributes().getSink());
        final UMessage newMessage = UMessageUtils.replaceSink(message, CLIENT2_URI);
        assertEquals(CLIENT2_URI, newMessage.getAttributes().getSink());
    }

    @Test
    public void testReplaceSinkEmpty() {
        final UMessage message = buildNotificationMessage(RESOURCE_URI, CLIENT_URI);
        assertEquals(CLIENT_URI, message.getAttributes().getSink());
        UMessage newMessage = UMessageUtils.replaceSink(message, EMPTY_URI);
        assertFalse(newMessage.getAttributes().hasSink());
        newMessage = UMessageUtils.replaceSink(message, null);
        assertFalse(newMessage.getAttributes().hasSink());
    }

    @Test
    public void testRemoveSink() {
        final UMessage message = buildNotificationMessage(RESOURCE_URI, CLIENT_URI);
        assertEquals(CLIENT_URI, message.getAttributes().getSink());
        final UMessage newMessage = UMessageUtils.removeSink(message);
        assertFalse(newMessage.getAttributes().hasSink());
    }

    @Test
    public void testAddSinkIfEmpty() {
        final UMessage message = buildPublishMessage();
        assertFalse(message.getAttributes().hasSink());
        UMessage newMessage = UMessageUtils.addSinkIfEmpty(message, REMOTE_SERVER_URI);
        assertEquals(REMOTE_SERVER_URI, newMessage.getAttributes().getSink());
        newMessage = UMessageUtils.addSinkIfEmpty(newMessage, LOCAL_CLIENT2_URI);
        assertEquals(REMOTE_SERVER_URI, newMessage.getAttributes().getSink());
    }

    @Test
    public void testIsExpired() {
        final UMessage message = buildRequestMessage(RESPONSE_URI, METHOD_URI, DELAY_MS - DELTA);
        assertFalse(UMessageUtils.isExpired(message));
        sleep(DELAY_MS);
        assertTrue(UMessageUtils.isExpired(message));
    }

    @Test
    public void testIsExpiredNoTtl() {
        final UMessage message = buildPublishMessage();
        assertFalse(UMessageUtils.isExpired(message));
        assertFalse(UMessageUtils.isExpired(UMessage.newBuilder(message)
                .setAttributes(UAttributes.newBuilder(message.getAttributes())
                        .setTtl(-1)
                        .build())
                .build()));
    }

    @Test
    public void testGetElapsedTime() {
        final UMessage message = buildPublishMessage();
        sleep(DELAY_MS);
        assertEquals(DELAY_MS, UMessageUtils.getElapsedTime(message).orElse(0L), DELTA);
    }

    @Test
    public void testGetElapsedTimeCreationTimeUnknown() {
        assertFalse(UMessageUtils.getElapsedTime(EMPTY_MESSAGE).isPresent());
    }

    @Test
    public void testGetElapsedTimeCreationTimeInFuture() {
        try (MockedStatic<UuidUtils> mockedUuidUtils = mockStatic(UuidUtils.class)) {
            final UMessage message = buildPublishMessage();
            mockedUuidUtils.when(() -> UuidUtils.getTime(message.getAttributes().getId()))
                    .thenReturn(Optional.of( System.currentTimeMillis() + DELAY_LONG_MS));
            assertFalse(UMessageUtils.getElapsedTime(message).isPresent());
        }
    }

    @Test
    public void testGetRemainingTime() {
        final UMessage message = buildRequestMessage(RESPONSE_URI, METHOD_URI, TTL);
        assertEquals(TTL, UMessageUtils.getRemainingTime(message).orElse(0L), DELTA);
        sleep(DELAY_MS);
        assertEquals(TTL - DELAY_MS, UMessageUtils.getRemainingTime(message).orElse(0L), DELTA);
    }

    @Test
    public void testGetRemainingTimeNoTtl() {
        assertFalse(UMessageUtils.getRemainingTime(buildPublishMessage()).isPresent());
    }

    @Test
    public void testGetRemainingTimeExpired() {
        final UMessage message = buildRequestMessage(RESPONSE_URI, METHOD_URI, DELAY_MS - DELTA);
        sleep(DELAY_MS);
        assertFalse(UMessageUtils.getRemainingTime(message).isPresent());
    }

    @Test
    public void testBuildResponseMessage() {
        final UMessage requestMessage = buildRequestMessage();
        final UMessage responseMessage = UMessageUtils.buildResponseMessage(requestMessage, PAYLOAD);
        assertNotEquals(requestMessage.getAttributes().getId(), responseMessage.getAttributes().getId());
        assertEquals(requestMessage.getAttributes().getSink(), responseMessage.getAttributes().getSource());
        assertEquals(requestMessage.getAttributes().getSource(), responseMessage.getAttributes().getSink());
        assertEquals(requestMessage.getAttributes().getId(), responseMessage.getAttributes().getReqid());
        assertEquals(requestMessage.getAttributes().getPriority(), responseMessage.getAttributes().getPriority());
        assertEquals(PAYLOAD, responseMessage.getPayload());
    }

    @Test
    public void testBuildFailedResponseMessage() {
        final UMessage requestMessage = buildRequestMessage();
        final UMessage responseMessage = UMessageUtils.buildFailedResponseMessage(requestMessage, UCode.ABORTED);
        assertNotEquals(requestMessage.getAttributes().getId(), responseMessage.getAttributes().getId());
        assertEquals(requestMessage.getAttributes().getSink(), responseMessage.getAttributes().getSource());
        assertEquals(requestMessage.getAttributes().getSource(), responseMessage.getAttributes().getSink());
        assertEquals(requestMessage.getAttributes().getId(), responseMessage.getAttributes().getReqid());
        assertEquals(requestMessage.getAttributes().getPriority(), responseMessage.getAttributes().getPriority());
        assertEquals(UCode.ABORTED.getNumber(), responseMessage.getAttributes().getCommstatus());
    }
}
