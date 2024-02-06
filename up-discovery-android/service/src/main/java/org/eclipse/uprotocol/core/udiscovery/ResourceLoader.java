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

import static org.eclipse.uprotocol.common.util.UStatusUtils.checkState;
import static org.eclipse.uprotocol.common.util.UStatusUtils.checkStringNotEmpty;
import static org.eclipse.uprotocol.common.util.log.Formatter.join;
import static org.eclipse.uprotocol.common.util.log.Formatter.tag;
import static org.eclipse.uprotocol.core.udiscovery.common.Constants.LDS_AUTHORITY;
import static org.eclipse.uprotocol.core.udiscovery.common.Constants.LDS_DB_FILENAME;
import static org.eclipse.uprotocol.core.udiscovery.v3.UDiscovery.SERVICE;

import android.content.Context;
import android.util.Log;

import androidx.annotation.VisibleForTesting;

import org.eclipse.uprotocol.common.UStatusException;
import org.eclipse.uprotocol.common.util.log.Key;
import org.eclipse.uprotocol.core.udiscovery.db.DiscoveryManager;

/**
 * The ResourceLoader class is responsible for loading resources in the application.
 * It uses an AssetManager to manage assets and a DiscoveryManager to manage discovery operations.
 * It also provides methods to initialize LDS, save and load data.
 * <p>
 * The class contains the following fields:
 * - DEBUG: A boolean value indicating if the debug log is enabled.
 * - VERBOSE: A boolean value indicating if the verbose log is enabled.
 * - mAssetManager: An instance of AssetManager to manage assets.
 * - mDiscoveryMgr: An instance of DiscoveryManager to manage discovery operations.
 * - mContext: An instance of Context.
 * - mCode: An instance of InitLDSCode.
 * <p>
 * The class provides the following methods:
 * - initializeLDS(): Initializes LDS and returns an instance of InitLDSCode.
 * - save(String filename): Saves data to a file and returns a boolean value indicating the success of the operation.
 * - load(String filename): Loads data from a file.
 * <p>
 * The class also contains an enum InitLDSCode with the following values:
 * - FAILURE
 * - RECOVERY
 * - SUCCESS
 */
public class ResourceLoader {
    private static final String TAG = tag(SERVICE.getName());

    protected static boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);
    protected static boolean VERBOSE = Log.isLoggable(TAG, Log.VERBOSE);
    public final AssetManager mAssetManager;
    public final DiscoveryManager mDiscoveryMgr;
    private final Context mContext;
    @VisibleForTesting
    private InitLDSCode mCode;

    /**
     * Constructor for the ResourceLoader class.
     *
     * @param context The application context.
     * @param au An instance of AssetManager to manage assets.
     * @param mgr An instance of DiscoveryManager to manage discovery operations.
     */
    public ResourceLoader(Context context, AssetManager au, DiscoveryManager mgr) {
        mContext = context;
        mAssetManager = au;
        mDiscoveryMgr = mgr;
    }

    /**
     * Constructor for the ResourceLoader class (used for testing).
     *
     * @param context The application context.
     * @param au An instance of AssetManager to manage assets.
     * @param mgr An instance of DiscoveryManager to manage discovery operations.
     * @param code An instance of InitLDSCode.
     */
    @VisibleForTesting
    ResourceLoader(Context context, AssetManager au, DiscoveryManager mgr, InitLDSCode code) {
        mContext = context;
        mAssetManager = au;
        mDiscoveryMgr = mgr;
        mCode = code;
    }

    /**
     * Initializes LDS and returns an instance of InitLDSCode.
     *
     * @return An instance of InitLDSCode indicating the status of the operation.
     * @throws UStatusException If an error occurs during the operation.
     */
    public InitLDSCode initializeLDS() throws UStatusException {
        InitLDSCode code = InitLDSCode.FAILURE;
        load(LDS_DB_FILENAME);
        code = (null != mCode) ? mCode : InitLDSCode.SUCCESS;
        if (code == InitLDSCode.RECOVERY) {
            Log.w(TAG, join(Key.MESSAGE, "initializing empty LDS database"));
            mDiscoveryMgr.init(LDS_AUTHORITY);
            if (!save(LDS_DB_FILENAME)) {
                code = InitLDSCode.FAILURE;
            }
        }
        if (code == InitLDSCode.FAILURE) {
            Log.e(TAG, join(Key.MESSAGE, "DB initialization failed"));
        } else {
            Log.d(TAG, join(Key.MESSAGE, "DB initialization successful"));
            if (VERBOSE) {
                Log.v(TAG, join(Key.MESSAGE, mDiscoveryMgr.export()));
            }
        }
        return code;
    }

    /**
     * Saves data to a file and returns a boolean value indicating the success of the operation.
     *
     * @param filename The name of the file to save the data to.
     * @return A boolean value indicating the success of the operation.
     */
    private boolean save(String filename) {
        checkStringNotEmpty(filename, "[save] filename empty string");
        final String db = mDiscoveryMgr.export();
        return mAssetManager.writeFileToInternalStorage(mContext, filename, db);
    }

    /**
     * Loads data from a file.
     *
     * @param filename The name of the file to load the data from.
     */
    private void load(String filename) {
        checkStringNotEmpty(filename, "[load] filename empty string");

        final String json = mAssetManager.readFileFromInternalStorage(mContext, filename);
        checkStringNotEmpty(json, "[load] database is empty");

        final boolean bLoadResult = mDiscoveryMgr.load(json);
        checkState(bLoadResult, "[load] failed to load database");

        Log.d(TAG, join(Key.EVENT, "load", Key.STATUS, "successful"));
    }

    /**
     * Enum representing the possible statuses of the LDS initialization operation.
     */
    enum InitLDSCode {
        FAILURE,
        RECOVERY,
        SUCCESS
    }
}
