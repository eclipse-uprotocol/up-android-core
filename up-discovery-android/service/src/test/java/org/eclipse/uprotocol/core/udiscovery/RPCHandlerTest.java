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

import static org.eclipse.uprotocol.common.util.UStatusUtils.buildStatus;
import static org.eclipse.uprotocol.core.udiscovery.common.Constants.LDS_DB_FILENAME;
import static org.eclipse.uprotocol.core.udiscovery.common.Constants.UNEXPECTED_PAYLOAD;
import static org.eclipse.uprotocol.core.udiscovery.db.JsonNodeTest.REGISTRY_JSON;
import static org.eclipse.uprotocol.core.udiscovery.internal.Utils.toLongUri;
import static org.eclipse.uprotocol.core.udiscovery.v3.UDiscovery.METHOD_REGISTER_FOR_NOTIFICATIONS;
import static org.eclipse.uprotocol.core.udiscovery.v3.UDiscovery.METHOD_UNREGISTER_FOR_NOTIFICATIONS;
import static org.eclipse.uprotocol.transport.builder.UPayloadBuilder.packToAny;
import static org.eclipse.uprotocol.transport.builder.UPayloadBuilder.unpack;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;

import com.google.protobuf.InvalidProtocolBufferException;

import org.eclipse.uprotocol.common.UStatusException;
import org.eclipse.uprotocol.core.udiscovery.db.DiscoveryManager;
import org.eclipse.uprotocol.core.udiscovery.v3.AddNodesRequest;
import org.eclipse.uprotocol.core.udiscovery.v3.DeleteNodesRequest;
import org.eclipse.uprotocol.core.udiscovery.v3.FindNodePropertiesRequest;
import org.eclipse.uprotocol.core.udiscovery.v3.FindNodePropertiesResponse;
import org.eclipse.uprotocol.core.udiscovery.v3.FindNodesRequest;
import org.eclipse.uprotocol.core.udiscovery.v3.FindNodesResponse;
import org.eclipse.uprotocol.core.udiscovery.v3.LookupUriResponse;
import org.eclipse.uprotocol.core.udiscovery.v3.Node;
import org.eclipse.uprotocol.core.udiscovery.v3.NotificationsRequest;
import org.eclipse.uprotocol.core.udiscovery.v3.ObserverInfo;
import org.eclipse.uprotocol.core.udiscovery.v3.PropertyValue;
import org.eclipse.uprotocol.core.udiscovery.v3.UpdateNodeRequest;
import org.eclipse.uprotocol.core.udiscovery.v3.UpdatePropertyRequest;
import org.eclipse.uprotocol.v1.UCode;
import org.eclipse.uprotocol.v1.UEntity;
import org.eclipse.uprotocol.v1.UMessage;
import org.eclipse.uprotocol.v1.UPayload;
import org.eclipse.uprotocol.v1.UStatus;
import org.eclipse.uprotocol.v1.UUri;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowLog;

import java.io.IOException;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
public class RPCHandlerTest extends TestBase {

    private static final String TEST_INVALID_METHOD = "invalid method";
    public Context mContext;
    @Mock
    public Notifier mNotifier;
    @Mock
    public AssetManager mAssetManager;
    @Rule
    public MockitoRule rule = MockitoJUnit.rule();
    public RPCHandler mRPCHandler;
    public DiscoveryManager mDiscoveryManager;
    @Mock
    private ObserverManager mObserverManager;

    private static void setLogLevel(int level) {
        RPCHandler.DEBUG = (level <= Log.DEBUG);
        RPCHandler.VERBOSE = (level <= Log.VERBOSE);
    }

    @Before
    public void setUp() throws IOException {
        MockitoAnnotations.openMocks(this);
        ShadowLog.stream = System.out;
        setLogLevel(Log.INFO);
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        // initialize Discovery Manager
        initializeDb();
        mObserverManager = mock(ObserverManager.class);
        mRPCHandler = new RPCHandler(mContext, mAssetManager, mDiscoveryManager,
                mObserverManager);
    }

    @After
    public void shutdown() {
        mRPCHandler.shutdown();
    }

    public void initializeDb() {
        mDiscoveryManager = spy(new DiscoveryManager(mNotifier));
        mDiscoveryManager.init(TEST_AUTHORITY);
        Node node = jsonToNode(REGISTRY_JSON);
        UStatus status = mDiscoveryManager.updateNode(node, -1);
        assertEquals(UCode.OK, status.getCode());
    }

