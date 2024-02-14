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

import androidx.annotation.NonNull;

import org.eclipse.uprotocol.v1.UEntity;
import org.eclipse.uprotocol.v1.UUri;

public final class Credentials {
    private final String mPackageName;
    private final int mPid;
    private final int mUid;
    private final UUri mUri;

    public Credentials(@NonNull String packageName, int pid, int uid, @NonNull UUri uri) {
        mPackageName = packageName;
        mPid = pid;
        mUid = uid;
        mUri = uri;
    }

    public @NonNull String getPackageName() {
        return mPackageName;
    }

    public int getPid() {
        return mPid;
    }

    public int getUid() {
        return mUid;
    }

    public @NonNull UUri getUri() {
        return mUri;
    }

    public @NonNull UEntity getEntity() {
        return mUri.getEntity();
    }
}
