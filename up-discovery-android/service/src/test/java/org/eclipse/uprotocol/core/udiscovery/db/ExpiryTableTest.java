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


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.eclipse.uprotocol.core.udiscovery.TestBase;
import org.eclipse.uprotocol.uri.serializer.LongUriSerializer;
import org.eclipse.uprotocol.v1.UUri;
import org.junit.Before;
import org.junit.Test;

import java.time.Instant;

public class ExpiryTableTest extends TestBase {
    public ExpiryTable tbl;

    private String mUUri;
    private String muUri;

    @Before
    public void setUp() {
        tbl = new ExpiryTable();
        UUri uUri = UUri.newBuilder().setAuthority(TEST_AUTHORITY).setEntity(TEST_ENTITY).build();
        UUri uuri = UUri.newBuilder().setAuthority(TEST_AUTHORITY2).setEntity(TEST_ENTITY).build();
        mUUri = LongUriSerializer.instance().serialize(uUri);
        muUri = LongUriSerializer.instance().serialize(uuri);
    }

    public ExpiryData buildExpiryData(String uri) {
        return new ExpiryData(uri, Instant.now().toString(), null);
    }

    @Test
    public void positive_clear() {
        ExpiryData ed = buildExpiryData(mUUri);
        assertTrue(tbl.add(ed));
        tbl.clear();
        assertNull(tbl.remove(mUUri));
    }

    @Test
    public void positive_remove() {
        ExpiryData ed = buildExpiryData(mUUri);
        assertTrue(tbl.add(ed));
        assertEquals(ed, tbl.remove(mUUri));
    }

    @Test
    public void negative_remove_mismatch_src() {
        ExpiryData ed = buildExpiryData(mUUri);
        assertTrue(tbl.add(ed));
        ExpiryData result = tbl.remove(muUri);
        assertNull(result);
    }

    @Test
    public void negative_remove_mismatch_uri() {
        ExpiryData ed = buildExpiryData(mUUri);
        assertTrue(tbl.add(ed));
        ExpiryData result = tbl.remove(muUri);
        assertNull(result);
    }

    @Test
    public void negative_remove_mismatch_src_and_uri() {
        ExpiryData ed = buildExpiryData(mUUri);
        assertTrue(tbl.add(ed));
        ExpiryData result = tbl.remove(muUri);
        assertNull(result);
    }

    @Test
    public void positive_export() {
        ExpiryData ed = buildExpiryData(mUUri);
        assertTrue(tbl.add(ed));
        tbl.export();
    }
}
