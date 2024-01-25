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

public class ResourceLoader {
    private static final String TAG = tag(SERVICE.getName());

    protected static boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);
    protected static boolean VERBOSE = Log.isLoggable(TAG, Log.VERBOSE);
    public final AssetManager mAssetManager;
    public final DiscoveryManager mDiscoveryMgr;
    private final Context mContext;
    @VisibleForTesting
    private InitLDSCode mCode;

    public ResourceLoader(Context context, AssetManager au, DiscoveryManager mgr) {
        mContext = context;
        mAssetManager = au;
        mDiscoveryMgr = mgr;
        mCode = null;
    }

    @VisibleForTesting
    ResourceLoader(Context context, AssetManager au, DiscoveryManager mgr, InitLDSCode code) {
        mContext = context;
        mAssetManager = au;
        mDiscoveryMgr = mgr;
        mCode = code;
    }

    public InitLDSCode initializeLDS() throws UStatusException {
        InitLDSCode code = (null != mCode) ? mCode : InitLDSCode.FAILURE;
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

    private boolean save(String filename) {
        checkStringNotEmpty(filename, "[save] filename empty string");
        final String db = mDiscoveryMgr.export();
        return mAssetManager.writeFileToInternalStorage(mContext, filename, db);
    }

    private void load(String filename) {
        checkStringNotEmpty(filename, "[load] filename empty string");

        final String json = mAssetManager.readFileFromInternalStorage(mContext, filename);
        checkStringNotEmpty(json, "[load] database is empty");

        final boolean bLoadResult = mDiscoveryMgr.load(json);
        checkState(bLoadResult, "[load] failed to load database");

        Log.d(TAG, join(Key.EVENT, "load", Key.STATUS, "successful"));
    }

    enum InitLDSCode {
        FAILURE,
        RECOVERY,
        SUCCESS
    }
}
