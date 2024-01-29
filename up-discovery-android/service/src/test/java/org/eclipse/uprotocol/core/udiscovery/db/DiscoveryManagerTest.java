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

package org.eclipse.uprotocol.core.udiscovery.db;

import static org.eclipse.uprotocol.common.util.log.Formatter.tag;
import static org.eclipse.uprotocol.core.udiscovery.common.Constants.JSON_AUTHORITY;
import static org.eclipse.uprotocol.core.udiscovery.db.JsonNodeTest.JSON_PROTOBUF_EXCEPTION;
import static org.eclipse.uprotocol.core.udiscovery.db.JsonNodeTest.REGISTRY_JSON;
import static org.eclipse.uprotocol.core.udiscovery.internal.Utils.toLongUri;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import android.util.Pair;

import com.google.protobuf.InvalidProtocolBufferException;

import org.eclipse.uprotocol.common.UStatusException;
import org.eclipse.uprotocol.core.udiscovery.IntegrityCheck;
import org.eclipse.uprotocol.core.udiscovery.Notifier;
import org.eclipse.uprotocol.core.udiscovery.TestBase;
import org.eclipse.uprotocol.core.udiscovery.interfaces.PersistInterface;
import org.eclipse.uprotocol.core.udiscovery.v3.Node;
import org.eclipse.uprotocol.core.udiscovery.v3.PropertyValue;
import org.eclipse.uprotocol.uri.serializer.LongUriSerializer;
import org.eclipse.uprotocol.v1.UAuthority;
import org.eclipse.uprotocol.v1.UCode;
import org.eclipse.uprotocol.v1.UEntity;
import org.eclipse.uprotocol.v1.UResource;
import org.eclipse.uprotocol.v1.UStatus;
import org.eclipse.uprotocol.v1.UUri;
import org.eclipse.uprotocol.v1.UUriBatch;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowLog;

import java.util.List;
import java.util.Map;

@RunWith(RobolectricTestRunner.class)
public class DiscoveryManagerTest extends TestBase implements PersistInterface {

    public static final String TAG = tag(SERVICE.getName());
    private static final String UNKNOWN_PROPERTY_NAME = "unknown_property";
    private final IntegrityCheck mIntegrity = spy(IntegrityCheck.class);
    public Notifier mNotifier = mock(Notifier.class);
    private DiscoveryManager mDiscoveryMgr;
    private boolean mPersistFlag;
    private UEntity mEntityV1;
    private UEntity mEntityV2;
    private UEntity mUnknownEntity;
    private UResource mNewResource;

    @Before
    public void setUp() {
        ShadowLog.stream = System.out;
        doNothing().when(mNotifier).notifyObserversAddNodes(any(), any());
        doNothing().when(mNotifier).notifyObserversWithParentUri(any(), any());
        mDiscoveryMgr = new DiscoveryManager(mNotifier);
        mDiscoveryMgr.init(TEST_AUTHORITY);
        mDiscoveryMgr.setPersistInterface(this);
        mDiscoveryMgr.setChecksumInterface(mIntegrity);
        mPersistFlag = false;

        mEntityV1 = UEntity.newBuilder().setName(TEST_ENTITY_NAME).setVersionMajor(1).build();
        mEntityV2 = UEntity.newBuilder().setName(TEST_ENTITY_NAME).setVersionMajor(2).build();

        mUnknownEntity = UEntity.newBuilder().setName("unknown_entity").build();
        mNewResource = UResource.newBuilder().setName("new_resource").build();
    }

    @Override
    public void persist(String data) {
        //Log.i(LOG_TAG, Key.MESSAGE, "persist", data);
        mPersistFlag = true;
    }

    private void populateDatabase() {
        Node node = jsonToNode(REGISTRY_JSON);
        UStatus sts = mDiscoveryMgr.updateNode(node, -1);
        assertEquals(UCode.OK, sts.getCode());
    }

    @Test
    public void negative_init_exception() {
        DiscoveryManager dm = new DiscoveryManager(mNotifier);
        assertFalse(dm.init(UAuthority.getDefaultInstance()));
    }

