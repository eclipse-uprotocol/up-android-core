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
package org.eclipse.uprotocol.core.utwin;

import static org.eclipse.uprotocol.common.util.UStatusUtils.STATUS_OK;
import static org.eclipse.uprotocol.core.utwin.UTwin.Method.GET_LAST_MESSAGES;
import static org.eclipse.uprotocol.core.utwin.UTwin.Method.SET_LAST_MESSAGE;
import static org.eclipse.uprotocol.transport.builder.UPayloadBuilder.packToAny;
import static org.eclipse.uprotocol.transport.builder.UPayloadBuilder.unpack;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.google.protobuf.Message;

import org.eclipse.uprotocol.core.TestBase;
import org.eclipse.uprotocol.core.UCore;
import org.eclipse.uprotocol.core.ubus.UBus;
import org.eclipse.uprotocol.core.utwin.v2.GetLastMessagesResponse;
import org.eclipse.uprotocol.core.utwin.v2.MessageResponse;
import org.eclipse.uprotocol.v1.UCode;
import org.eclipse.uprotocol.v1.UMessage;
import org.eclipse.uprotocol.v1.UStatus;
import org.eclipse.uprotocol.v1.UUri;
import org.eclipse.uprotocol.v1.UUriBatch;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.robolectric.RuntimeEnvironment;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RunWith(AndroidJUnit4.class)
public class UTwinTest extends TestBase {
    private final UBus mUBus = mock(UBus.class);
    private UTwin mUTwin;

    @Before
    public void setUp() {
        final Context context = RuntimeEnvironment.getApplication();
        mUTwin = new UTwin(context);
        final UCore uCore = newMockUCoreBuilder(context)
                .setUBus(mUBus)
                .setUTwin(mUTwin)
                .build();
        when(mUBus.registerClient(any(), any(), any())).thenReturn(STATUS_OK);
        when(mUBus.unregisterClient(any())).thenReturn(STATUS_OK);
        when(mUBus.enableDispatching(any(), anyInt(), any())).thenReturn(STATUS_OK);
        when(mUBus.disableDispatching(any(), anyInt(), any())).thenReturn(STATUS_OK);
        mUTwin.init(uCore);
    }

    private static <T extends Message> @NonNull UMessage buildRequestMessage(UUri methodUri, T message) {
        return buildRequestMessage(buildResponseUri(CLIENT_URI), methodUri, packToAny(message));
    }

    private static @NonNull UUriBatch buildUUriBatch(@NonNull Collection<UUri> uris) {
        final UUriBatch.Builder builder = UUriBatch.newBuilder();
        uris.forEach(builder::addUris);
        return builder.build();
    }

    private @NonNull GetLastMessagesResponse invokeGetLastMessages(@NonNull Collection<UUri> topics) {
        mUTwin.inject(buildRequestMessage(GET_LAST_MESSAGES.localUri(), buildUUriBatch(topics)));
        final ArgumentCaptor<UMessage> captor = ArgumentCaptor.forClass(UMessage.class);
        verify(mUBus, timeout(DELAY_LONG_MS).times(1)).send(captor.capture(), any());
        return unpack(captor.getValue().getPayload(), GetLastMessagesResponse.class).orElseThrow(RuntimeException::new);
    }

    @SuppressWarnings("SameParameterValue")
    private @NonNull GetLastMessagesResponse invokeGetLastMessages(@NonNull Message message) {
        final ArgumentCaptor<UMessage> captor = ArgumentCaptor.forClass(UMessage.class);
        mUTwin.inject(buildRequestMessage(GET_LAST_MESSAGES.localUri(), message));
        verify(mUBus, timeout(DELAY_LONG_MS).times(1)).send(captor.capture(), any());
        return unpack(captor.getValue().getPayload(), GetLastMessagesResponse.class).orElseThrow(RuntimeException::new);
    }

    private @NonNull UStatus invokeSetLastMessage(UMessage message) {
        final ArgumentCaptor<UMessage> captor = ArgumentCaptor.forClass(UMessage.class);
        mUTwin.inject(buildRequestMessage(SET_LAST_MESSAGE.localUri(), message));
        verify(mUBus, timeout(DELAY_LONG_MS).times(1)).send(captor.capture(), any());
        return unpack(captor.getValue().getPayload(), UStatus.class).orElseThrow(RuntimeException::new);
    }

    @Test
    public void testInit() {
        verify(mUBus, times(1)).registerClient(eq(UTwin.SERVICE), any(), any());
        verify(mUBus, times(1)).enableDispatching(eq(GET_LAST_MESSAGES.localUri()), anyInt(), any());
        verify(mUBus, times(1)).enableDispatching(eq(SET_LAST_MESSAGE.localUri()), anyInt(), any());
    }

