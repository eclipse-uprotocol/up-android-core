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
package org.eclipse.uprotocol.core.ubus;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import android.os.Binder;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.eclipse.uprotocol.core.TestBase;
import org.eclipse.uprotocol.core.ubus.client.Client;
import org.eclipse.uprotocol.core.ubus.client.Credentials;
import org.eclipse.uprotocol.core.ubus.client.InternalClient;
import org.eclipse.uprotocol.transport.UListener;
import org.eclipse.uprotocol.v1.UUri;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@RunWith(AndroidJUnit4.class)
public class LinkedClientsTest extends TestBase {
    private LinkedClients mLinkedClients;

    @Before
    public void setUp() {
        mLinkedClients = new LinkedClients();
    }

    private void clear() {
        mLinkedClients.clear();
        assertTrue(mLinkedClients.isEmpty());
    }

    private static Client newClient(UUri clientUri) {
        final Credentials credentials = new Credentials(PACKAGE_NAME, 0, 0, clientUri);
        return new InternalClient(credentials, new Binder(), mock(UListener.class));
    }

    @Test
    public void testLinkToDispatch() {
        final Client client = newClient(CLIENT_URI);
        mLinkedClients.linkToDispatch(RESOURCE_URI, client);
        assertTrue(mLinkedClients.getClients(RESOURCE_URI).contains(client));
    }

    @Test
    public void testLinkToDispatchAlreadyLinked() {
        final Client client = newClient(CLIENT_URI);
        mLinkedClients.linkToDispatch(RESOURCE_URI, client);
        mLinkedClients.linkToDispatch(RESOURCE_URI, client);
        assertTrue(mLinkedClients.getClients(RESOURCE_URI).contains(client));
    }

    @Test
    public void testLinkToDispatchReleasedClient() {
        final Client client = newClient(CLIENT_URI);
        client.release();
        mLinkedClients.linkToDispatch(RESOURCE_URI, client);
        assertFalse(mLinkedClients.getClients(RESOURCE_URI).contains(client));
    }

    @Test
    public void testLinkToDispatchSameTopicDifferentClients() {
        final Client client1 = newClient(CLIENT_URI);
        final Client client2 = newClient(CLIENT2_URI);
        mLinkedClients.linkToDispatch(RESOURCE_URI, client1);
        assertEquals(Set.of(client1), mLinkedClients.getClients(RESOURCE_URI));
        mLinkedClients.linkToDispatch(RESOURCE_URI, client2);
        assertEquals(Set.of(client1, client2), mLinkedClients.getClients(RESOURCE_URI));
    }

    @Test
    public void testUnlinkFromDispatch() {
        final Client client = newClient(CLIENT_URI);
        mLinkedClients.linkToDispatch(RESOURCE_URI, client);
        assertTrue(mLinkedClients.getClients(RESOURCE_URI).contains(client));

        mLinkedClients.unlinkFromDispatch(RESOURCE_URI, client);
        assertFalse(mLinkedClients.getClients(RESOURCE_URI).contains(client));
    }

    @Test
    public void testUnlinkFromDispatchNotLinked() {
        final Client client = newClient(CLIENT_URI);
        mLinkedClients.linkToDispatch(RESOURCE_URI, client);
        assertTrue(mLinkedClients.getClients(RESOURCE_URI).contains(client));

        mLinkedClients.unlinkFromDispatch(RESOURCE2_URI, client);
        assertTrue(mLinkedClients.getClients(RESOURCE_URI).contains(client));
    }

    @Test
    public void testUnlinkFromDispatchSameTopicDifferentClients() {
        final Client client1 = newClient(CLIENT_URI);
        final Client client2 = newClient(CLIENT2_URI);
        mLinkedClients.linkToDispatch(RESOURCE_URI, client1);
        mLinkedClients.linkToDispatch(RESOURCE_URI, client2);
        assertTrue(mLinkedClients.getClients(RESOURCE_URI).containsAll(Set.of(client1, client2)));

        mLinkedClients.unlinkFromDispatch(RESOURCE_URI, client1);
        assertEquals(Set.of(client2), mLinkedClients.getClients(RESOURCE_URI));
        mLinkedClients.unlinkFromDispatch(RESOURCE_URI, client2);
        assertTrue(mLinkedClients.getClients(RESOURCE_URI).isEmpty());
    }

