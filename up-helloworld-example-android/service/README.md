# up Hello World! uService

* [How to write an UProtocol Service](#how-to-write-an-uprotocol-service-usrv)
    * [Android Manifest](#androidmanifest)
    * [Gradle](#gradle)
    * [Connecting To UPClient](#connecting-to-the-upclient)
    * [Remote Procedure Calls](#remote-procedure-calls-rpc)
    * [Publishing Topics](#publishing-topics)
* [Build](#build)
* [Running](#running)

## Synopsis

`HelloWorldService` serves as an example to demonstrate basic uProtocol operations. It is intended
to be used in conjunction with the Hello World App, which makes use of the RPC calls and
subscribes to the topics below.

The service contains one remote procedure call (RPC):

- `SayHello`: Takes a name in the request argument, and returns another string
  "Hello " + name + "!".

It publishes two topics, each with a timestamp payload (current time):

- `up:/example.hello_world/1/one_second#Timer`: Publishes at a rate of once per second
- `up:/example.hello_world/1/one_minute#Timer`: Publishes at a rate of once per minute

## How to Write an UProtocol Service (uSrv)

Before starting, create an Android service that runs in the foreground.


### AndroidManifest

The AndroidManifest must contain this permission to gain access to the UProtocol Bus:

```
    ` <uses-permission android:name="uprotocol.permission.ACCESS_UBUS" />`
```

The `<service>` entry must also declare its UProtocol entity name and version as metadata:

```
    <meta-data
        android:name="uprotocol.entity.name"
        android:value="example.hello_world" />
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

### Connecting to the UPClient

When your service's `onCreate` function runs, it should first create an instance of `UPClient`.
In doing so, your service should register any RPC methods it provides and create any topics it
publishes. Then, it should connect the `UPClient` instance and check to be sure it was
successful. In `HelloWorldService`, the `initUPClient()` function performs these steps.

The next two sections discuss how to add RPCs to the service and how to publish topics.

### Remote Procedure Calls (RPC)

RPC requests to your service will be handled by RPC event listeners. `HelloWorldService` uses one
`RPCEventListener` class to handle all RPC requests.

The first step in adding an RPC is to build the RPC method URI. See `RPC_SAY_HELLO` in the
`HelloWorldService` companion object for an example.

Next, create a method in `RPCEventListener` to handle the RPC request. See
`processHelloRequest()` for an example. It takes two parameters:

- A `CloudEvent` with the RPC request's payload
- A `CompletableFuture`, used to send the response

Then, the `onEvent()` method in `RPCEventListener` needs to call that handler function when a
matching RPC request comes in. Add a branch to the `when` for your RPC method URI that calls your
new handler function.

Finally, `RPCEventListener` must be registered to handle the new RPC. To do that, pass the RPC
method URI to `registerRPCMethods()` in `initUPClient()`.

### Publishing Topics

Your service can periodically publish events to topics.

To add a new topic, first you must create the topic URI. See `TOPIC_MINUTE` and `TOPIC_SECOND` in
the `HelloWorldService` companion object for examples. They publish instances of the `Timer` class.

Next, you must request creation of the topic. This is handled by the `createTopic()` function,
called as part of `initUPClient()`.

Finally, your service must actually publish events from time to time. `HelloWorldService` does this
in `beginPublishTimeOfDay()`, but your service can determine when to publish using whatever means
is appropriate. These are the steps to publish:

1. Build the payload for your event. `HelloWorldService` does this in `buildTimeOfDay()`.
2. Serialize the payload with `Any.pack()`
3. Publish the payload to your topic, handled by the `publish()` function.

## Build

Builds can be performed in Android Studio or via the command line.

In Android Studio, build by clicking the green hammer toolbar icon, or in the "Build" menu, click
"Make Project."

To build from the command line, run `gradlew build`.

To clean the build output, go to "Build" -> "Clean Project" in Android Studio or run
`gradlew clean`.

## Running

Once the service is installed, use the following command to launch it:

    adb shell am start-foreground-service org.eclipse.uprotocol.uphelloworld.service/.HelloWorldService
