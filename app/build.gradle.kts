import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy
import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.secrets)
  alias(libs.plugins.google.services)
}

android {
  namespace = "com.l3ad3r1.octojotter"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.l3ad3r1.octojotter"
    minSdk = 24
    targetSdk = 36
    versionCode = 18
    versionName = "2.7"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    create("release") {
      // Credentials come from keystore.properties (gitignored, repo root) so local
      // release builds work without exporting anything. Environment variables still
      // win when set, which is what CI uses.
      val keystoreProps = Properties().apply {
        val file = rootProject.file("keystore.properties")
        if (file.exists()) file.inputStream().use { load(it) }
      }
      fun credential(env: String, property: String): String? =
        System.getenv(env) ?: keystoreProps.getProperty(property)

      val keystorePath = credential("KEYSTORE_PATH", "storeFile") ?: "my-upload-key.jks"
      storeFile = rootProject.file(keystorePath)
      storePassword = credential("STORE_PASSWORD", "storePassword")
      keyAlias = credential("KEY_ALIAS", "keyAlias") ?: "upload"
      keyPassword = credential("KEY_PASSWORD", "keyPassword")
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  // How this build is distributed. It decides one thing: whether the app is
  // allowed to update itself.
  //
  // Play's Device and Network Abuse policy forbids an app shipped through Play
  // from installing an APK itself, so the `play` flavour carries neither the
  // updater UI nor REQUEST_INSTALL_PACKAGES (declared in src/github/AndroidManifest.xml).
  flavorDimensions += "distribution"
  productFlavors {
    create("github") {
      dimension = "distribution"
      buildConfigField("boolean", "SELF_UPDATE_ENABLED", "true")
    }
    create("play") {
      dimension = "distribution"
      buildConfigField("boolean", "SELF_UPDATE_ENABLED", "false")
    }
  }

  buildTypes {
    release {
      // Smoke-testing a minified build normally means uninstalling the debug
      // build first, because the two are signed with different keys - which
      // destroys the notes on that device. With -PsideBySide the release build
      // gets its own applicationId and installs alongside instead.
      if (project.hasProperty("sideBySide")) applicationIdSuffix = ".r8test"
      isCrunchPngs = false
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      signingConfig = signingConfigs.getByName("debugConfig")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  packaging {
    jniLibs {
      // :ondevice-llm builds llama.cpp with GGML_BACKEND_DL=ON, so ggml
      // dlopen()s its backend .so files at runtime. Legacy packaging extracts
      // them to the filesystem, which is what makes that dlopen resolve.
      useLegacyPackaging = true
    }
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
}

// Room writes the schema JSON for every version here. Checked in, so a future
// schema bump can be diffed and migration-tested instead of trusted.
ksp {
  arg("room.schemaLocation", "$projectDir/schemas")
}

kotlin {
  compilerOptions {
    freeCompilerArgs.add("-Xannotation-default-target=param-property")
  }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
}

googleServices {
  missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN
}


// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  // On-device LLM inference (Phase 0 — see docs/ON-DEVICE-AI.md).
  implementation(project(":ondevice-llm"))
  // On-device embeddings for semantic search (Phase 1).
  implementation(libs.onnxruntime.android)
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.core.splashscreen)
  implementation(libs.androidx.biometric)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.work.runtime)
  implementation(libs.androidx.security.crypto)
  // implementation(libs.coil.compose)
  implementation(libs.converter.moshi)
  implementation(libs.firebase.ai)
  implementation(libs.firebase.appcheck.recaptcha)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  // implementation(libs.play.services.location)
  implementation(libs.retrofit)
  // Mozilla Rhino: pure-JVM JS engine for the community-plugin scripting runtime
  // (runs interpreted/sandboxed on Android — no native code).
  implementation("org.mozilla:rhino:1.7.14")
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
}
