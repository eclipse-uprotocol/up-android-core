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

package org.eclipse.uprotocol.example.uphelloworldapp

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.google.protobuf.Message
import org.covesa.uservice.example.hello_world.v1.HelloRequest
import org.covesa.uservice.example.hello_world.v1.HelloResponse
import org.covesa.uservice.example.hello_world.v1.Timer
import org.eclipse.uprotocol.UPClient
import org.eclipse.uprotocol.common.util.UStatusUtils
import org.eclipse.uprotocol.common.util.log.Formatter
import org.eclipse.uprotocol.core.usubscription.v3.SubscriberInfo
import org.eclipse.uprotocol.core.usubscription.v3.SubscriptionRequest
import org.eclipse.uprotocol.core.usubscription.v3.USubscription
import org.eclipse.uprotocol.core.usubscription.v3.UnsubscribeRequest
import org.eclipse.uprotocol.example.uphelloworldapp.databinding.ActivityMainBinding
import org.eclipse.uprotocol.rpc.CallOptions
import org.eclipse.uprotocol.rpc.RpcMapper
import org.eclipse.uprotocol.transport.UListener
import org.eclipse.uprotocol.transport.builder.UPayloadBuilder
import org.eclipse.uprotocol.uri.factory.UResourceBuilder
import org.eclipse.uprotocol.v1.UCode
import org.eclipse.uprotocol.v1.UEntity
import org.eclipse.uprotocol.v1.UResource
import org.eclipse.uprotocol.v1.UStatus
import org.eclipse.uprotocol.v1.UUri
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.jvm.optionals.getOrNull

class MainActivity : AppCompatActivity() {
    private val tag = "HelloWorldApp"

    private lateinit var binding: ActivityMainBinding

    private lateinit var mUPClient: UPClient

    private val mExecutor = Executors.newCachedThreadPool()

    private val eventListenerMap = ConcurrentHashMap<UUri, UListener>()

    /**
     * jv#0 Create an android application with a pre-required UI
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)
        initUPClient()
        addButtonListener()
    }

    override fun onDestroy() {
        super.onDestroy()
        mExecutor.shutdownNow()
    }

    /**
     * jv#3 Register Event Listener
     *   > Add On Click Listener for Buttons (define handlers for the given buttons/on-click events)
     */
    @SuppressLint("SetTextI18n")
    private fun addButtonListener() {
        with(binding) {
            buttonSayHello.setOnClickListener {
                triggerSayHelloRPC()
            }

            buttonSubSec.setOnClickListener {
                subscribeToUUri<Timer>(UURI_SECOND) { message ->
                    runOnUiThread {
                        val time = message.time
                        textViewSecDisplay.text =
                            getString(R.string.time_display, time.hours, time.minutes, time.seconds)
                    }
                }
            }

            buttonUnsubSec.setOnClickListener {
                unsubscribeToUUri(UURI_SECOND)
                textViewSecDisplay.text = "hh:mm:ss"
            }

            buttonSubMin.setOnClickListener {
                subscribeToUUri<Timer>(UURI_MINUTE) { message ->
                    runOnUiThread {
                        val time = message.time
                        textViewMinDisplay.text =
                            getString(R.string.time_display, time.hours, time.minutes, time.seconds)
                    }
                }
            }

            buttonUnsubMin.setOnClickListener {
                unsubscribeToUUri(UURI_MINUTE)
                textViewMinDisplay.text = "hh:mm:ss"
            }
        }
    }

    /**
     * jv#1 Create new instance of UPClient and connect to it
     */
    private fun initUPClient() {
        val requestHandlerThread = HandlerThread("HelloWorldAppThread")
        requestHandlerThread.start()
        mUPClient = UPClient.create(this, Handler(requestHandlerThread.looper)) { _, isReady ->
            Log.i(tag, "Connect to UPClient, isReady: $isReady")
        }
        mExecutor.execute {
            mUPClient.connect().exceptionally(UStatusUtils::toStatus)
                .thenAccept { status ->
                    status.foldLog("UPClient Connected")
                }
        }
    }

