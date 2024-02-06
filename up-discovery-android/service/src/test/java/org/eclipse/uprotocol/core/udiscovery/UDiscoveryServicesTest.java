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

import static org.eclipse.uprotocol.common.util.UStatusUtils.STATUS_OK;
import static org.eclipse.uprotocol.common.util.UStatusUtils.buildStatus;
import static org.eclipse.uprotocol.common.util.UStatusUtils.toStatus;
import static org.eclipse.uprotocol.common.util.log.Formatter.join;
import static org.eclipse.uprotocol.common.util.log.Formatter.tag;
import static org.eclipse.uprotocol.core.udiscovery.common.Constants.TOPIC_NODE_NOTIFICATION;
import static org.eclipse.uprotocol.core.udiscovery.common.Constants.UNEXPECTED_PAYLOAD;
import static org.eclipse.uprotocol.core.udiscovery.db.JsonNodeTest.REGISTRY_JSON;
import static org.eclipse.uprotocol.core.udiscovery.internal.Utils.hasCharAt;
import static org.eclipse.uprotocol.core.udiscovery.v3.UDiscovery.METHOD_ADD_NODES;
import static org.eclipse.uprotocol.core.udiscovery.v3.UDiscovery.METHOD_DELETE_NODES;
import static org.eclipse.uprotocol.core.udiscovery.v3.UDiscovery.METHOD_FIND_NODES;
import static org.eclipse.uprotocol.core.udiscovery.v3.UDiscovery.METHOD_FIND_NODE_PROPERTIES;
import static org.eclipse.uprotocol.core.udiscovery.v3.UDiscovery.METHOD_LOOKUP_URI;
import static org.eclipse.uprotocol.core.udiscovery.v3.UDiscovery.METHOD_REGISTER_FOR_NOTIFICATIONS;
import static org.eclipse.uprotocol.core.udiscovery.v3.UDiscovery.METHOD_UNREGISTER_FOR_NOTIFICATIONS;
import static org.eclipse.uprotocol.core.udiscovery.v3.UDiscovery.METHOD_UPDATE_NODE;
import static org.eclipse.uprotocol.core.udiscovery.v3.UDiscovery.METHOD_UPDATE_PROPERTY;
import static org.eclipse.uprotocol.transport.builder.UPayloadBuilder.packToAny;
import static org.eclipse.uprotocol.transport.builder.UPayloadBuilder.unpack;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;

import com.google.protobuf.Any;

import org.eclipse.uprotocol.UPClient;
import org.eclipse.uprotocol.common.UStatusException;
import org.eclipse.uprotocol.common.util.log.Key;
import org.eclipse.uprotocol.core.udiscovery.v3.AddNodesRequest;
import org.eclipse.uprotocol.core.udiscovery.v3.DeleteNodesRequest;
import org.eclipse.uprotocol.core.udiscovery.v3.FindNodePropertiesRequest;
import org.eclipse.uprotocol.core.udiscovery.v3.FindNodePropertiesResponse;
import org.eclipse.uprotocol.core.udiscovery.v3.FindNodesRequest;
import org.eclipse.uprotocol.core.udiscovery.v3.FindNodesResponse;
import org.eclipse.uprotocol.core.udiscovery.v3.LookupUriResponse;
import org.eclipse.uprotocol.core.udiscovery.v3.Node;
import org.eclipse.uprotocol.core.udiscovery.v3.NotificationsRequest;
import org.eclipse.uprotocol.core.udiscovery.v3.PropertyValue;
import org.eclipse.uprotocol.core.udiscovery.v3.UpdateNodeRequest;
import org.eclipse.uprotocol.core.udiscovery.v3.UpdatePropertyRequest;
import org.eclipse.uprotocol.rpc.CallOptions;
import org.eclipse.uprotocol.rpc.URpcListener;
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
import org.eclipse.uprotocol.v1.UUri;
import org.eclipse.uprotocol.v1.UUriBatch;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowLog;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;

@SuppressWarnings({"java:S1200", "java:S3008", "java:S1134", "java:S2925", "java:S3415",
        "java:S5845"})
