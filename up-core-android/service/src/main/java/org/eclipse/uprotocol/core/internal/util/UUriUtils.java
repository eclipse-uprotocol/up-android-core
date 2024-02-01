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

import static org.eclipse.uprotocol.common.util.UStatusUtils.checkArgument;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.eclipse.uprotocol.common.UStatusException;
import org.eclipse.uprotocol.uri.factory.UResourceBuilder;
import org.eclipse.uprotocol.uri.serializer.LongUriSerializer;
import org.eclipse.uprotocol.uri.validator.UriValidator;
import org.eclipse.uprotocol.v1.UAuthority;
import org.eclipse.uprotocol.v1.UResource;
import org.eclipse.uprotocol.v1.UUri;
import org.eclipse.uprotocol.validation.ValidationResult;

public interface UUriUtils {
    UResource RESPONSE_RESOURCE = UResourceBuilder.forRpcResponse();

    static @NonNull UUri checkTopicUriValid(@NonNull UUri uri) {
        final ValidationResult result = UriValidator.validate(uri);
        if (result.isFailure()) {
            throw new UStatusException(result.toStatus());
        }
        checkArgument(isTopic(uri.getResource()), "Invalid topic URI");
        return uri;
    }

    static @NonNull UUri checkMethodUriValid(@NonNull UUri uri) {
        final ValidationResult result = UriValidator.validateRpcMethod(uri);
        if (result.isFailure()) {
            throw new UStatusException(result.toStatus());
        }
        checkArgument(isMethod(uri.getResource()), "Invalid method URI");
        return uri;
    }

    static @NonNull UUri checkResponseUriValid(@NonNull UUri uri) {
        final ValidationResult result = UriValidator.validateRpcResponse(uri);
        if (result.isFailure()) {
            throw new UStatusException(result.toStatus());
        }
        return uri;
    }

    static @NonNull UUri getClientUri(@NonNull UUri uri) {
        return UUri.newBuilder(uri).clearResource().build();
    }

    static @NonNull UUri addAuthority(@NonNull UUri uri, @NonNull UAuthority authority) {
        return UUri.newBuilder(uri).setAuthority(authority).build();
    }

    static @NonNull UUri removeAuthority(@NonNull UUri uri) {
        return UUri.newBuilder(uri).clearAuthority().build();
    }

    static boolean isSameClient(@NonNull UUri uri1, @NonNull UUri uri2) {
        return uri1.getAuthority().equals(uri2.getAuthority()) && uri1.getEntity().equals(uri2.getEntity());
    }

    static boolean isRemoteUri(@NonNull UUri uri) {
        return UriValidator.isRemote(uri.getAuthority());
    }

    static boolean isLocalUri(@NonNull UUri uri) {
        return !isRemoteUri(uri);
    }

    static boolean isMethodUri(@NonNull UUri uri) {
        return isMethod(uri.getResource());
    }

    private static boolean isTopic(@NonNull UResource resource) {
        final String name = resource.getName();
        return !name.isEmpty() && !RESPONSE_RESOURCE.getName().equals(resource.getName());
    }

    private static boolean isMethod(@NonNull UResource resource) {
        return RESPONSE_RESOURCE.getName().equals(resource.getName()) &&
                !RESPONSE_RESOURCE.getInstance().equals(resource.getInstance());
    }

    static @NonNull String toUriString(@Nullable UUri uri) {
        return LongUriSerializer.instance().serialize(uri);
    }

    static @NonNull UUri toUri(@Nullable String string) {
        return LongUriSerializer.instance().deserialize(string);
    }
}
