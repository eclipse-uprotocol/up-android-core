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

import static org.eclipse.uprotocol.common.util.log.Formatter.join;
import static org.eclipse.uprotocol.core.internal.util.CommonUtils.emptyIfNull;
import static org.eclipse.uprotocol.core.ubus.UBusManager.ACTION_BIND_UBUS;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import org.eclipse.uprotocol.common.util.log.Formatter;
import org.eclipse.uprotocol.common.util.log.Key;
import org.eclipse.uprotocol.core.ubus.UBusAdapter;

import java.io.FileDescriptor;
import java.io.PrintWriter;

public class UCoreService extends Service {
    private static final String TAG = Formatter.tag("core", "UCoreService");
    private UCore mUCore;
    private UBusAdapter mUBusAdapter;

    public UCoreService() {
        //Nothing to do
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, join(Key.EVENT, "Service create", Key.VERSION, BuildConfig.VERSION_NAME));
        mUCore = newUCore(this);
        mUCore.init();
        mUCore.startup();
    }

    @VisibleForTesting
    @NonNull UCore newUCore(@NonNull Context context) {
        return new UCore.Builder(context).build();
    }

    private UBusAdapter getUBusAdapter() {
        if (mUBusAdapter == null) {
            mUBusAdapter = new UBusAdapter(mUCore.getUBus());
        }
        return mUBusAdapter;
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.i(TAG, join(Key.EVENT, "Service bind", Key.INTENT, intent));
        return ACTION_BIND_UBUS.equals(intent.getAction()) ? getUBusAdapter() : null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, join(Key.EVENT, "Service start command", Key.INTENT, intent));
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, join(Key.EVENT, "Service destroy"));
        mUCore.shutdown();
        super.onDestroy();
    }

    @Override
    protected void dump(FileDescriptor fd, @NonNull PrintWriter writer, String[] args) {
        // adb shell dumpsys activity service org.eclipse.uprotocol.core [OPTION]
        // Options:
        //  -t [<TOPIC>]    Print information related to a topic or all topics.
        //  -s [<SERVICE>]  Print information related to a service or all services,
        //                  where <SERVICE> is an entity like 'core.utwin/1'
        writer.println("*UCore*");
        if (emptyIfNull(args).length == 0) {
            writer.println(String.format("  %s: %s", BuildConfig.APPLICATION_ID, BuildConfig.VERSION_NAME));
            writer.println(String.format("  %s: %s", org.eclipse.uprotocol.client.BuildConfig.LIBRARY_PACKAGE_NAME,
                                                     org.eclipse.uprotocol.client.BuildConfig.VERSION_NAME));
        }
        mUCore.dump(writer, args);
    }
}
