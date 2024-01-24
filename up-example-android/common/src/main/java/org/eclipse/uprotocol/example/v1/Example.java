/*
 * Copyright (c) 2023 General Motors GTO LLC
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
package org.eclipse.uprotocol.example.v1;

import static org.eclipse.uprotocol.rpc.RpcMapper.mapResponse;
import static org.eclipse.uprotocol.transport.builder.UPayloadBuilder.packToAny;

import com.google.protobuf.DescriptorProtos.ServiceOptions;

import org.eclipse.uprotocol.UprotocolOptions;
import org.eclipse.uprotocol.rpc.CallOptions;
import org.eclipse.uprotocol.rpc.RpcClient;
import org.eclipse.uprotocol.uri.builder.UResourceBuilder;
import org.eclipse.uprotocol.v1.UAuthority;
import org.eclipse.uprotocol.v1.UEntity;
import org.eclipse.uprotocol.v1.UStatus;
import org.eclipse.uprotocol.v1.UUri;

import java.util.Optional;
import java.util.concurrent.CompletionStage;

@SuppressWarnings({"unused", "SameParameterValue"})
public class Example {
    public static final UEntity SERVICE = UEntity.newBuilder()
            .setName(getServiceName())
            .setVersionMajor(getServiceVersion())
            .build();
    public static final String METHOD_EXECUTE_DOOR_COMMAND = "ExecuteDoorCommand";

    private Example() {}

    private static String getServiceName() {
        final ServiceOptions options = ExampleProto.getDescriptor().findServiceByName("Example").getOptions();
        return (options != null) ? options.getExtension(UprotocolOptions.name) : "";
    }

    private static int getServiceVersion() {
        final ServiceOptions options = ExampleProto.getDescriptor().findServiceByName("Example").getOptions();
        return (options != null) ? options.getExtension(UprotocolOptions.versionMajor) : 0;
    }

    public static Example.Stub newStub(RpcClient proxy) {
        return newStub(proxy, null, CallOptions.DEFAULT);
    }

    public static Example.Stub newStub(RpcClient proxy, CallOptions options) {
        return newStub(proxy, null, options);
    }

    public static Example.Stub newStub(RpcClient proxy, UAuthority authority, CallOptions options) {
        return new Example.Stub(proxy, authority, options);
    }

    public static class Stub {
        private final RpcClient proxy;
        private final UAuthority authority;
        private final CallOptions options;

        private Stub(RpcClient proxy, UAuthority authority, CallOptions options) {
            this.proxy = proxy;
            this.authority = authority;
            this.options = options;
        }

        private UUri buildUri(String method) {
            final UUri.Builder builder = UUri.newBuilder()
                    .setEntity(SERVICE)
                    .setResource(UResourceBuilder.forRpcRequest(method));
            if (authority != null) {
                builder.setAuthority(authority);
            }
            return builder.build();
        }

        public Optional<UAuthority> getAuthority() {
            return (authority != null) ? Optional.of(authority) : Optional.empty();
        }

        public CallOptions getOptions() {
            return options;
        }

        public CompletionStage<UStatus> executeDoorCommand(DoorCommand request) {
            return mapResponse(proxy.invokeMethod(buildUri(METHOD_EXECUTE_DOOR_COMMAND), packToAny(request), options), UStatus.class);
        }
    }
}
