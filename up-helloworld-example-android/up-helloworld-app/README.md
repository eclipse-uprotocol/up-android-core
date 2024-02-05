# "Hello Ultifi User!" uApplication

* [How to write an Ultifi Application](#how-to-write)
  * [Android Manifest](#android-manifest)
  * [Gradle](#gradle)
  * [Connecting To UltifiLink](#connecting-to-ultifi)
  * [Remote Procedure Calls](#rpc)
  * [Subscribing to Topics](#subscribing)
  * [Unsubscribing from Topics](#unsubscribing)
* [Build](#build)
* [Test and Reports](#test-and-reports)
* [Running](#running)

## Synopsis

The purpose of the Hello Ultifi App is to demonstrate the basic use of connecting to UltifiLink, subscribing and unsubscribing to topics, and making RPC calls. This app is intended to be used in conjunction with the Hello Ultifi Service which publishes data to those topics and responds to those RPC calls.

## How to Write an Ultifi Application (uApp)

Before starting, create an empty Android application project.


### AndroidManifest

The 'AndroidManifest' uses the permission `uprotocol.permission.ACCESS_UBUS` so that the app can connect to UltifiLink.

```
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="ultifi.permission.ACCESS_UBUS" />
```

The meta-data for 'uprotocol.entity.name' and 'uprotocol.entity.version' is also required to use UltifiLink.

```
    <meta-data
        android:name="uprotocol.entity.name"
        android:value="gm.vehicle.hellouser_app" />
    <meta-data
        android:name="uprotocol.entity.version"
        android:value="1" />
```


### Gradle

At the project level ***apps-HelloUserApp/blob/main/build.gradle***, the 'https://info-artifactory.gm.com/artifactory/info-maven' maven URL allows us to import the Ultifi libraries.

```
    repositories {
        maven {
            url 'https://info-artifactory.gm.com/artifactory/info-maven'
        }
        google()
        mavenCentral()
    }
```

At the application level ***apps-HelloUserApp/blob/main/app/build.gradle***, the following libraries are needed for utilizing Ultifi:

* `com.gm:ultifi-proto-java:x.x.x` contains the Ultifi protos used for packing and unpacking data.
* `com.gm:ultifi-proto-rpc-java:x.x.x` contains the methods to execute RPC calls for a given proto.
* `com.gm.ultifi.sdk.uprotocol:core:x.x.x` is needed for accessing 'UCloudEvent' and Ultifi URI related classes used to subscribe and unsubscribe to topics.
* `com.ultifi:ultifi-core-vehicle-api:x.x.x` is needed for accessing the 'UltifiLink' class.
* `com.ultifi:ultifi-utils:x.x.x` contains Ultifi utils, such as StatusUtils
    *note*! `StatusUtils` is used to map the results of 'CompletableFuture<Status>', which is used for operations exemplified in this case case by UltifiLink connection and RPC responses.

```
    dependencies {
        ...
        // Ultifi
        implementation "com.gm:ultifi-proto-java:0.0.53"
        implementation "com.gm:ultifi-proto-rpc-java:0.0.53"
        implementation "com.gm.ultifi.sdk.uprotocol:core:0.0.20"
        implementation "com.ultifi:ultifi-core-vehicle-api:2.1.0"
        implementation "com.ultifi:ultifi-utils:1.0.0"
        ...
    }
```

### Connecting to UltifiLink

UltifiLink connection is established by the `initUltifi()` function, called from the 'MainActivity' `onCreate()` method.

```
    override fun onCreate(savedInstanceState: Bundle?) {
        ...
        initUltifi()
        ...
    }
```

To connect to UltifiLink, first use the `UltifiLink.create()` method from the 'UltifiLink' class to generate an `mUltifiLink` object. Call the `connect()` method on this, to establish the corresponding connection.

```kotlin
    mUltifiLink =
        UltifiLink.create(this, Handler(requestHandlerThread.looper)) { _, isReady ->
          Log.i(tag, "Connect to Ultifi Link, isReady: $isReady")
        }
    mExecutor.execute {
      mUltifiLink.connect().exceptionally(StatusUtils::throwableToStatus)
        .thenAccept { status ->
            if (isOk(status)) {
              Log.i(tag, "Ultifilink Connected!")
            } else {
              Log.e(tag, "Ultifilink Failed to connect: " + toShortString(status))
            }
        }
    }
```

### Remote Procedure Calls (RPC)

Any tap onto UI's **<mark>Say Hello!</mark>** button triggers an RPC call via the `triggerSayHelloRPC()` function. 
This function will manage its execution in a different thread, will build the RPC call and awaits for an appropriate response. That particular response will be displayed in the UI text field next to the ***<mark>RPC Response:</mark> *** box. 
![HelloUltifiUser](.\image\skApp_RPC.jpg)
This example uses the `com.ultifi.example.hello_world.v1.HelloWorld` service from a proto file and the `sayHello()` method made available by that service. 

To accomplish the RPC call, first build a `HelloWorld.newStub` of the 'HelloWorld'_service generated class, and then call the `sayHello(...)` method on it. This procedure executes the RPC and awaits for the 'CompletableFuture' response.
Some RPC methods require data to be passed as a parameter. In this example, building a 'HelloRequest' proto message includes the string (name target) the user entered in the UI field, and passes it onto `sayHello()`.

```kotlin
    mExecutor.execute {
        val name = binding.editTextName.textoString()
        HelloWorld.newStub(mUltifiLink).sayHello(
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

When the user taps onto either the **<mark>SUBSCRIBE SEC</mark>** or the **<mark>SUBSCRIBE MIN</mark>** button from the apps's UI, a subscription to the Ultifi topic for *one second* respectively *one minute* is being triggered. Each topic has an Ultifi URI.

![HelloUltifiUser](.\image\skApp_SUBs.jpg)
See *TOPIC_MINUTE* and *TOPIC_SECOND* in the 'MainActivity' `companion object {...}` for examples. The `subscribeToTopic()` method handles the subscription.

To subscribe, first build a ***SubscriptionRequest*** object. This requires setting a *topic* with the correct URI that you want to subscribe to, and setting the *subscriberInfo*, which is the URI of the client holding the UltifiLink instance.

```kotlin
    private inline fun <reified T : Message> subscribeToTopic(
        topic: UltifiUri,
        crossinline action: (message: T) -> Unit
    ) {
        mExecutor.execute {
            val request = SubscriptionRequest.newBuilder()
                .setTopic(Topic.newBuilder().setUri(topic.uProtocolUri()).build())
                .setSubscriber(SubscriberInfo.newBuilder().setUri(mUltifiLink.clientUri).build())
                .build()
            ...
        }
    }
```

Create then a `USubscription.newStub(mUltifiLink)` object and call `subscribe(...)`, passing in the 'SubscriptionRequest' object that you just built.

```kotlin
    private inline fun <reified T : Message> subscribeToTopic(
        topic: UltifiUri,
        crossinline action: (message: T) -> Unit
    ) {
        mExecutor.execute {
            val request = SubscriptionRequest.newBuilder()
                .setTopic(Topic.newBuilder().setUri(topic.uProtocolUri()).build())
                .setSubscriber(SubscriberInfo.newBuilder().setUri(mUltifiLink.clientUri).build())
                .build()
            USubscription.newStub(mUltifiLink).subscribe(request)
                .whenComplete { ... }
        }
    }
```

Check the result, and if `subscribe(request)` is successful, setup the `UltifiLink.EventListener` for the topic. Changes to the topic will be received as a 'CloudEvent' via the 'EventListener'.

```kotlin
            USubscription.newStub(mUltifiLink).subscribe(request)
                .whenComplete { response, exception ->
                    val status = response.status
                    if (...) {
                        Log.i(tag, "Subscribed: $status")
                        val eventListener = UltifiLink.EventListener { event ->
                            unpackOrNull<T>(getPayload(event))?.let {
                                action(it)
                            }
                        }
                        val regStatus = mUltifiLink.registerEventListener(topic, eventListener)
                        if (isOk(regStatus)) {
                            Log.i(tag, "Register Event Listener: " + toShortString(regStatus))
                        } else { ... }
                    }
                }
```

You can specify what you want to do with the incoming events. Use the `getPayload(event)` function from the 'UCloudEvent' class to convert the 'CloudEvent' to a 'com.google.protobuf.Any'. Then use the `unPackOrNull(...)` to unpack the 'com.google.protobuf.Any' to a message.

```kotlin
    private inline fun <reified T : Message> unpackOrNull(data: Any?): T? {
        if (data != null) {
            try {
                return data.unpack(T::class.java)
            } catch (e: InvalidProtocolBufferException) {
                Log.e(tag, "Exception during unpack: $e")
            }
        }
        return null
    }
```

For this particular example project, the message is a `Timer`. Every second/minute the app will receive an event with a message reflecting the current time and that will be displayed in the app's UI fields, called **<mark>textView_sec_display</mark>** respectivelly **<mark>textView_min_display</mark>**.

### Unsubscribing from Topics

When the user hits either the **<mark>UNSUBSCRIBE SEC</mark>** or the **<mark>UNSUBSCRIBE MIN</mark>** button within apps's UI, the Ultifi topic will be unsubscribed by the `unsubscribeToTopic(topic)` function. Then the app is no longer going to receive updates on that particular topic, hence no longer re-renders of the app's sec/min time text-field(s).

![HelloUltifiUser](.\image\skApp_uSUB.jpg)

To unsubscribe, first build an ***UnsubscribeRequest*** object. This requires setting a *topic* with the correct URI that you want to (un)subscribe from, and setting the *subscriberInfo*, procedure similar to the 'SubscriptionRequest' from the section above.

```kotlin
    private fun unsubscribeToTopic(topic: UltifiUri) {
        mExecutor.execute {
            val request = UnsubscribeRequest.newBuilder()
                .setTopic(Topic.newBuilder().setUri(topic.uProtocolUri()).build())
                .setSubscriber(SubscriberInfo.newBuilder().setUri(mUltifiLink.clientUri).build())
                .build()
            ...
        }
    }
```

Create then a `USubscription.newStub(mUltifiLink)` new stub object and call `unsubscribe()`, passing in the 'UnsubscribeRequest' object that you just built.

```kotlin
    private fun unsubscribeToTopic(topic: UltifiUri) {
        mExecutor.execute {
            val request = UnsubscribeRequest.newBuilder()
                .setTopic(Topic.newBuilder().setUri(topic.uProtocolUri()).build())
                .setSubscriber(SubscriberInfo.newBuilder().setUri(mUltifiLink.clientUri).build())
                .build()
            USubscription.newStub(mUltifiLink).unsubscribe(request).exceptionally(
                StatusUtils::throwableToStatus
            )...
        }
    }
```

Check the result to see if the `unsubscribe(request)` was successful.

## Build

Builds can be performed in Android Studio or via the command line.

In Android Studio, build by clicking the green hammer toolbar icon, or navigate inside the "Build" menu bar and select "Make Project Ctrl+F9" option.
To build from the command line, run `./gradlew build`.

![HelloUltifiUser](.\image\skIDE_BuildApp.jpg)

To clean the build output, go to "Build" menu bar and select "Clean Project" option in Android Studio or run `./gradlew clean` from inside the "Terminal" tab.

## Tests and Reports

There are no unit tests nor instrumentation tests or any other kind or code-quality run-time generated reports within this project.
The main purpose of implementing and publishing the **HelloUltifiUser** project was to ***explain the Ultifi connectivity***, which is beyond teaching Android development fundamentals.

## Running

The application itself can be (uploaded onto the physical/virtual target and) launched from inside Android Studio IDE by clicking the "Run 'app' Shift+F10" button in the top right toolbar, or from the "Run" menu bar. User must select the correct run configuration in the dropdown to the left of the "Run... Alt+Shift+F10" option.

![HelloUltifiUser](.\image\skIDE_RunApp.jpg)

In addition, the emulator shall be started and fully booted before attempting to run the HelloUltifiUser application.

## References

[uHelloWorld App | SDV-4018](https://confluence.ultracruise.gm.com/display/UL/uHelloWorld+App+%7C+SDV-4018)
[uHelloWorld Service | SDV-3999](https://confluence.ultracruise.gm.com/display/UL/uHelloWorld+Service+%7C+SDV-3999)

