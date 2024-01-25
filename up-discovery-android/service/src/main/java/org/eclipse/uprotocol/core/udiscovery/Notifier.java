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

import static org.eclipse.uprotocol.common.util.log.Formatter.join;
import static org.eclipse.uprotocol.common.util.log.Formatter.tag;
import static org.eclipse.uprotocol.core.udiscovery.internal.Utils.toLongUri;
import static org.eclipse.uprotocol.core.udiscovery.v3.UDiscovery.SERVICE;

import android.util.Log;

import androidx.annotation.NonNull;

import org.eclipse.uprotocol.UPClient;
import org.eclipse.uprotocol.common.util.log.Key;
import org.eclipse.uprotocol.core.udiscovery.v3.Notification;
import org.eclipse.uprotocol.core.udiscovery.v3.Notification.Operation;
import org.eclipse.uprotocol.v1.UUri;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;


public class Notifier {
    public static final String PARENT_URI = "parentUri";
    public static final String OBSERVER_URI = "observerUri";
    public static final String NOTIFICATION = "notification";
    private static final String TAG = tag(SERVICE.getName());
    protected static boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);
    protected static boolean VERBOSE = Log.isLoggable(TAG, Log.VERBOSE);
    private final ObserverManager mObserverManager;
    private final UPClient mUpClient;

    public Notifier(ObserverManager observerManager, UPClient upClient) {
        mObserverManager = observerManager;
        mUpClient = upClient;
    }

    /**
     * notifyObservers - called when there is a change to a node.
     * Observers of respective nodes will be notified of the change
     *
     * @param operation - type of Operation - ADD, REMOVE, UPDATE
     * @param nodePath  - List of node uris from the root till the last node.
     */
    public void notifyObserversWithParentUri(Operation operation, @NonNull List<UUri> nodePath) {
        if (operation.equals(Operation.INVALID) || operation.equals(Operation.ADD)) {
            Log.e(TAG, join(Key.MESSAGE, "findObservers invalid operation " + operation));
            return;
        }
        if (nodePath.size() < 2) {
            Log.e(TAG, join(Key.MESSAGE, "findObservers nodePath invalid size"));
            return;
        }
        final List<UUri> observerList = getListOfObservers(nodePath);
        final UUri child = nodePath.get(nodePath.size() - 1);
        final UUri parent = nodePath.get(nodePath.size() - 2);
        observerList.forEach((observer) -> {
            if (DEBUG) {
                Log.d(TAG, join(Key.MESSAGE, "notify",
                        Key.URI, toLongUri(child),
                        PARENT_URI, toLongUri(parent),
                        OBSERVER_URI, toLongUri(observer)));
            }
            buildNotifications(child, parent, operation, observer);
        });
    }

    public void notifyObserversAddNodes(List<UUri> nodePath, List<UUri> addedNodes) {
        if (nodePath.isEmpty()) {
            Log.w(TAG, join(Key.MESSAGE, "notifyObserversAddNodes nodePath is Empty"));
            return;
        }
        final List<UUri> observerList = getListOfObservers(nodePath);
        final UUri parent = nodePath.get(nodePath.size() - 1);
        for (UUri observer : observerList) {
            for (UUri child : addedNodes) {
                if (DEBUG) {
                    Log.d(TAG, join(Key.MESSAGE, "notify",
                            Key.URI, toLongUri(child),
                            PARENT_URI, toLongUri(parent),
                            OBSERVER_URI, toLongUri(observer)));
                }
                buildNotifications(child, parent, Operation.ADD, observer);
            }
        }
    }

    private List<UUri> getListOfObservers(List<UUri> nodePath) {
        ArrayList<UUri> observerList = new ArrayList<>();
        for (UUri nodePathUri : nodePath) {
            for (Map.Entry<UUri, Set<UUri>> entry : mObserverManager.getObserverMap().entrySet()) {
                if (nodePathUri.equals(entry.getKey())) {
                    observerList.addAll(entry.getValue());
                }
            }
        }
        return observerList;
    }

    private void buildNotifications(UUri nodeUri, UUri parentUri, Operation operation,
                                    UUri observerUri) {
        final String child = toLongUri(nodeUri);
        final String parent = toLongUri(parentUri);
        final Notification notification = Notification.newBuilder()
                .setOperation(operation)
                .setUri(child)
                .setParentUri(parent)
                .build();
        sendNotification(observerUri, notification);
    }

    /**
     * sendNotification - Notify the observer of the change to a node by sending
     * Notification on topic: core.udiscovery/3/nodes#Notification
     *
     * @param observer     - observer to whom the notification should be sent to
     * @param notification - Notification containing the details of the node and operation
     */
    private void sendNotification(@NonNull UUri observer, @NonNull Notification notification) {
        Log.d(TAG, join(OBSERVER_URI, observer.toString(), NOTIFICATION,
                notification));
        // TODO
//        final Any payload = Any.pack(notification);
//        final String sink = toLongUri(observer);
//        final CloudEvent notificationEvent =
//                CloudEventFactory.notification(TOPIC_NODE_NOTIFICATION, sink, payload,
//                        UCloudEventAttributes.empty());
//        if (DEBUG) {
//            Log.d(LOG_TAG, Key.EVENT, "sendNotification", Key.MESSAGE, stringify(notificationEvent));
//        }
//        final Status status = mUltifiLink.publish(notificationEvent);
//        if (isOk(status)) {
//            Log.debugStatus(LOG_TAG, "sendNotification", status);
//        } else {
//            Log.warningStatus(LOG_TAG,"sendNotification", status, Key.MESSAGE, "Failed to send notification");
//        }
    }
}
