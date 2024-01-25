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

import static org.eclipse.uprotocol.common.util.UStatusUtils.checkStringNotEmpty;
import static org.eclipse.uprotocol.common.util.UStatusUtils.isOk;
import static org.eclipse.uprotocol.common.util.UStatusUtils.toStatus;
import static org.eclipse.uprotocol.common.util.log.Formatter.join;
import static org.eclipse.uprotocol.common.util.log.Formatter.tag;
import static org.eclipse.uprotocol.core.udiscovery.Notifier.OBSERVER_URI;
import static org.eclipse.uprotocol.core.udiscovery.Notifier.PARENT_URI;
import static org.eclipse.uprotocol.core.udiscovery.common.Constants.UNEXPECTED_PAYLOAD;
import static org.eclipse.uprotocol.core.udiscovery.internal.Utils.deserializeUriList;
import static org.eclipse.uprotocol.core.udiscovery.internal.Utils.logStatus;
import static org.eclipse.uprotocol.core.udiscovery.internal.Utils.toLongUri;
import static org.eclipse.uprotocol.core.udiscovery.v3.UDiscovery.METHOD_REGISTER_FOR_NOTIFICATIONS;
import static org.eclipse.uprotocol.core.udiscovery.v3.UDiscovery.METHOD_UNREGISTER_FOR_NOTIFICATIONS;
import static org.eclipse.uprotocol.core.udiscovery.v3.UDiscovery.SERVICE;
import static org.eclipse.uprotocol.transport.builder.UPayloadBuilder.packToAny;
import static org.eclipse.uprotocol.transport.builder.UPayloadBuilder.unpack;

import android.content.Context;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;

import com.google.protobuf.ProtocolStringList;

import org.eclipse.uprotocol.common.UStatusException;
import org.eclipse.uprotocol.common.util.log.Key;
import org.eclipse.uprotocol.core.udiscovery.common.Constants;
import org.eclipse.uprotocol.core.udiscovery.db.DiscoveryManager;
import org.eclipse.uprotocol.core.udiscovery.interfaces.PersistInterface;
import org.eclipse.uprotocol.core.udiscovery.v3.AddNodesRequest;
import org.eclipse.uprotocol.core.udiscovery.v3.DeleteNodesRequest;
import org.eclipse.uprotocol.core.udiscovery.v3.FindNodePropertiesRequest;
import org.eclipse.uprotocol.core.udiscovery.v3.FindNodePropertiesResponse;
import org.eclipse.uprotocol.core.udiscovery.v3.FindNodesRequest;
import org.eclipse.uprotocol.core.udiscovery.v3.FindNodesResponse;
import org.eclipse.uprotocol.core.udiscovery.v3.LookupUriResponse;
import org.eclipse.uprotocol.core.udiscovery.v3.Node;
import org.eclipse.uprotocol.core.udiscovery.v3.NotificationsRequest;
import org.eclipse.uprotocol.core.udiscovery.v3.PropertyValue;
import org.eclipse.uprotocol.core.udiscovery.v3.UpdateNodeRequest;
import org.eclipse.uprotocol.core.udiscovery.v3.UpdatePropertyRequest;
import org.eclipse.uprotocol.uri.serializer.LongUriSerializer;
import org.eclipse.uprotocol.v1.UCode;
import org.eclipse.uprotocol.v1.UMessage;
import org.eclipse.uprotocol.v1.UPayload;
import org.eclipse.uprotocol.v1.UStatus;
import org.eclipse.uprotocol.v1.UUri;
import org.eclipse.uprotocol.v1.UUriBatch;

import java.util.List;
import java.util.Map;

@SuppressWarnings({"java:S1200", "java:S3008"})
public class RPCHandler implements PersistInterface {