    @Test
    public void positive_lookupuri() {
        populateDatabase();

        UEntity entity = UEntity.newBuilder().setName(TEST_ENTITY_NAME).build();
        UUri uri = UUri.newBuilder().setEntity(entity).build();

        Pair<UUriBatch, UStatus> pair = mDiscoveryMgr.lookupUri(uri);
        UUriBatch actual = pair.first;
        UStatus sts = pair.second;

        UUri fullyQualifiedUri = UUri.newBuilder().setAuthority(TEST_AUTHORITY).setEntity(mEntityV1).build();
        UUriBatch expected = UUriBatch.newBuilder().addUris(fullyQualifiedUri).build();

        assertEquals(expected, actual);
        assertEquals(UCode.OK, sts.getCode());
    }

    @Test
    public void positive_lookupuri_authority() {
        UEntity entity = UEntity.newBuilder().setName(JSON_AUTHORITY).build();
        UUri uri = UUri.newBuilder().setEntity(entity).build();

        Pair<UUriBatch, UStatus> pair = mDiscoveryMgr.lookupUri(uri);
        UUriBatch actual = pair.first;
        UStatus sts = pair.second;

        UUri authorityUri = UUri.newBuilder().setAuthority(TEST_AUTHORITY).build();
        UUriBatch expected = UUriBatch.newBuilder().addUris(authorityUri).build();

        assertEquals(expected, actual);
        assertEquals(UCode.OK, sts.getCode());
    }

    @Test
    public void negative_lookupuri_empty() {
        populateDatabase();

        Pair<UUriBatch, UStatus> pair = mDiscoveryMgr.lookupUri(UUri.getDefaultInstance());
        UUriBatch batch = pair.first;
        UStatus sts = pair.second;

        assertTrue(batch.getUrisList().isEmpty());
        assertEquals(UCode.INVALID_ARGUMENT, sts.getCode());
    }

    @Test
    public void negative_lookupuri_not_found() {
        populateDatabase();
        UUri uri = UUri.newBuilder().setEntity(mUnknownEntity).build();

        Pair<UUriBatch, UStatus> pair = mDiscoveryMgr.lookupUri(uri);
        UUriBatch batch = pair.first;
        UStatus sts = pair.second;

        assertTrue(batch.getUrisList().isEmpty());
        assertEquals(UCode.NOT_FOUND, sts.getCode());
    }

    @Test
    public void positive_find_and_update_property() {
        populateDatabase();

        UEntity entity = UEntity.newBuilder().setName(TEST_ENTITY_NAME).build();
        UUri uri = UUri.newBuilder().setAuthority(TEST_AUTHORITY).setEntity(entity).build();


        Pair<Map<String, PropertyValue>, UStatus> pair = mDiscoveryMgr.findNodeProperties(uri, List.of(TEST_PROPERTY1));
        Map<String, PropertyValue> map = pair.first;
        UStatus status = pair.second;
        assertEquals(UCode.OK, status.getCode());

        PropertyValue oldProperty = map.get(TEST_PROPERTY1);
        PropertyValue newProperty = PropertyValue.newBuilder().setUInteger(2024).build();

        status = mDiscoveryMgr.updateProperty(TEST_PROPERTY1, newProperty, uri);
        assertEquals(UCode.OK, status.getCode());

        pair = mDiscoveryMgr.findNodeProperties(uri, List.of(TEST_PROPERTY1));
        map = pair.first;
        status = pair.second;
        assertEquals(UCode.OK, status.getCode());
        PropertyValue resultProperty = map.get(TEST_PROPERTY1);

        assertNotEquals(oldProperty, resultProperty);
        assertEquals(newProperty, resultProperty);
    }

    @Test
    public void negative_updateProperty_not_found() {
        populateDatabase();
        UUri uri = UUri.newBuilder().setAuthority(TEST_AUTHORITY).setEntity(mUnknownEntity).build();
        UStatus sts = mDiscoveryMgr.updateProperty(UNKNOWN_PROPERTY_NAME, PropertyValue.getDefaultInstance(), uri);
        assertEquals(UCode.NOT_FOUND, sts.getCode());
    }

    @Test
    public void positive_update_not_found() {
        populateDatabase();
        UUri uri = UUri.newBuilder().setAuthority(TEST_AUTHORITY).setEntity(mUnknownEntity).build();
        Node node = Node.newBuilder().setUri(toLongUri(uri)).setType(Node.Type.ENTITY).build();

        UStatus sts = mDiscoveryMgr.updateNode(node, -1);
        assertEquals(UCode.NOT_FOUND, sts.getCode());
    }

    @Test
    public void negative_update_unspecified_type() {
        Node emptyNode = Node.getDefaultInstance();
        UStatus sts = mDiscoveryMgr.updateNode(emptyNode, -1);
        assertEquals(UCode.INVALID_ARGUMENT, sts.getCode());
    }

