plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.ksp)
}

ksp {
  arg("room.schemaLocation", "$projectDir/schemas")
}

val envFile = rootProject.file(".env")
val envProps = mutableMapOf<String, String>()
if (envFile.exists()) {
    envFile.readLines().forEach { line ->
        val trimmed = line.trim()
        if (trimmed.isNotEmpty() && !trimmed.startsWith("#") && trimmed.contains("=")) {
            val key = trimmed.substringBefore("=").trim()
            val value = trimmed.substringAfter("=").trim().removeSurrounding("\"").removeSurrounding("'")
            envProps[key] = value
        }
    }
}

fun escapeString(str: String): String = str.replace("\\", "\\\\").replace("\"", "\\\"")

android {
    namespace = "dev.dylanakp.abysstvlauncher"
    compileSdk = 36

    val vName = providers.gradleProperty("versionName").getOrElse("26.11.0")
    val vCode = providers.gradleProperty("versionCode").map { it.toInt() }.getOrElse(261100)

    defaultConfig {
        applicationId = "dev.dylanakp.abysstvlauncher"
        minSdk = 26
        targetSdk = 36
        versionCode = vCode
        versionName = vName

        buildConfigField("String", "TMDB_API_KEY", "\"${escapeString(envProps["TMDB_API_KEY"] ?: "")}\"")
    }

    val releaseKeystore = rootProject.file("release.jks")
    val releaseRequested = gradle.startParameter.taskNames.any { it.contains("release", ignoreCase = true) }
    if (releaseRequested) {
        val missing = buildList {
            if (!releaseKeystore.exists()) add("release.jks")
            if (System.getenv("KEYSTORE_PASSWORD").isNullOrBlank()) add("KEYSTORE_PASSWORD")
            if (System.getenv("KEY_ALIAS").isNullOrBlank()) add("KEY_ALIAS")
            if (System.getenv("KEY_PASSWORD").isNullOrBlank()) add("KEY_PASSWORD")
        }
        if (missing.isNotEmpty()) throw GradleException("Release signing requires: ${missing.joinToString(", ")}")
    }

    signingConfigs {
        create("release") {
            storeFile = releaseKeystore
            storePassword = System.getenv("KEYSTORE_PASSWORD") ?: ""
            keyAlias = System.getenv("KEY_ALIAS") ?: ""
            keyPassword = System.getenv("KEY_PASSWORD") ?: ""
        }
    }

    buildTypes {
        debug {
            buildConfigField("String", "DEFAULT_JELLYFIN_SERVER", "\"${escapeString(envProps["JELLYFIN_SERVER_URL"] ?: "")}\"")
            buildConfigField("String", "DEFAULT_JELLYFIN_USER", "\"${escapeString(envProps["JELLYFIN_USERNAME"] ?: "")}\"")
            buildConfigField("String", "DEFAULT_JELLYFIN_PASS", "\"${escapeString(envProps["JELLYFIN_PASSWORD"] ?: "")}\"")
            buildConfigField("String", "DEFAULT_SEER_SERVER", "\"${escapeString(envProps["SEER_SERVER_URL"] ?: "")}\"")
            buildConfigField("String", "DEFAULT_SEER_API_KEY", "\"${escapeString(envProps["SEER_API_KEY"] ?: "")}\"")
        }
        release {
            buildConfigField("String", "DEFAULT_JELLYFIN_SERVER", "\"\"")
            buildConfigField("String", "DEFAULT_JELLYFIN_USER", "\"\"")
            buildConfigField("String", "DEFAULT_JELLYFIN_PASS", "\"\"")
            buildConfigField("String", "DEFAULT_SEER_SERVER", "\"\"")
            buildConfigField("String", "DEFAULT_SEER_API_KEY", "\"\"")
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
      compose = true
      aidl = false
      buildConfig = true
      shaders = false
    }

    packaging {
      resources {
        excludes += "/META-INF/{AL2.0,LGPL2.1}"
        excludes += "/META-INF/INDEX.LIST"
        excludes += "/META-INF/io.netty.versions.properties"
      }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.appcompat)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)

  // Arch Components
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  implementation("androidx.compose.material:material-icons-core")
  // Tooling
  debugImplementation(libs.androidx.compose.ui.tooling)
  // Instrumented tests
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Local tests: jUnit, coroutines, Android runner
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)

  // Instrumented tests: jUnit rules and runners
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)

  // AndroidX Media3 (ExoPlayer)
  implementation(libs.androidx.media3.exoplayer)
  implementation(libs.androidx.media3.exoplayer.hls)
  implementation(libs.androidx.media3.exoplayer.dash)
  implementation(libs.androidx.media3.ui)
  implementation(libs.androidx.media3.common)
  implementation(libs.jellyfin.media3.ffmpeg)

  // Image Loading
  implementation(libs.coil.compose)

  // Serialization
  implementation(libs.kotlinx.serialization.json)

  // Room Persistence
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.room.ktx)
  ksp(libs.androidx.room.compiler)

  // Preferences DataStore
  implementation(libs.androidx.datastore.preferences)

  // Dependency Injection (Koin)
  implementation(libs.koin.android)
  implementation(libs.koin.androidx.compose)

  // Embedded Web Remote Server (Ktor CIO) & QR Code
  implementation(libs.ktor.server.core)
  implementation(libs.ktor.server.cio)
  implementation(libs.ktor.server.content.negotiation)
  implementation(libs.ktor.serialization.kotlinx.json)
  implementation(libs.ktor.server.sse)
  implementation(libs.zxing.core)

  // Baseline Profiles / AOT compilation
  implementation(libs.androidx.profileinstaller)
}