@RunWith(RobolectricTestRunner.class)
public class UDiscoveryServicesTest extends TestBase {
    private static final String TAG = tag(SERVICE.getName());
    private static UStatus mFailedStatus;
    private static UStatus mNotFoundStatus;
    private static UMessage mLookupUriUMsg;
    private static UMessage mFindNodeUMsg;
    private static UMessage mFindNodePropertiesUMsg;
    private static UMessage mUpdateNodeUMsg;
    private static UMessage mAddNodesUMsg;
    private static UMessage mDeleteNodesUMsg;
    private static UMessage mRegisterUMsg;
    private static UMessage mUnRegisterUMsg;
    private static UMessage mUpdatePropertyUMsg;
    private static LookupUriResponse mLookupUriResponse;
    private static FindNodesResponse mFindNodesResponse;
    private static FindNodePropertiesResponse mFindNodePropertiesResponse;
    @Rule
    public MockitoRule rule = MockitoJUnit.rule();
    private UDiscoveryService mService;
    private URpcListener mHandler;
    @Mock
    private UPClient mUpClient;
    @Mock
    private RPCHandler mRpcHandler;
    @Mock
    private ResourceLoader mResourceLoader;
    private Context mContext;


    private static void setLogLevel(int level) {
        UDiscoveryService.VERBOSE = (level <= Log.VERBOSE);
        UDiscoveryService.DEBUG = (level <= Log.DEBUG);
    }

    @BeforeClass
    public static void init() {
        mLookupUriUMsg = buildUMessage(METHOD_LOOKUP_URI, packToAny(UUri.getDefaultInstance()), true);

        mFindNodeUMsg = buildUMessage(METHOD_FIND_NODES, packToAny(FindNodesRequest.getDefaultInstance()), true);

        mUpdateNodeUMsg = buildUMessage(METHOD_UPDATE_NODE, packToAny(UpdateNodeRequest.getDefaultInstance()), true);

        mFindNodePropertiesUMsg = buildUMessage(METHOD_FIND_NODE_PROPERTIES,
                packToAny(FindNodePropertiesRequest.getDefaultInstance()), true);

        mAddNodesUMsg = buildUMessage(METHOD_ADD_NODES, packToAny(AddNodesRequest.getDefaultInstance()), true);

        mDeleteNodesUMsg = buildUMessage(METHOD_DELETE_NODES, packToAny(DeleteNodesRequest.getDefaultInstance()), true);

        mRegisterUMsg = buildUMessage(METHOD_REGISTER_FOR_NOTIFICATIONS,
                packToAny(NotificationsRequest.getDefaultInstance()), true);

        mUnRegisterUMsg = buildUMessage(METHOD_UNREGISTER_FOR_NOTIFICATIONS,
                packToAny(NotificationsRequest.getDefaultInstance()), true);

        mUpdatePropertyUMsg = buildUMessage(METHOD_UPDATE_PROPERTY,
                packToAny(UpdatePropertyRequest.getDefaultInstance()), true);

        UEntity entity1 = UEntity.newBuilder().setName("body.cabin_climate/1").build();
        UEntity entity2 = UEntity.newBuilder().setName("body.cabin_climate/2").build();
        UEntity entity3 = UEntity.newBuilder().setName("body.cabin_climate/3").build();
        UUri uri1 = UUri.newBuilder().setAuthority(TEST_AUTHORITY).setEntity(entity1).build();
        UUri uri2 = UUri.newBuilder().setAuthority(TEST_AUTHORITY).setEntity(entity2).build();
        UUri uri3 = UUri.newBuilder().setAuthority(TEST_AUTHORITY).setEntity(entity3).build();

        LookupUriResponse.Builder lookupUriRespBld = LookupUriResponse.newBuilder()
                .setUris(UUriBatch.newBuilder().addUris(uri1).addUris(uri2).addUris(uri3));
        mLookupUriResponse = lookupUriRespBld.build();

        Node node = jsonToNode(REGISTRY_JSON);
        mFindNodesResponse = FindNodesResponse.newBuilder().addNodes(node).setStatus(buildStatus(UCode.OK, "OK")).
                build();

        PropertyValue propString = PropertyValue.newBuilder().setUString("hello world").build();
        PropertyValue propInteger = PropertyValue.newBuilder().setUInteger(2024).build();
        PropertyValue propBoolean = PropertyValue.newBuilder().setUBoolean(true).build();

        FindNodePropertiesResponse.Builder fnpRespBld = FindNodePropertiesResponse.newBuilder();
        fnpRespBld.putProperties("message", propString);
        fnpRespBld.putProperties("year", propInteger);
        fnpRespBld.putProperties("enabled", propBoolean);
        mFindNodePropertiesResponse = fnpRespBld.build();

        mFailedStatus = buildStatus(UCode.FAILED_PRECONDITION, "test exception");

        mNotFoundStatus = buildStatus(UCode.NOT_FOUND, "NOT FOUND");
    }