    @Test
    public void negative_update_device_node_missing_domain() {
        // build a device node with missing uri
        Node.Builder childBld = Node.newBuilder();
        childBld.setType(Node.Type.DEVICE);

        Node.Builder bld = Node.newBuilder();
        UAuthority domain = UAuthority.newBuilder().setName(TEST_DOMAIN).build();
        UUri rootUri = UUri.newBuilder().setAuthority(domain).build();
        bld.setUri(toLongUri(rootUri));
        bld.setType(Node.Type.DOMAIN);
        bld.addNodes(childBld);
        Node badNode = bld.build();

        UStatus sts = mDiscoveryMgr.updateNode(badNode, -1);
        assertEquals(UCode.INVALID_ARGUMENT, sts.getCode());
    }

    @Test
    public void negative_update_device_node_bad_domain() {
        Node.Builder childBld = Node.newBuilder();
        // truncated child authority name
        String name = String.join(".", TEST_DEVICE, TEST_VIN);
        UAuthority authority = UAuthority.newBuilder().setName(name).build();
        String uri = toLongUri(authority);
        childBld.setUri(uri);
        childBld.setType(Node.Type.DEVICE);

        Node.Builder bld = Node.newBuilder();
        UAuthority domain = UAuthority.newBuilder().setName(TEST_DOMAIN).build();
        UUri rootUri = UUri.newBuilder().setAuthority(domain).build();
        bld.setUri(toLongUri(rootUri));
        bld.setType(Node.Type.DOMAIN);
        bld.addNodes(childBld);
        Node badNode = bld.build();

        UStatus sts = mDiscoveryMgr.updateNode(badNode, -1);
        assertEquals(UCode.INVALID_ARGUMENT, sts.getCode());
    }

    @Test
    public void positive_save_load() {
        populateDatabase();
        String before = mDiscoveryMgr.export();

        // delete this instance and create a new one
        mDiscoveryMgr.cleanup();
        mDiscoveryMgr = new DiscoveryManager(mNotifier);
        mDiscoveryMgr.setChecksumInterface(mIntegrity);
        assertTrue(mDiscoveryMgr.load(before));

        String after = mDiscoveryMgr.export();
        assertEquals(before, after);
    }

    @Test
    public void negative_load_invalid_authority() {
        String saveData = mDiscoveryMgr.export();
        String original = "\"" + JSON_AUTHORITY + "\": \"" + TEST_AUTHORITY.getName() + "\"";
        String replacement = "\"" + JSON_AUTHORITY + "\": \"corrupt data\"";
        String corruptData = saveData.replace(original, replacement);

        // delete this instance and create a new one
        mDiscoveryMgr.cleanup();
        mDiscoveryMgr = new DiscoveryManager(mNotifier);
        mDiscoveryMgr.setChecksumInterface(mIntegrity);
        assertFalse(mDiscoveryMgr.load(corruptData));
    }

    @Test
    public void negative_load_null_checksum_interface() {
        mDiscoveryMgr.setChecksumInterface(null);
        String before = mDiscoveryMgr.export();
        assertTrue(mDiscoveryMgr.load(before));
        String after = mDiscoveryMgr.export();
        assertEquals(before, after);
    }

    @Test
    public void negative_load_hash_check_failure() {
        String before = mDiscoveryMgr.export();

        populateDatabase();
        String validJson = mDiscoveryMgr.export();

        String corruptJson = validJson.replace(TEST_ENTITY_NAME, "");

        // if load fails the database shall be reinitialized
        assertTrue(mDiscoveryMgr.load(corruptJson));

        String after = mDiscoveryMgr.export();
        // verify LDS has reverted to it's initial state
        assertEquals(before, after);
    }

    @Test
    public void negative_load_json_exception() {
        assertFalse(mDiscoveryMgr.load("invalid json string"));
    }

    @Test
    public void negative_load_protobuf_exception() {
        assertFalse(mDiscoveryMgr.load(JSON_PROTOBUF_EXCEPTION));
    }

    @Test
    public void negative_export_protobuf_exception() {
        doAnswer(invocation -> {
            throw new InvalidProtocolBufferException("mock exception");
        }).when(mIntegrity).generateHash(any(String.class));
        String result = mDiscoveryMgr.export();
        assertTrue(result.isEmpty());
    }

