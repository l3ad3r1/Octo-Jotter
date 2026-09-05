# R8 rules for Octo Jotter release builds.
#
# Most libraries here ship their own consumer rules (Room, Compose, WorkManager,
# OkHttp, Coil). What follows covers the parts of this app that R8 cannot see
# through: reflection, JNI, and a scripting engine that resolves everything by
# name at runtime.

# Keep crash-report line numbers useful, and hide the original file names.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Annotations, generics and Kotlin metadata. Retrofit needs generic signatures
# to resolve Call<T> return types; Moshi's reflective adapter needs the Kotlin
# metadata to find constructor parameters and defaults.
-keepattributes Signature,InnerClasses,EnclosingMethod
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault
-keep class kotlin.Metadata { *; }

# ---------------------------------------------------------------------------
# Moshi
# ---------------------------------------------------------------------------
# The GitHub API models use @JsonClass(generateAdapter = true), so their adapters
# are generated and safe. Converters.kt, however, builds a Moshi with
# KotlinJsonAdapterFactory and adapts List<String> reflectively — that path needs
# Kotlin reflection intact or tags silently stop deserialising.
-keep class com.squareup.moshi.** { *; }
-keep interface com.squareup.moshi.** { *; }
-keep class kotlin.reflect.jvm.internal.** { *; }
-keepclassmembers class * {
    @com.squareup.moshi.FromJson <methods>;
    @com.squareup.moshi.ToJson <methods>;
}
# Generated adapters are looked up by name from the class they adapt.
-keep class **JsonAdapter { *; }
-keepnames @com.squareup.moshi.JsonClass class *

# Every model that crosses the JSON boundary, plus the plugin manifest models.
-keep class com.l3ad3r1.octojotter.data.remote.** { *; }
-keep class com.l3ad3r1.octojotter.plugin.** { *; }

# ---------------------------------------------------------------------------
# Retrofit / OkHttp
# ---------------------------------------------------------------------------
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ---------------------------------------------------------------------------
# Rhino — the community-plugin script engine
# ---------------------------------------------------------------------------
# Rhino resolves host objects, methods and properties by name at runtime. Any
# renaming here breaks every script plugin, and it fails silently because a
# broken plugin is skipped rather than fatal.
-keep class org.mozilla.javascript.** { *; }
-keep interface org.mozilla.javascript.** { *; }
-dontwarn org.mozilla.javascript.**
-dontwarn org.mozilla.classfile.**
# The `octo` API surface exposed into scripts is likewise reached by name.
-keepclassmembers class com.l3ad3r1.octojotter.plugin.** {
    public *;
}

# ---------------------------------------------------------------------------
# ONNX Runtime — semantic search embeddings (JNI)
# ---------------------------------------------------------------------------
-keep class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**

# ---------------------------------------------------------------------------
# Room
# ---------------------------------------------------------------------------
# Room ships consumer rules, but entities are also read reflectively by the
# JSON export in Database Backup.
-keep class com.l3ad3r1.octojotter.data.local.** { *; }

# ---------------------------------------------------------------------------
# Firebase AI
# ---------------------------------------------------------------------------
-dontwarn com.google.firebase.**
-keep class com.google.firebase.** { *; }

# ---------------------------------------------------------------------------
# Kotlin coroutines / serialization internals
# ---------------------------------------------------------------------------
-dontwarn kotlinx.coroutines.**
-keepclassmembers class kotlin.coroutines.jvm.internal.** { *; }
