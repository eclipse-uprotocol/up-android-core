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
package org.eclipse.uprotocol.core.ubus.client;

import static org.eclipse.uprotocol.common.util.UStatusUtils.checkNotNull;
import static org.eclipse.uprotocol.core.ubus.client.ClientManager.isRemoteClient;

import android.os.IBinder;

import androidx.annotation.NonNull;

import org.eclipse.uprotocol.transport.UListener;
import org.eclipse.uprotocol.v1.UMessage;

public class InternalClient extends Client {
    private final UListener mListener;
    private final boolean mRemote;

    public InternalClient(@NonNull Credentials credentials, @NonNull IBinder token,
            @NonNull UListener listener) {
        super(credentials, token, null);
        mListener = checkNotNull(listener, "Listener is null");
        mRemote = isRemoteClient(credentials.getEntity());
    }

    @Override
    public boolean isRemote() {
        return mRemote;
    }

    @Override
    public boolean isInternal() {
        return true;
    }

    @Override
    public @NonNull UListener getListener() {
        return mListener;
    }

    @Override
    public void send(@NonNull UMessage message) {
        mListener.onReceive(message);
    }
}