    @Test
    public void positive_persist() {
        String payload = mDiscoveryManager.export();
        mRPCHandler.persist(payload);
        verify(mAssetManager).writeFileToInternalStorage(mContext, LDS_DB_FILENAME, payload);
    }

    @Test
    public void positive_processLookupUri() throws InvalidProtocolBufferException {
        UEntity entity = UEntity.newBuilder().setName(TEST_ENTITY_NAME).build();
        UUri uri = UUri.newBuilder().setEntity(entity).build();
        UMessage uMsg = UMessage.newBuilder().setPayload(packToAny(uri)).build();

        UPayload uPayload = mRPCHandler.processLookupUriFromLDS(uMsg);
        LookupUriResponse response = unpack(uPayload, LookupUriResponse.class).
                orElseThrow(() -> new UStatusException(UCode.INVALID_ARGUMENT, UNEXPECTED_PAYLOAD));

        verify(mDiscoveryManager).lookupUri(uri);
        assertEquals(UCode.OK, response.getStatus().getCode());
    }

    @Test
    public void positive_processLookupUri_debug() throws InvalidProtocolBufferException {
        setLogLevel(Log.DEBUG);
        UEntity entity = UEntity.newBuilder().setName(TEST_ENTITY_NAME).build();

        UUri uri = UUri.newBuilder().setEntity(entity).build();
        UMessage uMsg = UMessage.newBuilder().setPayload(packToAny(uri)).build();

        UPayload uPayload = mRPCHandler.processLookupUriFromLDS(uMsg);
        LookupUriResponse response = unpack(uPayload, LookupUriResponse.class).
                orElseThrow(() -> new UStatusException(UCode.INVALID_ARGUMENT, UNEXPECTED_PAYLOAD));

        verify(mDiscoveryManager).lookupUri(uri);
        assertEquals(UCode.OK, response.getStatus().getCode());
    }

    @Test
    public void negative_processLookupUri_exception() throws InvalidProtocolBufferException {
        // pack incorrect protobuf message type
        UMessage uMsg = UMessage.newBuilder().setPayload(packToAny(UStatus.getDefaultInstance())).build();

        UPayload uPayload = mRPCHandler.processLookupUriFromLDS(uMsg);
        LookupUriResponse response = unpack(uPayload, LookupUriResponse.class).
                orElseThrow(() -> new UStatusException(UCode.INVALID_ARGUMENT, UNEXPECTED_PAYLOAD));

        verify(mDiscoveryManager, never()).lookupUri(any());
        assertEquals(UCode.INVALID_ARGUMENT, response.getStatus().getCode());
    }

    @Test
    public void positive_processFindNodes() throws InvalidProtocolBufferException {
        UUri uri = UUri.newBuilder().setAuthority(TEST_AUTHORITY).build();
        FindNodesRequest request = FindNodesRequest.newBuilder().setUri(toLongUri(uri)).build();

        UMessage uMsg = UMessage.newBuilder().setPayload(packToAny(request)).build();

        UPayload uPayload = mRPCHandler.processFindNodesFromLDS(uMsg);
        FindNodesResponse response = unpack(uPayload, FindNodesResponse.class).
                orElseThrow(() -> new UStatusException(UCode.INVALID_ARGUMENT, UNEXPECTED_PAYLOAD));

        verify(mDiscoveryManager).findNode(uri, -1);
        assertEquals(UCode.OK, response.getStatus().getCode());
    }

    @Test
    public void positive_processFindNodes_debug() throws InvalidProtocolBufferException {
        setLogLevel(Log.DEBUG);
        UUri uri = UUri.newBuilder().setAuthority(TEST_AUTHORITY).build();
        FindNodesRequest request = FindNodesRequest.newBuilder().setUri(toLongUri(uri)).setDepth(0).build();
        UMessage uMsg = UMessage.newBuilder().setPayload(packToAny(request)).build();

        UPayload uPayload = mRPCHandler.processFindNodesFromLDS(uMsg);
        FindNodesResponse response = unpack(uPayload, FindNodesResponse.class).
                orElseThrow(() -> new UStatusException(UCode.INVALID_ARGUMENT, UNEXPECTED_PAYLOAD));

        verify(mDiscoveryManager).findNode(uri, 0);
        assertEquals(UCode.OK, response.getStatus().getCode());
    }

