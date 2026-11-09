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
