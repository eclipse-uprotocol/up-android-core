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
package org.eclipse.uprotocol.core.internal.util.log;

import android.os.IBinder;

import androidx.annotation.NonNull;

import org.eclipse.uprotocol.common.util.log.Formatter;
import org.eclipse.uprotocol.common.util.log.Key;
import org.eclipse.uprotocol.core.usubscription.v3.Update;

public interface FormatterExt {

    static @NonNull String stringify(Update update) {
        return (update != null) ? Formatter.joinGrouped(
                Key.TOPIC, Formatter.stringify(update.getTopic()),
                Key.SUBSCRIBER, org.eclipse.uprotocol.common.util.log.Formatter.stringify(update.getSubscriber().getUri()),
                Key.STATE, update.getStatus().getState()) : "";
    }

    static @NonNull String stringify(IBinder binder) {
        return (binder != null) ? Integer.toHexString(binder.hashCode()) : "";
    }
}
