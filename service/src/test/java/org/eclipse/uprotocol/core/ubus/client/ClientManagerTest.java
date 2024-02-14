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

import static org.eclipse.uprotocol.core.ubus.client.ClientManager.REMOTE_CLIENT_NAME;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Binder;
import android.os.IBinder;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.eclipse.uprotocol.core.TestBase;
import org.eclipse.uprotocol.core.UCore;
import org.eclipse.uprotocol.core.ubus.UBus;
import org.eclipse.uprotocol.core.ubus.client.ClientManager.RegistrationListener;
import org.eclipse.uprotocol.transport.UListener;
import org.eclipse.uprotocol.v1.UCode;
import org.eclipse.uprotocol.v1.UEntity;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowBinder;
import org.robolectric.shadows.ShadowPackageManager;

import java.util.Random;

@RunWith(AndroidJUnit4.class)
public class ClientManagerTest extends TestBase {
    private static final UEntity REMOTE_CLIENT = buildEntity(REMOTE_CLIENT_NAME, 1);
    private static final Random sRandom = new Random();

    private final IBinder mClientToken = spy(Binder.class);
    private final UEntity mEntity = CLIENT;
    private final MockListener mListener = new MockListener();
    private final RegistrationListener mClientRegistrationListener = spy(new RegistrationListener() {});
    private ShadowPackageManager mShadowPackageManager;
    private ClientManager mClientManager;

    @Before
    public void setUp() {
        final Context context = RuntimeEnvironment.getApplication();
        mShadowPackageManager = Shadows.shadowOf(context.getPackageManager());
        mClientManager = Mockito.spy(new ClientManager(context));
        final UCore uCore = newMockUCoreBuilder(context)
                .setUBus(new UBus(context, mClientManager, null))
                .build();
        uCore.init();
    }

    @After
    public void tearDown() {
        mClientManager.shutdown();
        ShadowPackageManager.reset();
    }

    private static int randomId() {
        return sRandom.nextInt() & Integer.MAX_VALUE;
    }

    private void simulateRemoteCall(PackageInfo packageInfo) {
        simulateRemoteCall(randomId(), randomId(), packageInfo);
    }

    private void simulateRemoteCall(int pid, int uid, PackageInfo packageInfo) {
        ShadowBinder.setCallingPid(pid);
        ShadowBinder.setCallingUid(uid);
        injectPackage(packageInfo);
    }

    private void injectPackage(PackageInfo packageInfo) {
        if (packageInfo == null) {
            mShadowPackageManager.setPackagesForCallingUid(PACKAGE_NAME);
        } else {
            mShadowPackageManager.setPackagesForCallingUid(PACKAGE_NAME);
            mShadowPackageManager.installPackage(packageInfo);
        }
    }

    @SuppressWarnings("SameParameterValue")
    private static PackageInfo buildPackageInfoApp(String packageName, UEntity entity) {
        return buildPackageInfo(packageName, new MetaDataBuilder()
                .setEntity(entity)
                .build());
    }

    @SuppressWarnings("SameParameterValue")
    private static PackageInfo buildPackageInfoService(String packageName, UEntity entity) {
        final ComponentName component = new ComponentName(packageName, packageName + ".Class");
        return buildPackageInfo(packageName, buildServiceInfo(component, new MetaDataBuilder()
                .setEntity(entity)
                .build()));
    }

    @Test
    public void testShutdown() {
        mClientManager.registerClient(PACKAGE_NAME, mEntity, mClientToken, mListener);
        assertFalse(mClientManager.getClients().isEmpty());
        mClientManager.shutdown();
        assertTrue(mClientManager.getClients().isEmpty());
    }

    @Test
    public void testRegisterListener() {
        mClientManager.registerListener(mClientRegistrationListener);
        assertTrue(mClientManager.isRegistered(mClientRegistrationListener));
    }