    /**
     * jv#2a Subscribe to "given" UURIs
     */
    private inline fun <reified T : Message> subscribeToUUri(
        uri: UUri,
        crossinline action: (message: T) -> Unit
    ) {
        mExecutor.execute {
            val request = SubscriptionRequest.newBuilder().setTopic(uri)
                .setSubscriber(SubscriberInfo.newBuilder().setUri(mUPClient.uri).build())
                .build()
            USubscription.newStub(mUPClient).subscribe(request).whenComplete { response, exception ->
                val status = response.status
                if (exception != null) {
                    Log.e(tag, "Failed to subscribe: ${UStatusUtils.toStatus(exception)}")
                } else if (response.status.code != UCode.OK) {
                    Log.e(tag, "Failed to subscribe: $status")
                } else {
                    Log.i(tag, "Subscribed: $status")
                    val eventListener = UListener { _, payload, _ ->
                        UPayloadBuilder.unpack(payload, T::class.java).getOrNull()?.let { message ->
                            action(message)
                        }
                    }
                    mUPClient.registerListener(uri, eventListener).foldLog(
                        "Register Listener ${ Formatter.stringify(uri) }"
                    ) {
                        eventListenerMap[uri] = eventListener
                    }
                }
            }
        }
    }

    /**
     * jv#2b Unsubscribe from "given" UURIs
     */
    private fun unsubscribeToUUri(uri: UUri) {
        mExecutor.execute {
            val request = UnsubscribeRequest.newBuilder()
                .setTopic(uri)
                .setSubscriber(SubscriberInfo.newBuilder().setUri(mUPClient.uri).build())
                .build()
            USubscription.newStub(mUPClient).unsubscribe(request).exceptionally(UStatusUtils::toStatus)
                .thenAccept { status ->
                    status.foldLog("Unsubscribed ${Formatter.stringify(uri)}")
                }
            eventListenerMap[uri]?.let {
                mUPClient.unregisterListener(uri, it).foldLog(
                    "Unregister Listener ${
                        Formatter.stringify(
                            uri
                        )
                    }"
                ) {
                    eventListenerMap.remove(uri)
                }
            }
        }
    }

    /**
     * jv#4 Invoke RPC method when user make RPC calls
     */
    private fun triggerSayHelloRPC() {
        // execute in a different thread
        mExecutor.execute {
            mUPClient.invokeMethod(
                RPC_SAY_HELLO,
                UPayloadBuilder.packToAny(
                    HelloRequest.newBuilder().setName(binding.editTextName.text.toString()).build()
                ),
                CallOptions.DEFAULT
            ).let {
                RpcMapper.mapResponse(it, HelloResponse::class.java).thenAccept { response ->
                    Log.i(tag, "rpc response $response")
                    runOnUiThread {
                        binding.textViewResponse.text = response.message
                    }
                }
            }
        }
    }

    private fun UStatus.foldLog(
        logDescription: String = "",
        onError: (status: UStatus) -> Unit = {},
        onSuccess: (status: UStatus) -> Unit = {}
    ) {
        if (UStatusUtils.isOk(this)) {
            logDescription.takeIf { it.isNotEmpty() }?.let {
                Log.i(tag, "$logDescription: ${Formatter.stringify(this)}")
            }
            onSuccess(this)
        } else {
            logDescription.takeIf { it.isNotEmpty() }?.let {
                Log.e(tag, "$logDescription: ${Formatter.stringify(this)}")
            }
            onError(this)
        }
    }

    companion object {
        private val HELLO_USER_SERVICE_UENTITY = UEntity.newBuilder().setName("example.hello_world").setVersionMajor(1).build()
        val RPC_SAY_HELLO: UUri = UUri.newBuilder().setEntity(HELLO_USER_SERVICE_UENTITY)
            .setResource(UResourceBuilder.forRpcRequest("SayHello"))
            .build()
        val UURI_MINUTE: UUri = UUri.newBuilder().setEntity(HELLO_USER_SERVICE_UENTITY)
            .setResource(UResource.newBuilder().setName("one_minute").setMessage("Timer").build()).build()

        val UURI_SECOND: UUri = UUri.newBuilder().setEntity(HELLO_USER_SERVICE_UENTITY)
            .setResource(UResource.newBuilder().setName("one_second").setMessage("Timer").build()).build()
    }
}
