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

import static org.eclipse.uprotocol.common.util.UStatusUtils.STATUS_OK;
import static org.eclipse.uprotocol.common.util.UStatusUtils.buildStatus;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.os.Binder;
import android.os.IBinder;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.eclipse.uprotocol.common.UStatusException;
import org.eclipse.uprotocol.core.TestBase;
import org.eclipse.uprotocol.core.ubus.UBus;
import org.eclipse.uprotocol.rpc.CallOptions;
import org.eclipse.uprotocol.rpc.URpcListener;
import org.eclipse.uprotocol.transport.UListener;
import org.eclipse.uprotocol.v1.UAttributes;
import org.eclipse.uprotocol.v1.UCode;
import org.eclipse.uprotocol.v1.UMessage;
import org.eclipse.uprotocol.v1.UPayload;
import org.eclipse.uprotocol.v1.UStatus;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import java.util.concurrent.CompletableFuture;

@RunWith(AndroidJUnit4.class)
public class MessageHandlerTest extends TestBase {
    private static final UStatus STATUS_ERROR = buildStatus(UCode.UNKNOWN, "unknown");
    private final UBus mUBus = mock(UBus.class);
    private final UListener mListener1 = mock(UListener.class);
    private final UListener mListener2 = mock(UListener.class);
    private final URpcListener mRpcListener1 = mock(URpcListener.class);
    private final URpcListener mRpcListener2 = mock(URpcListener.class);
    private final IBinder mClientToken = new Binder();
    private MessageHandler mMessageHandler;

    @Before
    public void setUp() {
        mMessageHandler = new MessageHandler(mUBus, CLIENT, mClientToken);
        when(mUBus.enableDispatching(any(), anyInt(), any())).thenReturn(STATUS_OK);
        when(mUBus.disableDispatching(any(), anyInt(), any())).thenReturn(STATUS_OK);
    }

    @Test
    public void testGetRpcExecutor() {
        assertNotNull(mMessageHandler.getRpcExecutor());
    }

    @Test
    public void testRegisterListener() {
        assertTrue(mMessageHandler.registerListener(RESOURCE_URI, mListener1));
    }

    @Test
    public void testRegisterListenerRejected() {
        when(mUBus.enableDispatching(any(), anyInt(), any())).thenReturn(STATUS_ERROR);
        assertFalse(mMessageHandler.registerListener(RESOURCE_URI, mListener1));
    }

    @Test
    public void testRegisterListenerOtherRegistered() {
        assertTrue(mMessageHandler.registerListener(RESOURCE_URI, mListener1));
        assertTrue(mMessageHandler.registerListener(RESOURCE_URI, mListener2));
    }

    @Test
    public void testUnregisterListener() {
        testRegisterListener();
        assertTrue(mMessageHandler.unregisterListener(RESOURCE_URI, mListener1));
    }

    @Test
    public void testUnregisterListenerOtherRegistered() {
        testRegisterListenerOtherRegistered();
        assertTrue(mMessageHandler.unregisterListener(RESOURCE_URI, mListener1));
        assertTrue(mMessageHandler.unregisterListener(RESOURCE_URI, mListener2));
    }

    @Test
    public void testUnregisterListenerNotRegistered() {
        assertFalse(mMessageHandler.unregisterListener(RESOURCE_URI, mListener1));
    }

    @Test
    public void testRegisterRpcListener() {
        assertTrue(mMessageHandler.registerRpcListener(METHOD_URI, mRpcListener1));
    }

    @Test
    public void testRegisterRpcListenerRejected() {
        when(mUBus.enableDispatching(any(), anyInt(), any())).thenReturn(STATUS_ERROR);
        assertFalse(mMessageHandler.registerRpcListener(METHOD_URI, mRpcListener1));
    }

    @Test
    public void testRegisterRpcListenerOtherRegistered() {
        assertTrue(mMessageHandler.registerRpcListener(METHOD_URI, mRpcListener1));
        assertFalse(mMessageHandler.registerRpcListener(METHOD_URI, mRpcListener2));
    }

    @Test
    public void testUnregisterRpcListener() {
        testRegisterRpcListener();
        assertTrue(mMessageHandler.unregisterRpcListener(METHOD_URI, mRpcListener1));
    }

    @Test
    public void testUnregisterRpcListenerNotRegistered() {
        assertTrue(mMessageHandler.registerRpcListener(METHOD_URI, mRpcListener1));
        assertFalse(mMessageHandler.unregisterRpcListener(METHOD_URI, mRpcListener2));
    }

    @Test
    public void testUnregisterAllListeners() {
        registerListeners();
        assertTrue(mMessageHandler.isRegistered(RESOURCE_URI, mListener1));
        assertTrue(mMessageHandler.isRegistered(METHOD_URI, mRpcListener1));
        assertFalse(mMessageHandler.isRegistered(RESOURCE_URI, mListener2));
        assertFalse(mMessageHandler.isRegistered(METHOD_URI, mRpcListener2));
        mMessageHandler.unregisterAllListeners();
        assertFalse(mMessageHandler.isRegistered(RESOURCE_URI, mListener1));
        assertFalse(mMessageHandler.isRegistered(METHOD_URI, mRpcListener1));
    }
    