    private static UMessage buildUMessage(String methodUri, UPayload uPayload, boolean isSink) {
        UResource uResource = UResourceBuilder.forRpcRequest(methodUri);
        UUri uUri = UUri.newBuilder().setEntity(SERVICE).setResource(uResource).build();
        UAttributesBuilder uAttributesBuilder = UAttributesBuilder.request(UPriority.UPRIORITY_CS4, uUri, TTL);
        if (isSink) {
            return UMessage.newBuilder().setAttributes(uAttributesBuilder.build()).setPayload(uPayload).build();
        }
        return UMessage.newBuilder().setAttributes(uAttributesBuilder.build()).clearAttributes().setPayload(uPayload).build();
    }

    @Before
    public void setUp() throws InterruptedException {
        ShadowLog.stream = System.out;

        CompletableFuture<UStatus> response = CompletableFuture.completedFuture(STATUS_OK);
        when(mUpClient.connect()).thenReturn(response);
        when(mResourceLoader.initializeLDS()).thenReturn(ResourceLoader.InitLDSCode.SUCCESS);
        when(mUpClient.isConnected()).thenReturn(true);

        UStatus okStatus = buildStatus(UCode.OK, "OK");
        when(mUpClient.registerRpcListener(any(), any())).thenReturn(okStatus);
        UMessage message = UMessage.newBuilder().setPayload(packToAny(STATUS_OK)).build();
        CompletableFuture<UMessage> responseCreateTopic = CompletableFuture.completedFuture(message);
        UStatusException statusException = new UStatusException(UCode.UNKNOWN, "Unable to connect");
        CompletableFuture<UMessage> responseCreateTopicException = CompletableFuture.failedFuture(statusException);
        setLogLevel(Log.DEBUG);
        when(mUpClient.invokeMethod(TOPIC_NODE_NOTIFICATION, UPayload.getDefaultInstance(), CallOptions.DEFAULT)).
                thenReturn(responseCreateTopic);
        Thread.sleep(100);
        setLogLevel(Log.INFO);
        when(mUpClient.invokeMethod(TOPIC_NODE_NOTIFICATION, UPayload.getDefaultInstance(), CallOptions.DEFAULT)).
                thenReturn(responseCreateTopicException);

        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        mService = new UDiscoveryService(mContext, mRpcHandler, mUpClient, mResourceLoader);

        // sleep to ensure registerAllMethods completes in the async thread before the verify
        // registerEventListener call below
        Thread.sleep(100);

        // capture the request event listener to inject rpc request cloud events
        ArgumentCaptor<URpcListener> captor = ArgumentCaptor.forClass(
                URpcListener.class);
        verify(mUpClient, atLeastOnce()).registerRpcListener(any(UUri.class), captor.capture());
        mHandler = captor.getValue();
    }

    @Test
    public void initialization_test() {
        assertTrue(true);
    }


