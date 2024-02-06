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

package org.eclipse.uprotocol.core.udiscovery.common;

import static org.eclipse.uprotocol.core.udiscovery.v3.UDiscovery.SERVICE;

import org.eclipse.uprotocol.v1.UAuthority;
import org.eclipse.uprotocol.v1.UResource;
import org.eclipse.uprotocol.v1.UUri;


public class Constants {
    /******************************************************************************
     * This code general constant declaration class.
     *****************************************************************************/

    public static final String JSON_HIERARCHY = "hierarchy";
    public static final String JSON_AUTHORITY = "authority";
    public static final String JSON_TTL = "ttl";
    public static final String JSON_DATA = "data";
    public static final String JSON_HASH = "hash";
    public static final String LDS_DB_FILENAME = "lds.json";
    public static final String LDS_DEVICE_NAME = "device_name";
    public static final String LDS_DOMAIN_NAME = "domain_name";
    public static final UAuthority LDS_AUTHORITY = UAuthority.newBuilder().setName(
            String.join(".", LDS_DEVICE_NAME, LDS_DOMAIN_NAME)).build();

    public static final String UNEXPECTED_PAYLOAD = "Unexpected payload";

    public static final UUri TOPIC_NODE_NOTIFICATION = UUri.newBuilder().setEntity(SERVICE)
                .setResource(UResource.newBuilder().setName("nodes").setMessage("Notification").build()).build();

    private Constants() {
    }
}
