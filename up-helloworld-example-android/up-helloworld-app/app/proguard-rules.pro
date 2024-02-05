#
# Copyright (c) 2024 General Motors GTO LLC.
#
# This program and the accompanying materials are made available under the
# terms of the Eclipse Public License v. 2.0 which is available at
# http://www.eclipse.org/legal/epl-2.0.
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

# Proto
-keep class * extends com.google.protobuf.**$** {*;}
-keep class * extends com.google.protobuf.GeneratedMessageV3{*;}

# Keep common dependency classes
-keep class android.** {*;}
-keep class android.**$** {*;}
-keep class androidx.** {*;}
-keep class androidx.**$** {*;}
-keepnames class * extends androidx.fragment.app.Fragment{ *; }

# This is generated automatically by the Android Gradle plugin.
-dontwarn javax.lang.model.element.Modifier