    @Test
    public void testUnregisterListener() {
        mClientManager.registerListener(mClientRegistrationListener);
        mClientManager.unregisterListener(mClientRegistrationListener);
        assertFalse(mClientManager.isRegistered(mClientRegistrationListener));
    }

    @Test
    public void testRegisterClientInternal() {
        testRegisterListener();
        assertStatus(UCode.OK, mClientManager.registerClient(PACKAGE_NAME, mEntity, mClientToken, mock(UListener.class)));
        final Client client = mClientManager.getClient(mClientToken);
        assertNotNull(client);
        verify(mClientRegistrationListener, times(1)).onClientRegistered(client);
    }

    @Test
    public void testRegisterClientRemote() {
        testRegisterListener();
        assertStatus(UCode.OK, mClientManager.registerClient(PACKAGE_NAME, REMOTE_CLIENT, mClientToken, mock(UListener.class)));
        final Client client = mClientManager.getClient(mClientToken);
        assertNotNull(client);
        verify(mClientRegistrationListener, times(1)).onClientRegistered(client);
    }

    @Test
    public void testRegisterClientApp() {
        simulateRemoteCall(buildPackageInfoApp(PACKAGE_NAME, mEntity));
        testRegisterListener();
        assertStatus(UCode.OK, mClientManager.registerClient(PACKAGE_NAME, mEntity, mClientToken, mListener));
        final Client client = mClientManager.getClient(mClientToken);
        assertNotNull(client);
        verify(mClientRegistrationListener, times(1)).onClientRegistered(client);
    }

    @Test
    public void testRegisterClientService() {
        simulateRemoteCall(buildPackageInfoService(PACKAGE_NAME, mEntity));
        testRegisterListener();
        assertStatus(UCode.OK, mClientManager.registerClient(PACKAGE_NAME, mEntity, mClientToken, mListener));
        final Client client = mClientManager.getClient(mClientToken);
        assertNotNull(client);
        verify(mClientRegistrationListener, times(1)).onClientRegistered(client);
    }

    @Test
    public void testRegisterClientSameRegistered() {
        simulateRemoteCall(buildPackageInfoService(PACKAGE_NAME, mEntity));
        testRegisterListener();
        assertStatus(UCode.OK, mClientManager.registerClient(PACKAGE_NAME, mEntity, mClientToken, mListener));
        assertStatus(UCode.OK, mClientManager.registerClient(PACKAGE_NAME, mEntity, mClientToken, mListener));
        final Client client = mClientManager.getClient(mClientToken);
        assertNotNull(client);
        verify(mClientRegistrationListener, times(1)).onClientRegistered(client);
    }

    @Test
    public void testRegisterClientSamePid() {
        simulateRemoteCall(myPid(), randomId(), buildPackageInfoApp(PACKAGE_NAME, mEntity));
        assertStatus(UCode.OK, mClientManager.registerClient(PACKAGE_NAME, mEntity, mClientToken, mListener));
    }

    @Test
    public void testRegisterClientSameUid() {
        simulateRemoteCall(randomId(), myUid(), buildPackageInfoApp(PACKAGE_NAME, mEntity));
        assertStatus(UCode.OK, mClientManager.registerClient(PACKAGE_NAME, mEntity, mClientToken, mListener));
    }

    @Test
    public void testRegisterClientSameDifferentListener() {
        testRegisterClientService();
        final Client client = mClientManager.getClient(mClientToken);
        assertStatus(UCode.INVALID_ARGUMENT,
                mClientManager.registerClient(PACKAGE_NAME, mEntity, mClientToken, new MockListener()));
        assertEquals(client, mClientManager.getClient(mClientToken));
    }

    @Test
    public void testRegisterClientPackageInfoFetchFailed() {
        simulateRemoteCall(null);
        assertStatus(UCode.UNAUTHENTICATED,
                mClientManager.registerClient(PACKAGE_NAME, mEntity, mClientToken, mListener));
        assertNull(mClientManager.getClient(mClientToken));
    }