    @Test
    public void negative_upClient_connect_exception() {
        setLogLevel(Log.VERBOSE);
        CompletableFuture<UStatus> connectFut = CompletableFuture.completedFuture(mFailedStatus);
        UPClient mockLink = mock(UPClient.class);
        when(mockLink.connect()).thenReturn(connectFut);
        boolean bException = false;
        try {
            new UDiscoveryService(mContext, mRpcHandler, mockLink, mResourceLoader);
        } catch (CompletionException e) {
            bException = true;
            Log.e(TAG, join(Key.MESSAGE, "negative_upClient_connect_exception", Key.FAILURE, toStatus(e)));
        }
        assertTrue(bException);
    }

    @Test
    public void negative_upClient_isConnected_false() {
        UPClient mockLink = mock(UPClient.class);
        //USubscription.Stub mockStub = mock(USubscription.Stub.class);
        CompletableFuture<UStatus> connectFut = CompletableFuture.completedFuture(STATUS_OK);

        when(mockLink.connect()).thenReturn(connectFut);
        when(mockLink.isConnected()).thenReturn(false);

        new UDiscoveryService(mContext, mRpcHandler, mockLink, mResourceLoader);

        verify(mockLink, never()).registerRpcListener(any(UUri.class),
                any(URpcListener.class));
        //verify(mockStub, never()).createTopic(any(CreateTopicRequest.class));
    }

    @Test
    public void negative_handler_uninitialized_exception() throws InterruptedException {
        setLogLevel(Log.DEBUG);
        ResourceLoader mockLoader = mock(ResourceLoader.class);
        when(mockLoader.initializeLDS()).thenReturn(ResourceLoader.InitLDSCode.FAILURE);
        new UDiscoveryService(mContext, mRpcHandler, mUpClient, mockLoader);

        // sleep to ensure registerAllMethods completes in the async thread before the verify
        // registerEventListener call below
        Thread.sleep(100);

        // capture the request event listener to inject rpc request cloud events
        ArgumentCaptor<URpcListener> captor = ArgumentCaptor.forClass(
                URpcListener.class);
        verify(mUpClient, atLeastOnce()).registerRpcListener(any(UUri.class), captor.capture());
        URpcListener handler = captor.getValue();

        List<UMessage> UMessageList = List.of(mLookupUriUMsg,
                mFindNodeUMsg,
                mAddNodesUMsg,
                mUpdateNodeUMsg,
                mDeleteNodesUMsg,
                mUpdatePropertyUMsg,
                mFindNodePropertiesUMsg,
                mRegisterUMsg,
                mUnRegisterUMsg);
        UPayload response = packToAny(mFailedStatus);
        for (int i = 0; i < UMessageList.size(); i++) {
            CompletableFuture<UPayload> fut = new CompletableFuture<>();
            handler.onReceive(UMessageList.get(i), fut);
            fut.whenComplete((result, ex) -> {
                assertEquals(response, result);
                assertNotNull(ex);
            });
        }
    }

    @Test
    public void negative_upClient_registerMethod_exception() throws InterruptedException {
        setLogLevel(Log.VERBOSE);
        CompletableFuture<UStatus> connectFut = CompletableFuture.completedFuture(STATUS_OK);
        when(mUpClient.connect()).thenReturn(connectFut);

        when(mUpClient.registerRpcListener(any(UUri.class),
                any(URpcListener.class))).thenReturn(mFailedStatus);

        new UDiscoveryService(mContext, mRpcHandler, mUpClient, mResourceLoader);
        // wait for register async tasks to complete
        Thread.sleep(100);
        verify(mUpClient, atLeastOnce()).registerRpcListener(any(UUri.class),
                any(URpcListener.class));
    }

    @Test
    public void negative_upClient_unRegisterMethod_exception() throws InterruptedException {
        setLogLevel(Log.DEBUG);
        //when(mDatabaseLoader.getAuthority()).thenReturn(TEST_AUTHORITY);
        when(mUpClient.unregisterRpcListener(any(UUri.class),
                any(URpcListener.class))).thenReturn(mFailedStatus);

        mService.onDestroy();
        // wait for unregister async tasks to complete
        Thread.sleep(100);
        verify(mUpClient, atLeastOnce()).unregisterRpcListener(any(UUri.class),
                any(URpcListener.class));
    }

