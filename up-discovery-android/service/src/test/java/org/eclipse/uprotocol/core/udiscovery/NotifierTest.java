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
package org.eclipse.uprotocol.core.udiscovery;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.util.Log;

import org.eclipse.uprotocol.UPClient;
import org.eclipse.uprotocol.core.udiscovery.v3.Notification;
import org.eclipse.uprotocol.v1.UAuthority;
import org.eclipse.uprotocol.v1.UEntity;
import org.eclipse.uprotocol.v1.UResource;
import org.eclipse.uprotocol.v1.UUri;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowLog;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


@RunWith(RobolectricTestRunner.class)
public class NotifierTest extends TestBase {
    private UPClient mUpClient;
    private ObserverManager mObserverManager;
    private Notifier mNotifier;
    private List<UUri> mUriPath;
    private List<UUri> mAddedUris;
    private Map<UUri, Set<UUri>> mObserverMap;

    private static void setLogLevel(int level) {
        Notifier.DEBUG = (level <= Log.DEBUG);
        Notifier.VERBOSE = (level <= Log.VERBOSE);
    }

    @Before
    public void setUp() {
        ShadowLog.stream = System.out;
        setLogLevel(Log.INFO);
        mObserverManager = mock(ObserverManager.class);
        mNotifier = new Notifier(mObserverManager, mUpClient);
        mObserverMap = buildObserverMap();
        mUriPath = getUriPath();
        mAddedUris = getAddedUris();
    }

    private HashMap<UUri, Set<UUri>> buildObserverMap() {
        UEntity observerEntity = UEntity.newBuilder().setName(TEST_OBSERVER1).build();
        UUri observer = UUri.newBuilder().setAuthority(TEST_AUTHORITY).setEntity(observerEntity).build();

        UEntity entity = UEntity.newBuilder().setName(TEST_ENTITY_NAME).build();
        UUri nodeUri = UUri.newBuilder().setAuthority(TEST_AUTHORITY).setEntity(entity).build();

        Set<UUri> set = new HashSet<>();
        set.add(observer);

        HashMap<UUri, Set<UUri>> map = new HashMap<>();
        map.put(nodeUri, set);

        return map;
    }

    private List<UUri> getUriPath() {
        UAuthority domainAuthority = UAuthority.newBuilder().setName(TEST_DOMAIN).build();

        UEntity serviceEntity = UEntity.newBuilder().setName(TEST_ENTITY_NAME).build();
        UEntity versionEntity = UEntity.newBuilder().setName(TEST_ENTITY_NAME).setVersionMajor(1).build();

        UUri domainUri = UUri.newBuilder().setAuthority(domainAuthority).build();
        UUri deviceUri = UUri.newBuilder().setAuthority(TEST_AUTHORITY).build();
        UUri entityUri = UUri.newBuilder().setAuthority(TEST_AUTHORITY).setEntity(serviceEntity).build();
        UUri versionUri = UUri.newBuilder().setAuthority(TEST_AUTHORITY).setEntity(versionEntity).build();

        return List.of(domainUri, deviceUri, entityUri, versionUri);
    }

    private List<UUri> getAddedUris() {
        UEntity versionEntity = UEntity.newBuilder().setName(TEST_ENTITY_NAME).setVersionMajor(1).build();
        UResource resource = UResource.newBuilder().setName(TEST_RESOURCE).setInstance(TEST_INSTANCE_1).build();
        UUri resourceUri = UUri.newBuilder().setAuthority(TEST_AUTHORITY).setEntity(versionEntity).setResource(resource).build();
        return List.of(resourceUri);
    }

    @Test
    public void testNotifyObserver() {
        when(mObserverManager.getObserverMap()).thenReturn(mObserverMap);
        mNotifier.notifyObserversWithParentUri(Notification.Operation.UPDATE, mUriPath);
        //TODO capture and verify the notification event sent to uLink
        //when(mUltifiLink.publish(any())).thenReturn(buildStatus(Code.OK));
    }

    @Test
    public void testNotifyObserver_debug() {
        setLogLevel(Log.DEBUG);
        when(mObserverManager.getObserverMap()).thenReturn(mObserverMap);
        mNotifier.notifyObserversWithParentUri(Notification.Operation.UPDATE, mUriPath);
        //TODO capture and verify the notification event sent to uLink
        //when(mUltifiLink.publish(any())).thenReturn(buildStatus(Code.OK));
    }

    @Test
    public void testNotifyObserver_operation_invalid() {
        when(mObserverManager.getObserverMap()).thenReturn(mObserverMap);
        mNotifier.notifyObserversWithParentUri(Notification.Operation.INVALID, mUriPath);
        //TODO capture and verify the notification event sent to uLink
        //verifyNoInteractions(mUltifiLink);
    }

    @Test
    public void testNotifyObserver_operation_add() {
        when(mObserverManager.getObserverMap()).thenReturn(mObserverMap);
        mNotifier.notifyObserversWithParentUri(Notification.Operation.ADD, mUriPath);
        //TODO capture and verify the notification event sent to uLink
        //verifyNoInteractions(mUltifiLink);
    }

    @Test
    public void testNotifyNodePath_empty() {
        mNotifier.notifyObserversWithParentUri(Notification.Operation.UPDATE, List.of());
        //verifyNoInteractions(mUltifiLink);
    }

    @Test
    public void testNotifyObserverAddNodes() {
        when(mObserverManager.getObserverMap()).thenReturn(mObserverMap);
        mNotifier.notifyObserversAddNodes(mUriPath, mAddedUris);
        //TODO capture and verify the notification event sent to uLink
        //when(mUltifiLink.publish(any())).thenReturn(buildStatus(Code.OK));
    }

    @Test
    public void testNotifyObserverAddNodes_debug() {
        setLogLevel(Log.DEBUG);
        when(mObserverManager.getObserverMap()).thenReturn(mObserverMap);
        mNotifier.notifyObserversAddNodes(mUriPath, mAddedUris);
        //TODO capture and verify the notification event sent to uLink
        //when(mUltifiLink.publish(any())).thenReturn(buildStatus(Code.OK));
    }

    @Test
    public void testNotifyObserverAddNodes_empty() {
        mNotifier.notifyObserversAddNodes(List.of(), List.of());
        //verifyNoInteractions(mUltifiLink);
    }
}
