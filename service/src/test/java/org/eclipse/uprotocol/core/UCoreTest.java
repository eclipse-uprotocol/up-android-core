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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.eclipse.uprotocol.core.ubus.UBus;
import org.eclipse.uprotocol.core.usubscription.USubscription;
import org.eclipse.uprotocol.core.utwin.UTwin;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.PrintWriter;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class UCoreTest extends TestBase {
    private final Context mContext = mock(Context.class);
    private final UBus mUBus = mock(UBus.class);
    private final UTwin mUTwin = mock(UTwin.class);
    private final USubscription mUSubscription = mock(USubscription.class);
    private final UCore mUCore = new UCore.Builder(mContext)
            .setUBus(mUBus)
            .setUTwin(mUTwin)
            .setUSubscription(mUSubscription)
            .build();

    @Test
    public void testConstructor() {
        final UCore core = new UCore.Builder(mContext).build();
        assertNotNull(core.getUBus());
        assertNotNull(core.getUTwin());
        assertNotNull(core.getUSubscription());
    }

    @Test
    public void testInit() {
        mUCore.init();
        mUCore.getComponents().forEach(component -> verify(component, times(1)).init(mUCore));
    }

    @Test
    public void testStartup() {
        mUCore.startup();
        mUCore.getComponents().forEach(component -> verify(component, times(1)).startup());
    }

    @Test
    public void testShutdown() {
        mUCore.shutdown();
        mUCore.getComponents().forEach(component -> verify(component, times(1)).shutdown());
    }

    @Test
    public void testClearCache() {
        mUCore.clearCache();
        mUCore.getComponents().forEach(component -> verify(component, times(1)).clearCache());
    }

    @Test
    public void testDump() {
        final PrintWriter writer = mock(PrintWriter.class);
        final String[] args = new String[0];
        mUCore.dump(writer, args);
        mUCore.getComponents().forEach(component -> verify(component, times(1)).dump(writer, args));
    }

    @Test
    public void testGetComponents() {
        final List<UCore.Component> components = mUCore.getComponents();
        assertTrue(components.contains(mUBus));
        assertTrue(components.contains(mUTwin));
        assertTrue(components.contains(mUSubscription));
    }

    @Test
    public void testGetContext() {
        assertEquals(mContext, mUCore.getContext());
    }
    @Test
    public void testGetUBus() {
        assertEquals(mUBus, mUCore.getUBus());
    }

    @Test
    public void testGetUTwin() {
        assertEquals(mUTwin, mUCore.getUTwin());
    }

    @Test
    public void testGetUSubscription() {
        assertEquals(mUSubscription, mUCore.getUSubscription());
    }

    @Test
    public void testComponentDefaultImplementation() {
        final UCore.Component component = new UCore.Component() {};
        assertNotNull(component);
        component.init(mUCore);
        component.startup();
        component.shutdown();
        component.clearCache();
        component.dump(mock(PrintWriter.class), new String[0]);
    }
}
