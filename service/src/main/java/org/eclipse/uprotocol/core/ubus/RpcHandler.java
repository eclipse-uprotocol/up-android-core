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

import static org.eclipse.uprotocol.common.util.UStatusUtils.STATUS_OK;
import static org.eclipse.uprotocol.common.util.UStatusUtils.checkArgument;
import static org.eclipse.uprotocol.common.util.UStatusUtils.checkNotNull;
import static org.eclipse.uprotocol.common.util.UStatusUtils.toStatus;
import static org.eclipse.uprotocol.common.util.log.Formatter.join;
import static org.eclipse.uprotocol.common.util.log.Formatter.stringify;
import static org.eclipse.uprotocol.core.internal.util.CommonUtils.emptyIfNull;
import static org.eclipse.uprotocol.core.internal.util.UMessageUtils.buildFailedResponseMessage;
import static org.eclipse.uprotocol.core.internal.util.UUriUtils.checkMethodUriValid;
import static org.eclipse.uprotocol.core.internal.util.UUriUtils.isRemoteUri;
import static org.eclipse.uprotocol.core.ubus.Dispatcher.checkAuthority;
import static org.eclipse.uprotocol.uuid.factory.UuidUtils.getRemainingTime;
import static org.eclipse.uprotocol.uuid.factory.UuidUtils.isExpired;

import static java.util.concurrent.ConcurrentHashMap.newKeySet;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import com.google.common.base.Strings;

import org.eclipse.uprotocol.common.util.log.Key;
import org.eclipse.uprotocol.core.ubus.client.Client;
import org.eclipse.uprotocol.core.ubus.client.ClientManager;
import org.eclipse.uprotocol.core.ubus.client.ClientManager.RegistrationListener;
import org.eclipse.uprotocol.v1.UCode;
import org.eclipse.uprotocol.v1.UMessage;
import org.eclipse.uprotocol.v1.UStatus;
import org.eclipse.uprotocol.v1.UUID;
import org.eclipse.uprotocol.v1.UUri;

import java.io.PrintWriter;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

class RpcHandler extends UBus.Component {
    private static final int RETRY_DELAY_MS = 500;

    private final Map<UUri, Client> mServerByMethod = new ConcurrentHashMap<>();
    private final Map<Client, Set<UUri>> mMethodsByServer = new ConcurrentHashMap<>();
    private final Map<UUID, Request> mRequests = new ConcurrentHashMap<>();
    private final RegistrationListener mClientRegistrationListener = new RegistrationListener() {
        @Override
        public void onClientUnregistered(@NonNull Client client) {
            unregisterServer(client);
        }
    };
    private ClientManager mClientManager;
    private Dispatcher mDispatcher;
    private ScheduledExecutorService mExecutor;

    @SuppressWarnings("unused")
    private static class Request {
        final UMessage message;
        final UUri methodUri;
        final Client client;
        final long timestamp;
        final ScheduledFuture<?> timeoutFuture;
        volatile boolean dispatched;

        public Request(UMessage message, Client client, long timestamp, ScheduledFuture<?> timeoutFuture, boolean dispatched) {
            this.message = message;
            this.methodUri = message.getAttributes().getSink();
            this.client = client;
            this.timestamp = timestamp;
            this.timeoutFuture = timeoutFuture;
            this.dispatched = dispatched;
        }
    }

    @VisibleForTesting
    @NonNull RegistrationListener getClientRegistrationListener() {
        return mClientRegistrationListener;
    }

    @Override
    public void init(@NonNull UBus.Components components) {
        mClientManager = components.getClientManager();
        mDispatcher = components.getDispatcher();
        mExecutor = mDispatcher.getExecutor();
        mClientManager.registerListener(mClientRegistrationListener);
    }

    @Override
    public void shutdown() {
        mClientManager.unregisterListener(mClientRegistrationListener);
        mServerByMethod.clear();
        mMethodsByServer.clear();
        mRequests.clear();
    }

