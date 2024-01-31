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

package org.eclipse.uprotocol.core.usubscription.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SubscriptionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun addSubscription(subscription: SubscriptionsRecord): Long

    @Query("DELETE FROM subscriptions WHERE topic = :topic")
    fun deleteTopic(topic: String)

    @Query("SELECT COUNT(*) FROM subscriptions WHERE state = -1")
    fun getDeprecatedTopicsCount(): Int

    @Query("SELECT state FROM subscriptions WHERE topic = :topic")
    fun getSubscriptionState(topic: String): Int

    @Query("UPDATE subscriptions SET state = :state WHERE topic = :topic")
    fun updateState(topic: String, state: Int)

    @Query("SELECT topic FROM subscriptions WHERE requestId = :requestId")
    fun getTopic(requestId: String): String

    @Query("SELECT topic FROM subscriptions WHERE state = 1 or state = 2")
    fun getSubscribedTopics(): List<String>

    @Query("SELECT * FROM subscriptions WHERE state = 1 or state = 3")
    fun getPendingTopics(): List<SubscriptionsRecord>
}