    @Test
    public void testRegisterClientEntityNameNotDeclared() {
        simulateRemoteCall(buildPackageInfoService(PACKAGE_NAME, buildEntity(mEntity.getName(), 0)));
        assertStatus(UCode.UNAUTHENTICATED,
                mClientManager.registerClient(PACKAGE_NAME, mEntity, mClientToken, mListener));
        assertNull(mClientManager.getClient(mClientToken));
    }

    @Test
    public void testRegisterClientEntityVersionNotDeclared() {
        simulateRemoteCall(buildPackageInfoService(PACKAGE_NAME, buildEntity(null, mEntity.getVersionMajor())));
        assertStatus(UCode.UNAUTHENTICATED,
                mClientManager.registerClient(PACKAGE_NAME, mEntity, mClientToken, mListener));
        assertNull(mClientManager.getClient(mClientToken));
    }

    @Test
    @SuppressWarnings("DataFlowIssue")
    public void testRegisterClientInvalidArguments() {
        assertStatus(UCode.INVALID_ARGUMENT,
                mClientManager.registerClient(null, mEntity, mClientToken, mListener));
        assertStatus(UCode.INVALID_ARGUMENT,
                mClientManager.registerClient("", mEntity, mClientToken, mListener));
        assertStatus(UCode.INVALID_ARGUMENT,
                mClientManager.registerClient(PACKAGE_NAME, null, mClientToken, mListener));
        assertStatus(UCode.INVALID_ARGUMENT,
                mClientManager.registerClient(PACKAGE_NAME,
                        buildEntity(null, mEntity.getVersionMajor()), mClientToken, mListener));
        assertStatus(UCode.INVALID_ARGUMENT,
                mClientManager.registerClient(PACKAGE_NAME,
                        buildEntity(mEntity.getName(), 0), mClientToken, mListener));
        assertStatus(UCode.INVALID_ARGUMENT,
                mClientManager.registerClient(PACKAGE_NAME, mEntity, null, mListener));
        assertStatus(UCode.INVALID_ARGUMENT,
                mClientManager.registerClient(PACKAGE_NAME, mEntity, mClientToken, null));
        assertStatus(UCode.UNIMPLEMENTED,
                mClientManager.registerClient(PACKAGE_NAME, mEntity, mClientToken, new Object()));
    }

    @Test
    public void testUnregisterClientInternal() {
        testRegisterClientInternal();
        final Client client = mClientManager.getClient(mClientToken);
        assertNotNull(client);
        assertStatus(UCode.OK, mClientManager.unregisterClient(mClientToken));
        verify(mClientRegistrationListener, times(1)).onClientUnregistered(client);
    }

    @Test
    public void testUnregisterClientRemote() {
        testRegisterClientRemote();
        final Client client = mClientManager.getClient(mClientToken);
        assertNotNull(client);
        assertStatus(UCode.OK, mClientManager.unregisterClient(mClientToken));
        verify(mClientRegistrationListener, times(1)).onClientUnregistered(client);
    }

    @Test
    public void testUnregisterClientNotRegistered() {
        testRegisterListener();
        assertStatus(UCode.OK, mClientManager.unregisterClient(mClientToken));
        verify(mClientRegistrationListener, times(0)).onClientUnregistered(any());
    }

    @Test
    public void testUnregisterClientSamePid() {
        testRegisterClientSamePid();
        assertStatus(UCode.OK, mClientManager.unregisterClient(mClientToken));
    }

    @Test
    public void testUnregisterClientSameUid() {
        testRegisterClientSameUid();
        assertStatus(UCode.OK, mClientManager.unregisterClient(mClientToken));
    }

    @Test
    public void testUnregisterClientDifferentPid() {
        testRegisterClientApp();
        ShadowBinder.setCallingPid(randomId());
        assertStatus(UCode.UNAUTHENTICATED, mClientManager.unregisterClient(mClientToken));
    }