    @Test
    public void negative_processFindNodes_exception() throws InvalidProtocolBufferException {
        // pack incorrect protobuf message type
        UMessage uMsg = UMessage.newBuilder().setPayload(packToAny(UStatus.getDefaultInstance())).build();

        UPayload uPayload = mRPCHandler.processFindNodesFromLDS(uMsg);
        ;
        FindNodesResponse response = unpack(uPayload, FindNodesResponse.class).
                orElseThrow(() -> new UStatusException(UCode.INVALID_ARGUMENT, UNEXPECTED_PAYLOAD));

        assertEquals(UCode.INVALID_ARGUMENT, response.getStatus().getCode());
    }

    @Test
    public void positive_processFindNodeProperties() throws InvalidProtocolBufferException {

        UEntity entity = UEntity.newBuilder().setName(TEST_ENTITY_NAME).build();
        UUri uri = UUri.newBuilder().setAuthority(TEST_AUTHORITY).setEntity(entity).build();
        FindNodePropertiesRequest request = FindNodePropertiesRequest.newBuilder()
                .setUri(toLongUri(uri))
                .addProperties(TEST_PROPERTY1)
                .addProperties(TEST_PROPERTY2)
                .build();
        UMessage uMsg = UMessage.newBuilder().setPayload(packToAny(request)).build();

        UPayload uPayload = mRPCHandler.processFindNodeProperties(uMsg);
        FindNodePropertiesResponse response = unpack(uPayload, FindNodePropertiesResponse.class).
                orElseThrow(() -> new UStatusException(UCode.INVALID_ARGUMENT, UNEXPECTED_PAYLOAD));

        verify(mDiscoveryManager).findNodeProperties(uri, request.getPropertiesList());
        assertEquals(UCode.OK, response.getStatus().getCode());
    }

    @Test
    public void positive_processFindNodeProperties_debug() throws InvalidProtocolBufferException {
        setLogLevel(Log.DEBUG);

        UEntity entity = UEntity.newBuilder().setName(TEST_ENTITY_NAME).build();
        UUri uri = UUri.newBuilder().setAuthority(TEST_AUTHORITY).setEntity(entity).build();
        FindNodePropertiesRequest request = FindNodePropertiesRequest.newBuilder()
                .setUri(toLongUri(uri))
                .addProperties(TEST_PROPERTY1)
                .addProperties(TEST_PROPERTY2)
                .build();
        UMessage uMsg = UMessage.newBuilder().setPayload(packToAny(request)).build();

        UPayload uPayload = mRPCHandler.processFindNodeProperties(uMsg);
        FindNodePropertiesResponse response = unpack(uPayload, FindNodePropertiesResponse.class).
                orElseThrow(() -> new UStatusException(UCode.INVALID_ARGUMENT, UNEXPECTED_PAYLOAD));

        verify(mDiscoveryManager).findNodeProperties(uri, request.getPropertiesList());
        assertEquals(UCode.OK, response.getStatus().getCode());
    }

    @Test
    public void negative_processFindNodeProperties_exception() throws InvalidProtocolBufferException {
        // pack incorrect protobuf message type
        UMessage uMsg = UMessage.newBuilder().setPayload(packToAny(UStatus.getDefaultInstance())).build();

        UPayload uPayload = mRPCHandler.processFindNodeProperties(uMsg);
        FindNodePropertiesResponse response = unpack(uPayload, FindNodePropertiesResponse.class).
                orElseThrow(() -> new UStatusException(UCode.INVALID_ARGUMENT, UNEXPECTED_PAYLOAD));

        verifyNoInteractions(mAssetManager);
        assertEquals(UCode.INVALID_ARGUMENT, response.getStatus().getCode());
    }

    @Test
    public void positive_processUpdateNode() throws InvalidProtocolBufferException {

        UEntity entity = UEntity.newBuilder().setName(TEST_ENTITY_NAME).build();
        UUri uri = UUri.newBuilder().setAuthority(TEST_AUTHORITY).setEntity(entity).build();
        Node node = Node.newBuilder().setUri(toLongUri(uri)).setType(Node.Type.ENTITY).build();
        UpdateNodeRequest request = UpdateNodeRequest.newBuilder().setNode(node).build();
        UMessage uMsg = UMessage.newBuilder().setPayload(packToAny(request)).build();

        UPayload uPayload = mRPCHandler.processLDSUpdateNode(uMsg);
        UStatus status = unpack(uPayload, UStatus.class).
                orElseThrow(() -> new UStatusException(UCode.INVALID_ARGUMENT, UNEXPECTED_PAYLOAD));

        verify(mAssetManager).writeFileToInternalStorage(eq(mContext), eq(LDS_DB_FILENAME), anyString());
        assertEquals(UCode.OK, status.getCode());
    }

