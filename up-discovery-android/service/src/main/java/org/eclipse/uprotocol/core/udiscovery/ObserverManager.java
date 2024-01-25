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

import static org.eclipse.uprotocol.common.util.UStatusUtils.buildStatus;
import static org.eclipse.uprotocol.common.util.UStatusUtils.toStatus;
import static org.eclipse.uprotocol.common.util.log.Formatter.join;
import static org.eclipse.uprotocol.common.util.log.Formatter.quote;
import static org.eclipse.uprotocol.common.util.log.Formatter.tag;
import static org.eclipse.uprotocol.core.udiscovery.Notifier.OBSERVER_URI;
import static org.eclipse.uprotocol.core.udiscovery.RPCHandler.NODEURI;
import static org.eclipse.uprotocol.core.udiscovery.internal.Utils.logStatus;
import static org.eclipse.uprotocol.core.udiscovery.internal.Utils.toLongUri;
import static org.eclipse.uprotocol.core.udiscovery.v3.UDiscovery.SERVICE;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import com.google.common.collect.Sets;

import org.eclipse.uprotocol.common.util.log.Key;
import org.eclipse.uprotocol.core.udiscovery.db.Observer;
import org.eclipse.uprotocol.core.udiscovery.db.ObserverDao;
import org.eclipse.uprotocol.core.udiscovery.db.ObserverDatabase;
import org.eclipse.uprotocol.core.udiscovery.db.ObserverDatabaseKt;
import org.eclipse.uprotocol.uri.serializer.LongUriSerializer;
import org.eclipse.uprotocol.v1.UCode;
import org.eclipse.uprotocol.v1.UStatus;
import org.eclipse.uprotocol.v1.UUri;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ObserverManager {
    protected static final String TAG = tag(SERVICE.getName());
    protected static boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);
    protected static boolean VERBOSE = Log.isLoggable(TAG, Log.VERBOSE);
    @VisibleForTesting
    final ConcurrentHashMap<UUri, Set<UUri>> mObservers = new ConcurrentHashMap<>();
    private final Object mDBLock = new Object();
    private final ObserverDatabase mDatabase;

    public ObserverManager(@NonNull Context context) {
        mDatabase = ObserverDatabaseKt.createDbExtension(context);
    }

    @VisibleForTesting
    ObserverManager(ObserverDatabase database) {
        mDatabase = database;

    }

    private void loadMapDataFromDb() {
        List<String> nodeUrisList = observerDao().getNodeUrisList();
        for (String nodeUri : nodeUrisList) {
            List<String> observerList = observerDao().getObserverList(nodeUri);
            for (String observer : observerList) {
                final LongUriSerializer serializer = LongUriSerializer.instance();
                final UUri key = serializer.deserialize(nodeUri);
                final UUri val = serializer.deserialize(observer);
                addObserverToMap(key, val);
            }
        }
    }

    /**
     * registerObserver - Process NotificationRequest after checking for valid URIs
     * by updating data to Map and DB
     *
     * @param nodeUris    - List of UUri's that will be monitored for any dp update
     * @param observerUri - observer uri where the notification should be sent on any db update
     * @return Status OK - if success, INVALID_ARGUMENT if exception occurs, INTERNAL if DB operation failed
     */
    public UStatus registerObserver(@NonNull List<UUri> nodeUris, UUri observerUri) {
        if (mObservers.isEmpty()) {
            loadMapDataFromDb();
        }
        // TODO
        //checkUriValid(observerUri);
        long addedCount = nodeUris.stream().filter(nodeUri -> addObserver(nodeUri, observerUri)).count();
        if (addedCount == nodeUris.size()) {
            return buildStatus(UCode.OK);
        } else {
            //TODO: check with Cloud team to retry request if partial success case
            return buildStatus(UCode.INTERNAL, "Operation didn't complete for all Nodes");
        }
    }

    /**
     * addObserver - adds node and observer to Map and DB
     *
     * @param nodeUri     - node
     * @param observerUri - observer
     * @return true - if added successfully , false otherwise
     */
    private boolean addObserver(@NonNull UUri nodeUri, @NonNull UUri observerUri) {
        if (addObserverToMap(nodeUri, observerUri)) {
            return addObserverToDb(nodeUri, observerUri);
        }
        return true;
    }

    /**
     * addObserverToMap - Adds observer to map with respective node
     *
     * @param nodeUri  - node
     * @param observer - Observer
     * @return true - if Map was modified, false otherwise
     */
    private boolean addObserverToMap(@NonNull UUri nodeUri, @NonNull UUri observer) {
        var wrapper = new Object() {
            boolean added = false;
        };
        mObservers.compute(nodeUri, (key, value) -> {
            Set<UUri> observers = mObservers.get(nodeUri);
            if (observers == null) {
                observers = Sets.newHashSet();
            }
            wrapper.added = observers.add(observer);
            if (DEBUG) {
                Log.d(TAG, join(Key.EVENT, "addObserverToMap",
                        NODEURI, toLongUri(nodeUri),
                        OBSERVER_URI, toLongUri(observer)));
            }
            return observers;
        });
        return wrapper.added;
    }

    /**
     * addObserverToDb - Adds node and observer to DB
     *
     * @param nodeUri     - node
     * @param observerUri - observer
     * @return true - if added to db successfully, false otherwise
     */
    private boolean addObserverToDb(@NonNull UUri nodeUri, @NonNull UUri observerUri) {
        synchronized (mDBLock) {
            try {
                long retCode = observerDao().addObserver(
                        new Observer(toLongUri(nodeUri), toLongUri(observerUri)));
                if (retCode < 0) {
                    Log.e(TAG, join(Key.MESSAGE, "Failed to add to DB with code", Key.CODE, retCode));
                }
                return retCode >= 0;
            } catch (Exception e) {
                logStatus(TAG, "addObserverToDb", toStatus(e), Key.MESSAGE, "Failed to add to DB");
                return false;
            }
        }
    }

    /**
     * unregisterObserver - Process NotificationRequest by updating data to Map and DB
     *
     * @param nodeUris    - List of UUri's that will be monitored for any dp update
     * @param observerUri - observer uri where the notification should be sent on any db update
     * @return Status OK - if success, INVALID_ARGUMENT if exception occurs, INTERNAL if DB operation failed
     */
    public UStatus unregisterObserver(@NonNull List<UUri> nodeUris, UUri observerUri) {
        if (mObservers.isEmpty()) {
            loadMapDataFromDb();
        }
        // TODO
        //checkUriValid(observerUri);
        int removedCount = 0;
        for (UUri nodeUri : nodeUris) {
            if (removeObserver(nodeUri, observerUri)) {
                removedCount++;
            }
        }
        if (removedCount == nodeUris.size()) {
            return buildStatus(UCode.OK);
        } else {
            //TODO: check with Cloud team to retry request if partial success case
            return buildStatus(UCode.INTERNAL, "Operation didn't complete for all Nodes");
        }
    }

    /**
     * Remove Observer and Node based on state in Map and DB
     *
     * @param nodeUri     - node
     * @param observerUri - observer
     * @return true - if removed from Map and DB successfully, false otherwise
     */
    private boolean removeObserver(@NonNull UUri nodeUri, @NonNull UUri observerUri) {
        if (removeObserverFromMap(nodeUri, observerUri)) {
            return removeObserverFromDb(nodeUri, observerUri);
        }
        return true;
    }

    /**
     * removeObserverFromMap - Removes observer from map with respective node
     *
     * @param nodeUri  - node
     * @param observer - Observer
     * @return true - if Map was modified, false otherwise
     */
    public boolean removeObserverFromMap(@NonNull UUri nodeUri, @NonNull UUri observer) {
        var wrapper = new Object() {
            boolean removed = false;
        };
        mObservers.computeIfPresent(nodeUri, (key, value) -> {
            Set<UUri> observers = value;
            if (observers.size() == 1) {
                mObservers.remove(nodeUri);
                wrapper.removed = true;
            } else {
                wrapper.removed = observers.remove(observer);
            }
            if (DEBUG) {
                Log.d(TAG, join(Key.EVENT, "removeObserverFromMap",
                        NODEURI, toLongUri(nodeUri),
                        OBSERVER_URI, toLongUri(observer)));
            }
            return observers;
        });
        return wrapper.removed;
    }

    /**
     * removeObserverFromDb - Removes node and observer entry from DB
     *
     * @param nodeUri     - node
     * @param observerUri - observer
     * @return true - if removed from db successfully, false otherwise
     */
    private boolean removeObserverFromDb(@NonNull UUri nodeUri, @NonNull UUri observerUri) {
        synchronized (mDBLock) {
            try {
                int retCode = observerDao().removeObserver(toLongUri(nodeUri), toLongUri(observerUri));
                if (retCode < 0) {
                    Log.e(TAG, join(Key.EVENT, "Failed to remove from DB with code", Key.CODE, retCode));
                }
                return retCode >= 0;
            } catch (Exception e) {
                logStatus(TAG, "removeObserverFromDb", toStatus(e), Key.MESSAGE, "Failed to remove from DB");
                return false;
            }
        }
    }

    /**
     * getObservers - fetch the observers for a particular node from the Map
     *
     * @param nodeUri - node
     * @return set of observers for that node
     */
    public @NonNull Set<UUri> getObservers(@NonNull UUri nodeUri) {
        Set<UUri> observers = mObservers.get(nodeUri);
        if (null == observers) {
            return Collections.emptySet();
        }
        if (DEBUG) {
            Log.d(TAG, join(Key.MESSAGE, "List of observer(s) for node", NODEURI, nodeUri.toString()));
            for (UUri observerUri : observers) {
                Log.d(TAG, join(OBSERVER_URI, observerUri.toString()));
            }
        }
        return observers;
    }

    public Map<UUri, Set<UUri>> getObserverMap() {
        if (mObservers.isEmpty()) {
            loadMapDataFromDb();
        }
        final ConcurrentHashMap<UUri, Set<UUri>> observersMap = mObservers;
        if (VERBOSE) {
            Log.v(TAG, join(Key.EVENT, "getObserverMap"));
            for (Map.Entry<UUri, Set<UUri>> entry : observersMap.entrySet()) {
                for (UUri observer : entry.getValue()) {
                    String key = toLongUri(entry.getKey());
                    String val = toLongUri(observer);
                    Log.v(TAG, join(NODEURI, quote(key), OBSERVER_URI, quote(val)));
                }
            }
        }
        return observersMap;
    }

    private @NonNull ObserverDao observerDao() {
        return mDatabase.observerDao();
    }
}