    @Test
    public void negative_withoutSink_handleRequestEvent() {
        UUri uri = UUri.newBuilder().setEntity(TEST_ENTITY).
                setResource(UResourceBuilder.forRpcRequest("fakeMethod")).build();
        UMessage uMsg = buildUMessage(uri.toString(), packToAny(Any.getDefaultInstance()), false);
        CompletableFuture<UPayload> fut = new CompletableFuture<>();
        mHandler.onReceive(uMsg, fut);
        fut.whenComplete((result, ex) -> {
            assertNotNull(ex);
            assertEquals(UCode.INVALID_ARGUMENT_VALUE, toStatus(ex).getCode());
        });
    }

    @Test
    public void negative_handleRequestEvent() {
        UAttributes.newBuilder().setSink(UUri.newBuilder().setAuthority(UAuthority.newBuilder().setName("hello"))).
                setSink(UUri.newBuilder().setAuthority(UAuthority.newBuilder().setName("hello"))).build();
        UUri uri = UUri.newBuilder().setEntity(SERVICE).
                setResource(UResourceBuilder.forRpcRequest("fakeMethod")).build();
        UMessage uMsg = buildUMessage(uri.toString(), packToAny(Any.getDefaultInstance()), true);
        CompletableFuture<UPayload> fut = new CompletableFuture<>();
        mHandler.onReceive(uMsg, fut);
        fut.whenComplete((result, ex) -> {
            assertNotNull(ex);
            assertEquals(UCode.INVALID_ARGUMENT_VALUE, toStatus(ex).getCode());
        });
    }

    @Test
    public void positive_executeLookupUri_LDS() {
        UPayload lookupUriResult = packToAny(mLookupUriResponse);
        when(mRpcHandler.processLookupUriFromLDS(any(UMessage.class))).thenReturn(lookupUriResult);

        CompletableFuture<UPayload> fut = new CompletableFuture<>();
        mHandler.onReceive(mLookupUriUMsg, fut);

        fut.whenComplete((result, ex) -> {
            assertEquals(lookupUriResult, result);
            assertNull(ex);
        });
    }

    @Test
    public void positive_executeLookupUri_LDS_not_found() {
        Any responseMessage = Any.pack(mLookupUriResponse);
        UPayload lookupUriResult = packToAny(mNotFoundStatus);
        when(mRpcHandler.processLookupUriFromLDS(any(UMessage.class))).thenReturn(lookupUriResult);

        UMessage uMsg = buildUMessage(METHOD_LOOKUP_URI, packToAny(UMessage.getDefaultInstance()), true);
        CompletableFuture<UPayload> fut = new CompletableFuture<>();
        mHandler.onReceive(uMsg, fut);

        fut.whenComplete((result, ex) -> {
            assertEquals(responseMessage, result);
            assertNull(ex);
        });
    }

    @Test
    public void negative_executeLookupUri_throw_exception() {
        setLogLevel(Log.DEBUG);
        CompletableFuture<UPayload> fut = new CompletableFuture<>();
        mHandler.onReceive(mLookupUriUMsg, fut);

        fut.whenComplete((result, ex) -> {
            assertNull(ex);
            LookupUriResponse resp = unpack(result, LookupUriResponse.class).
                    orElseThrow(() -> new UStatusException(UCode.INVALID_ARGUMENT, UNEXPECTED_PAYLOAD));
            assertEquals(UCode.INVALID_ARGUMENT_VALUE, resp.getStatus().getCodeValue());
        });
    }

    @Test
    public void positive_executeFindNodes_LDS() {
        Any response = Any.pack(mFindNodesResponse);
        UPayload findNodeResult = packToAny(response);
        when(mRpcHandler.processFindNodesFromLDS(any(UMessage.class))).thenReturn(findNodeResult);

        CompletableFuture<UPayload> fut = new CompletableFuture<>();
        mHandler.onReceive(mFindNodeUMsg, fut);

        fut.whenComplete((result, ex) -> {
            assertEquals(response, result);
            assertNull(ex);
        });
    }

