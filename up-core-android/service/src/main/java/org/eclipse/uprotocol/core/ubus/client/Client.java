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

import static org.eclipse.uprotocol.common.util.log.Formatter.joinGrouped;
import static org.eclipse.uprotocol.common.util.log.Formatter.quote;
import static org.eclipse.uprotocol.common.util.log.Formatter.stringify;
import static org.eclipse.uprotocol.core.internal.util.log.FormatterExt.stringify;

import android.os.IBinder;
import android.os.IBinder.DeathRecipient;
import android.os.RemoteException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.eclipse.uprotocol.common.UStatusException;
import org.eclipse.uprotocol.common.util.log.Key;
import org.eclipse.uprotocol.v1.UCode;
import org.eclipse.uprotocol.v1.UEntity;
import org.eclipse.uprotocol.v1.UMessage;
import org.eclipse.uprotocol.v1.UUri;

import java.util.concurrent.atomic.AtomicBoolean;

public abstract class Client {
    private final Credentials mCredentials;
    private final IBinder mToken;
    private final DeathRecipient mDeathRecipient;
    private final AtomicBoolean mReleased = new AtomicBoolean(false);

    protected Client(@NonNull Credentials credentials, @NonNull IBinder token,
            @Nullable DeathRecipient recipient) {
        mCredentials = credentials;
        mToken = token;
        mDeathRecipient = recipient;
        if (mDeathRecipient != null) {
            try {
                mToken.linkToDeath(mDeathRecipient, 0);
            } catch (RemoteException e) {
                throw new UStatusException(UCode.UNKNOWN, "Client is already dead", e);
            }
        }
    }

    public void release() {
        if (!mReleased.compareAndSet(false, true)) {
            return;
        }
        if (mDeathRecipient != null) {
            mToken.unlinkToDeath(mDeathRecipient, 0);
        }
    }

    public boolean isReleased() {
        return mReleased.get();
    }

    public @NonNull Credentials getCredentials() {
        return mCredentials;
    }

    public @NonNull UUri getUri() {
        return mCredentials.getUri();
    }

    public @NonNull UEntity getEntity() {
        return mCredentials.getEntity();
    }

    public @NonNull IBinder getToken() {
        return mToken;
    }

    public @Nullable DeathRecipient getDeathRecipient() {
        return mDeathRecipient;
    }

    public final boolean isLocal() {
        return !isRemote();
    }

    public boolean isRemote() {
        return false;
    }

    public abstract boolean isInternal();

    public abstract @NonNull Object getListener();

    public abstract void send(@NonNull UMessage message) throws RemoteException;

    public @NonNull String toString() {
        return joinGrouped(Key.PID, mCredentials.getPid(), Key.UID, mCredentials.getUid(),
                Key.PACKAGE, quote(mCredentials.getPackageName()), Key.ENTITY, stringify(mCredentials.getEntity()),
                Key.TOKEN, stringify(mToken));
    }
}
