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
 * SPDX-FileCopyrightText: 2024 General Motors GTO LLC
 * SPDX-License-Identifier: Apache-2.0
 */

package org.eclipse.uprotocol.core.udiscovery;

import static org.eclipse.uprotocol.common.util.UStatusUtils.toStatus;
import static org.eclipse.uprotocol.common.util.log.Formatter.tag;
import static org.eclipse.uprotocol.core.udiscovery.internal.Utils.logStatus;
import static org.eclipse.uprotocol.core.udiscovery.v3.UDiscovery.SERVICE;

import androidx.annotation.VisibleForTesting;

import org.eclipse.uprotocol.common.util.log.Key;
import org.eclipse.uprotocol.core.udiscovery.interfaces.ChecksumInterface;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * The IntegrityCheck class implements the ChecksumInterface.
 * It provides methods to generate and verify hashes.
 * The hash algorithm used is SHA-256 by default, but can be changed by providing a different algorithm when
 * creating an instance of the class.
 *
 * @see ChecksumInterface
 */
public class IntegrityCheck implements ChecksumInterface {
    protected static final String TAG = tag(SERVICE.getName());

    private String mAlgorithm = "SHA-256";


    public IntegrityCheck() {
    }

    @VisibleForTesting
    IntegrityCheck(String algo) {
        mAlgorithm = algo;
    }

    /**
     * @return base64 encoded hash string
     * @fn generateHash
     * @brief Generate a sha256 hash of string object
     * @param[in] input - String
     */
    public String generateHash(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        try {
            var digest = MessageDigest.getInstance(mAlgorithm);
            byte[] bArray = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(bArray);
        } catch (NoSuchAlgorithmException e) {
            logStatus(TAG, "generateHash", toStatus(e), Key.MESSAGE, "Failed");
        }
        return "";
    }

    /**
     * @return true if the hash matches, false otherwise
     * @fn verifyHash
     * @brief Calculate the hash and compare it to the persisted hash to confirm
     * the integrity of the data
     * @param[in] input - String
     */
    public boolean verifyHash(String data, String hash) {
        String signature = generateHash(data);
        return signature.equals(hash);
    }
}
