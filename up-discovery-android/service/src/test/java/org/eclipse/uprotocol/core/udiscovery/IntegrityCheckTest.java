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

package org.eclipse.uprotocol.core.udiscovery;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowLog;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

@RunWith(RobolectricTestRunner.class)
public class IntegrityCheckTest extends IntegrityCheckTestBase {
    public final String payload = TEST_PAYLOAD;
    public String expectedHash;
    IntegrityCheck integrity = new IntegrityCheck();

    @Before
    public void setUp() throws NoSuchAlgorithmException {
        ShadowLog.stream = System.out;
        var digest = MessageDigest.getInstance("SHA-256");
        byte[] bArray = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
        expectedHash = Base64.getEncoder().encodeToString(bArray);
    }

    @Test
    public void positive_generate_hash() {
        assertEquals(expectedHash, integrity.generateHash(payload));
    }

    @Test
    public void positive_verify_hash() {
        assertTrue(integrity.verifyHash(payload, expectedHash));
    }

    @Test
    public void negative_verify_hash() {
        StringBuilder bld = new StringBuilder(payload);
        // modify a character to corrupt the payload
        bld.setCharAt(3, 'z');
        final String corruptPayload = bld.toString();
        assertFalse(integrity.verifyHash(corruptPayload, expectedHash));
    }

    @Test
    public void negative_null_input() {
        assertEquals("", integrity.generateHash(null));
    }

    @Test
    public void negative_empty_input() {
        assertEquals("", integrity.generateHash(""));
    }

    @Test
    public void negative_generateHash_NoSuchAlgorithmException() {
        IntegrityCheck integrityCheck = new IntegrityCheck("adfasfsdfsdf");
        assertEquals("", integrityCheck.generateHash(payload));
    }
}