    @Test
    public void positive_processUpdateNode_debug() throws InvalidProtocolBufferException {
        setLogLevel(Log.DEBUG);

        UEntity entity = UEntity.newBuilder().setName(TEST_ENTITY_NAME).build();
        UUri uri = UUri.newBuilder().setAuthority(TEST_AUTHORITY).setEntity(entity).build();
        Node node = Node.newBuilder().setUri(toLongUri(uri)).setType(Node.Type.ENTITY).build();
        UpdateNodeRequest request = UpdateNodeRequest.newBuilder().setNode(node).setTtl(0).build();
        UMessage uMsg = UMessage.newBuilder().setPayload(packToAny(request)).build();

        UPayload uPayload = mRPCHandler.processLDSUpdateNode(uMsg);
        UStatus status = unpack(uPayload, UStatus.class).
                orElseThrow(() -> new UStatusException(UCode.INVALID_ARGUMENT, UNEXPECTED_PAYLOAD));

        verify(mAssetManager).writeFileToInternalStorage(eq(mContext), eq(LDS_DB_FILENAME), anyString());
        assertEquals(UCode.OK, status.getCode());
    }

    @Test
    public void negative_processUpdateNode_exception() throws InvalidProtocolBufferException {
        // pack incorrect protobuf message type
        UMessage uMsg = UMessage.newBuilder().setPayload(packToAny(UStatus.getDefaultInstance())).build();

        UPayload uPayload = mRPCHandler.processLDSUpdateNode(uMsg);
        UStatus status = unpack(uPayload, UStatus.class).
                orElseThrow(() -> new UStatusException(UCode.INVALID_ARGUMENT, UNEXPECTED_PAYLOAD));

        verifyNoInteractions(mAssetManager);
        assertEquals(UCode.INVALID_ARGUMENT, status.getCode());
    }

    @Test
    public void positive_processUpdateProperty() throws InvalidProtocolBufferException {

        UEntity entity = UEntity.newBuilder().setName(TEST_ENTITY_NAME).build();
        UUri uri = UUri.newBuilder().setAuthority(TEST_AUTHORITY).setEntity(entity).build();
        PropertyValue propertyValue = PropertyValue.newBuilder().setUInteger(0).build();
        UpdatePropertyRequest request = UpdatePropertyRequest.newBuilder()
                .setUri(toLongUri(uri))
                .setProperty(TEST_PROPERTY1)
                .setValue(propertyValue)
                .build();
        UMessage uMsg = UMessage.newBuilder().setPayload(packToAny(request)).build();

        UPayload uPayload = mRPCHandler.processLDSUpdateProperty(uMsg);
        UStatus sts = unpack(uPayload, UStatus.class).
                orElseThrow(() -> new UStatusException(UCode.INVALID_ARGUMENT, UNEXPECTED_PAYLOAD));

        verify(mDiscoveryManager).updateProperty(TEST_PROPERTY1, propertyValue, uri);
        verify(mAssetManager).writeFileToInternalStorage(eq(mContext), eq(LDS_DB_FILENAME), anyString());
        assertEquals(UCode.OK, sts.getCode());
    }

    @Test
    public void positive_processUpdateProperty_debug() throws InvalidProtocolBufferException {
        setLogLevel(Log.DEBUG);

        UEntity entity = UEntity.newBuilder().setName(TEST_ENTITY_NAME).build();
        UUri uri = UUri.newBuilder().setAuthority(TEST_AUTHORITY).setEntity(entity).build();
        PropertyValue propertyValue = PropertyValue.newBuilder().setUInteger(0).build();
        UpdatePropertyRequest request = UpdatePropertyRequest.newBuilder()
                .setUri(toLongUri(uri))
                .setProperty(TEST_PROPERTY1)
                .setValue(propertyValue)
                .build();
        UMessage uMsg = UMessage.newBuilder().setPayload(packToAny(request)).build();

        UPayload uPayload = mRPCHandler.processLDSUpdateProperty(uMsg);
        UStatus sts = unpack(uPayload, UStatus.class).
                orElseThrow(() -> new UStatusException(UCode.INVALID_ARGUMENT, UNEXPECTED_PAYLOAD));

        verify(mDiscoveryManager).updateProperty(TEST_PROPERTY1, propertyValue, uri);
        verify(mAssetManager).writeFileToInternalStorage(eq(mContext), eq(LDS_DB_FILENAME), anyString());
        assertEquals(UCode.OK, sts.getCode());
    }

