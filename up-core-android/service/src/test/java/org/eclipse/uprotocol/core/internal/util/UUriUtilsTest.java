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
package org.eclipse.uprotocol.core.internal.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.eclipse.uprotocol.core.TestBase;
import org.eclipse.uprotocol.v1.UCode;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class UUriUtilsTest extends TestBase {

    @Test
    public void testCheckTopicUriValid() {
        assertEquals(RESOURCE_URI, UUriUtils.checkTopicUriValid(RESOURCE_URI));
    }

    @Test
    public void testCheckTopicUriValidNegative() {
        assertThrowsStatusException(UCode.INVALID_ARGUMENT, () -> UUriUtils.checkTopicUriValid(EMPTY_URI));
        assertThrowsStatusException(UCode.INVALID_ARGUMENT, () -> UUriUtils.checkTopicUriValid(CLIENT_URI));
        assertThrowsStatusException(UCode.INVALID_ARGUMENT, () -> UUriUtils.checkTopicUriValid(METHOD_URI));
        assertThrowsStatusException(UCode.INVALID_ARGUMENT, () -> UUriUtils.checkTopicUriValid(RESPONSE_URI));
    }

    @Test
    public void testCheckMethodUriValid() {
        assertEquals(METHOD_URI, UUriUtils.checkMethodUriValid(METHOD_URI));
    }

    @Test
    public void testCheckMethodUriValidNegative() {
        assertThrowsStatusException(UCode.INVALID_ARGUMENT, () -> UUriUtils.checkMethodUriValid(EMPTY_URI));
        assertThrowsStatusException(UCode.INVALID_ARGUMENT, () -> UUriUtils.checkMethodUriValid(CLIENT_URI));
        assertThrowsStatusException(UCode.INVALID_ARGUMENT, () -> UUriUtils.checkMethodUriValid(RESOURCE_URI));
        assertThrowsStatusException(UCode.INVALID_ARGUMENT, () -> UUriUtils.checkMethodUriValid(RESPONSE_URI));
    }

    @Test
    public void testCheckResponseUriValid() {
        assertEquals(RESPONSE_URI, UUriUtils.checkResponseUriValid(RESPONSE_URI));
    }

    @Test
    public void testCheckResponseUriValidNegative() {
        assertThrowsStatusException(UCode.INVALID_ARGUMENT, () -> UUriUtils.checkResponseUriValid(EMPTY_URI));
        assertThrowsStatusException(UCode.INVALID_ARGUMENT, () -> UUriUtils.checkResponseUriValid(CLIENT_URI));
        assertThrowsStatusException(UCode.INVALID_ARGUMENT, () -> UUriUtils.checkResponseUriValid(RESOURCE_URI));
        assertThrowsStatusException(UCode.INVALID_ARGUMENT, () -> UUriUtils.checkResponseUriValid(METHOD_URI));
    }

    @Test
    public void testGetClientUri() {
        assertEquals(CLIENT_URI, UUriUtils.getClientUri(buildResponseUri(CLIENT_URI)));
    }

    @Test
    public void testAddAuthority() {
        assertEquals(REMOTE_SERVER_URI, UUriUtils.addAuthority(SERVER_URI, REMOTE_AUTHORITY));
    }

    @Test
    public void testRemoveAuthority() {
        assertEquals(SERVER_URI, UUriUtils.removeAuthority(REMOTE_SERVER_URI));
    }

    @Test
    public void testIsSameClient() {
        assertTrue(UUriUtils.isSameClient(METHOD_URI, SERVER_URI));
        assertTrue(UUriUtils.isSameClient(REMOTE_METHOD_URI, REMOTE_SERVER_URI));
        assertFalse(UUriUtils.isSameClient(METHOD_URI, SERVER2_URI));
        assertFalse(UUriUtils.isSameClient(REMOTE_METHOD_URI, SERVER_URI));
    }

    @Test
    public void testIsRemoteUri() {
        assertTrue(UUriUtils.isRemoteUri(REMOTE_METHOD_URI));
        assertFalse(UUriUtils.isRemoteUri(LOCAL_METHOD_URI));
    }

    @Test
    public void testIsLocal() {
        assertTrue(UUriUtils.isLocalUri(LOCAL_METHOD_URI));
        assertFalse(UUriUtils.isLocalUri(REMOTE_METHOD_URI));
    }

    @Test
    public void testIsMethodUri() {
        assertTrue(UUriUtils.isMethodUri(METHOD_URI));
        assertFalse(UUriUtils.isMethodUri(RESOURCE_URI));
        assertFalse(UUriUtils.isMethodUri(RESPONSE_URI));
    }

    @Test
    public void testToUriString() {
        assertEquals("/test.srv/1/door.front_left#Door", UUriUtils.toUriString(RESOURCE_URI));
        assertEquals("", UUriUtils.toUriString(null));
    }

    @Test
    public void testToUri() {
        assertEquals(RESOURCE_URI, UUriUtils.toUri("/test.srv/1/door.front_left#Door"));
        assertEquals("", UUriUtils.toUriString(null));
    }
}