    @Test
    public void positive_executeFindNodes_LDS_not_found() {
        Any response = Any.pack(mFindNodesResponse);
        UPayload findNodeResult = packToAny(response);
        when(mRpcHandler.processFindNodesFromLDS(any(UMessage.class))).thenReturn(findNodeResult);

        UMessage uMsg = buildUMessage(METHOD_FIND_NODES, UPayload.getDefaultInstance(), true);
        CompletableFuture<UPayload> fut = new CompletableFuture<>();
        mHandler.onReceive(uMsg, fut);

        fut.whenComplete((result, ex) -> {
            assertEquals(response, result);
            assertNull(ex);
        });
    }

    @Test
    public void executeFindNodes_throw_exception() {
        setLogLevel(Log.DEBUG);
        CompletableFuture<UPayload> fut = new CompletableFuture<>();
        mHandler.onReceive(mFindNodeUMsg, fut);

        fut.whenComplete((result, ex) -> {
            assertNull(ex);
            FindNodesResponse resp = unpack(result, FindNodesResponse.class).
                    orElseThrow(() -> new UStatusException(UCode.INVALID_ARGUMENT, UNEXPECTED_PAYLOAD));
            assertEquals(UCode.INVALID_ARGUMENT, resp.getStatus().getCode());
            assertNull(ex);
        });
    }

    @Test
    public void positive_executeUpdateNode() {
        UStatus okSts = buildStatus(UCode.OK, "OK");
        UPayload response = packToAny(okSts);
        when(mRpcHandler.processLDSUpdateNode(any(UMessage.class))).thenReturn(response);

        CompletableFuture<UPayload> fut = new CompletableFuture<>();
        mHandler.onReceive(mUpdateNodeUMsg, fut);

        fut.whenComplete((result, ex) -> {
            assertEquals(response, result);
            assertNull(ex);
        });
    }

    @Test
    public void negative_executeUpdateNode_throw_exception() {
        setLogLevel(Log.DEBUG);
        CompletableFuture<UPayload> fut = new CompletableFuture<>();
        mHandler.onReceive(mUpdateNodeUMsg, fut);

        fut.whenComplete((result, ex) -> {
            assertNull(ex);
            UStatus resp = unpack(result, UStatus.class).
                    orElseThrow(() -> new UStatusException(UCode.INVALID_ARGUMENT, UNEXPECTED_PAYLOAD));
            assertEquals(UCode.FAILED_PRECONDITION_VALUE, resp.getCode());
        });
    }

    @Test
    public void positive_executeFindNodesProperty() {
        UPayload response = packToAny(mFindNodePropertiesResponse);
        when(mRpcHandler.processFindNodeProperties(any(UMessage.class))).thenReturn(response);

        CompletableFuture<UPayload> fut = new CompletableFuture<>();
        mHandler.onReceive(mFindNodePropertiesUMsg, fut);

        fut.whenComplete((result, ex) -> {
            assertEquals(response, result);
            assertNull(ex);
        });
    }

    @Test
    public void negative_executeFindNodesProperty_throw_exception() {
        setLogLevel(Log.DEBUG);
        CompletableFuture<UPayload> fut = new CompletableFuture<>();
        mHandler.onReceive(mFindNodePropertiesUMsg, fut);

        fut.whenComplete((result, ex) -> {
            assertNull(ex);
            UStatus resp = unpack(result, UStatus.class).
                    orElseThrow(() -> new UStatusException(UCode.INVALID_ARGUMENT, UNEXPECTED_PAYLOAD));
            assertEquals(UCode.FAILED_PRECONDITION_VALUE, resp.getCode());
        });
    }

    @Test
    public void positive_executeAddNodes() {
        UStatus okSts = buildStatus(UCode.OK, "OK");
        UPayload response = packToAny(okSts);
        when(mRpcHandler.processAddNodesLDS(any(UMessage.class))).thenReturn(response);

        CompletableFuture<UPayload> fut = new CompletableFuture<>();
        mHandler.onReceive(mAddNodesUMsg, fut);

        fut.whenComplete((result, ex) -> {
            assertEquals(response, result);
            assertNull(ex);
        });
    }

