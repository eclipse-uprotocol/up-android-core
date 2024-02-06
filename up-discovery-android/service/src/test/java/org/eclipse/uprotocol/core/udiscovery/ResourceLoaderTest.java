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

import static android.util.Log.INFO;
import static android.util.Log.VERBOSE;
import static org.eclipse.uprotocol.common.util.UStatusUtils.toStatus;
import static org.eclipse.uprotocol.common.util.log.Formatter.join;
import static org.eclipse.uprotocol.common.util.log.Formatter.tag;
import static org.eclipse.uprotocol.core.udiscovery.db.JsonNodeTest.REGISTRY_JSON;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.util.Log;

import org.eclipse.uprotocol.common.UStatusException;
import org.eclipse.uprotocol.common.util.log.Key;
import org.eclipse.uprotocol.core.udiscovery.common.Constants;
import org.eclipse.uprotocol.core.udiscovery.db.DiscoveryManager;
import org.eclipse.uprotocol.v1.UCode;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowLog;

import java.lang.reflect.Method;

@RunWith(RobolectricTestRunner.class)
public class ResourceLoaderTest extends TestBase {

    public static final String TAG = tag(SERVICE.getName());
    @Mock
    public AssetManager mAssetManager;
    @Mock
    public Context appContext;
    @Mock
    public Notifier mNotifier;
    @Rule //initMocks
    public MockitoRule rule = MockitoJUnit.rule();
    public IntegrityCheck mIntegrity;
    public DiscoveryManager mDiscoveryMgr;
    ResourceLoader mResourceLoader;

    // using reflection to access hidden static SystemProperties.setProperty function
    public static void setProperty(String key, String value) {
        try {
            Class<?> c = Class.forName("android.os.SystemProperties");
            Method set = c.getMethod("set", String.class, String.class);
            set.invoke(c, key, value);
        } catch (Exception e) {
            Log.e(TAG, join(Key.MESSAGE, "setProperty", Key.FAILURE, toStatus(e)));
            e.printStackTrace();
        }
    }

    private static void setLogLevel(int level) {
        ResourceLoader.DEBUG = (level <= Log.DEBUG);
        ResourceLoader.VERBOSE = (level <= VERBOSE);
    }

    @Before
    public void setUp() {
        ShadowLog.stream = System.out;
        setLogLevel(Log.DEBUG);
        mIntegrity = new IntegrityCheck();
        mDiscoveryMgr = new DiscoveryManager(mNotifier);
        mResourceLoader = Mockito.spy(new ResourceLoader(appContext, mAssetManager, mDiscoveryMgr));
    }

    @Test
    public void testinitializeLDS() {
        setLogLevel(VERBOSE);
        when(mAssetManager.readFileFromInternalStorage(appContext, Constants.LDS_DB_FILENAME)).thenReturn(REGISTRY_JSON);
        DiscoveryManager discoveryManager = Mockito.spy(new DiscoveryManager(mNotifier));
        when(discoveryManager.load(REGISTRY_JSON)).thenReturn(true);
        mResourceLoader = Mockito.spy(new ResourceLoader(appContext, mAssetManager, discoveryManager));
        assertEquals(ResourceLoader.InitLDSCode.SUCCESS, mResourceLoader.initializeLDS());
    }

    @Test
    public void testLoglevelinitializeLDS() {
        setLogLevel(INFO);
        when(mAssetManager.readFileFromInternalStorage(appContext, Constants.LDS_DB_FILENAME)).thenReturn(REGISTRY_JSON);
        DiscoveryManager discoveryManager = Mockito.spy(new DiscoveryManager(mNotifier));
        when(discoveryManager.load(REGISTRY_JSON)).thenReturn(true);
        mResourceLoader = Mockito.spy(new ResourceLoader(appContext, mAssetManager, discoveryManager));
        assertEquals(ResourceLoader.InitLDSCode.SUCCESS, mResourceLoader.initializeLDS());
    }


    @Test
    public void testinitializeLDS_Recovery() {
        when(mAssetManager.readFileFromInternalStorage(appContext, Constants.LDS_DB_FILENAME)).thenReturn(REGISTRY_JSON);
        DiscoveryManager discoveryManager = Mockito.spy(new DiscoveryManager(mNotifier));
        when(discoveryManager.load(REGISTRY_JSON)).thenReturn(true);
        mResourceLoader = Mockito.spy(new ResourceLoader(appContext, mAssetManager, discoveryManager, ResourceLoader.InitLDSCode.RECOVERY));
        assertEquals(ResourceLoader.InitLDSCode.FAILURE, mResourceLoader.initializeLDS());
    }

    @Test
    public void testLoadFailure1() {
        when(mAssetManager.readFileFromInternalStorage(appContext, Constants.LDS_DB_FILENAME)).thenReturn("corrupt lds");
        UStatusException uStatusException = assertThrows(UStatusException.class, () -> mResourceLoader.initializeLDS());
        assertEquals(UCode.FAILED_PRECONDITION, uStatusException.getCode());
        verify(mAssetManager, times(1)).readFileFromInternalStorage(appContext, Constants.LDS_DB_FILENAME);
    }

    @Test
    public void testSaveDBFaliure2() {
        setLogLevel(INFO);
        when(mAssetManager.readFileFromInternalStorage(appContext, Constants.LDS_DB_FILENAME)).thenReturn(REGISTRY_JSON);
        when(mAssetManager.writeFileToInternalStorage(appContext, Constants.LDS_DB_FILENAME, REGISTRY_JSON)).thenReturn(false);
        DiscoveryManager discoveryManager = Mockito.spy(new DiscoveryManager(mNotifier));
        when(discoveryManager.load(REGISTRY_JSON)).thenReturn(true);
        mResourceLoader = Mockito.spy(new ResourceLoader(appContext, mAssetManager, discoveryManager, ResourceLoader.InitLDSCode.RECOVERY));
        assertEquals(ResourceLoader.InitLDSCode.FAILURE, mResourceLoader.initializeLDS());
    }

    @Test
    public void testSaveDBFaliure3() {
        setLogLevel(INFO);
        when(mAssetManager.readFileFromInternalStorage(appContext, Constants.LDS_DB_FILENAME)).thenReturn(REGISTRY_JSON);
        when(mAssetManager.writeFileToInternalStorage(appContext, Constants.LDS_DB_FILENAME, REGISTRY_JSON)).thenReturn(true);
        DiscoveryManager discoveryManager = Mockito.spy(new DiscoveryManager(mNotifier));
        when(discoveryManager.export()).thenReturn(REGISTRY_JSON);
        when(discoveryManager.load(REGISTRY_JSON)).thenReturn(true);
        mResourceLoader = Mockito.spy(new ResourceLoader(appContext, mAssetManager, discoveryManager, ResourceLoader.InitLDSCode.RECOVERY));
        assertEquals(ResourceLoader.InitLDSCode.RECOVERY, mResourceLoader.initializeLDS());
    }
}
