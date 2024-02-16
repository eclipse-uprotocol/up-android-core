#
# Copyright (c) 2024 General Motors GTO LLC.
#
# This program and the accompanying materials are made available under the
# terms of the Apache Public License v2.
#
# SPDX-FileType: DOCUMENTATION
# SPDX-FileCopyrightText: 2023 General Motors GTO LLC
# SPDX-License-Identifier: Apache-2.0
#
# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Uncomment this to preserve the line number information for
# debugging stack traces.
-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
-renamesourcefileattribute SourceFile

##----- Begin: proguard configuration
# keep the public classes used by others
-keep public class org.eclipse.uprotocol.core.ubus.* {
    public *;
    <init>();
}
-keep public interface org.eclipse.uprotocol.core.ubus.* {
    *;
}
-keep public class org.eclipse.uprotocol.core.usubscription.* {
    public *;
    public static final *;
    private final *;
    <init>(...);
    <fields>;
    <methods>;
}
-keep public interface org.eclipse.uprotocol.core.usubscription.* {
    *;
}
-keep public class org.eclipse.uprotocol.core.usubscription.database.* {
    public *;
}
-keep public interface org.eclipse.uprotocol.core.usubscription.database.* {
    public *;
}
-keep public class org.eclipse.uprotocol.core.internal.util.* {
    public *;
}
-keepclassmembers,allowobfuscation class * {
  @com.google.gson.annotations.SerializedName <fields>;
}

-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*
-optimizationpasses 5

-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-verbose

# Preserve some attributes that may be required for reflection.
-keepattributes AnnotationDefault,
                EnclosingMethod,
                InnerClasses,
                RuntimeVisibleAnnotations,
                RuntimeVisibleParameterAnnotations,
                RuntimeVisibleTypeAnnotations,
                Signature

# For native methods, see http://proguard.sourceforge.net/manual/examples.html#native
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# The support libraries contains references to newer platform versions.
# Don't warn about those in case this app is linking against an older
# platform version. We know about them, and they are safe.
-dontnote android.support.**
-dontnote androidx.**
-dontwarn android.support.**
-dontwarn androidx.**

-keepclasseswithmembers class io.cloudevents.** {*;}

-keepclasseswithmembers class android.security.keystore.** {*;}
-keepclasseswithmembers interface android.security.** {*;}
-keepclasseswithmembers class com.android.org.bouncycastle.asn1.x509.** {*;}

-keep class * extends com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
-keep class com.google.gson.examples.android.model.** { *; }

-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class * extends com.google.gson.reflect.TypeToken


# Please add these rules to your existing keep rules in order to suppress warnings.
# This is generated automatically by the Android Gradle plugin.
-dontwarn io.cloudevents.v1.proto.**
-dontwarn javax.lang.model.element.Modifier
-dontwarn sun.misc.**
##----- End: proguard configuration