    @Test
    public void negative_executeAddNodes_throw_exception() {
        setLogLevel(Log.DEBUG);
        CompletableFuture<UPayload> fut = new CompletableFuture<>();
        mHandler.onReceive(mAddNodesUMsg, fut);

        fut.whenComplete((result, ex) -> {
            assertNull(ex);
            UStatus resp = unpack(result, UStatus.class).
                    orElseThrow(() -> new UStatusException(UCode.INVALID_ARGUMENT, UNEXPECTED_PAYLOAD));
            assertEquals(UCode.FAILED_PRECONDITION_VALUE, resp.getCode());
        });
    }

    @Test
    public void positive_executeDeleteNodes() {
        UStatus okSts = buildStatus(UCode.OK, "OK");
        UPayload response = packToAny(okSts);
        when(mRpcHandler.processDeleteNodes(any(UMessage.class))).thenReturn(response);

        CompletableFuture<UPayload> fut = new CompletableFuture<>();
        mHandler.onReceive(mDeleteNodesUMsg, fut);

        fut.whenComplete((result, ex) -> {
            assertEquals(response, result);
            assertNull(ex);
        });
    }

    @Test
    public void negative_executeDeleteNodes_throw_exception() {
        CompletableFuture<UPayload> fut = new CompletableFuture<>();
        mHandler.onReceive(mDeleteNodesUMsg, fut);

        fut.whenComplete((result, ex) -> {
            assertNull(ex);
            UStatus resp = unpack(result, UStatus.class).
                    orElseThrow(() -> new UStatusException(UCode.INVALID_ARGUMENT, UNEXPECTED_PAYLOAD));
            assertEquals(UCode.FAILED_PRECONDITION_VALUE, resp.getCode());
        });
    }

    @Test
    public void positive_executeUpdateProperty() {
        UStatus okSts = buildStatus(UCode.OK, "OK");
        UPayload response = packToAny(okSts);
        when(mRpcHandler.processLDSUpdateProperty(any(UMessage.class))).thenReturn(response);

        CompletableFuture<UPayload> fut = new CompletableFuture<>();
        mHandler.onReceive(mUpdatePropertyUMsg, fut);

        fut.whenComplete((result, ex) -> {
            assertEquals(response, result);
            assertNull(ex);
        });

    }

    @Test
    public void negative_executeUpdateProperty() {
        CompletableFuture<UPayload> fut = new CompletableFuture<>();
        mHandler.onReceive(mUpdatePropertyUMsg, fut);

        fut.whenComplete((result, ex) -> {
            assertNull(ex);
            UStatus resp = unpack(result, UStatus.class).
                    orElseThrow(() -> new UStatusException(UCode.INVALID_ARGUMENT, UNEXPECTED_PAYLOAD));
            assertEquals(UCode.FAILED_PRECONDITION_VALUE, resp.getCode());
        });
    }

    @Test
    public void positive_executeRegisterNotification() {
        CompletableFuture<UPayload> fut = new CompletableFuture<>();
        mHandler.onReceive(mRegisterUMsg, fut);

        fut.whenComplete((result, ex) -> {
            assertNull(ex);
            UStatus resp = unpack(result, UStatus.class).
                    orElseThrow(() -> new UStatusException(UCode.INVALID_ARGUMENT, UNEXPECTED_PAYLOAD));
            assertEquals(UCode.UNIMPLEMENTED_VALUE, resp.getCode());
        });
    }

    @Test
    public void negative_executeRegisterNotification_throw_exception() {
        CompletableFuture<UPayload> fut = new CompletableFuture<>();
        mHandler.onReceive(mRegisterUMsg, fut);

        fut.whenComplete((result, ex) -> {
            assertNull(ex);
            UStatus resp = unpack(result, UStatus.class).
                    orElseThrow(() -> new UStatusException(UCode.INVALID_ARGUMENT, UNEXPECTED_PAYLOAD));
            assertEquals(UCode.FAILED_PRECONDITION_VALUE, resp.getCode());
        });
    }