    public @NonNull UStatus registerServer(@NonNull UUri methodUri, @NonNull Client server) {
        try {
            checkMethodUriValid(methodUri);
            checkAuthority(methodUri, server);
            mServerByMethod.compute(methodUri, (key, currentServer) -> {
                if (currentServer != null && !currentServer.isReleased()) {
                    checkArgument(currentServer == server, UCode.ALREADY_EXISTS, "Method is registered by other client");
                    return server;
                }
                mMethodsByServer.compute(server, (it, methods) -> {
                    if (methods == null) {
                        methods = newKeySet();
                    }
                    methods.add(methodUri);
                    mExecutor.execute(() -> dispatchPostponedRequestMessages(methodUri));
                    return methods;
                });
                logStatus(Log.INFO, "registerServer", STATUS_OK, Key.METHOD, stringify(methodUri), Key.SERVER, server);
                return server;
            });
            return STATUS_OK;
        } catch (Exception e) {
            return logStatus(Log.ERROR, "registerServer", toStatus(e), Key.METHOD, stringify(methodUri), Key.SERVER, server);
        }
    }

    @SuppressWarnings("java:S1192")
    public @NonNull UStatus unregisterServer(@NonNull UUri methodUri, @NonNull Client server) {
        try {
            checkMethodUriValid(methodUri);
            checkAuthority(methodUri, server);
            mServerByMethod.computeIfPresent(methodUri, (key, currentServer) -> {
                checkArgument(currentServer == server, UCode.NOT_FOUND, "Method is registered by other client");
                mMethodsByServer.computeIfPresent(server, (k, methods) -> {
                    methods.remove(methodUri);
                    return methods.isEmpty() ? null : methods;
                });
                logStatus(Log.INFO, "unregisterServer", STATUS_OK, Key.METHOD, stringify(methodUri), Key.SERVER, server);
                return null;
            });
            return STATUS_OK;
        } catch (Exception e) {
            return logStatus(Log.ERROR, "unregisterServer", toStatus(e), Key.METHOD, stringify(methodUri), Key.SERVER, server);
        }
    }

    @SuppressWarnings("java:S3398")
    private void unregisterServer(@NonNull Client server) {
        final Set<UUri> methods = emptyIfNull(mMethodsByServer.remove(server));
        methods.forEach(methodUri -> mServerByMethod.computeIfPresent(methodUri, (key, currentServer) -> {
            if (currentServer == server) {
                logStatus(Log.INFO, "unregisterServer", STATUS_OK, Key.METHOD, stringify(methodUri), Key.SERVER, server);
                return null;
            }
            return currentServer;
        }));
    }

    @VisibleForTesting
    Client getServer(@NonNull UUri methodUri) {
        return isRemoteUri(methodUri) ?
                mClientManager.getRemoteClient() : mServerByMethod.computeIfPresent(methodUri, (key, server) -> server);
    }

    @VisibleForTesting
    Map<UUri, Client> getServers() {
        return mServerByMethod;
    }

    private @NonNull ScheduledFuture<?> scheduleTimeoutResponseMessage(@NonNull UUID requestId, long timeout) {
        return mExecutor.schedule(() -> mRequests.computeIfPresent(requestId, (key, request) -> {
            Log.w(TAG, join(Key.EVENT, "Timeout while waiting for response", Key.REQUEST, stringify(request.message)));
            final UMessage responseMessage = buildFailedResponseMessage(request.message, UCode.DEADLINE_EXCEEDED);
            mDispatcher.dispatchTo(responseMessage, request.client);
            return null;
        }), timeout, TimeUnit.MILLISECONDS);
    }

