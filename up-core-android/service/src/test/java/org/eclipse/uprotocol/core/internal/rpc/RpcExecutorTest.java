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
package org.eclipse.uprotocol.core.internal.rpc;

import static org.eclipse.uprotocol.common.util.UStatusUtils.STATUS_OK;
import static org.eclipse.uprotocol.common.util.UStatusUtils.buildStatus;
import static org.eclipse.uprotocol.common.util.UStatusUtils.toStatus;
import static org.eclipse.uprotocol.transport.builder.UPayloadBuilder.packToAny;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.os.Binder;
import android.os.IBinder;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.eclipse.uprotocol.core.TestBase;
import org.eclipse.uprotocol.core.ubus.UBus;
import org.eclipse.uprotocol.rpc.CallOptions;
import org.eclipse.uprotocol.v1.UCode;
import org.eclipse.uprotocol.v1.UMessage;
import org.eclipse.uprotocol.v1.UPayload;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class RpcExecutorTest extends TestBase {
    protected static final UPayload REQUEST_PAYLOAD = PAYLOAD;
    protected static final UPayload RESPONSE_PAYLOAD = packToAny(STATUS_OK);

    private final UBus mUBus = mock(UBus.class);
    private final IBinder mClientToken = new Binder();
    private RpcExecutor mRpcExecutor;

    @Before
    public void setUp() {
        mRpcExecutor = new RpcExecutor(mUBus, CLIENT, mClientToken);
        when(mUBus.send(any(), any())).thenReturn(STATUS_OK);
    }

    @Test
    public void testEmpty() {
        final RpcExecutor empty = RpcExecutor.empty();
        assertNotNull(empty);
        final CompletableFuture<UMessage> responseFuture =
                empty.invokeMethod(METHOD_URI, REQUEST_PAYLOAD, CallOptions.DEFAULT).toCompletableFuture();
        assertTrue(responseFuture.isCompletedExceptionally());
        assertFalse(empty.hasPendingRequests());
        final UMessage responseMessage = buildResponseMessage(buildRequestMessage());
        empty.onReceive(responseMessage);
        empty.onReceive(responseMessage.getSource(), responseMessage.getPayload(), responseMessage.getAttributes());
    }

    @Test
    @SuppressWarnings("BlockingMethodInNonBlockingContext")
    public void testInvokeMethod() throws Exception {
        final CompletableFuture<UMessage> responseFuture =
                mRpcExecutor.invokeMethod(METHOD_URI, REQUEST_PAYLOAD, CallOptions.DEFAULT).toCompletableFuture();
        assertTrue(mRpcExecutor.hasPendingRequests());
        final ArgumentCaptor<UMessage> captor = ArgumentCaptor.forClass(UMessage.class);
        verify(mUBus, timeout(DELAY_MS).times(1)).send(captor.capture(), any());
        assertFalse(responseFuture.isDone());
        final UMessage responseMessage = buildResponseMessage(captor.getValue(), RESPONSE_PAYLOAD, TTL);
        mRpcExecutor.onReceive(responseMessage);
        assertEquals(responseMessage, responseFuture.get(DELAY_MS, TimeUnit.MILLISECONDS));
        assertTrue(responseFuture.isDone());
        assertFalse(mRpcExecutor.hasPendingRequests());
    }

    @Test
    @SuppressWarnings("BlockingMethodInNonBlockingContext")
    public void testInvokeMethodCommunicationFailure() {
        final CompletableFuture<UMessage> responseFuture =
                mRpcExecutor.invokeMethod(METHOD_URI, REQUEST_PAYLOAD, CallOptions.DEFAULT).toCompletableFuture();
        assertTrue(mRpcExecutor.hasPendingRequests());
        final ArgumentCaptor<UMessage> captor = ArgumentCaptor.forClass(UMessage.class);
        verify(mUBus, timeout(DELAY_MS).times(1)).send(captor.capture(), any());
        assertFalse(responseFuture.isDone());
        final UMessage responseMessage = buildFailureResponseMessage(captor.getValue(), UCode.ABORTED);
        mRpcExecutor.onReceive(responseMessage.getSource(), responseMessage.getPayload(), responseMessage.getAttributes());
        final Exception exception = assertThrows(ExecutionException.class,
                () -> responseFuture.get(DELAY_MS, TimeUnit.MILLISECONDS));
        assertStatus(UCode.ABORTED, toStatus(exception));
        assertFalse(mRpcExecutor.hasPendingRequests());
    }

    @Test
    @SuppressWarnings("BlockingMethodInNonBlockingContext")
    public void testInvokeMethodSendFailed() {
        when(mUBus.send(any(), any())).thenReturn(buildStatus(UCode.ABORTED));
        final CompletableFuture<UMessage> responseFuture =
                mRpcExecutor.invokeMethod(METHOD_URI, REQUEST_PAYLOAD, CallOptions.DEFAULT).toCompletableFuture();
        verify(mUBus, timeout(DELAY_MS).times(1)).send(any(), any());
        final Exception exception = assertThrows(ExecutionException.class,
                () -> responseFuture.get(DELAY_MS, TimeUnit.MILLISECONDS));
        assertStatus(UCode.ABORTED, toStatus(exception));
        assertFalse(mRpcExecutor.hasPendingRequests());
    }

    @Test
    public void testResponseListenerUnexpectedType() {
        mRpcExecutor.onReceive(buildPublishMessage());
        assertFalse(mRpcExecutor.hasPendingRequests());
    }

    @Test
    public void testResponseListenerUnexpectedResponse() {
        mRpcExecutor.invokeMethod(METHOD_URI, PAYLOAD, CallOptions.DEFAULT);
        mRpcExecutor.onReceive(buildResponseMessage(buildRequestMessage()));
        assertTrue(mRpcExecutor.hasPendingRequests());
    }
}
