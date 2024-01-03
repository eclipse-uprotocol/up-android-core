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
package org.eclipse.uprotocol.core;

import static org.eclipse.uprotocol.client.BuildConfig.LIBRARY_PACKAGE_NAME;
import static org.eclipse.uprotocol.core.ubus.UBusManager.ACTION_BIND_UBUS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.eclipse.uprotocol.core.ubus.UBusAdapter;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.robolectric.RuntimeEnvironment;

import java.io.PrintWriter;
import java.io.StringWriter;

@RunWith(AndroidJUnit4.class)
public class UCoreServiceTest extends TestBase {
    private UCore mUCore;
    private UCoreService mService;

    @Before
    public void setUp() {
        mUCore = spy(newMockUCoreBuilder().build());
        mService = newUCoreService(mUCore);
    }

    private static UCoreService newUCoreService(@NonNull UCore uCore) {
        final UCoreService service = Mockito.spy(new UCoreService());
        doReturn(true).when(service).bindServiceAsUser(any(), any(), anyInt(), any());
        doReturn(uCore).when(service).newUCore(any());
        service.onCreate();
        return service;
    }

    @Test
    public void testNewUCore() {
        final UCoreService service = Mockito.spy(new UCoreService());
        doCallRealMethod().when(service).newUCore(any());
        assertNotNull(service.newUCore(RuntimeEnvironment.getApplication()));
    }

    @Test
    public void testOnCreate() {
        verify(mUCore, times(1)).init();
        verify(mUCore, times(1)).startup();
    }

    @Test
    public void testOnBind() {
        final Intent intent = new Intent(ACTION_BIND_UBUS);
        final IBinder binder = mService.onBind(intent);
        assertTrue(binder instanceof UBusAdapter);
        assertEquals(binder, mService.onBind(intent));
    }

    @Test
    public void testOnBindNoAction() {
        assertNull(mService.onBind(new Intent()));
    }

    @Test
    public void testOnStartCommand() {
        assertEquals(Service.START_STICKY, mService.onStartCommand(new Intent(), 0, 0));
    }

    @Test
    public void testOnDestroy() {
        mService.onDestroy();
        verify(mUCore, times(1)).shutdown();
    }

    @Test
    public void testDump() {
        final StringWriter out = new StringWriter();
        final PrintWriter writer = new PrintWriter(out);
        mService.dump(null, writer, null);
        verify(mUCore, times(1)).dump(writer, null);
        writer.flush();
        final String output = out.toString();
        assertTrue(output.contains(LIBRARY_PACKAGE_NAME));
    }

    @Test
    public void testDumpWithArgs() {
        final StringWriter out = new StringWriter();
        final PrintWriter writer = new PrintWriter(out);
        final String[] args = {"-t"};
        mService.dump(null, writer, args);
        verify(mUCore, times(1)).dump(writer, args);
        writer.flush();
        final String output = out.toString();
        assertFalse(output.contains(LIBRARY_PACKAGE_NAME));
    }
}
