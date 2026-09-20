# R8 rules for VelCenter.
#
# VelCenter handles the most privileged operations in the suite (seller approval,
# product moderation, staff access), so the instinct is to keep everything. That is not
# a security measure: R8 rules do not make code safer, they only make it harder to
# shrink. Real protection lives server-side, where every `/api/admin/*` route re-checks
# the caller's role and permission code.
#
# Library rules cover the rest: Hilt ships its own, `core:network/consumer-rules.pro`
# keeps the kotlinx.serialization and Retrofit metadata, OkHttp and Coil publish theirs.

# Keep line numbers so an obfuscated crash report stays actionable.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Compose runtime metadata is read reflectively by the Compose compiler plugin.
-keep class androidx.compose.runtime.** { *; }

# Entry points referenced only from Hilt-generated code.
-keep class com.velnox.velcenter.VelCenterApplication { *; }
-keep class com.velnox.velcenter.MainActivity { *; }
