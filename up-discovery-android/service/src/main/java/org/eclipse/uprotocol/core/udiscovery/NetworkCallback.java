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

import static org.eclipse.uprotocol.common.util.log.Formatter.join;
import static org.eclipse.uprotocol.common.util.log.Formatter.tag;
import static org.eclipse.uprotocol.core.udiscovery.UDiscoveryService.VERBOSE;
import static org.eclipse.uprotocol.core.udiscovery.v3.UDiscovery.SERVICE;

import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.util.Log;

import androidx.annotation.NonNull;

import org.eclipse.uprotocol.common.util.log.Key;
import org.eclipse.uprotocol.core.udiscovery.interfaces.NetworkStatusInterface;

public class NetworkCallback extends ConnectivityManager.NetworkCallback {
    private static final String TAG = tag(SERVICE.getName());
    private static final String NETWORK = "network";
    private static final String NETWORKCAPABILITIES = "networkcapabilities";
    private final NetworkStatusInterface mNwStatusInterface;

    NetworkCallback(NetworkStatusInterface nwStatusInterface) {
        mNwStatusInterface = nwStatusInterface;
    }

    @Override
    public void onAvailable(@NonNull Network network) {
        Log.i(TAG, join(Key.EVENT, "Network available", NETWORK, network));
        mNwStatusInterface.setNetworkStatus(true);
    }

    @Override
    public void onLost(@NonNull Network network) {
        Log.i(TAG, join(Key.EVENT, "Network lost", NETWORK, network));
        mNwStatusInterface.setNetworkStatus(false);
    }

    @Override
    public void onCapabilitiesChanged(@NonNull Network network, @NonNull NetworkCapabilities networkCapabilities) {
        if (VERBOSE) {
            Log.v(TAG, join(Key.CONNECTION, "Network capabilities changed", Key.STATE, network, NETWORKCAPABILITIES, networkCapabilities));
        }
    }
}
