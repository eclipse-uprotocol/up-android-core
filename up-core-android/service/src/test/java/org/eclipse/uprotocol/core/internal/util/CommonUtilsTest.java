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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.eclipse.uprotocol.core.TestBase;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.Set;

@RunWith(AndroidJUnit4.class)
public class CommonUtilsTest extends TestBase {

    @Test
    public void testEmptyIfNullList() {
        final List<String> list = List.of("test");
        assertEquals(list, CommonUtils.emptyIfNull(list));
        assertEquals(emptyList(), CommonUtils.emptyIfNull((List<String>) null));
    }

    @Test
    public void testEmptyIfNullSet() {
        final Set<String> set = Set.of("test");
        assertEquals(set, CommonUtils.emptyIfNull(set));
        assertEquals(emptySet(), CommonUtils.emptyIfNull((Set<String>) null));
    }

    @Test
    public void testEmptyIfNullString() {
        final String string = "test";
        assertEquals(string, CommonUtils.emptyIfNull(string));
        assertEquals("", CommonUtils.emptyIfNull((String) null));
    }

    @Test
    public void testEmptyIfNullStringArray() {
        final String[] array = new String[] {"test"};
        assertArrayEquals(array, CommonUtils.emptyIfNull(array));
        assertArrayEquals(CommonUtils.EMPTY_STRING_ARRAY, CommonUtils.emptyIfNull((String[]) null));
    }
}