    @Test
    public void positive_ttl() throws InterruptedException {
        populateDatabase();

        UEntity entity = UEntity.newBuilder().setName(TEST_ENTITY_NAME).build();
        UUri uri = UUri.newBuilder().setAuthority(TEST_AUTHORITY).setEntity(entity).build();
        Node nodeToExpire = Node.newBuilder().setUri(toLongUri(uri)).setType(Node.Type.ENTITY).build();

        UStatus sts = mDiscoveryMgr.updateNode(nodeToExpire, 1000);
        assertEquals(UCode.OK, sts.getCode());

        Thread.sleep(2000);
        Pair<Node, UStatus> pair = mDiscoveryMgr.findNode(uri, -1);
        Node node = pair.first;
        sts = pair.second;
        assertTrue(mPersistFlag);
        assertNull(node);
        assertEquals(UCode.NOT_FOUND, sts.getCode());
    }

    @Test
    public void positive_save_load_pending_ttl() throws InterruptedException {
        populateDatabase();

        UEntity entity = UEntity.newBuilder().setName(TEST_ENTITY_NAME).build();
        UUri uri = UUri.newBuilder().setAuthority(TEST_AUTHORITY).setEntity(entity).build();
        Node nodeToExpire = Node.newBuilder().setUri(toLongUri(uri)).setType(Node.Type.ENTITY).build();

        UStatus sts = mDiscoveryMgr.updateNode(nodeToExpire, 1000);
        assertEquals(UCode.OK, sts.getCode());

        String saveData = mDiscoveryMgr.export();

        // delete this instance and create a new one
        mDiscoveryMgr.cleanup();
        mDiscoveryMgr = new DiscoveryManager(mNotifier);
        mDiscoveryMgr.setChecksumInterface(mIntegrity);
        mDiscoveryMgr.setPersistInterface(this);

        // expire after load
        assertTrue(mDiscoveryMgr.load(saveData));
        Thread.sleep(2000);

        Pair<Node, UStatus> pair = mDiscoveryMgr.findNode(uri, -1);
        Node node = pair.first;
        sts = pair.second;
        assertTrue(mPersistFlag);
        assertNull(node);
        assertEquals(UCode.NOT_FOUND, sts.getCode());
    }

    @Test
    public void positive_save_load_expired_ttl() throws InterruptedException {
        populateDatabase();

        UEntity entity = UEntity.newBuilder().setName(TEST_ENTITY_NAME).build();
        UUri uri = UUri.newBuilder().setAuthority(TEST_AUTHORITY).setEntity(entity).build();
        Node nodeToExpire = Node.newBuilder().setUri(toLongUri(uri)).setType(Node.Type.ENTITY).build();

        UStatus sts = mDiscoveryMgr.updateNode(nodeToExpire, 1000);
        assertEquals(UCode.OK, sts.getCode());

        String saveData = mDiscoveryMgr.export();

        // delete this instance and create a new one
        mDiscoveryMgr.cleanup();
        mDiscoveryMgr = new DiscoveryManager(mNotifier);
        mDiscoveryMgr.setChecksumInterface(mIntegrity);
        mDiscoveryMgr.setPersistInterface(this);

        // expire before load
        Thread.sleep(2000);
        assertTrue(mDiscoveryMgr.load(saveData));

        Pair<Node, UStatus> pair = mDiscoveryMgr.findNode(uri, -1);
        Node node = pair.first;
        sts = pair.second;
        assertFalse(mPersistFlag);
        assertNull(node);
        assertEquals(UCode.NOT_FOUND, sts.getCode());
    }

    @Test
    public void negative_save_load_expired_ttl() throws InterruptedException {
        populateDatabase();

        UEntity entity = UEntity.newBuilder().setName(TEST_ENTITY_NAME).build();
        UUri uri = UUri.newBuilder().setAuthority(TEST_AUTHORITY).setEntity(entity).build();
        Node nodeToExpire = Node.newBuilder().setUri(toLongUri(uri)).setType(Node.Type.ENTITY).build();

        UStatus sts = mDiscoveryMgr.updateNode(nodeToExpire, 1000);
        assertEquals(UCode.OK, sts.getCode());

        String saveData = mDiscoveryMgr.export();

        String target = toLongUri(uri) + "\":";
        UEntity corruptEntity = UEntity.newBuilder().setName("corrupt_value").build();
        UUri corruptUri = UUri.newBuilder().setAuthority(TEST_AUTHORITY).setEntity(corruptEntity).build();
        String replacement = toLongUri(corruptUri) + "\":";
        String corruptTTL = saveData.replace(target, replacement);

        // delete this instance and create a new one
        mDiscoveryMgr.cleanup();
        mDiscoveryMgr = new DiscoveryManager(mNotifier);
        mDiscoveryMgr.setChecksumInterface(mIntegrity);
        mDiscoveryMgr.setPersistInterface(this);

        Thread.sleep(2000);
        assertTrue(mDiscoveryMgr.load(corruptTTL));

        // verify my_service still exists due to ttl corruption
        Pair<Node, UStatus> pair = mDiscoveryMgr.findNode(uri, -1);
        Node node = pair.first;
        sts = pair.second;
        assertFalse(mPersistFlag);
        assertNotNull(node);
        assertEquals(UCode.OK, sts.getCode());
    }

