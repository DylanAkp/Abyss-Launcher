# Abyss TV Launcher Proguard Rules
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# Compose rules
-keep class androidx.compose.** { *; }

# Serialization
-keepclassmembers class * {
    @kotlinx.serialization.Serializable *;
}
-keepclassmembers class * {
    *** Companion;
}

# Coil
-keep class coil.** { *; }

# Media3
-keep class androidx.media3.** { *; }

# Ktor CIO (JVM-only debug/logging refs absent on Android)
-dontwarn java.lang.management.**
-dontwarn io.ktor.util.debug.**
-dontwarn org.slf4j.**