    public @NonNull UStatus handleRequestMessage(@NonNull UMessage requestMessage, @NonNull Client client) {
        final long startTime = System.currentTimeMillis();
        final UUID requestId = requestMessage.getAttributes().getId();
        final UUri methodUri = requestMessage.getAttributes().getSink();
        final long timeout = getRemainingTime(requestMessage.getAttributes()).orElse(0L);
        try {
            checkAuthority(requestMessage.getAttributes().getSource(), client);
            checkArgument(timeout > 0, UCode.DEADLINE_EXCEEDED, "Message expired");

            final Request request = mRequests.compute(requestId, (key, currentRequest) -> {
                checkArgument(currentRequest == null, UCode.ABORTED, "Duplicated request found");
                final Client server = getServer(methodUri);
                checkNotNull(server, UCode.UNAVAILABLE, "Service is not available");
                final boolean dispatched = mDispatcher.dispatchTo(requestMessage, server);
                if (!dispatched) {
                    mExecutor.schedule(() -> dispatchRequestMessage(requestId), RETRY_DELAY_MS, TimeUnit.MILLISECONDS);
                }
                return new Request(requestMessage, client, startTime,
                        scheduleTimeoutResponseMessage(requestId, timeout), dispatched);
            });
            checkNotNull(request, UCode.UNAVAILABLE, "Service is not available");
            return STATUS_OK;
        } catch (Exception e) {
            return toStatus(e);
        }
    }

    private void dispatchRequestMessage(@NonNull UUID requestId) {
        mRequests.computeIfPresent(requestId, (key, request) -> {
            if (request.dispatched) {
                return request;
            }
            final Client server = getServer(request.methodUri);
            request.dispatched = (server != null) && mDispatcher.dispatchTo(request.message, server);
            return request;
        });
    }

    private void dispatchPostponedRequestMessages(@NonNull UUri methodUri) {
        mRequests.values().stream()
                .filter(request -> request.methodUri.equals(methodUri))
                .forEach(request -> dispatchRequestMessage(request.message.getAttributes().getId()));
    }

    @SuppressWarnings("DataFlowIssue")
    public @NonNull UStatus handleResponseMessage(@NonNull UMessage responseMessage, @NonNull Client server) {
        final UUID requestId = responseMessage.getAttributes().getReqid();
        final UUri methodUri = responseMessage.getAttributes().getSource();
        try {
            checkAuthority(methodUri, server);
            checkArgument(!isExpired(responseMessage.getAttributes()), UCode.DEADLINE_EXCEEDED, "Message expired");
            mRequests.compute(requestId, (key, request) -> {
                checkNotNull(request, UCode.CANCELLED, "Request was either cancelled or expired");
                request.timeoutFuture.cancel(false);
                mDispatcher.dispatchTo(responseMessage, request.client);
                return null;
            });
            return STATUS_OK;
        } catch (Exception e) {
            return toStatus(e);
        }
    }

    public @NonNull Set<UUri> getMethods(@NonNull Client server) {
        return emptyIfNull(mMethodsByServer.get(server));
    }

    protected void dump(@NonNull PrintWriter writer, String[] args) {
        args = emptyIfNull(args);
        if (args.length > 0) {
            if ("-s".equals(args[0])) {
                if (args.length > 1) {
                    dumpServer(writer, args[1]);
                    return;
                }
            } else {
                return;
            }
        }
        dumpSummary(writer);
    }

    private void dumpSummary(@NonNull PrintWriter writer) {
        writer.println("  ========");
        final Set<Client> servers = mMethodsByServer.keySet();
        writer.println("  There are " + servers.size() + " RPC server(s)");
        servers.forEach(server -> writer.println("    " + server));

        dumpAllServers(writer);
    }

    private void dumpAllServers(@NonNull PrintWriter writer) {
        mMethodsByServer.keySet().forEach(server -> dumpServer(writer, server));
    }

    private void dumpServer(@NonNull PrintWriter writer, @NonNull String flattenEntity) {
        mMethodsByServer.keySet().stream()
                .filter(server -> stringify(server.getEntity()).equals(flattenEntity))
                .forEach(server -> dumpServer(writer, server));
    }

    private void dumpServer(@NonNull PrintWriter writer, @NonNull Client server) {
        final StringBuilder sb = new StringBuilder();
        getMethods(server).forEach(methodUri -> {
            if (sb.length() > 1) {
                sb.append("\n").append(Strings.repeat(" ", 11));
            }
            sb.append(stringify(methodUri));
        });
        final String formattedMethods = sb.toString();

        writer.println("  --------");
        writer.println("   Server: " + server);
        writer.println("  Methods: " + formattedMethods);
    }
}