    @Test
    public void testShutdown() {
        mUTwin.shutdown();
        verify(mUBus, times(1)).disableDispatching(eq(GET_LAST_MESSAGES.localUri()), anyInt(), any());
        verify(mUBus, times(1)).disableDispatching(eq(SET_LAST_MESSAGE.localUri()), anyInt(), any());
        verify(mUBus, times(1)).unregisterClient(any());
    }

    @Test
    public void testShutdownTimeout() {
        mUTwin.getExecutor().execute(() -> sleep(200));
        mUTwin.shutdown();
        verify(mUBus, times(1)).unregisterClient(any());
    }

    @Test
    public void testShutdownInterrupted() {
        mUTwin.getExecutor().execute(() -> sleep(200));
        final Thread thread = new Thread(() -> mUTwin.shutdown());
        thread.start();
        thread.interrupt();
        verify(mUBus, timeout(DELAY_LONG_MS).times(1)).unregisterClient(any());
    }

    @Test
    public void testClearCache() {
        assertTrue(mUTwin.addMessage(buildPublishMessage(RESOURCE_URI)));
        assertEquals(1, mUTwin.getMessageCount());
        mUTwin.clearCache();
        assertEquals(0, mUTwin.getMessageCount());
    }

    @Test
    public void testSetLastMessage() {
        assertStatus(UCode.PERMISSION_DENIED, invokeSetLastMessage(buildPublishMessage()));
    }

    @Test
    @SuppressWarnings("SimplifyStreamApiCallChains")
    public void testGetLastMessages() {
        final List<UUri> topics = List.of(RESOURCE_URI, RESOURCE2_URI);
        final List<UMessage> messages = topics.stream().map(TestBase::buildPublishMessage).collect(Collectors.toList());
        messages.forEach(message -> mUTwin.addMessage(message));
        final List<MessageResponse> responses = invokeGetLastMessages(topics).getResponsesList();
        assertEquals(topics.size(), responses.size());
        responses.forEach(response -> {
            assertStatus(UCode.OK, response.getStatus());
            final UUri topic = response.getTopic();
            final UMessage message = response.getMessage();
            assertEquals(topic, message.getAttributes().getSource());
            assertTrue(topics.contains(topic));
            assertTrue(messages.contains(message));
        });
    }

    @Test
    public void testGetLastMessageNotFound() {
        final List<MessageResponse> responses = invokeGetLastMessages(List.of(RESOURCE_URI)).getResponsesList();
        assertEquals(1, responses.size());
        assertStatus(UCode.NOT_FOUND, responses.get(0).getStatus());
        assertEquals(RESOURCE_URI, responses.get(0).getTopic());
        assertFalse(responses.get(0).hasMessage());
    }

    @Test
    public void testGetLastMessageEmptyTopic() {
        final List<MessageResponse> responses = invokeGetLastMessages(List.of(EMPTY_URI)).getResponsesList();
        assertEquals(1, responses.size());
        assertStatus(UCode.INVALID_ARGUMENT, responses.get(0).getStatus());
        assertEquals(EMPTY_URI, responses.get(0).getTopic());
        assertFalse(responses.get(0).hasMessage());
    }

    @Test
    public void testGetLastMessageUnexpectedPayload() {
        final List<MessageResponse> responses = invokeGetLastMessages(EMPTY_URI).getResponsesList();
        assertEquals(1, responses.size());
        assertStatus(UCode.INVALID_ARGUMENT, responses.get(0).getStatus());
        assertEquals(EMPTY_URI, responses.get(0).getTopic());
        assertFalse(responses.get(0).hasMessage());
    }

    @Test
    public void testAddMessage() {
        final UMessage message = buildPublishMessage(RESOURCE_URI);
        assertTrue(mUTwin.addMessage(message));
        assertEquals(message, mUTwin.getMessage(RESOURCE_URI));
    }

    @Test
    public void testRemoveMessage() {
        testAddMessage();
        assertTrue(mUTwin.removeMessage(RESOURCE_URI));
        assertNull(mUTwin.getMessage(RESOURCE_URI));
    }

    @Test
    public void testGetMessage() {
        final UMessage message = buildPublishMessage(RESOURCE_URI);
        assertTrue(mUTwin.addMessage(message));
        assertEquals(message, mUTwin.getMessage(RESOURCE_URI));
        assertTrue(mUTwin.removeMessage(RESOURCE_URI));
        assertNull(mUTwin.getMessage(RESOURCE_URI));
    }

    @Test
    public void testGetTopics() {
        assertTrue(mUTwin.getTopics().isEmpty());
        final Set<UUri> topics = Set.of(RESOURCE_URI, RESOURCE2_URI);
        topics.forEach(topic -> mUTwin.addMessage(buildPublishMessage(topic)));
        assertEquals(topics, mUTwin.getTopics());
    }
}