    @Test
    public void negative_update_ttl_null_persist_interface() throws InterruptedException {
        mDiscoveryMgr.setPersistInterface(null);
        populateDatabase();

        UEntity entity = UEntity.newBuilder().setName(TEST_ENTITY_NAME).build();
        UUri uri = UUri.newBuilder().setAuthority(TEST_AUTHORITY).setEntity(entity).build();
        Node nodeToExpire = Node.newBuilder().setUri(toLongUri(uri)).setType(Node.Type.ENTITY).build();

        UStatus sts = mDiscoveryMgr.updateNode(nodeToExpire, 1000);
        assertEquals(UCode.OK, sts.getCode());
        Thread.sleep(2000);

        // verify that the node was deleted properly but persist callback was not executed
        // (due to null pointer)
        Pair<Node, UStatus> pair = mDiscoveryMgr.findNode(uri, -1);
        Node node = pair.first;
        sts = pair.second;
        assertFalse(mPersistFlag);
        assertNull(node);
        assertEquals(UCode.NOT_FOUND, sts.getCode());
    }

    @Test
    public void positive_findnode_depth_parent() {
        populateDatabase();
        UUri uri = UUri.newBuilder().setAuthority(TEST_AUTHORITY).build();

        Pair<Node, UStatus> pair = mDiscoveryMgr.findNode(uri, 0);
        Node node = pair.first;
        UStatus sts = pair.second;
        assertNotNull(node);
        assertEquals(UCode.OK, sts.getCode());
        assertEquals(0, node.getNodesCount());
    }

    @Test
    public void positive_findnode_depth_all_nodes() {
        populateDatabase();
        UUri uri = UUri.newBuilder().setAuthority(TEST_AUTHORITY).build();

        Pair<Node, UStatus> pair = mDiscoveryMgr.findNode(uri, DEFAULT_DEPTH);
        Node node = pair.first;
        UStatus sts = pair.second;
        assertNotNull(node);
        assertEquals(UCode.OK, sts.getCode());

        int depth = 0;
        while (node.getNodesCount() != 0) {
            node = node.getNodes(0);
            depth++;
        }
        assertEquals(3, depth);
    }

    @Test
    public void positive_findnode_depth_non_default() {
        populateDatabase();
        UUri uri = UUri.newBuilder().setAuthority(TEST_AUTHORITY).build();

        Pair<Node, UStatus> pair = mDiscoveryMgr.findNode(uri, 1);
        Node node = pair.first;
        UStatus sts = pair.second;
        assertNotNull(node);
        assertEquals(UCode.OK, sts.getCode());

        int depth = 0;
        while (node.getNodesCount() != 0) {
            node = node.getNodes(0);
            depth++;
        }
        assertEquals(1, depth);
    }

    @Test
    public void negative_findnode_empty_uri() {
        Pair<Node, UStatus> pair = mDiscoveryMgr.findNode(UUri.getDefaultInstance(), DEFAULT_DEPTH);
        Node node = pair.first;
        UStatus sts = pair.second;
        assertNull(node);
        assertEquals(UCode.INVALID_ARGUMENT, sts.getCode());
    }

    @Test
    public void positive_deletenode() {
        populateDatabase();
        UResource resource = UResource.newBuilder().setName(TEST_RESOURCE).setInstance(TEST_INSTANCE_1).build();
        UUri uri = UUri.newBuilder().setAuthority(TEST_AUTHORITY).setEntity(mEntityV1).setResource(resource).build();

        UStatus status = mDiscoveryMgr.deleteNodes(List.of(uri));
        assertEquals(UCode.OK, status.getCode());
    }

