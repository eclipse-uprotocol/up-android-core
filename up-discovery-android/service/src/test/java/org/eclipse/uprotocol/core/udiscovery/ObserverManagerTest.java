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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.util.Log;

import androidx.room.Room;

import org.eclipse.uprotocol.core.udiscovery.db.ObserverDao;
import org.eclipse.uprotocol.core.udiscovery.db.ObserverDatabase;
import org.eclipse.uprotocol.core.udiscovery.db.ObserverDatabaseKt;
import org.eclipse.uprotocol.uri.serializer.LongUriSerializer;
import org.eclipse.uprotocol.v1.UCode;
import org.eclipse.uprotocol.v1.UEntity;
import org.eclipse.uprotocol.v1.UResource;
import org.eclipse.uprotocol.v1.UStatus;
import org.eclipse.uprotocol.v1.UUri;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowLog;

import java.util.List;
import java.util.Map;
import java.util.Set;

@RunWith(RobolectricTestRunner.class)
public class ObserverManagerTest extends TestBase {

    List<UUri> mUriList;
    private ObserverManager mObserverManager;
    @Mock
    private ObserverDatabase mDatabase;
    @Mock
    private ObserverDao mObserverDao;
    private UUri mObserver1;
    private UUri mObserver2;

    private static void setLogLevel(int level) {
        ObserverManager.DEBUG = (level <= Log.DEBUG);
        ObserverManager.VERBOSE = (level <= Log.VERBOSE);
    }

    @Before
    public void setUp() {
        ShadowLog.stream = System.out;
        setLogLevel(Log.INFO);
        Context context = spy(Context.class);
        mDatabase = spy(Room.inMemoryDatabaseBuilder(context,
                ObserverDatabase.class).allowMainThreadQueries().build());
        when(ObserverDatabaseKt.createDbExtension(context)).thenReturn(mDatabase);
        mObserverManager = spy(new ObserverManager(mDatabase));
        UEntity versionEntity = UEntity.newBuilder().setName(TEST_ENTITY_NAME).setVersionMajor(1).build();
        UEntity observerEntity1 = UEntity.newBuilder().setName(TEST_OBSERVER1).build();
        UEntity observerEntity2 = UEntity.newBuilder().setName(TEST_OBSERVER2).build();
        UResource resource1 = UResource.newBuilder().setName(TEST_RESOURCE).setInstance(
                TEST_INSTANCE_1).build();
        UResource resource2 = UResource.newBuilder().setName(TEST_RESOURCE).setInstance(
                TEST_INSTANCE_2).build();

        mObserver1 = UUri.newBuilder().setAuthority(TEST_AUTHORITY).setEntity(observerEntity1).build();
        mObserver2 = UUri.newBuilder().setAuthority(TEST_AUTHORITY).setEntity(observerEntity2).build();
        UUri uri1 = UUri.newBuilder().setAuthority(TEST_AUTHORITY).setEntity(versionEntity).setResource(resource1).build();
        UUri uri2 = UUri.newBuilder().setAuthority(TEST_AUTHORITY).setEntity(versionEntity).setResource(resource2).build();
        mUriList = List.of(uri1, uri2);
    }

    @After
    public void tearDown() {
        mDatabase.close();
    }

    @Test
    public void testRegisterObserverSuccess() {
        UStatus status = mObserverManager.registerObserver(mUriList, mObserver1);
        assertEquals(UCode.OK, status.getCode());
    }

    @Test
    public void testRegisterObserver_empty() {
        UStatus status = mObserverManager.registerObserver(List.of(), mObserver1);
        assertEquals(UCode.OK, status.getCode());
    }

    @Test
    public void testRegisterObserverDBInsertionFailed() {
        mObserverDao = Mockito.mock(ObserverDao.class, Answers.RETURNS_MOCKS);
        when(mDatabase.observerDao()).thenReturn(mObserverDao);
        when(mObserverDao.addObserver(any())).thenReturn(-1L);
        UStatus status = mObserverManager.registerObserver(mUriList, mObserver1);
        assertEquals(UCode.INTERNAL, status.getCode());
    }

    @Test
    public void testUnregisterObserverSuccess() {
        testRegisterObserverSuccess();
        UStatus status = mObserverManager.unregisterObserver(mUriList, mObserver1);
        assertEquals(UCode.OK, status.getCode());
    }

    @Test
    public void testUnregisterObserverNotInMap() {
        UStatus status = mObserverManager.unregisterObserver(mUriList, mObserver1);
        assertEquals(UCode.OK, status.getCode());
    }

