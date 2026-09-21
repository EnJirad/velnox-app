// Shared build-time configuration for the Velnox Android modules.
//
// Resolution order (first match wins):
//   1. local.properties        — developer machine override (git-ignored)
//   2. -P<key>=... Gradle property — what CI passes
//   3. gradle.properties       — checked-in default
//
// Apply from a module build script with:
//   apply(from = rootProject.file("gradle/velnox-properties.gradle.kts"))
// and then read `extra["velnoxApiBaseUrl"]` / `extra["velnoxGoogleWebClientId"]`.
//
// The three sources are read explicitly instead of through `project.findProperty`,
// which merges them and cannot say which one won. That distinction is the whole
// point: a **blank** override must fall through to the next source rather than
// erase it. CI always passes `-Pvelnox.google.webClientId="$VELNOX_GOOGLE_WEB_CLIENT_ID"`,
// and that variable is empty whenever the operator has not set the optional
// repository variable — a blank value there used to wipe the checked-in client id
// and silently produce an APK that reports "sign-in is not configured".

import java.util.Properties

fun velnoxPropertiesFile(path: String): Properties =
    rootProject.file(path)
        .takeIf { it.isFile }
        ?.let { file -> Properties().apply { file.inputStream().use { load(it) } } }
        ?: Properties()

/** `local.properties` only — machine-local, git-ignored. */
val velnoxLocalProperties: Properties = velnoxPropertiesFile("local.properties")

/** `gradle.properties` only — the checked-in defaults. */
val velnoxGradleProperties: Properties = velnoxPropertiesFile("gradle.properties")

/** Only the properties actually given as `-P<key>=...` on this command line. */
val velnoxCommandLineProperties: Map<String, String> =
    project.gradle.startParameter.projectProperties

fun velnoxProperty(key: String): String {
    val local = velnoxLocalProperties.getProperty(key)
    if (!local.isNullOrBlank()) return local.trim()

    val fromCli = velnoxCommandLineProperties[key]
    if (!fromCli.isNullOrBlank()) return fromCli.trim()

    val checkedIn = velnoxGradleProperties.getProperty(key)
    if (!checkedIn.isNullOrBlank()) return checkedIn.trim()

    return ""
}

fun velnoxBuildConfigString(value: String): String =
    "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

// The API origin is the one value with a last-resort constant: an empty base URL
// would make every request fail with an opaque transport error, which is strictly
// worse than pointing at the documented production origin. The Google client id has
// deliberately no constant fallback — no client id means "sign-in not configured",
// which is reported to the user instead of being papered over.
extra["velnoxApiBaseUrl"] =
    velnoxProperty("velnox.api.baseUrl").ifBlank { "https://velnox-api.onrender.com" }
extra["velnoxGoogleWebClientId"] = velnoxProperty("velnox.google.webClientId")
extra["velnoxBuildConfigString"] = ::velnoxBuildConfigString