    @Test
    public void positive_deletenode_same_path() {
        populateDatabase();

        UEntity entity = UEntity.newBuilder().setName(TEST_ENTITY_NAME).build();
        UResource resource1 = UResource.newBuilder().setName(TEST_RESOURCE).setInstance(TEST_INSTANCE_1).build();
        UResource resource2 = UResource.newBuilder().setName(TEST_RESOURCE).setInstance(TEST_INSTANCE_2).build();
        UResource resource3 = UResource.newBuilder().setName(TEST_RESOURCE).setInstance(TEST_INSTANCE_3).build();
        UResource resource4 = UResource.newBuilder().setName(TEST_RESOURCE).setInstance(TEST_INSTANCE_4).build();
        UUri grandParentUri = UUri.newBuilder().setAuthority(TEST_AUTHORITY).setEntity(entity).build();
        UUri parentUri = UUri.newBuilder().setAuthority(TEST_AUTHORITY).setEntity(mEntityV1).build();
        UUri childUri1 = UUri.newBuilder().setAuthority(TEST_AUTHORITY).setEntity(mEntityV1).setResource(resource1).build();
        UUri childUri2 = UUri.newBuilder().setAuthority(TEST_AUTHORITY).setEntity(mEntityV1).setResource(resource2).build();
        UUri childUri3 = UUri.newBuilder().setAuthority(TEST_AUTHORITY).setEntity(mEntityV1).setResource(resource3).build();
        UUri childUri4 = UUri.newBuilder().setAuthority(TEST_AUTHORITY).setEntity(mEntityV1).setResource(resource4).build();
        // random ordered list
        List<UUri> list = List.of(childUri2, parentUri, childUri4, childUri3, grandParentUri, childUri1);

        UStatus status = mDiscoveryMgr.deleteNodes(list);
        assertEquals(UCode.OK, status.getCode());
    }

    @Test
    public void positive_deletenode_different_path() {
        populateDatabase();

        UEntity entity1 = UEntity.newBuilder().setName(TEST_ENTITY_NAME).build();
        UEntity entity2 = UEntity.newBuilder().setName(TEST_ALTERNATE_ENTITY).build();
        UUri parentUri = UUri.newBuilder().setAuthority(TEST_AUTHORITY).build();
        UUri uri1 = UUri.newBuilder().setAuthority(TEST_AUTHORITY).setEntity(entity1).build();
        UUri uri2 = UUri.newBuilder().setAuthority(TEST_AUTHORITY).setEntity(entity2).build();

        Node node = Node.newBuilder().setUri(toLongUri(uri2)).setType(Node.Type.ENTITY).build();
        UStatus status = mDiscoveryMgr.addNodes(parentUri, List.of(node));
        assertEquals(UCode.OK, status.getCode());

        status = mDiscoveryMgr.deleteNodes(List.of(uri1, uri2));
        assertEquals(UCode.OK, status.getCode());
    }

    @Test
    public void negative_deletenode_not_found() {
        populateDatabase();
        UUri uri = UUri.newBuilder().setAuthority(TEST_AUTHORITY).setEntity(mUnknownEntity).build();

        UStatus status = mDiscoveryMgr.deleteNodes(List.of(uri));
        assertEquals(UCode.NOT_FOUND, status.getCode());
    }

    @Test
    public void negative_deletenode_root() {
        UAuthority domain = UAuthority.newBuilder().setName(TEST_DOMAIN).build();
        UUri rootUri = UUri.newBuilder().setAuthority(domain).build();

        UStatus status = mDiscoveryMgr.deleteNodes(List.of(rootUri));
        assertEquals(UCode.NOT_FOUND, status.getCode());
    }

    @Test
    public void negative_deletenode_exception() {
        UAuthority domain = UAuthority.newBuilder().setName(TEST_DOMAIN).build();
        UUri rootUri = spy(UUri.newBuilder().setAuthority(domain).build());
        when(rootUri.hasAuthority()).thenThrow(new UStatusException(UCode.INVALID_ARGUMENT, "INVALID"));
        List<UUri> uriList = List.of(rootUri);
        UStatus status = mDiscoveryMgr.deleteNodes(uriList);

        assertEquals(UCode.INVALID_ARGUMENT, status.getCode());
    }

