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

public class IntegrityCheckTestBase {
    public final static String TEST_PAYLOAD = "{\n"
            + "               \"uri\": \"body.access\",\n"
            + "               \"nodes\": [\n"
            + "                 {\n"
            + "                   \"uri\": \"body.access/1\",\n"
            + "                   \"nodes\": [\n"
            + "                     {\n"
            + "                       \"uri\": \"body.access/1/door.front_left\",\n"
            + "                       \"properties\": {\n"
            + "                         \"ent_prop1\": {\n"
            + "                           \"uInteger\": 1\n"
            + "                         },\n"
            + "                         \"ent_prop2\": {\n"
            + "                           \"uInteger\": 2\n"
            + "                         }\n"
            + "                       },\n"
            + "                       \"type\": \"MESSAGE\"\n"
            + "                     },\n"
            + "                     {\n"
            + "                       \"uri\": \"body.access/1/door.rear_left\",\n"
            + "                       \"properties\": {\n"
            + "                         \"topic_prop1\": {\n"
            + "                           \"uInteger\": 1\n"
            + "                         },\n"
            + "                         \"topic_prop2\": {\n"
            + "                           \"uInteger\": 2\n"
            + "                         }\n"
            + "                       },\n"
            + "                       \"type\": \"TOPIC\"\n"
            + "                     }\n"
            + "                   ],\n"
            + "                   \"type\": \"VERSION\"\n"
            + "                 }\n"
            + "               ],\n"
            + "               \"properties\": {\n"
            + "                 \"ent_prop1\": {\n"
            + "                   \"uInteger\": 1\n"
            + "                 },\n"
            + "                 \"ent_prop2\": {\n"
            + "                   \"uInteger\": 2\n"
            + "                 }\n"
            + "               },\n"
            + "               \"type\": \"ENTITY\"\n"
            + "             }";
}
