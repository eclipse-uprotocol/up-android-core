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

import static android.os.Process.myPid;
import static android.os.Process.myUid;

import static org.junit.Assert.assertEquals;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.eclipse.uprotocol.core.TestBase;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class CredentialsTest extends TestBase {
    private final Credentials mCredentials = new Credentials(PACKAGE_NAME, myPid(), myUid(), CLIENT_URI);

    @Test
    public void testGetPackageName() {
        assertEquals(PACKAGE_NAME, mCredentials.getPackageName());
    }

    @Test
    public void testGetPid() {
        assertEquals(myPid(), mCredentials.getPid());
    }

    @Test
    public void testGetUid() {
        assertEquals(myUid(), mCredentials.getUid());
    }

    @Test
    public void testGetUri() {
        assertEquals(CLIENT_URI, mCredentials.getUri());
    }

    @Test
    public void testGetEntity() {
        assertEquals(CLIENT, mCredentials.getEntity());
    }
}