    @Test
    public void negative_deletenode_expired_root() throws InterruptedException {
        Node cdsNode = jsonToNode(REGISTRY_JSON);

        UStatus sts = mDiscoveryMgr.updateNode(cdsNode, 1000);
        assertEquals(UCode.OK, sts.getCode());

        Thread.sleep(2000);
        UUri uri = LongUriSerializer.instance().deserialize(cdsNode.getUri());

        Pair<Node, UStatus> pair = mDiscoveryMgr.findNode(uri, -1);
        Node node = pair.first;
        sts = pair.second;
        assertEquals(UCode.OK, sts.getCode());
        assertNotNull(node);
    }

    @Test
    public void positive_findnode_properties_partial() {
        populateDatabase();
        List<String> list = List.of(TEST_PROPERTY1, TEST_PROPERTY2, UNKNOWN_PROPERTY_NAME);

        UEntity entity = UEntity.newBuilder().setName(TEST_ENTITY_NAME).build();
        UUri uri = UUri.newBuilder().setAuthority(TEST_AUTHORITY).setEntity(entity).build();

        Pair<Map<String, PropertyValue>, UStatus> pair = mDiscoveryMgr.findNodeProperties(uri, list);
        Map<String, PropertyValue> map = pair.first;
        UStatus sts = pair.second;

        assertEquals(2, map.size());
        assertTrue(map.containsKey(TEST_PROPERTY1));
        assertTrue(map.containsKey(TEST_PROPERTY2));
        assertEquals(UCode.OK, sts.getCode());
        assertEquals("[findNodeProperties] Success for limited properties", sts.getMessage());
    }

    @Test
    public void negative_findnode_properties_unavailable() {
        populateDatabase();
        List<String> list = List.of(TEST_PROPERTY1, TEST_PROPERTY2);
        UUri uri = UUri.newBuilder().setAuthority(TEST_AUTHORITY).setEntity(mUnknownEntity).build();

        Pair<Map<String, PropertyValue>, UStatus> pair = mDiscoveryMgr.findNodeProperties(uri, list);
        Map<String, PropertyValue> map = pair.first;
        UStatus sts = pair.second;

        assertTrue(map.isEmpty());
        assertEquals(UCode.UNAVAILABLE, sts.getCode());
    }

    @Test
    public void negative_findnode_properties_not_found() {
        populateDatabase();
        List<String> list = List.of(UNKNOWN_PROPERTY_NAME);

        UEntity entity = UEntity.newBuilder().setName(TEST_ENTITY_NAME).build();

        UUri uri = UUri.newBuilder().setAuthority(TEST_AUTHORITY).setEntity(entity).build();

        Pair<Map<String, PropertyValue>, UStatus> pair = mDiscoveryMgr.findNodeProperties(uri, list);
        Map<String, PropertyValue> map = pair.first;
        UStatus sts = pair.second;

        assertTrue(map.isEmpty());
        assertEquals(UCode.NOT_FOUND, sts.getCode());
    }

    @Test
    public void negative_findnode_properties_empty_list() {
        populateDatabase();
        UUri uri = UUri.newBuilder().setAuthority(TEST_AUTHORITY).build();

        Pair<Map<String, PropertyValue>, UStatus> pair = mDiscoveryMgr.findNodeProperties(uri, List.of());
        Map<String, PropertyValue> map = pair.first;
        UStatus sts = pair.second;

        assertTrue(map.isEmpty());
        assertEquals(UCode.INVALID_ARGUMENT, sts.getCode());
    }

    @Test
    public void negative_resource_level_authority_mismatch() {
        populateDatabase();
        UUri parent = UUri.newBuilder().setEntity(mEntityV1).build();
        UUri child = UUri.newBuilder().setAuthority(TEST_AUTHORITY).setEntity(mEntityV1).setResource(
                mNewResource).build();
        String versionUri = toLongUri(parent);
        String topicUri = toLongUri(child);
        Node topicNode = Node.newBuilder()
                .setUri(topicUri)
                .setType(Node.Type.TOPIC)
                .build();
        Node versionNode = Node.newBuilder()
                .setUri(versionUri)
                .setType(Node.Type.VERSION)
                .addNodes(topicNode)
                .build();

        UStatus sts = mDiscoveryMgr.updateNode(versionNode, -1);
        assertEquals(UCode.INVALID_ARGUMENT, sts.getCode());
    }

