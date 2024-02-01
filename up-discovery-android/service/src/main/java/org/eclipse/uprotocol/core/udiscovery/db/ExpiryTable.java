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

package org.eclipse.uprotocol.core.udiscovery.db;

import static org.eclipse.uprotocol.core.udiscovery.common.Constants.JSON_HIERARCHY;

import com.google.gson.JsonObject;

import java.util.ArrayList;

public class ExpiryTable extends ArrayList<ExpiryData> {
    public void clear() {
        for (ExpiryData ed : this) {
            if (ed.mFuture != null) {
                ed.mFuture.cancel(false);
            }
        }
        super.clear();
    }

    public ExpiryData remove(String uri) {
        for (var i = 0; i < this.size(); i++) {
            ExpiryData ed = this.get(i);
            if (ed.mUri.equals(uri)) {
                if (ed.mFuture != null) {
                    ed.mFuture.cancel(false);
                }
                return this.remove(i);
            }
        }
        return null;
    }

    public JsonObject export() {
        var ota = new JsonObject();
        for (ExpiryData ed : this) {
            ota.addProperty(ed.mUri, ed.mExpireDateTime);
        }
        var ttl = new JsonObject();
        ttl.add(JSON_HIERARCHY, ota);
        return ttl;
    }
}