    @Test
    public void negative_processUpdateProperty_exception() throws InvalidProtocolBufferException {
        // pack incorrect protobuf message type
        UMessage uMsg = UMessage.newBuilder().setPayload(packToAny(UStatus.getDefaultInstance())).build();

        UPayload uPayload = mRPCHandler.processLDSUpdateProperty(uMsg);
        UStatus status = unpack(uPayload, UStatus.class).
                orElseThrow(() -> new UStatusException(UCode.INVALID_ARGUMENT, UNEXPECTED_PAYLOAD));

        verify(mDiscoveryManager, never()).updateProperty(anyString(), any(), any());
        verifyNoInteractions(mAssetManager);
        assertEquals(UCode.INVALID_ARGUMENT, status.getCode());
    }

    @Test
    public void positive_processAddNodes() throws InvalidProtocolBufferException {
        UUri parentUri = UUri.newBuilder().setAuthority(TEST_AUTHORITY).build();
        UEntity entity = UEntity.newBuilder().setName(TEST_ALTERNATE_ENTITY).build();
        UUri uri = UUri.newBuilder().setAuthority(TEST_AUTHORITY).setEntity(entity).build();
        Node node = Node.newBuilder().setUri(toLongUri(uri)).setType(Node.Type.ENTITY).build();

        AddNodesRequest request = AddNodesRequest.newBuilder()
                .setParentUri(toLongUri(parentUri))
                .addNodes(node)
                .build();
        UMessage uMsg = UMessage.newBuilder().setPayload(packToAny(request)).build();

        UPayload uPayload = mRPCHandler.processAddNodesLDS(uMsg);
        UStatus status = unpack(uPayload, UStatus.class).
                orElseThrow(() -> new UStatusException(UCode.INVALID_ARGUMENT, UNEXPECTED_PAYLOAD));

        verify(mDiscoveryManager).addNodes(any(), any());
        verify(mAssetManager).writeFileToInternalStorage(eq(mContext), eq(LDS_DB_FILENAME), anyString());
        assertEquals(UCode.OK, status.getCode());
    }

    @Test
    public void positive_processAddNodes_verbose() throws InvalidProtocolBufferException {
        setLogLevel(Log.VERBOSE);
        UUri parentUri = UUri.newBuilder().setAuthority(TEST_AUTHORITY).build();
        UEntity entity = UEntity.newBuilder().setName(TEST_ALTERNATE_ENTITY).build();
        UUri uri = UUri.newBuilder().setAuthority(TEST_AUTHORITY).setEntity(entity).build();
        Node node = Node.newBuilder().setUri(toLongUri(uri)).setType(Node.Type.ENTITY).build();

        AddNodesRequest request = AddNodesRequest.newBuilder()
                .setParentUri(toLongUri(parentUri))
                .addNodes(node)
                .build();
        UMessage uMsg = UMessage.newBuilder().setPayload(packToAny(request)).build();

        UPayload uPayload = mRPCHandler.processAddNodesLDS(uMsg);
        UStatus status = unpack(uPayload, UStatus.class).
                orElseThrow(() -> new UStatusException(UCode.INVALID_ARGUMENT, UNEXPECTED_PAYLOAD));

        verify(mDiscoveryManager).addNodes(any(), any());
        verify(mAssetManager).writeFileToInternalStorage(eq(mContext), eq(LDS_DB_FILENAME), anyString());
        assertEquals(UCode.OK, status.getCode());
    }

    @Test
    public void negative_processAddNodes_exception() throws InvalidProtocolBufferException {
        // pack incorrect protobuf message type
        UMessage uMsg = UMessage.newBuilder().setPayload(packToAny(UStatus.getDefaultInstance())).build();

        UPayload uPayload = mRPCHandler.processAddNodesLDS(uMsg);
        UStatus status = unpack(uPayload, UStatus.class).
                orElseThrow(() -> new UStatusException(UCode.INVALID_ARGUMENT, UNEXPECTED_PAYLOAD));

        verifyNoInteractions(mAssetManager);
        assertEquals(UCode.INVALID_ARGUMENT, status.getCode());
    }

