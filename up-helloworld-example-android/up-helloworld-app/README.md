# "Hello World!" uApplication

* [How to write an UProtocol Application](#how-to-write-an-uprotocol-application-uapp)
  * [Android Manifest](#androidmanifest)
  * [Gradle](#gradle)
  * [Connecting To uBus](#connecting-to-ubus)
  * [Remote Procedure Calls](#remote-procedure-calls-rpc)
  * [Subscribing to Topics](#subscribing-to-topics)
  * [Unsubscribing from Topics](#unsubscribing-from-topics)
* [Build](#build)
* [Test and Reports](#tests-and-reports)
* [Running](#running)

## Synopsis

The purpose of the Hello World App is to demonstrate the basic use of connecting to UProtocol uBus, subscribing and unsubscribing to topics, and making RPC calls. This app is intended to be used in conjunction with the Hello World Service which publishes data to those topics and responds to those RPC calls.

## How to Write an UProtocol Application (uApp)

Before starting, create an empty Android application project.


### AndroidManifest

The 'AndroidManifest' uses the permission `uprotocol.permission.ACCESS_UBUS` so that the app can connect to UProtocol uBus.

```
    <uses-permission android:name="uprotocol.permission.ACCESS_UBUS" />
```

The meta-data for 'uprotocol.entity.name' and 'uprotocol.entity.version' is also required to use UPClient.

```
    <meta-data
        android:name="uprotocol.entity.name"
        android:value="example.hello_world_app" />
    <meta-data
        android:name="uprotocol.entity.version"
        android:value="1" />
```


### Gradle

At the service level build.gradle, the following libraries are needed for utilizing uProtocol:
`org.eclipse.uprotocol:up-java:x.x.x` is needed for accessing 'UPClient' and UP related classes used to subscribe and unsubscribe to topics.
`org.eclipse.uprotocol:up-client-android-java:x.x.x` is needed for accessing the 'UPClient' class.
`com.google.protobuf:protobuf-java:x.x.x` is needed for generate proto java classes.
`com.google.api.grpc:proto-google-common-protos:x.x.x` is needed as a dependency for up-client-android-java.

### Connecting to uBus

UPClient connection is established by the `initUPClient()` function, called from the 'MainActivity' `onCreate()` method.

```
    override fun onCreate(savedInstanceState: Bundle?) {
        ...
        initUPClient()
        ...
    }
```

To connect to UPClient, first use the `UPClient.create()` method from the 'UPClient' class to generate an `mUPClient` object. Call the `connect()` method on this, to establish the corresponding connection.

```kotlin
        mUPClient =
            UPClient.create(this, Handler(requestHandlerThread.looper)) { _, isReady ->
                Log.i(tag, "Connect to UPClient, isReady: $isReady")
            }
        mExecutor.execute {
            mUPClient.connect().exceptionally(UStatusUtils::toStatus)
            .thenAccept { status ->
            status.foldLog("UPClient Connected") 
        }
}
```

### Remote Procedure Calls (RPC)

Any tap onto UI's **<mark>Say Hello!</mark>** button triggers an RPC call via the `triggerSayHelloRPC()` function. 
This function will manage its execution in a different thread, will build the RPC call and awaits for an appropriate response. That particular response will be displayed in the UI text field next to the ***<mark>RPC Response:</mark> *** box. 

This example uses the `org.covesa.uservice.example.hello_world.v1.HelloWorld` service from a proto file and the `sayHello()` method made available by that service. 

To accomplish the RPC call, first build a `HelloWorld.newStub` of the 'HelloWorld'_service generated class, and then call the `sayHello(...)` method on it. This procedure executes the RPC and awaits for the 'CompletableFuture' response.
Some RPC methods require data to be passed as a parameter. In this example, building a 'HelloRequest' proto message includes the string (name target) the user entered in the UI field, and passes it onto `sayHello()`.

```kotlin
    mExecutor.execute {
        val name = binding.editTextName.textoString()
        HelloWorld.newStub(mUPClient).sayHello(
            HelloRequest.newBuilder()
                .setName(name)
                .build()
        ).thenAccept { response ->
            Log.i(tag, "rpc response $response")
            runOnUiThread {
                binding.textViewResponse.text response = message
            }
        }
    }
```

### Subscribing to Topics

When the user taps onto either the **<mark>SUBSCRIBE SEC</mark>** or the **<mark>SUBSCRIBE MIN</mark>** button from the apps's UI, a subscription to the UProtocol topic for *one second* respectively *one minute* is being triggered. Each topic has an UProtocol URI.

See *TOPIC_MINUTE* and *TOPIC_SECOND* in the 'MainActivity' `companion object {...}` for examples. The `subscribeToTopic()` method handles the subscription.

To subscribe, first build a ***SubscriptionRequest*** object. This requires setting a *topic* with the correct URI that you want to subscribe to, and setting the *subscriberInfo*, which is the URI of the client holding the UPClient instance.

```kotlin
    private inline fun <reified T : Message> subscribeToUUri(
      uri: UUri,
      crossinline action: (message: T) -> Unit,
    ) {
        mExecutor.execute {
            val request =
                SubscriptionRequest.newBuilder()
                    .setTopic(uri)
                    .setSubscriber(SubscriberInfo.newBuilder().setUri(mUPClient.uri).build())
                    .build()
            
        }
        ...
    }
```

Create then a `USubscription.newStub(mUPClient)` object and call `subscribe(...)`, passing in the 'SubscriptionRequest' object that you just built.

```kotlin
    private inline fun <reified T : Message> subscribeToUUri(
        uri: UUri, 
        crossinline action: (message: T) -> Unit,
    ) {
        mExecutor.execute {
            val request =
                SubscriptionRequest.newBuilder()
                .setTopic(uri)
                .setSubscriber(SubscriberInfo.newBuilder().setUri(mUPClient.uri).build())
                .build()
            USubscription.newStub(mUPClient).subscribe(request)
            .whenComplete {
                ...
            }
        }
        ...
    }
```

Check the result, and if `subscribe(request)` is successful, setup the `UListener` for the topic. Changes to the topic will be received as a 'UPayload' via the 'EventListener'.

```kotlin
    USubscription.newStub(mUPClient).subscribe(request)
        .whenComplete { response, exception ->
            val status = response.status
            if (exception != null) {
                Log.e(tag, "Failed to subscribe: ${UStatusUtils.toStatus(exception)}")
            } else if (response.status.code != UCode.OK) {
                Log.e(tag, "Failed to subscribe: $status")
            } else {
                Log.i(tag, "Subscribed: $status")
            val eventListener = UListener { _, payload, _ ->
                UPayloadBuilder.unpack(payload, T::class.java)
                    .getOrNull()?.let { message ->
                        action(message)
                    }
                }
            mUPClient.registerListener(uri, eventListener).foldLog(
                "Register Listener ${Formatter.stringify(uri)}"
            ) {
                eventListenerMap[uri] = eventListener
            }
        }
    }
```

For this particular example project, the message is a `Timer`. Every second/minute the app will receive an event with a message reflecting the current time and that will be displayed in the app's UI fields, called **<mark>textView_sec_display</mark>** respectivelly **<mark>textView_min_display</mark>**.

### Unsubscribing from Topics

When the user hits either the **<mark>UNSUBSCRIBE SEC</mark>** or the **<mark>UNSUBSCRIBE MIN</mark>** button within apps's UI, the UProtocol topic will be unsubscribed by the `unsubscribeToTopic(topic)` function. Then the app is no longer going to receive updates on that particular topic, hence no longer re-renders of the app's sec/min time text-field(s).

To unsubscribe, first build an ***UnsubscribeRequest*** object. This requires setting a *topic* with the correct URI that you want to (un)subscribe from, and setting the *subscriberInfo*, procedure similar to the 'SubscriptionRequest' from the section above.

```kotlin
    private fun unsubscribeToUUri(uri: UUri) {
        mExecutor.execute {
            val request =
                UnsubscribeRequest.newBuilder()
                    .setTopic(uri)
                    .setSubscriber(SubscriberInfo.newBuilder().setUri(mUPClient.uri).build())
                    .build()
              ...
        }
```

Create then a `USubscription.newStub(mUPClient)` new stub object and call `unsubscribe()`, passing in the 'UnsubscribeRequest' object that you just built.

```kotlin
    private fun unsubscribeToUUri(uri: UUri) {
        mExecutor.execute {
            val request =
                UnsubscribeRequest.newBuilder()
                .setTopic(uri)
                .setSubscriber(SubscriberInfo.newBuilder().setUri(mUPClient.uri).build())
                .build()
            USubscription.newStub(mUPClient).unsubscribe(request).exceptionally(
                UStatusUtils::toStatus,
            ).thenAccept { status ->
                status.foldLog("Unsubscribed ${Formatter.stringify(uri)}")
            }
        ...
      }
```

Check the result to see if the `unsubscribe(request)` was successful.

## Build

Builds can be performed in Android Studio or via the command line.

In Android Studio, build by clicking the green hammer toolbar icon, or navigate inside the "Build" menu bar and select "Make Project Ctrl+F9" option.
To build from the command line, run `./gradlew build`.

To clean the build output, go to "Build" menu bar and select "Clean Project" option in Android Studio or run `./gradlew clean` from inside the "Terminal" tab.

## Tests and Reports

There are no unit tests nor instrumentation tests or any other kind or code-quality run-time generated reports within this project.
The main purpose of implementing and publishing the **upHelloWorld** project was to ***explain the uBus connectivity***, which is beyond teaching Android development fundamentals.

## Running

The application itself can be (uploaded onto the physical/virtual target and) launched from inside Android Studio IDE by clicking the "Run 'app' Shift+F10" button in the top right toolbar, or from the "Run" menu bar. User must select the correct run configuration in the dropdown to the left of the "Run... Alt+Shift+F10" option.


In addition, the emulator shall be started and fully booted before attempting to run the upHelloWorld application.
