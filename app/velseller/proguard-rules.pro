# R8 rules for Velseller.
#
# Library-provided rules do the heavy lifting: Hilt ships its own consumer rules,
# `core:network/consumer-rules.pro` keeps the kotlinx.serialization and Retrofit
# metadata, and OkHttp/Coil publish theirs. Nothing here weakens R8 — there is no
# blanket keep of `com.velnox.**`.

# Keep line numbers so an obfuscated crash report stays actionable.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Compose runtime metadata is read reflectively by the Compose compiler plugin.
-keep class androidx.compose.runtime.** { *; }

# Entry points referenced only from Hilt-generated code.
-keep class com.velnox.velseller.VelsellerApplication { *; }
-keep class com.velnox.velseller.MainActivity { *; }