    @Test
    public void testUnregisterClientDifferentUid() {
        testRegisterClientApp();
        ShadowBinder.setCallingUid(randomId());
        assertStatus(UCode.UNAUTHENTICATED, mClientManager.unregisterClient(mClientToken));
    }

    @Test
    public void testUnregisterClientUnauthenticated() {
        testRegisterClientApp();
        simulateRemoteCall(buildPackageInfoService(PACKAGE_NAME, mEntity));
        final Client client = mClientManager.getClient(mClientToken);
        assertNotNull(client);
        assertStatus(UCode.UNAUTHENTICATED, mClientManager.unregisterClient(mClientToken));
    }

    @Test
    @SuppressWarnings("DataFlowIssue")
    public void testUnregisterClientNegative() {
        assertStatus(UCode.UNAUTHENTICATED, mClientManager.unregisterClient(null));
    }

    @Test
    @SuppressWarnings("DataFlowIssue")
    public void testUnregisterClientDied() {
        testRegisterClientApp();
        final Client client = mClientManager.getClient(mClientToken);
        assertNotNull(client);
        client.getDeathRecipient().binderDied();
        assertNull(mClientManager.getClient(mClientToken));
        verify(mClientRegistrationListener, times(1)).onClientUnregistered(client);
    }

    @Test
    public void testGetClients() {
        mClientManager.registerClient(PACKAGE_NAME, mEntity, mClientToken, mListener);
        assertFalse(mClientManager.getClients().isEmpty());
    }

    @Test
    public void testGetClient() {
        mClientManager.registerClient(PACKAGE_NAME, mEntity, mClientToken, mListener);
        final Client client = mClientManager.getClient(mClientToken);
        assertNotNull(client);
        assertEquals(mEntity, client.getEntity());
    }

    @Test
    public void testGetClientNotRegistered() {
        assertNull(mClientManager.getClient(mClientToken));
    }

    @Test
    @SuppressWarnings("DataFlowIssue")
    public void testGetClientNegative() {
        assertThrowsStatusException(UCode.UNAUTHENTICATED, () -> mClientManager.getClient(null));
    }

    @Test
    public void testGetClientOrThrow() {
        mClientManager.registerClient(PACKAGE_NAME, mEntity, mClientToken, mListener);
        final Client client = mClientManager.getClientOrThrow(mClientToken);
        assertNotNull(client);
        assertEquals(mEntity, client.getEntity());
    }

    @Test
    public void testGetClientOrThrowNotRegistered() {
        assertThrowsStatusException(UCode.UNAUTHENTICATED, () -> mClientManager.getClientOrThrow(mClientToken));
    }

    @Test
    @SuppressWarnings("DataFlowIssue")
    public void testGetClientOrThrowNegative() {
        assertThrowsStatusException(UCode.UNAUTHENTICATED, () -> mClientManager.getClientOrThrow(null));
    }

    @Test
    public void testGetRemoteClient() {
        testRegisterClientRemote();
        final Client client = mClientManager.getRemoteClient();
        assertNotNull(client);
        assertEquals(REMOTE_CLIENT, client.getEntity());
    }

    @Test
    public void testIsRemoteClient() {
        assertTrue(ClientManager.isRemoteClient(REMOTE_CLIENT));
        assertFalse(ClientManager.isRemoteClient(CLIENT));
    }

    @Test
    public void testDefaultListener() {
        mClientManager.registerClient(PACKAGE_NAME, mEntity, mClientToken, mListener);
        final Client client = mClientManager.getClient(mClientToken);
        final RegistrationListener listener = new RegistrationListener() {
            @Override
            public void onClientRegistered(@NonNull Client client) {
                RegistrationListener.super.onClientRegistered(client);
                assertNotNull(client);
            }

            @Override
            public void onClientUnregistered(@NonNull Client client) {
                RegistrationListener.super.onClientUnregistered(client);
                assertNotNull(client);
            }
        };
        listener.onClientRegistered(client);
        listener.onClientUnregistered(client);
    }
}