    protected static final String TAG = tag(SERVICE.getName());
    protected static final String NODEURI = "nodeUri";
    protected static final String DEPTH = "depth";
    protected static final String NODE = "node";
    protected static final String NODELIST = "nodeList";
    protected static final String URILIST = "uriList";
    protected static boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);
    protected static boolean VERBOSE = Log.isLoggable(TAG, Log.VERBOSE);
    final DiscoveryManager mDiscoveryManager;
    private final Context mContext;
    private final AssetManager mAssetManager;

    private final ObserverManager mObserverManager;

    public RPCHandler(Context context, AssetManager assetManager, DiscoveryManager discoveryManager,
                      ObserverManager observerManager) {
        mContext = context;
        mDiscoveryManager = discoveryManager;
        mAssetManager = assetManager;
        mDiscoveryManager.setPersistInterface(this);
        mDiscoveryManager.setChecksumInterface(new IntegrityCheck());
        mObserverManager = observerManager;
    }

    public void shutdown() {
        mDiscoveryManager.shutdown();
    }

    @Override
    public void persist(String data) {
        mAssetManager.writeFileToInternalStorage(mContext, Constants.LDS_DB_FILENAME, data);
    }

    public UPayload processLookupUriFromLDS(@NonNull UMessage message) {
        LookupUriResponse response;
        try {
            final UPayload payload = message.getPayload();
            final UUri uri = unpack(payload, UUri.class).
                    orElseThrow(() -> new UStatusException(UCode.INVALID_ARGUMENT, UNEXPECTED_PAYLOAD));
            if (DEBUG) {
                Log.d(TAG, join(Key.REQUEST, "LookupUri", Key.URI, toLongUri(uri)));
            }
            final Pair<UUriBatch, UStatus> pair = mDiscoveryManager.lookupUri(uri);
            final UUriBatch batch = pair.first;
            final UStatus status = pair.second;
            if (DEBUG) {
                Log.d(TAG, join(Key.RESPONSE, "LookupUri", Key.STATUS, status, Key.URI, batch));
            }
            response = LookupUriResponse.newBuilder().setUris(batch).setStatus(status).build();
        } catch (Exception e) {
            final UStatus status = logStatus(TAG, "LookupUri", toStatus(e));
            response = LookupUriResponse.newBuilder().setStatus(status).build();
        }
        return packToAny(response);
    }

    public UPayload processFindNodesFromLDS(@NonNull UMessage message) {
        FindNodesResponse response;
        try {
            final UPayload payload = message.getPayload();
            final FindNodesRequest request = unpack(payload, FindNodesRequest.class).
                    orElseThrow(() -> new UStatusException(UCode.INVALID_ARGUMENT, UNEXPECTED_PAYLOAD));
            final String rawUri = request.getUri();
            final UUri uri = LongUriSerializer.instance().deserialize(rawUri);
            final int depth = request.hasDepth() ? request.getDepth() : -1;
            if (DEBUG) {
                Log.d(TAG, join(Key.REQUEST, "FindNodes", NODEURI, rawUri, DEPTH, depth));
            }
            final Pair<Node, UStatus> pair = mDiscoveryManager.findNode(uri, depth);
            final Node node = pair.first;
            final UStatus status = pair.second;
            if (DEBUG) {
                Log.d(TAG, join(Key.RESPONSE, "FindNodes", Key.STATUS, status, NODE, node));
            }
            response = FindNodesResponse.newBuilder().addNodes(node).setStatus(status).build();
        } catch (Exception e) {
            final UStatus status = logStatus(TAG, "FindNodes", toStatus(e));
            response = FindNodesResponse.newBuilder().setStatus(status).build();
        }
        return packToAny(response);
    }

    public UPayload processFindNodeProperties(@NonNull UMessage message) {
        FindNodePropertiesResponse response;
        try {
            final UPayload payload = message.getPayload();
            final FindNodePropertiesRequest request = unpack(payload, FindNodePropertiesRequest.class).
                    orElseThrow(() -> new UStatusException(UCode.INVALID_ARGUMENT, UNEXPECTED_PAYLOAD));
            final String rawUri = request.getUri();
            final UUri uri = LongUriSerializer.instance().deserialize(rawUri);
            final ProtocolStringList list = request.getPropertiesList();
            if (DEBUG) {
                Log.d(TAG, join(Key.REQUEST, "FindNodeProperties", NODEURI, rawUri,
                        "properties", list));
            }
            final Pair<Map<String, PropertyValue>, UStatus> pair = mDiscoveryManager.findNodeProperties(uri, list);
            final Map<String, PropertyValue> propertiesMap = pair.first;
            final UStatus status = pair.second;
            if (DEBUG) {
                Log.d(TAG, join(Key.RESPONSE, "FindNodeProperties", Key.STATUS, status,
                        "properties", propertiesMap));
            }
            response = FindNodePropertiesResponse.newBuilder()
                    .putAllProperties(propertiesMap)
                    .setStatus(status)
                    .build();
        } catch (Exception e) {
            final UStatus status = logStatus(TAG, "FindNodeProperties", toStatus(e));
            response = FindNodePropertiesResponse.newBuilder().setStatus(status).build();
        }
        return packToAny(response);
    }

    public UPayload processLDSUpdateNode(@NonNull UMessage message) {
        UStatus status;
        try {

            final UPayload payload = message.getPayload();
            final UpdateNodeRequest request = unpack(payload, UpdateNodeRequest.class).
                    orElseThrow(() -> new UStatusException(UCode.INVALID_ARGUMENT, UNEXPECTED_PAYLOAD));
            final Node node = request.getNode();
            final int ttl = request.hasTtl() ? message.getAttributes().getTtl() : -1;
            if (DEBUG) {
                Log.d(TAG, join(Key.REQUEST, "UpdateNode", NODE, node, Key.TTL, ttl));
            }
            status = mDiscoveryManager.updateNode(node, ttl);
            if (DEBUG) {
                Log.d(TAG, join(Key.RESPONSE, "UpdateNode", Key.STATUS, status));
            }
            refreshDatabase(status);
        } catch (Exception e) {
            status = logStatus(TAG, "UpdateNode", toStatus(e));
        }
        return packToAny(status);
    }

    public UPayload processLDSUpdateProperty(@NonNull UMessage message) {
        UStatus status;
        try {
            final UPayload payload = message.getPayload();
            final UpdatePropertyRequest request = unpack(payload, UpdatePropertyRequest.class).
                    orElseThrow(() -> new UStatusException(UCode.INVALID_ARGUMENT, UNEXPECTED_PAYLOAD));
            final String name = request.getProperty();
            final PropertyValue value = request.getValue();
            final String rawUri = request.getUri();
            final UUri uri = LongUriSerializer.instance().deserialize(rawUri);
            if (DEBUG) {
                Log.d(TAG, join(Key.REQUEST, "UpdateNodeProperty", Key.NAME, name,
                        Key.VALUE, value, NODEURI, rawUri));
            }
            status = mDiscoveryManager.updateProperty(name, value, uri);
            if (DEBUG) {
                Log.d(TAG, join(Key.RESPONSE, "UpdateNodeProperty", Key.STATUS, status));
            }
            refreshDatabase(status);
        } catch (Exception e) {
            status = logStatus(TAG, "UpdateNodeProperty", toStatus(e));
        }
        return packToAny(status);
    }

    public UPayload processAddNodesLDS(@NonNull UMessage message) {
        UStatus status;
        try {
            final UPayload payload = message.getPayload();
            final AddNodesRequest request = unpack(payload, AddNodesRequest.class).
                    orElseThrow(() -> new UStatusException(UCode.INVALID_ARGUMENT, UNEXPECTED_PAYLOAD));
            final String rawUri = request.getParentUri();
            final UUri uri = LongUriSerializer.instance().deserialize(rawUri);
            final List<Node> nodeList = request.getNodesList();
            if (DEBUG) {
                Log.d(TAG, join(Key.REQUEST, "AddNodes", PARENT_URI, rawUri));
            }
            if (VERBOSE) {
                Log.v(TAG, join(Key.REQUEST, "AddNodes", NODELIST, nodeList));
            }
            status = mDiscoveryManager.addNodes(uri, nodeList);
            refreshDatabase(status);
            if (DEBUG) {
                Log.d(TAG, join(Key.RESPONSE, "AddNodes", Key.STATUS, status));
            }
        } catch (Exception e) {
            status = logStatus(TAG, "AddNodes", toStatus(e));
        }
        return packToAny(status);
    }

    public UPayload processDeleteNodes(@NonNull UMessage message) {
        UStatus status;
        try {
            final UPayload payload = message.getPayload();
            final DeleteNodesRequest request = unpack(payload, DeleteNodesRequest.class).
                    orElseThrow(() -> new UStatusException(UCode.INVALID_ARGUMENT, UNEXPECTED_PAYLOAD));
            final ProtocolStringList urisList = request.getUrisList();
            final List<UUri> nodeUriList = deserializeUriList(urisList);
            if (DEBUG) {
                Log.d(TAG, join(Key.REQUEST, "DeleteNodes", URILIST, urisList));
            }
            status = mDiscoveryManager.deleteNodes(nodeUriList);
            if (DEBUG) {
                Log.d(TAG, join(Key.RESPONSE, "DeleteNodes", Key.STATUS, status));
            }
            refreshDatabase(status);
        } catch (Exception e) {
            status = logStatus(TAG, "DeleteNodes", toStatus(e));
        }
        return packToAny(status);
    }

    public UPayload processNotificationRegistration(@NonNull UMessage message, String methodName) {
        UStatus status;
        try {
            checkStringNotEmpty(methodName, "methodName is empty");
            final UPayload payload = message.getPayload();
            final NotificationsRequest request = unpack(payload, NotificationsRequest.class).
                    orElseThrow(() -> new UStatusException(UCode.INVALID_ARGUMENT, UNEXPECTED_PAYLOAD));
            final String rawUri = request.getObserver().getUri();
            final UUri observerUri = LongUriSerializer.instance().deserialize(rawUri);
            final List<UUri> nodeUriList = deserializeUriList(request.getUrisList());
            if (METHOD_REGISTER_FOR_NOTIFICATIONS.equals(methodName)) {
                status = registerNotifications(observerUri, nodeUriList);
            } else if (METHOD_UNREGISTER_FOR_NOTIFICATIONS.equals(methodName)) {
                status = UnregisterForNotifications(observerUri, nodeUriList);
            } else {
                throw new UStatusException(UCode.INVALID_ARGUMENT, "unknown method " + methodName);
            }
        } catch (Exception e) {
            status = logStatus(TAG, "processNotificationRegistration", toStatus(e));
        }
        return packToAny(status);
    }

    private UStatus registerNotifications(@NonNull UUri observer, @NonNull List<UUri> nodeUriList) {
        if (DEBUG) {
            Log.d(TAG, join(Key.REQUEST, "RegisterForNotifications",
                    OBSERVER_URI, toLongUri(observer), URILIST, nodeUriList));
        }
        final UStatus status = mObserverManager.registerObserver(nodeUriList, observer);
        if (DEBUG) {
            Log.d(TAG, join(Key.RESPONSE, "RegisterForNotifications", Key.STATUS, status));
        }
        return status;
    }

    private UStatus UnregisterForNotifications(@NonNull UUri observer, @NonNull List<UUri> nodeUriList) {
        if (DEBUG) {
            Log.d(TAG, join(Key.REQUEST, "UnregisterForNotifications",
                    OBSERVER_URI, toLongUri(observer), URILIST, nodeUriList));
        }
        final UStatus status = mObserverManager.unregisterObserver(nodeUriList, observer);
        if (DEBUG) {
            Log.d(TAG, join(Key.RESPONSE, "UnregisterForNotifications", Key.STATUS, status));
        }
        return status;
    }

    private void refreshDatabase(UStatus status) {
        if (isOk(status)) {
            mAssetManager.writeFileToInternalStorage(mContext, Constants.LDS_DB_FILENAME, mDiscoveryManager.export());
            if (VERBOSE) {
                Log.v(TAG, join(Key.MESSAGE, mDiscoveryManager.export()));
            }
        }
    }
}
