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
interface TopicsDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun addTopic(event: TopicsRecord): Long

    @Query("DELETE FROM topics WHERE topic = :topic ")
    fun deleteTopic(topic: String): Int

    @Query("SELECT COUNT(*) FROM topics")
    fun getActiveTopicsCount(): Int

    @Query("SELECT EXISTS(SELECT * FROM topics WHERE topic = :topic)")
    fun isTopicCreated(topic: String): Boolean

    @Query("SELECT publisher FROM topics WHERE topic = :topic")
    fun getPublisher(topic: String): String

    @Query("UPDATE topics SET isRegisterForNotification = :isRegister WHERE " +
           "topic = :topic AND isRegisterForNotification != :isRegister")
    fun updateTopicTable(topic: String, isRegister: Boolean)

    @Query("SELECT isRegisterForNotification FROM topics WHERE topic = :topic")
    fun isRegisteredForNotification(topic: String): Boolean

    @Query("SELECT publisher FROM topics WHERE topic = :topic AND isRegisterForNotification = 1")
    fun getPublisherIfRegistered(topic: String): String
}