    @Test
    public void positive_processDeleteNodes() throws InvalidProtocolBufferException {

        UEntity entity = UEntity.newBuilder().setName(TEST_ENTITY_NAME).build();
        UUri uri = UUri.newBuilder().setAuthority(TEST_AUTHORITY).setEntity(entity).build();
        DeleteNodesRequest request = DeleteNodesRequest.newBuilder().addUris(toLongUri(uri)).build();

        UMessage uMsg = UMessage.newBuilder().setPayload(packToAny(request)).build();

        UPayload uPayload = mRPCHandler.processDeleteNodes(uMsg);
        UStatus status = unpack(uPayload, UStatus.class).
                orElseThrow(() -> new UStatusException(UCode.INVALID_ARGUMENT, UNEXPECTED_PAYLOAD));

        verify(mDiscoveryManager).deleteNodes(List.of(uri));
        verify(mAssetManager).writeFileToInternalStorage(eq(mContext), eq(LDS_DB_FILENAME), anyString());
        assertEquals(UCode.OK, status.getCode());
    }

    @Test
    public void positive_processDeleteNodes_debug() throws InvalidProtocolBufferException {
        setLogLevel(Log.DEBUG);

        UEntity entity = UEntity.newBuilder().setName(TEST_ENTITY_NAME).build();
        UUri uri = UUri.newBuilder().setAuthority(TEST_AUTHORITY).setEntity(entity).build();
        DeleteNodesRequest request = DeleteNodesRequest.newBuilder().addUris(toLongUri(uri)).build();
        UMessage uMsg = UMessage.newBuilder().setPayload(packToAny(request)).build();

        UPayload uPayload = mRPCHandler.processDeleteNodes(uMsg);
        UStatus status = unpack(uPayload, UStatus.class).
                orElseThrow(() -> new UStatusException(UCode.INVALID_ARGUMENT, UNEXPECTED_PAYLOAD));

        verify(mDiscoveryManager).deleteNodes(List.of(uri));

        verify(mAssetManager).writeFileToInternalStorage(eq(mContext), eq(LDS_DB_FILENAME), anyString());
        assertEquals(UCode.OK, status.getCode());
    }

    @Test
    public void negative_processDeleteNodes_exception() throws InvalidProtocolBufferException {
        // pack incorrect protobuf message type
        UMessage uMsg = UMessage.newBuilder().setPayload(packToAny(UStatus.getDefaultInstance())).build();

        UPayload uPayload = mRPCHandler.processDeleteNodes(uMsg);
        UStatus status = unpack(uPayload, UStatus.class).
                orElseThrow(() -> new UStatusException(UCode.INVALID_ARGUMENT, UNEXPECTED_PAYLOAD));

        verifyNoInteractions(mAssetManager);
        assertEquals(UCode.INVALID_ARGUMENT, status.getCode());
    }

    @Test
    public void positive_RegisterForNotifications() throws InvalidProtocolBufferException {
        UUri ObserverUri = UUri.getDefaultInstance();
        UUri nodeUri = UUri.newBuilder().setAuthority(TEST_AUTHORITY).build();

        ObserverInfo info = ObserverInfo.newBuilder().setUri(toLongUri(ObserverUri)).build();
        NotificationsRequest request = NotificationsRequest.newBuilder()
                .setObserver(info)
                .addUris(toLongUri(nodeUri))
                .build();
        UMessage uMsg = UMessage.newBuilder().setPayload(packToAny(request)).build();

        UStatus okStatus = buildStatus(UCode.OK, "OK");
        when(mObserverManager.registerObserver(List.of(nodeUri), ObserverUri)).thenReturn(okStatus);

        UPayload uPayload = mRPCHandler.processNotificationRegistration(uMsg, METHOD_REGISTER_FOR_NOTIFICATIONS);
        UStatus status = unpack(uPayload, UStatus.class).
                orElseThrow(() -> new UStatusException(UCode.INVALID_ARGUMENT, UNEXPECTED_PAYLOAD));

        verify(mObserverManager).registerObserver(List.of(nodeUri), ObserverUri);
        assertEquals(UCode.OK, status.getCode());
    }

