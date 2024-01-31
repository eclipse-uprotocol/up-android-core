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

import static org.eclipse.uprotocol.core.internal.util.log.FormatterExt.stringify;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.os.Binder;
import android.os.IBinder;
import android.os.IBinder.DeathRecipient;
import android.os.RemoteException;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.eclipse.uprotocol.core.TestBase;
import org.eclipse.uprotocol.v1.UCode;
import org.eclipse.uprotocol.v1.UMessage;
import org.eclipse.uprotocol.v1.UUri;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ClientTest extends TestBase {
    private final UUri mClientUri = CLIENT_URI;
    private final Credentials mCredentials = new Credentials(PACKAGE_NAME, 0, 0, mClientUri);
    private final DeathRecipient mDeathRecipient = mock(DeathRecipient.class);
    private final Binder mToken = mock(Binder.class);
    private TestClient mClient;

    public static class TestClient extends Client {
        private final Object mListener = new Object();

        public TestClient(@NonNull Credentials credentials, @NonNull IBinder token, DeathRecipient recipient) {
            super(credentials, token, recipient);
        }

        @Override
        public boolean isInternal() {
            return true;
        }

        @Override
        public @NonNull Object getListener() {
            return mListener;
        }

        @Override
        public void send(@NonNull UMessage message) {}
    }

    @Before
    public void setUp() {
        mClient = spy(new TestClient(mCredentials, mToken, mDeathRecipient));
    }

    @Test
    public void testConstructorDeadClient() {
        doThrow(new RemoteException()).when(mToken).linkToDeath(mDeathRecipient, 0);
        assertThrowsStatusException(UCode.UNKNOWN, () ->
                new TestClient(mCredentials, mToken, mDeathRecipient));
    }

    @Test
    public void testConstructorDeathRecipient() {
        verify(mToken, times(1)).linkToDeath(mDeathRecipient, 0);
    }

    @Test
    public void testConstructorNoDeathRecipient() {
        mClient = new TestClient(mCredentials, mToken, null);
        assertNull(mClient.getDeathRecipient());
    }

    @Test
    public void testRelease() {
        mClient.release();
        assertTrue(mClient.isReleased());
        verify(mToken, times(1)).unlinkToDeath(mDeathRecipient, 0);
    }

    @Test
    public void testReleaseWhenReleased() {
        testRelease();
        testRelease();
    }

    @Test
    public void testReleaseNoDeathRecipient() {
        mClient = new TestClient(mCredentials, mToken, null);
        mClient.release();
        verify(mToken, never()).unlinkToDeath(any(), anyInt());
    }

    @Test
    public void testIsReleased() {
        assertFalse(mClient.isReleased());
        mClient.release();
        assertTrue(mClient.isReleased());
    }

    @Test
    public void testToString() {
        assertEquals("[pid: 0, uid: 0, package: \"org.eclipse.uprotocol.core.test\", entity: test.app/1, token: " +
                stringify(mClient.getToken()) + "]", mClient.toString());
    }

    @Test
    public void testGetCredentials() {
        assertEquals(mCredentials, mClient.getCredentials());
    }

    @Test
    public void testGetUri() {
        assertEquals(mClientUri, mClient.getUri());
    }

    @Test
    public void testEntity() {
        assertEquals(mClientUri.getEntity(), mClient.getEntity());
    }

    @Test
    public void testGetToken() {
        assertEquals(mToken, mClient.getToken());
    }

    @Test
    public void testGetDeathRecipient() {
        assertEquals(mDeathRecipient, mClient.getDeathRecipient());
    }

    @Test
    public void testIsLocal() {
        when(mClient.isRemote()).thenReturn(true);
        assertFalse(mClient.isLocal());
        when(mClient.isRemote()).thenReturn(false);
        assertTrue(mClient.isLocal());
    }

    @Test
    public void testIsRemote() {
        assertFalse(mClient.isRemote());
    }
}
