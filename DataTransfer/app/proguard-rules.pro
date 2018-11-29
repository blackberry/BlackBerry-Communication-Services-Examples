# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in C:\Users\mtoohey\Documents\android_sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
-target 1.8
-optimizationpasses 5
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-verbose

#-dump bin/class_files.txt
#-printseeds bin/seeds.txt
#-printusage bin/unused.txt
#-printmapping bin/mapping.txt

# These are the top level attributes that are kept when proguard runs.
# SourceFile - keeps the file to help create obfuscated stack traces
# LineNumberTable - keeps the line numbers for the obfuscated stack traces
# InnerClasses - used by compiler to find classes referenced in a complied lib via reflection
# EnclosingMethod - Specifies the method in which the class was defined, used for compile.
# Signature - Keep the generic signature of a class, field or method.
-keepattributes SourceFile,LineNumberTable,InnerClasses,EnclosingMethod,Signature

#Duplicate library definition notes:
-dontnote android.net.http.*
-dontnote org.apache.commons.codec.**
-dontnote org.apache.http.**
-dontnote com.google.android.gms.**
-dontnote com.google.common.cache.**
-dontnote com.google.appengine.api.**
-dontnote com.google.apphosting.api.**
-dontnote com.google.firebase.**

-dontwarn com.google.common.**
-dontnote com.google.common.util.concurrent.**
-dontwarn com.google.errorprone.**

# Checker framework
-dontwarn afu.org.checkerframework.**
-dontwarn org.checkerframework.**