    private void registerListeners() {
        assertTrue(mMessageHandler.registerListener(RESOURCE_URI, mListener1));
        assertTrue(mMessageHandler.registerRpcListener(METHOD_URI, mRpcListener1));
    }

    @Test
    public void testOnReceiveGenericMessage() {
        registerListeners();
        final UMessage message = buildPublishMessage(RESOURCE_URI);
        mMessageHandler.onReceive(message);
        verify(mListener1, times(1)).onReceive(message);
    }

    @Test
    public void testOnReceiveGenericMessageUnregistered() {
        final UMessage message = buildPublishMessage(RESOURCE2_URI);
        mMessageHandler.onReceive(message);
        verify(mListener1, never()).onReceive(message);
        verify(mRpcListener1, never()).onReceive(eq(message), any());
    }

    @Test
    public void testOnReceiveRequestMessage() {
        registerListeners();
        final UMessage requestMessage = buildRequestMessage(RESPONSE_URI, METHOD_URI);
        mMessageHandler.onReceive(requestMessage);
        verify(mRpcListener1, times(1)).onReceive(eq(requestMessage), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testOnReceiveRequestMessageSendResponse() {
        registerListeners();
        final UMessage requestMessage = buildRequestMessage(RESPONSE_URI, METHOD_URI);
        mMessageHandler.onReceive(requestMessage);
        final ArgumentCaptor<CompletableFuture<UPayload>> captor = ArgumentCaptor.forClass(CompletableFuture.class);
        verify(mRpcListener1, times(1)).onReceive(eq(requestMessage), captor.capture());
        final CompletableFuture<UPayload> responseFuture = captor.getValue();
        responseFuture.complete(PAYLOAD);
        verify(mUBus, times(1)).send(argThat(message -> {
            assertEquals(PAYLOAD, message.getPayload());
            assertEquals(METHOD_URI, message.getAttributes().getSource());
            assertEquals(RESPONSE_URI, message.getAttributes().getSink());
            return true;
        }), eq(mClientToken));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testOnReceiveRequestMessageSendResponseWithCommStatus() {
        registerListeners();
        final UMessage requestMessage = buildRequestMessage(RESPONSE_URI, METHOD_URI);
        mMessageHandler.onReceive(requestMessage);
        final ArgumentCaptor<CompletableFuture<UPayload>> captor = ArgumentCaptor.forClass(CompletableFuture.class);
        verify(mRpcListener1, times(1)).onReceive(eq(requestMessage), captor.capture());
        final CompletableFuture<UPayload> responseFuture = captor.getValue();
        responseFuture.completeExceptionally(new UStatusException(UCode.ABORTED, "Aborted"));
        verify(mUBus, times(1)).send(argThat(message -> {
            assertEquals(UPayload.getDefaultInstance(), message.getPayload());
            assertEquals(METHOD_URI, message.getAttributes().getSource());
            assertEquals(RESPONSE_URI, message.getAttributes().getSink());
            assertEquals(UCode.ABORTED_VALUE, message.getAttributes().getCommstatus());
            return true;
        }), eq(mClientToken));
    }


    @Test
    @SuppressWarnings("unchecked")
    public void testOnReceiveRequestMessageInvalidResponse() {
        registerListeners();
        final UMessage requestMessage = buildRequestMessage(RESPONSE_URI, METHOD_URI);
        mMessageHandler.onReceive(requestMessage);
        final ArgumentCaptor<CompletableFuture<UPayload>> captor = ArgumentCaptor.forClass(CompletableFuture.class);
        verify(mRpcListener1, times(1)).onReceive(eq(requestMessage), captor.capture());
        final CompletableFuture<UPayload> responseFuture = captor.getValue();
        responseFuture.complete(null);
        verify(mUBus, times(0)).send(any(), eq(mClientToken));
    }

    @Test
    public void testOnReceiveRequestMessageUnregistered() {
        registerListeners();
        final UMessage message = buildRequestMessage(RESPONSE_URI, METHOD2_URI);
        mMessageHandler.onReceive(message);
        verify(mListener1, never()).onReceive(message);
        verify(mRpcListener1, never()).onReceive(eq(message), any());
    }

    @Test
    public void testOnReceiveResponseMessage() {
        registerListeners();
        final CompletableFuture<UMessage> responseFuture =
                mMessageHandler.getRpcExecutor().invokeMethod(METHOD_URI, PAYLOAD, CallOptions.DEFAULT).toCompletableFuture();
        final ArgumentCaptor<UMessage> captor = ArgumentCaptor.forClass(UMessage.class);
        verify(mUBus, timeout(DELAY_LONG_MS).times(1)).send(captor.capture(), eq(mClientToken));
        assertFalse(responseFuture.isDone());
        final UMessage responseMessage = buildResponseMessage(captor.getValue());
        mMessageHandler.onReceive(responseMessage);
        assertTrue(responseFuture.isDone());
    }

    @Test
    public void testOnReceiveUnknownMessage() {
        registerListeners();
        final UMessage message = buildMessage(PAYLOAD, UAttributes.getDefaultInstance());
        mMessageHandler.onReceive(message);
        verify(mListener1, never()).onReceive(message);
        verify(mRpcListener1, never()).onReceive(eq(message), any());
        verify(mUBus, timeout(DELAY_MS).times(0)).send(message, mClientToken);
    }
}