    @Test
    public void testUnregisterObserverMultipleObservers() {
        setLogLevel(Log.DEBUG);
        testRegisterObserverSuccess();
        mObserverManager.registerObserver(mUriList, mObserver2);
        UStatus status = mObserverManager.unregisterObserver(mUriList, mObserver1);
        assertEquals(UCode.OK, status.getCode());
    }

    @Test
    public void testUnregisterObserverDBRemovalFailed() {
        testRegisterObserverSuccess();
        mObserverDao = Mockito.mock(ObserverDao.class, Answers.RETURNS_MOCKS);
        when(mDatabase.observerDao()).thenReturn(mObserverDao);
        when(mObserverDao.removeObserver(any(), any())).thenReturn(-1);
        UStatus status = mObserverManager.unregisterObserver(mUriList, mObserver1);
        assertEquals(UCode.INTERNAL, status.getCode());
    }

    @Test
    public void testUnregisterObserverDBException() {
        testRegisterObserverSuccess();
        when(mDatabase.observerDao()).thenThrow(IllegalArgumentException.class);
        UStatus status = mObserverManager.unregisterObserver(mUriList, mObserver1);
        assertEquals(UCode.INTERNAL, status.getCode());
    }

    @Test
    public void testGetObservers() {
        testRegisterObserverSuccess();
        Set<UUri> observers = mObserverManager.getObservers(mUriList.get(0));
        assertTrue(observers.contains(mObserver1));
    }

    @Test
    public void testGetObservers_debug() {
        setLogLevel(Log.DEBUG);
        testRegisterObserverSuccess();
        Set<UUri> observers = mObserverManager.getObservers(mUriList.get(0));
        assertTrue(observers.contains(mObserver1));
    }

    @Test
    public void testGetObserversEmpty() {
        assertTrue(mObserverManager.getObservers(mUriList.get(0)).isEmpty());
    }

    @Test
    public void testGetObserverMap() {
        testRegisterObserverSuccess();
        Map<UUri, Set<UUri>> observerMap = mObserverManager.getObserverMap();
        assertEquals(observerMap.entrySet().size(), 2);

        Set<UUri> observerSet = observerMap.get(mUriList.get(0));
        assertNotNull(observerSet);
        assertTrue(observerSet.contains(mObserver1));
    }

    @Test
    public void testRegisterObserverSuccessAfterLoadMapFromDb() {
        mObserverManager.mObservers.entrySet().clear();
        mObserverManager.mObservers.keySet().clear();
        mObserverDao = Mockito.mock(ObserverDao.class, Answers.RETURNS_MOCKS);
        when(mDatabase.observerDao()).thenReturn(mObserverDao);
        LongUriSerializer serializer = LongUriSerializer.instance();
        String node1 = serializer.serialize(mUriList.get(0));
        String node2 = serializer.serialize(mUriList.get(1));
        String observer = serializer.serialize(mObserver1);
        when(mObserverDao.getNodeUrisList()).thenReturn(List.of(node1, node2));
        when(mObserverDao.getObserverList(any())).thenReturn(List.of(observer));

        UStatus status = mObserverManager.registerObserver(mUriList, mObserver1);
        assertEquals(UCode.OK, status.getCode());
    }

    @Test
    public void testGetObserverMapAfterLoadMapFromDb() {
        setLogLevel(Log.VERBOSE);
        testRegisterObserverSuccess();
        mObserverManager.mObservers.entrySet().clear();
        mObserverManager.mObservers.keySet().clear();
        Map<UUri, Set<UUri>> observerMap = mObserverManager.getObserverMap();
        assertEquals(observerMap.entrySet().size(), 2);
        assertFalse(observerMap.isEmpty());
        Set<UUri> observerSet = observerMap.get(mUriList.get(0));
        assert observerSet != null;
        assertTrue(observerSet.contains(mObserver1));
    }

    @Test
    public void testAddObserverToDbException() {
        mObserverDao = Mockito.mock(ObserverDao.class, Answers.RETURNS_MOCKS);
        when(mDatabase.observerDao()).thenReturn(mObserverDao);
        when(mDatabase.observerDao().addObserver(any())).thenThrow(IllegalArgumentException.class);
        UStatus status = mObserverManager.registerObserver(mUriList, mObserver1);
        assertEquals(UCode.INTERNAL, status.getCode());
    }
}
