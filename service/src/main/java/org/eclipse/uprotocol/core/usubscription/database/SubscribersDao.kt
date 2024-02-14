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
interface SubscribersDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun addSubscriber(event: SubscribersRecord): Long

    @Query("DELETE FROM subscribers WHERE topicUri = :topic")
    fun deleteTopic(topic: String): Int

    @Query("DELETE FROM subscribers WHERE topicUri = :topic AND subscriberUri = :subscriber")
    fun deleteSubscriber(topic: String, subscriber: String): Int

    @Query("SELECT subscriberUri FROM subscribers WHERE topicUri = :topic")
    fun getSubscribers(topic: String): List<String>

    @Query("SELECT * FROM subscribers")
    fun getAllSubscriberRecords(): List<SubscribersRecord>

    @Query("SELECT * FROM subscribers WHERE topicUri = :topic AND subscriberUri = :subscriber")
    fun getSubscriber(topic: String, subscriber: String): SubscribersRecord

    @Query("SELECT * FROM subscribers WHERE topicUri = :topic ORDER BY id LIMIT 1")
    fun getFirstSubscriberForTopic(topic: String): SubscribersRecord

    @Query("SELECT * FROM subscribers WHERE topicUri = :topic")
    fun getSubscriptionsByTopic(topic: String): List<SubscribersRecord>

    @Query("SELECT * FROM subscribers WHERE subscriberUri = :subscriber")
    fun getSubscriptionsBySubscriber(subscriber: String): List<SubscribersRecord>
}