    @Test
    public void negative_resource_level_entity_mismatch() {
        populateDatabase();
        UUri parent = UUri.newBuilder().setAuthority(TEST_AUTHORITY).setEntity(mEntityV1).build();
        UUri child = UUri.newBuilder().setAuthority(TEST_AUTHORITY).setEntity(mEntityV2).setResource(
                mNewResource).build();
        String versionUri = toLongUri(parent);
        String topicUri = toLongUri(child);
        Node topicNode = Node.newBuilder()
                .setUri(topicUri)
                .setType(Node.Type.TOPIC)
                .build();
        Node versionNode = Node.newBuilder()
                .setUri(versionUri)
                .setType(Node.Type.VERSION)
                .addNodes(topicNode)
                .build();

        UStatus sts = mDiscoveryMgr.updateNode(versionNode, -1);
        assertEquals(UCode.INVALID_ARGUMENT, sts.getCode());
    }

    @Test
    public void negative_version_level_authority_mismatch() {
        assertTrue(mDiscoveryMgr.init(TEST_AUTHORITY));
        populateDatabase();

        UEntity entity = UEntity.newBuilder().setName(TEST_ENTITY_NAME).build();
        UUri parentUri = UUri.newBuilder().setEntity(entity).build();
        UUri childUri = UUri.newBuilder().setAuthority(TEST_AUTHORITY).setEntity(mEntityV1).build();
        String entityUri = toLongUri(parentUri);
        String verionUri = toLongUri(childUri);
        Node versionNode = Node.newBuilder()
                .setUri(verionUri)
                .setType(Node.Type.VERSION)
                .build();
        Node EntityNode = Node.newBuilder()
                .setUri(entityUri)
                .setType(Node.Type.ENTITY)
                .addNodes(versionNode)
                .build();

        UStatus sts = mDiscoveryMgr.updateNode(EntityNode, -1);
        assertEquals(UCode.INVALID_ARGUMENT, sts.getCode());
    }

    @Test
    public void positive_addNodes_add_device() {
        UAuthority root = UAuthority.newBuilder().setName(TEST_DOMAIN).build();
        String deviceName = String.join(".", TEST_ALTERNATE_DEVICE, TEST_DOMAIN);
        UAuthority device = UAuthority.newBuilder().setName(deviceName).build();
        UUri domainUri = UUri.newBuilder().setAuthority(root).build();
        UUri deviceUri = UUri.newBuilder().setAuthority(device).build();
        Node node = Node.newBuilder()
                .setUri(toLongUri(deviceUri))
                .setType(Node.Type.DEVICE)
                .build();

        UStatus sts = mDiscoveryMgr.addNodes(domainUri, List.of(node));
        assertEquals(UCode.OK, sts.getCode());
    }

    @Test
    public void positive_addNodes_add_entity() {
        populateDatabase();
        UUri versionUri = UUri.newBuilder().setAuthority(TEST_AUTHORITY).setEntity(mEntityV1).build();
        UUri resourceUri = UUri.newBuilder()
                .setAuthority(TEST_AUTHORITY)
                .setEntity(mEntityV1)
                .setResource(mNewResource)
                .build();

        Node node = Node.newBuilder()
                .setUri(toLongUri(resourceUri))
                .setType(Node.Type.RESOURCE)
                .build();

        UStatus sts = mDiscoveryMgr.addNodes(versionUri, List.of(node));
        assertEquals(UCode.OK, sts.getCode());
    }

    @Test
    public void negative_addNodes_already_exists() {
        populateDatabase();
        UEntity entity = UEntity.newBuilder().setName(TEST_ENTITY_NAME).build();
        UUri entityUri = UUri.newBuilder().setAuthority(TEST_AUTHORITY).setEntity(entity).build();
        Node duplicateNode = Node.newBuilder()
                .setUri(toLongUri(entityUri))
                .setType(Node.Type.ENTITY)
                .build();
        UUri parentUri = UUri.newBuilder().setAuthority(TEST_AUTHORITY).build();

        UStatus sts = mDiscoveryMgr.addNodes(parentUri, List.of(duplicateNode));
        assertEquals(UCode.ALREADY_EXISTS, sts.getCode());
    }

    @Test
    public void negative_addNodes_empty_list() {
        UStatus sts = mDiscoveryMgr.addNodes(UUri.getDefaultInstance(), List.of());
        assertEquals(UCode.INVALID_ARGUMENT, sts.getCode());
    }

    @Test
    public void negative_addNodes_not_found() {
        UUri parentUri = UUri.newBuilder().setAuthority(TEST_AUTHORITY).setEntity(mUnknownEntity).build();

        UStatus sts = mDiscoveryMgr.addNodes(parentUri, List.of(Node.getDefaultInstance()));
        assertEquals(UCode.NOT_FOUND, sts.getCode());
    }
}
