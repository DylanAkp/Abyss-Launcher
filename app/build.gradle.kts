plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
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

        buildConfigField("String", "DEFAULT_JELLYFIN_SERVER", "\"${escapeString(envProps["JELLYFIN_SERVER_URL"] ?: "")}\"")
        buildConfigField("String", "DEFAULT_JELLYFIN_USER", "\"${escapeString(envProps["JELLYFIN_USERNAME"] ?: "")}\"")
        buildConfigField("String", "DEFAULT_JELLYFIN_PASS", "\"${escapeString(envProps["JELLYFIN_PASSWORD"] ?: "")}\"")
        buildConfigField("String", "DEFAULT_SEER_SERVER", "\"${escapeString(envProps["SEER_SERVER_URL"] ?: "")}\"")
        buildConfigField("String", "DEFAULT_SEER_API_KEY", "\"${escapeString(envProps["SEER_API_KEY"] ?: "")}\"")
        buildConfigField("String", "TMDB_API_KEY", "\"${escapeString(envProps["TMDB_API_KEY"] ?: "")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("debug")
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

  // Navigation
  implementation(libs.androidx.navigation3.ui)
  implementation(libs.androidx.navigation3.runtime)
  implementation(libs.androidx.lifecycle.viewmodel.navigation3)

  // AndroidX Media3 (ExoPlayer)
  implementation(libs.androidx.media3.exoplayer)
  implementation(libs.androidx.media3.exoplayer.hls)
  implementation(libs.androidx.media3.exoplayer.dash)
  implementation(libs.androidx.media3.ui)
  implementation(libs.androidx.media3.common)

  // Image Loading
  implementation(libs.coil.compose)

  // Baseline Profiles / AOT compilation
  implementation(libs.androidx.profileinstaller)
}
