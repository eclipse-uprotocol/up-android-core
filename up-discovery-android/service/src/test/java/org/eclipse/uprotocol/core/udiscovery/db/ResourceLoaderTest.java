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


import static org.eclipse.uprotocol.core.udiscovery.TestBase.TEST_AUTHORITY;
import static org.eclipse.uprotocol.core.udiscovery.TestBase.TEST_INSTANCE_4;
import static org.eclipse.uprotocol.core.udiscovery.TestBase.TEST_RESOURCE;
import static org.eclipse.uprotocol.core.udiscovery.internal.Utils.parseAuthority;
import static org.eclipse.uprotocol.core.udiscovery.internal.Utils.toLongUri;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.eclipse.uprotocol.core.udiscovery.v3.Node;
import org.eclipse.uprotocol.v1.UAuthority;
import org.eclipse.uprotocol.v1.UEntity;
import org.eclipse.uprotocol.v1.UResource;
import org.eclipse.uprotocol.v1.UUri;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowLog;

import java.util.List;

@RunWith(RobolectricTestRunner.class)
public class ResourceLoaderTest {
    private String nameShort = "a";
    private String nameMedium = "ab";
    private String nameLong = "abc";
    private String mLevel0;
    private String mLevel1;
    private String mLevel2;
    private String mLevel3;
    private String mLevel4;
    private Node mNode;

    @Before
    public void setUp() {
        ShadowLog.stream = System.out;
        String domain = parseAuthority(TEST_AUTHORITY).second;
        UAuthority rootAuthority = UAuthority.newBuilder().setName(domain).build();
        UEntity entityShort = UEntity.newBuilder().setName(nameShort).build();
        UEntity entityMedium = UEntity.newBuilder().setName(nameMedium).build();
        UEntity entityLong = UEntity.newBuilder().setName(nameLong).build();
        UEntity entityV1 = UEntity.newBuilder().setName(nameMedium).setVersionMajor(1).build();
        UResource resource = UResource.newBuilder().setName(TEST_RESOURCE).setInstance(TEST_INSTANCE_4).build();

        mLevel0 = toLongUri(UUri.newBuilder().setAuthority(rootAuthority).build());
        mLevel1 = toLongUri(UUri.newBuilder().setAuthority(TEST_AUTHORITY).build());
        String level2_0 = toLongUri(UUri.newBuilder().setAuthority(TEST_AUTHORITY).setEntity(entityShort).build());
        String level2_1 = toLongUri(UUri.newBuilder().setAuthority(TEST_AUTHORITY).setEntity(entityLong).build());
        mLevel2 = toLongUri(UUri.newBuilder().setAuthority(TEST_AUTHORITY).setEntity(entityMedium).build());
        mLevel3 = toLongUri(UUri.newBuilder().setAuthority(TEST_AUTHORITY).setEntity(entityV1).build());
        mLevel4 = toLongUri(UUri.newBuilder().setAuthority(TEST_AUTHORITY).setEntity(entityV1).setResource(resource).build());

        Node.Builder builder4 = Node.newBuilder().setUri(mLevel4).setType(Node.Type.RESOURCE);
        Node.Builder builder3 = Node.newBuilder().setUri(mLevel3).setType(Node.Type.VERSION).addNodes(builder4);
        Node.Builder builder2_2 = Node.newBuilder().setUri(mLevel2).setType(Node.Type.ENTITY).addNodes(builder3);
        Node.Builder builder2_1 = Node.newBuilder().setUri(level2_1).setType(Node.Type.ENTITY);
        Node.Builder builder2_0 = Node.newBuilder().setUri(level2_0).setType(Node.Type.ENTITY);
        Node.Builder builder1 = Node.newBuilder().setUri(mLevel1).setType(Node.Type.DEVICE)
                .addNodes(builder2_0)
                .addNodes(builder2_1)
                .addNodes(builder2_2);
        Node.Builder builder0 = Node.newBuilder().setUri(mLevel0).setType(Node.Type.DOMAIN).addNodes(builder1);
        mNode = builder0.build();
    }

    @Test
    public void findNode_level_0() {
        Node result = DatabaseLoader.internalFindNode(mNode, mLevel0);
        assertNotNull(result);
    }

    @Test
    public void findNode_level_1() {
        Node result = DatabaseLoader.internalFindNode(mNode, mLevel1);
        assertNotNull(result);
    }

    @Test
    public void findNode_level_2() {
        Node result = DatabaseLoader.internalFindNode(mNode, mLevel2);
        assertNotNull(result);
    }

    @Test
    public void findNode_level_3() {
        Node result = DatabaseLoader.internalFindNode(mNode, mLevel3);
        assertNotNull(result);
    }

    @Test
    public void findNode_level_4() {
        Node result = DatabaseLoader.internalFindNode(mNode, mLevel4);
        assertNotNull(result);
    }

    @Test
    public void isSubUri_test() {
        String uri = toLongUri(UUri.newBuilder().setAuthority(TEST_AUTHORITY).build());
        Node.Builder child = Node.newBuilder().setUri(uri);
        Node.Builder parent = Node.newBuilder().addNodes(child);
        List<Node.Builder> path = DatabaseLoader.FindPathToNode(parent, uri);
        assertFalse(path.isEmpty());
    }

}
