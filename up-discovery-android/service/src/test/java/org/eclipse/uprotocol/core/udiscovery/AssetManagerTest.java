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

import static org.eclipse.uprotocol.core.udiscovery.common.Constants.LDS_DB_FILENAME;
import static org.eclipse.uprotocol.core.udiscovery.db.JsonNodeTest.REGISTRY_JSON;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import android.content.Context;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowLog;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

@RunWith(RobolectricTestRunner.class)
public class AssetManagerTest extends TestBase {

    public Context mContext;

    AssetManager mAssetManager;

    @Mock
    Context mockContext;
    private FileWriter mWriter;
    private BufferedReader mBufferedReader;
    private File mFile;

    @Before
    public void setUp() throws IOException {
        MockitoAnnotations.openMocks(this);
        ShadowLog.stream = System.out;
        mAssetManager = new AssetManager();
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        mFile = File.createTempFile("lds", ".json");
        mWriter = Mockito.spy(new FileWriter(mFile));
        mBufferedReader = Mockito.spy(new BufferedReader(new FileReader(mFile)));
        try (FileWriter writer = new FileWriter(mFile)) {
            writer.write(REGISTRY_JSON);
        }
    }

    @Test
    public void test_readFileFromInternalStorage() {
        when(mockContext.getFilesDir()).thenReturn(new File("/tmp"));
        boolean actual = mAssetManager.writeFileToInternalStorage(mockContext, LDS_DB_FILENAME,
                REGISTRY_JSON);
        assertTrue(actual);
        String actualReadData = mAssetManager.readFileFromInternalStorage(mockContext, LDS_DB_FILENAME);
        assertEquals(REGISTRY_JSON.replaceAll("\\n", ""), actualReadData);
    }

    @Test
    public void test_readFileFromInternalStorage_FileNotFound() {
        String actual = mAssetManager.readFileFromInternalStorage(mContext, LDS_DB_FILENAME);
        assertEquals("", actual);
    }

    @Test
    public void test_readFileFromInternalStorage_IOException() {
        String data = null;
        when(mockContext.getFilesDir()).thenReturn(new File("/tmp"));
        mAssetManager = new AssetManager(mBufferedReader, mWriter, mFile);
        try {
            doThrow(new IOException()).when(mBufferedReader).readLine();
            data = mAssetManager.readFileFromInternalStorage(mockContext, LDS_DB_FILENAME);
        } catch (Exception ignored) {
        }
        assert data != null;
        assertTrue(data.isEmpty());
    }

    @Test
    public void test_writeFileToInternalStorage_IOException() {
        boolean actual = false;
        when(mockContext.getFilesDir()).thenReturn(new File("/tmp"));
        mAssetManager = new AssetManager(mBufferedReader, mWriter, mFile);
        try {
            doThrow(new IOException()).when(mWriter).close();
            actual = mAssetManager.writeFileToInternalStorage(mockContext, LDS_DB_FILENAME,
                    REGISTRY_JSON);
        } catch (Exception ignored) {
        }
        assertFalse(actual);
    }
}
