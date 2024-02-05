/*
* Copyright (C) GM Global Technology Operations LLC 2024
* All Rights Reserved.
* GM Confidential Restricted.
*/

package org.eclipse.uprotocol.example.uphelloworldsrv

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
import android.graphics.Color
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import com.google.type.TimeOfDay
import org.covesa.uservice.example.hello_world.v1.HelloRequest
import org.covesa.uservice.example.hello_world.v1.HelloResponse
import org.covesa.uservice.example.hello_world.v1.Timer
import org.eclipse.uprotocol.UPClient
import org.eclipse.uprotocol.common.util.UStatusUtils
import org.eclipse.uprotocol.common.util.UStatusUtils.isOk
import org.eclipse.uprotocol.common.util.log.Formatter.stringify
import org.eclipse.uprotocol.core.usubscription.v3.CreateTopicRequest
import org.eclipse.uprotocol.core.usubscription.v3.USubscription
import org.eclipse.uprotocol.rpc.URpcListener
import org.eclipse.uprotocol.transport.builder.UAttributesBuilder
import org.eclipse.uprotocol.transport.builder.UPayloadBuilder.packToAny
import org.eclipse.uprotocol.transport.builder.UPayloadBuilder.unpack
import org.eclipse.uprotocol.uri.factory.UResourceBuilder
import org.eclipse.uprotocol.v1.UEntity
import org.eclipse.uprotocol.v1.UMessage
import org.eclipse.uprotocol.v1.UPayload
import org.eclipse.uprotocol.v1.UPriority
import org.eclipse.uprotocol.v1.UResource
import org.eclipse.uprotocol.v1.UStatus
import org.eclipse.uprotocol.v1.UUri
import java.util.Calendar
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.jvm.optionals.getOrNull

class HelloWorldService : LifecycleService() {
    private val tag = "HelloUserService"

    private lateinit var mUPClient: UPClient

    private val rpcEventListener = RPCEventListener()

    private var uPFirstTimeConnection = true

    private val mExecutor = Executors.newScheduledThreadPool(0)

    private val createdTopicSet = ConcurrentHashMap.newKeySet<UUri>()

    override fun onCreate() {
        super.onCreate()
        startForeground()
        Log.i(tag, "Starting UserService Service")
        initUPClient()
        beginPublishTimeOfDay()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(tag, "Service UserService onDestroy")
        shutdownUPClient()
        mExecutor.shutdownNow()
    }

