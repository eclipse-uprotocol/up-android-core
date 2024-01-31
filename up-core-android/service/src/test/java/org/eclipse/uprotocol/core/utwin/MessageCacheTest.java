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
package org.eclipse.uprotocol.core.utwin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.eclipse.uprotocol.core.TestBase;
import org.eclipse.uprotocol.v1.UMessage;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Set;
import java.util.function.Consumer;

@RunWith(AndroidJUnit4.class)
public class MessageCacheTest extends TestBase {
    private MessageCache mMessageCache;

    private static class TestCallback implements Consumer<UMessage> {
        @Override
        public void accept(final UMessage message) {}
    }

    @Before
    public void setUp() {
        setLogLevel(Log.VERBOSE);
        mMessageCache = new MessageCache();
    }

    private static void setLogLevel(int level) {
        UTwin.VERBOSE = (level <= Log.VERBOSE);
    }

    @Test
    public void testAddMessage() {
        final UMessage message = buildPublishMessage(RESOURCE_URI);
        assertTrue(mMessageCache.addMessage(message));
        assertEquals(message, mMessageCache.getMessage(RESOURCE_URI));
    }

    @Test
    public void testAddMessageSame() {
        final UMessage message = buildPublishMessage(RESOURCE_URI);
        assertTrue(mMessageCache.addMessage(message));
        assertFalse(mMessageCache.addMessage(message));
        assertEquals(message, mMessageCache.getMessage(RESOURCE_URI));
    }

    @Test
    public void testAddMessageReplace() {
       testAddMessage();
       testAddMessage();
    }

    @Test
    public void testAddMessageWithCallback() {
        final UMessage message = buildPublishMessage(RESOURCE_URI);
        final Consumer<UMessage> callback = mock(TestCallback.class);
        assertTrue(mMessageCache.addMessage(message, callback));
        assertEquals(message, mMessageCache.getMessage(RESOURCE_URI));
        verify(callback, times(1)).accept(message);
    }

    @Test
    public void testAddMessageWithCallbackSame() {
        final UMessage message = buildPublishMessage(RESOURCE_URI);
        final Consumer<UMessage> callback = mock(TestCallback.class);
        assertTrue(mMessageCache.addMessage(message, callback));
        assertFalse(mMessageCache.addMessage(message, callback));
        assertEquals(message, mMessageCache.getMessage(RESOURCE_URI));
        verify(callback, times(1)).accept(message);
    }

    @Test
    public void testAddMessageWithCallbackReplace() {
        testAddMessageWithCallback();
        testAddMessageWithCallback();
    }

    @Test
    public void testRemoveMessage() {
        assertFalse(mMessageCache.removeMessage(RESOURCE_URI));
        assertNull(mMessageCache.getMessage(RESOURCE_URI));
    }

    @Test
    public void testRemoveMessageNotEmpty() {
        testAddMessage();
        assertTrue(mMessageCache.removeMessage(RESOURCE_URI));
        assertNull(mMessageCache.getMessage(RESOURCE_URI));
    }

    @Test
    public void testRemoveMessageAlreadyRemoved() {
        testRemoveMessageNotEmpty();
        assertFalse(mMessageCache.removeMessage(RESOURCE_URI));
        assertNull(mMessageCache.getMessage(RESOURCE_URI));
    }

    @Test
    public void testGetMessageExpired() {
        assertTrue(mMessageCache.addMessage(buildPublishMessage(RESOURCE_URI, 1)));
        sleep(DELAY_MS);
        assertNull(mMessageCache.getMessage(RESOURCE_URI));

        setLogLevel(Log.INFO);
        assertTrue(mMessageCache.addMessage(buildPublishMessage(RESOURCE_URI, 1)));
        sleep(DELAY_MS);
        assertNull(mMessageCache.getMessage(RESOURCE_URI));
    }

    @Test
    public void testGetTopics() {
        assertTrue(mMessageCache.getTopics().isEmpty());

        assertTrue(mMessageCache.addMessage(buildPublishMessage(RESOURCE_URI)));
        assertEquals(Set.of(RESOURCE_URI), mMessageCache.getTopics());

        assertTrue(mMessageCache.addMessage(buildPublishMessage(RESOURCE2_URI)));
        assertEquals(Set.of(RESOURCE_URI, RESOURCE2_URI), mMessageCache.getTopics());
    }

    @Test
    public void testSize() {
        setLogLevel(Log.INFO);
        assertEquals(0, mMessageCache.size());

        assertTrue(mMessageCache.addMessage(buildPublishMessage(RESOURCE_URI)));
        assertEquals(1, mMessageCache.size());

        assertTrue(mMessageCache.addMessage(buildPublishMessage(RESOURCE2_URI)));
        assertEquals(2, mMessageCache.size());

        assertTrue(mMessageCache.removeMessage(RESOURCE2_URI));
        assertEquals(1, mMessageCache.size());

        assertTrue(mMessageCache.removeMessage(RESOURCE_URI));
        assertEquals(0, mMessageCache.size());
    }

    @Test
    public void testIsEmpty() {
        assertTrue(mMessageCache.isEmpty());
        testAddMessage();
        assertFalse(mMessageCache.isEmpty());
    }

    @Test
    public void testClear() {
        mMessageCache.clear();
        assertTrue(mMessageCache.isEmpty());

        testAddMessage();
        mMessageCache.clear();
        assertTrue(mMessageCache.isEmpty());
    }
}