    @Test
    public void testUnlinkFromDispatchForAllTopics() {
        final Client client = newClient(CLIENT_URI);
        mLinkedClients.linkToDispatch(RESOURCE_URI, client);
        mLinkedClients.linkToDispatch(RESOURCE2_URI, client);
        assertTrue(mLinkedClients.getClients(RESOURCE_URI).contains(client));
        assertTrue(mLinkedClients.getClients(RESOURCE2_URI).contains(client));

        mLinkedClients.unlinkFromDispatch(client);
        assertFalse(mLinkedClients.getClients(RESOURCE_URI).contains(client));
        assertFalse(mLinkedClients.getClients(RESOURCE2_URI).contains(client));
    }

    @Test
    public void testUnlinkFromDispatchForAllTopicsDifferentClients() {
        final Client client1 = newClient(CLIENT_URI);
        final Client client2 = newClient(CLIENT2_URI);
        mLinkedClients.linkToDispatch(RESOURCE_URI, client1);
        mLinkedClients.linkToDispatch(RESOURCE_URI, client2);
        assertTrue(mLinkedClients.getClients(RESOURCE_URI).containsAll(Set.of(client1, client2)));

        mLinkedClients.unlinkFromDispatch(client1);
        assertFalse(mLinkedClients.getClients(RESOURCE_URI).contains(client1));
        mLinkedClients.unlinkFromDispatch(client2);
        assertFalse(mLinkedClients.getClients(RESOURCE_URI).contains(client1));
    }

    @Test
    public void testGetClients() {
        final Client client1 = newClient(CLIENT_URI);
        final Client client2 = newClient(CLIENT2_URI);
        mLinkedClients.linkToDispatch(RESOURCE_URI, client1);
        mLinkedClients.linkToDispatch(RESOURCE_URI, client2);
        mLinkedClients.linkToDispatch(RESOURCE2_URI, client2);

        Set<Client> clients = mLinkedClients.getClients(RESOURCE_URI);
        assertTrue(clients.contains(client1));
        assertTrue(clients.contains(client2));
        clients = mLinkedClients.getClients(RESOURCE2_URI);
        assertFalse(clients.contains(client1));
        assertTrue(clients.contains(client2));
    }

    @Test
    public void testGetClientsFiltered() {
        final Client client1 = newClient(CLIENT_URI);
        final Client client2 = newClient(CLIENT_URI);
        final Client client3 = newClient(CLIENT2_URI);
        mLinkedClients.linkToDispatch(RESOURCE_URI, client1);
        mLinkedClients.linkToDispatch(RESOURCE_URI, client2);
        mLinkedClients.linkToDispatch(RESOURCE_URI, client3);

        final Set<Client> clients = new HashSet<>();
        assertEquals(clients, mLinkedClients.getClients(RESOURCE_URI, CLIENT_URI, Collectors.toCollection(() -> clients)));
        assertTrue(clients.contains(client1));
        assertTrue(clients.contains(client2));
        assertFalse(clients.contains(client3));
    }

    @Test
    public void testGetTopics() {
        final Client client = newClient(CLIENT_URI);
        mLinkedClients.linkToDispatch(RESOURCE_URI, client);
        assertTrue(mLinkedClients.getTopics().contains(RESOURCE_URI));
        assertFalse(mLinkedClients.getTopics().contains(RESOURCE2_URI));
    }

    @Test
    public void testIsEmpty() {
        assertTrue(mLinkedClients.isEmpty());
        testLinkToDispatch();
        assertFalse(mLinkedClients.isEmpty());
    }

    @Test
    public void testClear() {
        final Client client = newClient(CLIENT_URI);
        clear();

        mLinkedClients.linkToDispatch(RESOURCE_URI, client);
        mLinkedClients.linkToDispatch(RESOURCE2_URI, client);
        assertFalse(mLinkedClients.isEmpty());
        clear();

        mLinkedClients.linkToDispatch(RESOURCE_URI, client);
        assertFalse(mLinkedClients.isEmpty());
        clear();
    }
}
