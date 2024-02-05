# Android HelloWorld Example

- [Android HelloWorld Example](#android-helloworld-example)
  - [1. Overview](#1-overview)
  - [2. Getting Started](#2-getting-started)
    - [2.1 Pre-requisites](#21-pre-requisites)
    - [2.2 Building the Project](#22-building-the-project)
    - [2.3 HelloWorldService](#23-helloworldservice)
    - [2.4 HelloWorldApp](#24-helloworldapp)

## 1. Overview

In this project, you will find two modules demonstrating Android Implementation of HelloWorldApp(uClient) and HelloWorldService(uService) using uProtocol. These implementations utilize the Binder uTransport ([Android uPClient](https://github.com/eclipse-uprotocol/up-client-android-java/blob/main/README.adoc)) for communication.

## 2. Getting Started

### 2.1 Pre-requisites

- UCoreService: The uProtocol Core service must be running on the device. For more details, refer to [UCoreService](../up-core-android/README.adoc)
- Emulator running Android 14 (API level 34) or higher. For more details, refer to [Testing Environment Setup](../testing.adoc)
-- The HelloWorldApp is tested with Pixel Tablet(2560x1600 xhdpi)

### 2.2 Building the Project

The Android Gradle Plugin provides several standard tasks that are commonly used in Android projects. To view the complete list, you can use the following command:
```
gradlew tasks
```
Assembling example applications is as simple as:
```
gradlew assembleRelease
```
This will generate the following APKs:
- HelloWorldService: `service/build/outputs/apk/release/service-release.apk`
- HelloWorldApp: `app/build/outputs/apk/release/app-release.apk`

Install the APKs using the following command:
```
adb install service/build/outputs/apk/release/service-release.apk
adb install app/build/outputs/apk/release/app-release.apk
```
### 2.3 HelloWorldService

Once the service is installed, use the following command to launch it:
```
adb shell am start-foreground-service org.eclipse.uprotocol.uphelloworld.service/.HelloWorldService
```
Or launch the HelloWorldApp would also launch the HelloWorldService for demo purposes.

For more details about HelloWorldService, refer to HelloWorldService [README](service/README.md)

### 2.4 HelloWorldApp

Once the App is installed, launch it from android home screen.

For more details about HelloWorldApp, refer to HelloWorldApp [README](app/README.md)

