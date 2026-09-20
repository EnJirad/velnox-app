# R8 rules for VelShop.
#
# Almost everything this app needs is already published by its own libraries:
#
#   * Hilt ships its own consumer rules through `hilt-android`, and Dagger's
#     generated components are kept by them.
#   * `core:network/consumer-rules.pro` keeps kotlinx.serialization serializers and
#     the Retrofit/OkHttp metadata that reflection and the serialization plugin rely
#     on.
#   * Coil and OkHttp publish their own rules.
#
# What is left is the app's own guidance. Nothing here weakens R8 — there is no
# `-dontobfuscate` and no blanket `-keep class com.velnox.** { *; }`, because keeping
# the whole app would defeat shrinking for no functional gain.

# Keep the source-file/line attributes so an obfuscated crash report is still
# actionable in Google Play Console, where the class names are rewritten but the
# line numbers still point at the real code.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Compose runtime metadata is read reflectively by the Compose compiler plugin.
-keep class androidx.compose.runtime.** { *; }

# The app's entry points are declared in the manifest; R8 keeps them via the
# manifest-referenced rules it applies automatically, but the Hilt-generated
# Application/Activity members are referenced only from generated code, so keep the
# two known entry points explicitly.
-keep class com.velnox.velshop.VelShopApplication { *; }
-keep class com.velnox.velshop.MainActivity { *; }