    @Test
    public void positive_RegisterForNotifications_debug() throws InvalidProtocolBufferException {
        setLogLevel(Log.DEBUG);
        UUri ObserverUri = UUri.getDefaultInstance();
        UUri nodeUri = UUri.newBuilder().setAuthority(TEST_AUTHORITY).build();
        ObserverInfo info = ObserverInfo.newBuilder().setUri(toLongUri(ObserverUri)).build();
        NotificationsRequest request = NotificationsRequest.newBuilder()
                .setObserver(info)
                .addUris(toLongUri(nodeUri))
                .build();
        UMessage uMsg = UMessage.newBuilder().setPayload(packToAny(request)).build();

        UStatus okStatus = buildStatus(UCode.OK, "OK");
        when(mObserverManager.registerObserver(List.of(nodeUri), ObserverUri)).thenReturn(okStatus);

        UPayload uPayload = mRPCHandler.processNotificationRegistration(uMsg,
                METHOD_REGISTER_FOR_NOTIFICATIONS);

        UStatus status = unpack(uPayload, UStatus.class).
                orElseThrow(() -> new UStatusException(UCode.INVALID_ARGUMENT, UNEXPECTED_PAYLOAD));

        verify(mObserverManager).registerObserver(List.of(nodeUri), ObserverUri);
        assertEquals(UCode.OK, status.getCode());
    }

    @Test
    public void positive_UnregisterForNotifications() throws InvalidProtocolBufferException {
        UUri ObserverUri = UUri.getDefaultInstance();
        UUri nodeUri = UUri.newBuilder().setAuthority(TEST_AUTHORITY).build();
        ObserverInfo info = ObserverInfo.newBuilder().setUri(toLongUri(ObserverUri)).build();
        NotificationsRequest request = NotificationsRequest.newBuilder()
                .setObserver(info)
                .addUris(toLongUri(nodeUri))
                .build();
        UMessage uMsg = UMessage.newBuilder().setPayload(packToAny(request)).build();

        UStatus okStatus = buildStatus(UCode.OK, "OK");
        when(mObserverManager.unregisterObserver(List.of(nodeUri), ObserverUri)).thenReturn(okStatus);

        UPayload uPayload = mRPCHandler.processNotificationRegistration(uMsg, METHOD_UNREGISTER_FOR_NOTIFICATIONS);
        UStatus status = unpack(uPayload, UStatus.class).
                orElseThrow(() -> new UStatusException(UCode.INVALID_ARGUMENT, UNEXPECTED_PAYLOAD));

        verify(mObserverManager).unregisterObserver(List.of(nodeUri), ObserverUri);
        assertEquals(UCode.OK, status.getCode());
    }

    @Test
    public void positive_UnregisterForNotifications_debug() throws InvalidProtocolBufferException {
        setLogLevel(Log.DEBUG);
        UUri ObserverUri = UUri.getDefaultInstance();
        UUri nodeUri = UUri.newBuilder().setAuthority(TEST_AUTHORITY).build();
        ObserverInfo info = ObserverInfo.newBuilder().setUri(toLongUri(ObserverUri)).build();
        NotificationsRequest request = NotificationsRequest.newBuilder()
                .setObserver(info)
                .addUris(toLongUri(nodeUri))
                .build();
        UMessage uMsg = UMessage.newBuilder().setPayload(packToAny(request)).build();
        UStatus okStatus = buildStatus(UCode.OK, "OK");
        when(mObserverManager.unregisterObserver(List.of(nodeUri), ObserverUri)).thenReturn(okStatus);

        UPayload uPayload = mRPCHandler.processNotificationRegistration(uMsg, METHOD_UNREGISTER_FOR_NOTIFICATIONS);
        UStatus status = unpack(uPayload, UStatus.class).
                orElseThrow(() -> new UStatusException(UCode.INVALID_ARGUMENT, UNEXPECTED_PAYLOAD));

        verify(mObserverManager).unregisterObserver(List.of(nodeUri), ObserverUri);
        assertEquals(UCode.OK, status.getCode());
    }

    @Test
    public void negative_NotificationRegistration() throws InvalidProtocolBufferException {
        UMessage uMsg = UMessage.newBuilder().setPayload(packToAny(NotificationsRequest.getDefaultInstance())).build();

        UPayload uPayload = mRPCHandler.processNotificationRegistration(uMsg, TEST_INVALID_METHOD);
        UStatus status = unpack(uPayload, UStatus.class).
                orElseThrow(() -> new UStatusException(UCode.INVALID_ARGUMENT, UNEXPECTED_PAYLOAD));

        verifyNoInteractions(mObserverManager);
        assertEquals(UCode.INVALID_ARGUMENT, status.getCode());
    }

