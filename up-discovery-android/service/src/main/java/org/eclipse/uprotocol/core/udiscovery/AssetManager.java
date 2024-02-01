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

import static org.eclipse.uprotocol.common.util.UStatusUtils.toStatus;
import static org.eclipse.uprotocol.common.util.log.Formatter.join;
import static org.eclipse.uprotocol.common.util.log.Formatter.tag;
import static org.eclipse.uprotocol.core.udiscovery.v3.UDiscovery.SERVICE;

import android.content.Context;
import android.util.Log;

import androidx.annotation.VisibleForTesting;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

/**
 * The AssetManager class is responsible for managing assets in the application.
 * It provides methods to read from and write to internal storage.
 * <p>
 * The class contains two private fields:
 * - mBufferReader: a BufferedReader used for reading files
 * - mWriter: a FileWriter used for writing files
 * <p>
 * The class provides two constructors:
 * - A default constructor that initializes mBufferReader and mWriter to null
 * - A constructor for testing that accepts a BufferedReader and FileWriter
 * <p>
 * The class provides two public methods:
 * - readFileFromInternalStorage: reads a file from internal storage and returns its content as a string
 * - writeFileToInternalStorage: writes a string to a file in internal storage and returns a boolean indicating success or failure
 */
public class AssetManager {

    private static final String TAG = tag(SERVICE.getName());
    private final BufferedReader mBufferReader;
    private final FileWriter mWriter;

    public AssetManager() {
        mBufferReader = null;
        mWriter = null;
    }

    @VisibleForTesting
    AssetManager(BufferedReader reader, FileWriter writer) {
        mBufferReader = reader;
        mWriter = writer;
    }

    /**
     * Reads a file from internal storage and returns its content as a string
     * @param context Context of the application
     * @param fileName Name of the file to read
     * @return Content of the file as a string
     */
    public String readFileFromInternalStorage(Context context, String fileName) {
        try {
            final String filepath = context.getFilesDir() + "/" + fileName;
            final StringBuilder buffer = new StringBuilder();
            BufferedReader reader = (null != mBufferReader) ? mBufferReader :
                    new BufferedReader(new FileReader(filepath));
            String currentLine;
            while ((currentLine = reader.readLine()) != null) {
                buffer.append(currentLine);
            }
            return buffer.toString();

        } catch (FileNotFoundException e) {
            Log.e(TAG, join("readFileFromInternalStorage", toStatus(e)));
            e.printStackTrace();
        } catch (IOException e) {
            Log.e(TAG, join("readFileFromInternalStorage", toStatus(e)));
            e.printStackTrace();
        }
        return "";
    }

    /**
     * Writes a string to a file in internal storage and returns a boolean indicating success or failure
     * @param context Context of the application
     * @param sFileName Name of the file to write to
     * @param sBody Content to write to the file
     * @return Boolean indicating success (true) or failure (false)
     */
    public boolean writeFileToInternalStorage(Context context, String sFileName, String sBody) {
        try {
            final File fd = new File(context.getFilesDir(), sFileName);
            final FileWriter writer = (null != mWriter) ? mWriter : new FileWriter(fd);
            writer.append(sBody);
            writer.flush();
            writer.close();
            return true;
        } catch (IOException e) {
            Log.e(TAG, join("writeFileToInternalStorage", toStatus(e)));
            e.printStackTrace();
        }
        return false;
    }
}
