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

import static java.util.Optional.ofNullable;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import org.eclipse.uprotocol.core.ubus.UBus;
import org.eclipse.uprotocol.core.usubscription.USubscription;
import org.eclipse.uprotocol.core.utwin.UTwin;

import java.io.PrintWriter;
import java.util.List;
import java.util.ListIterator;

public class UCore {
    private final Context mContext;
    private final UBus mUBus;
    private final UTwin mUTwin;
    private final USubscription mUSubscription;
    private final List<Component> mComponents;

    public abstract static class Component {
        protected void init(@NonNull UCore uCore) {}
        protected void startup() {}
        protected void shutdown() {}
        protected void clearCache() {}
        protected void dump(@NonNull PrintWriter writer, String[] args) {}
    }

    private UCore(@NonNull Builder builder) {
        mContext = builder.mContext;

        mUBus = ofNullable(builder.mUBus).orElseGet(() -> new UBus(mContext));
        mUTwin = ofNullable(builder.mUTwin).orElseGet(() -> new UTwin(mContext));
        mUSubscription = ofNullable(builder.mUSubscription).orElseGet(() -> new USubscription(mContext));

        mComponents = List.of(
                mUBus,
                mUTwin,
                mUSubscription);
    }

    public void init() {
        mComponents.forEach(it -> it.init(this));
    }

    public void startup() {
        mComponents.forEach(Component::startup);
    }

    public void shutdown() {
        final ListIterator<Component> iterator = mComponents.listIterator(mComponents.size());
        while (iterator.hasPrevious()) {
            iterator.previous().shutdown();
        }
    }

    public void clearCache() {
        mComponents.forEach(Component::clearCache);
    }

    public void dump(@NonNull PrintWriter writer, String[] args) {
        mComponents.forEach(component -> component.dump(writer, args));
    }

    public Context getContext() {
        return mContext;
    }


    public UBus getUBus() {
        return mUBus;
    }

    public UTwin getUTwin() {
        return mUTwin;
    }

    public USubscription getUSubscription() {
        return mUSubscription;
    }

    @VisibleForTesting
    @NonNull List<Component> getComponents() {
        return mComponents;
    }

    public static class Builder {
        private final Context mContext;
        private UBus mUBus;
        private UTwin mUTwin;
        private USubscription mUSubscription;

        public Builder(@NonNull Context context) {
            mContext = context;
        }

        public @NonNull Builder setUBus(UBus uBus) {
            mUBus = uBus;
            return this;
        }

        public @NonNull Builder setUTwin(UTwin uTwin) {
            mUTwin = uTwin;
            return this;
        }

        public @NonNull Builder setUSubscription(USubscription uSubscription) {
            mUSubscription = uSubscription;
            return this;
        }

        public @NonNull UCore build() {
            return new UCore(this);
        }
    }
}
