/*
 * :ondevice-llm — on-device LLM inference for Octo Jotter.
 *
 * Phase 0 of docs/ON-DEVICE-AI.md. This module vendors the llama.cpp GGUF
 * runtime (git submodule under src/main/cpp/llama.cpp) and the JNI wrapper
 * ported from the Hermes Agent Android project. It exposes a single public
 * surface — com.arm.aichat.InferenceEngine — that streams tokens from a
 * locally stored GGUF model. No note content ever leaves the device.
 *
 * The JNI classes intentionally keep the `com.arm.aichat` package: the native
 * symbol names in ai_chat.cpp are Java_com_arm_aichat_internal_* and renaming
 * the Kotlin package would break the JNI binding. The Gradle `namespace` below
 * (used for R/BuildConfig) is independent of those source packages.
 *
 * Only arm64-v8a is built — the on-device AI feature is device-gated to 64-bit
 * ARM at runtime (see AiCapability in :app), so every other ABI would be dead
 * weight in the APK.
 */
plugins {
    // AGP 9 provides built-in Kotlin compilation; do NOT apply kotlin-android.
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.l3ad3r1.ondevice"
    compileSdk = 36

    // Pin an installed NDK for reproducible native builds.
    ndkVersion = "28.2.13676358"

    defaultConfig {
        minSdk = 24

        ndk {
            abiFilters.add("arm64-v8a")
        }

        externalNativeBuild {
            cmake {
                arguments += "-DCMAKE_BUILD_TYPE=Release"
                arguments += "-DBUILD_SHARED_LIBS=ON"
                arguments += "-DLLAMA_BUILD_APP=OFF"
                arguments += "-DLLAMA_BUILD_COMMON=ON"
                arguments += "-DLLAMA_OPENSSL=OFF"
                arguments += "-DGGML_NATIVE=OFF"
                // Backends are dlopen()'d at runtime; the consuming app must set
                // packaging.jniLibs.useLegacyPackaging = true so they extract to
                // the filesystem and resolve. See :app build.gradle.kts.
                arguments += "-DGGML_BACKEND_DL=ON"
                arguments += "-DGGML_CPU_ALL_VARIANTS=ON"
                arguments += "-DGGML_LLAMAFILE=OFF"
                // Vulkan offload is off (DeviceLostError on Adreno in Hermes);
                // keeping it off also removes the host Vulkan-SDK build dependency.
                arguments += "-DGGML_OPENMP=OFF"
                arguments += "-DGGML_VULKAN=OFF"
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            // 3.31.6 ships ninja 1.12.x; the SDK's 3.22.1 ninja (1.10.2) crashes
            // (0xC0000005) building the multi-target ggml graph for the Release
            // variant on Windows. Newer ninja fixes it and yields a fresh .cxx dir.
            version = "3.31.6"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        // No Android resources, BuildConfig, or Compose in this module.
        buildConfig = false
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.android)
}