    @Test
    public void negative_extracPayload_exception() throws InvalidProtocolBufferException {
        // build a payload object omitting the format
        UPayload payload = UPayload.getDefaultInstance();
        UMessage uMsg = UMessage.newBuilder().setPayload(payload).build();

        UPayload uPayload = mRPCHandler.processLookupUriFromLDS(uMsg);
        UStatus lookupStatus = unpack(uPayload, LookupUriResponse.class).
                orElseThrow(() -> new UStatusException(UCode.INVALID_ARGUMENT, UNEXPECTED_PAYLOAD)).getStatus();

        uPayload = mRPCHandler.processFindNodesFromLDS(uMsg);
        UStatus findNodesStatus = unpack(uPayload, FindNodesResponse.class).
                orElseThrow(() -> new UStatusException(UCode.INVALID_ARGUMENT, UNEXPECTED_PAYLOAD)).getStatus();

        uPayload = mRPCHandler.processFindNodeProperties(uMsg);
        UStatus findNodePropertiesStatus = unpack(uPayload, FindNodePropertiesResponse.class).
                orElseThrow(() -> new UStatusException(UCode.INVALID_ARGUMENT, UNEXPECTED_PAYLOAD)).getStatus();

        uPayload = mRPCHandler.processLDSUpdateNode(uMsg);
        UStatus updateNodeStatus = unpack(uPayload, UStatus.class).
                orElseThrow(() -> new UStatusException(UCode.INVALID_ARGUMENT, UNEXPECTED_PAYLOAD));

        uPayload = mRPCHandler.processLDSUpdateProperty(uMsg);
        UStatus updateNodePropertyStatus = unpack(uPayload, UStatus.class).
                orElseThrow(() -> new UStatusException(UCode.INVALID_ARGUMENT, UNEXPECTED_PAYLOAD));

        uPayload = mRPCHandler.processAddNodesLDS(uMsg);
        UStatus addNodesStatus = unpack(uPayload, UStatus.class).
                orElseThrow(() -> new UStatusException(UCode.INVALID_ARGUMENT, UNEXPECTED_PAYLOAD));

        uPayload = mRPCHandler.processDeleteNodes(uMsg);
        UStatus deleteNodesStatus = unpack(uPayload, UStatus.class).
                orElseThrow(() -> new UStatusException(UCode.INVALID_ARGUMENT, UNEXPECTED_PAYLOAD));

        uPayload = mRPCHandler.processNotificationRegistration(uMsg, METHOD_REGISTER_FOR_NOTIFICATIONS);
        UStatus registerForNotificationStatus = unpack(uPayload, UStatus.class).
                orElseThrow(() -> new UStatusException(UCode.INVALID_ARGUMENT, UNEXPECTED_PAYLOAD));

        uPayload = mRPCHandler.processNotificationRegistration(uMsg, METHOD_UNREGISTER_FOR_NOTIFICATIONS);
        UStatus unregisterForNotificationStatus = unpack(uPayload, UStatus.class).
                orElseThrow(() -> new UStatusException(UCode.INVALID_ARGUMENT, UNEXPECTED_PAYLOAD));

        assertEquals(UCode.INVALID_ARGUMENT, lookupStatus.getCode());
        assertEquals(UCode.INVALID_ARGUMENT, findNodesStatus.getCode());
        assertEquals(UCode.INVALID_ARGUMENT, findNodePropertiesStatus.getCode());
        assertEquals(UCode.INVALID_ARGUMENT, updateNodeStatus.getCode());
        assertEquals(UCode.INVALID_ARGUMENT, updateNodePropertyStatus.getCode());
        assertEquals(UCode.INVALID_ARGUMENT, addNodesStatus.getCode());
        assertEquals(UCode.INVALID_ARGUMENT, deleteNodesStatus.getCode());
        assertEquals(UCode.INVALID_ARGUMENT, registerForNotificationStatus.getCode());
        assertEquals(UCode.INVALID_ARGUMENT, unregisterForNotificationStatus.getCode());
    }

    @Test
    public void negative_refreshDatabase() throws InvalidProtocolBufferException {
        UMessage uMsg = UMessage.newBuilder().setPayload(packToAny(UpdateNodeRequest.getDefaultInstance())).build();

        UPayload uPayload = mRPCHandler.processLDSUpdateNode(uMsg);
        UStatus status = unpack(uPayload, UStatus.class).
                orElseThrow(() -> new UStatusException(UCode.INVALID_ARGUMENT, UNEXPECTED_PAYLOAD));

        verifyNoInteractions(mAssetManager);
        assertEquals(UCode.INVALID_ARGUMENT, status.getCode());
    }
}