    private fun startForeground() {
        val channelID = "com.gm.ultifi.helloultifiusersrv.HelloUltifiUserService"
        val channelName = "HelloUser Service"
        val chan =
            NotificationChannel(
                channelID,
                channelName,
                NotificationManager.IMPORTANCE_NONE
            )
        chan.lightColor = Color.BLUE
        chan.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(chan)
        val notificationBuilder = NotificationCompat.Builder(this, channelID)
        val notification: Notification =
            notificationBuilder
                .setOngoing(true)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("uService is running in background")
                .setPriority(NotificationManager.IMPORTANCE_MIN)
                .setCategory(Notification.CATEGORY_SERVICE)
                .build()
        ServiceCompat.startForeground(this, SERVICE_ID, notification, FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    private fun beginPublishTimeOfDay() {
        mExecutor.scheduleAtFixedRate({
            publish(UURI_SECOND, packToAny(buildTimeOfDay()))
            if (Calendar.getInstance().get(Calendar.SECOND) == 0) {
                publish(UURI_MINUTE, packToAny(buildTimeOfDay()))
            }
        }, 0, 1, TimeUnit.SECONDS)
    }

    private fun buildTimeOfDay(): Timer {
        val calender = Calendar.getInstance()
        return Timer.newBuilder().setTime(
            TimeOfDay.newBuilder()
                .setHours(calender.get(Calendar.HOUR_OF_DAY))
                .setMinutes(calender.get(Calendar.MINUTE))
                .setSeconds(calender.get(Calendar.SECOND)).build()
        ).build()
    }

    /**
     * Create the instance of UPClient and connect,
     * Register RPC Methods and create Topic when connection is established
     */
    private fun initUPClient() {
        val requestHandlerThread = HandlerThread("HelloUserThread")
        requestHandlerThread.start()
        mUPClient =
            UPClient.create(this, Handler(requestHandlerThread.looper)) { _, isReady ->
                Log.i(tag, "Connect to UPClient, isReady: $isReady")
                if (uPFirstTimeConnection && isReady) {
                    registerRPCMethods(RPC_SAY_HELLO)
                    createTopic(UURI_MINUTE)
                    createTopic(UURI_SECOND)
                    uPFirstTimeConnection = false
                }
                // reset uPFirstTimeConnection flag if UPClient is reconnected
                if (!isReady) {
                    uPFirstTimeConnection = true
                }
            }
        mExecutor.execute {
            mUPClient.connect().exceptionally(UStatusUtils::toStatus)
                .thenAccept { status ->
                    status.foldLog("UPClient Connection")
                }
        }
    }

    private fun shutdownUPClient() {
        unregisterRPCMethods(RPC_SAY_HELLO)
        mUPClient.disconnect()
    }

    /**
     * Register the RPC methods so methods can be called by Apps
     */
    @Suppress("SameParameterValue")
    private fun registerRPCMethods(vararg methods: UUri) {
        mExecutor.execute {
            methods.forEach { method ->
                mUPClient.registerRpcListener(method, rpcEventListener).foldLog("Register ${stringify(method)}")
            }
        }
    }

    @Suppress("SameParameterValue")
    private fun unregisterRPCMethods(vararg methods: UUri) {
        mExecutor.execute {
            methods.forEach { method ->
                mUPClient.unregisterRpcListener(method, rpcEventListener).foldLog("Unregister ${stringify(method)}")
            }
        }
    }

    /**
     * Publish UMessage to uBus, Only when UPClient is connected and topic has been created.
     */
    private fun publish(uri: UUri, payload: UPayload) {
        if (!mUPClient.isConnected || !createdTopicSet.contains(uri)) return
        val message = UMessage.newBuilder().setSource(
            uri
        ).setPayload(payload).setAttributes(UAttributesBuilder.publish(UPriority.UPRIORITY_CS0).build()).build()
        mUPClient.send(message).foldLog("Publish ${stringify(uri)}")
    }

    /**
     * Topic must be created before publishing
     */
    private fun createTopic(uri: UUri) {
        mExecutor.execute {
            val createTopicRequest = CreateTopicRequest.newBuilder().setTopic(uri).build()
            USubscription.newStub(mUPClient).createTopic(createTopicRequest)
                .exceptionally { throwable ->
                    UStatusUtils.toStatus(throwable)
                }.thenAccept { status ->
                    status.foldLog("Create Topic ${stringify(uri)}") {
                        createdTopicSet.add(uri)
                    }
                }
        }
    }

    /**
     * Listener when RPC methods are called by App
     */
    inner class RPCEventListener : URpcListener {
        override fun onReceive(message: UMessage, responseFuture: CompletableFuture<UPayload>) {
            when (message.attributes.sink) {
                RPC_SAY_HELLO -> processHelloRequest(message, responseFuture)
                // Add More RPC Calls here if needed
                else -> {
                    // Do nothing
                }
            }
        }

        /**
         * Handler for the SayHello! RPC method
         */
        private fun processHelloRequest(
            message: UMessage,
            completableFuture: CompletableFuture<UPayload>
        ) {
            val request = unpack(message.payload, HelloRequest::class.java).getOrNull()
            val responseMessage =
                if (request == null) {
                    "Error Hello Request"
                } else if (request.name == "") {
                    "Hello World!"
                } else {
                    "Hello ${request.name}!"
                }
            val helloResponse = HelloResponse.newBuilder().setMessage(responseMessage).build()
            completableFuture.complete(packToAny(helloResponse))
        }
    }

    private fun UStatus.foldLog(
        logDescription: String = "",
        onError: (status: UStatus) -> Unit = {},
        onSuccess: (status: UStatus) -> Unit = {}
    ) {
        if (isOk(this)) {
            logDescription.takeIf { it.isNotEmpty() }?.let {
                Log.i(tag, "$logDescription: ${stringify(this)}")
            }
            onSuccess(this)
        } else {
            logDescription.takeIf { it.isNotEmpty() }?.let {
                Log.e(tag, "$logDescription: ${stringify(this)}")
            }
            onError(this)
        }
    }

    companion object {
        private const val SERVICE_ID = 3

        private val SERVICE_UENTITY = UEntity.newBuilder().setName("example.hello_world")
            .setVersionMajor(1).build()

        private val RPC_SAY_HELLO: UUri = UUri.newBuilder().setEntity(SERVICE_UENTITY)
            .setResource(UResourceBuilder.forRpcRequest("SayHello"))
            .build()

        private val UURI_MINUTE: UUri = UUri.newBuilder().setEntity(SERVICE_UENTITY)
            .setResource(
                UResource.newBuilder().setName("one_minute")
                    .setMessage("Timer").build()
            ).build()

        private val UURI_SECOND: UUri = UUri.newBuilder().setEntity(SERVICE_UENTITY)
            .setResource(
                UResource.newBuilder().setName("one_second").setMessage("Timer").build()
            ).build()
    }
}