    @Test
    public void positive_executeUnRegisterNotification() {
        CompletableFuture<UPayload> fut = new CompletableFuture<>();
        mHandler.onReceive(mUnRegisterUMsg, fut);

        fut.whenComplete((result, ex) -> {
            assertNull(ex);
            UStatus resp = unpack(result, UStatus.class).
                    orElseThrow(() -> new UStatusException(UCode.INVALID_ARGUMENT, UNEXPECTED_PAYLOAD));
            assertEquals(UCode.UNIMPLEMENTED_VALUE, resp.getCode());
        });
    }

    @Test
    public void negative_executeUnregisterNotification_throw_exception() {
        CompletableFuture<UPayload> fut = new CompletableFuture<>();
        mHandler.onReceive(mUnRegisterUMsg, fut);

        fut.whenComplete((result, ex) -> {
            assertNull(ex);
            UStatus resp = unpack(result, UStatus.class).
                    orElseThrow(() -> new UStatusException(UCode.INVALID_ARGUMENT, UNEXPECTED_PAYLOAD));
            assertEquals(UCode.FAILED_PRECONDITION_VALUE, resp.getCode());
        });
    }

    @Test
    public void testOnCreate() {
        setLogLevel(Log.DEBUG);
        UDiscoveryService service = Robolectric.setupService(UDiscoveryService.class);
        assertNotEquals(mService, service);
        setLogLevel(Log.INFO);
        service = Robolectric.setupService(UDiscoveryService.class);
        assertNotEquals(mService, service);
    }

    @SuppressWarnings("AssertBetweenInconvertibleTypes")
    @Test
    public void testBinder() {
        setLogLevel(Log.DEBUG);
        assertNotEquals(mService, mService.onBind(new Intent()));
        setLogLevel(Log.INFO);
        assertNotEquals(mService, mService.onBind(new Intent()));
    }

    @Test
    public void shutDown() throws InterruptedException {
        //when(mDatabaseLoader.getAuthority()).thenReturn(TEST_AUTHORITY);
        UStatus ok = buildStatus(UCode.OK, "OK");
        when(mUpClient.unregisterRpcListener(any(UUri.class),
                any(URpcListener.class))).thenReturn(ok);
        when(mUpClient.isConnected()).thenReturn(true);
        when(mUpClient.unregisterRpcListener(any(UUri.class),
                any(URpcListener.class))).thenReturn(ok);
        UEntity entity = UEntity.newBuilder().setName("vcu.VIN.veh.gm.com").build();
        UUri uri = UUri.newBuilder().setAuthority(TEST_AUTHORITY).setEntity(entity).build();
        LookupUriResponse uriResponse = LookupUriResponse.newBuilder().setUris(UUriBatch.newBuilder().addUris(uri).build()).build();
        UPayload authorityDetails = packToAny(uriResponse);
        when(mRpcHandler.processLookupUriFromLDS(any(UMessage.class))).thenReturn(authorityDetails);
        mService.onDestroy();
        // wait for unregister async tasks to complete
        Thread.sleep(100);
        verify(mUpClient, atLeastOnce()).unregisterRpcListener(any(UUri.class),
                any(URpcListener.class));
    }

    @Test
    public void positive_serviceListener() {
        setLogLevel(Log.INFO);
        mService.onLifecycleChanged(mUpClient, true);
        AtomicBoolean flag = new AtomicBoolean(false);
        UPClient.ServiceLifecycleListener sl = (upClient, ready) -> flag.set(ready);
        sl.onLifecycleChanged(mUpClient, true);
        assertTrue(flag.get());
    }

    @Test
    public void negative_serviceListener() {
        setLogLevel(Log.DEBUG);
        mService.onLifecycleChanged(mUpClient, false);
        AtomicBoolean flag = new AtomicBoolean(false);
        UPClient.ServiceLifecycleListener sl = (upClient, ready) -> flag.set(ready);
        sl.onLifecycleChanged(mUpClient, false);
        assertFalse(flag.get());
    }

    @Test
    public void test_UtilsChatAt() {
        assertFalse(hasCharAt("", 1, '4'));
        assertFalse(hasCharAt("", -1, '4'));
    }
}
