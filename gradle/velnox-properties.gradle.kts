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

import java.util.Properties

val velnoxLocalProperties: Properties = rootProject.file("local.properties")
    .takeIf { it.isFile }
    ?.let { file -> Properties().apply { file.inputStream().use { load(it) } } }
    ?: Properties()

fun velnoxProperty(key: String, fallback: String): String {
    val local = velnoxLocalProperties.getProperty(key)
    if (!local.isNullOrBlank()) return local.trim()
    val fromCli = project.findProperty(key) as? String
    if (!fromCli.isNullOrBlank()) return fromCli.trim()
    return fallback
}

fun velnoxBuildConfigString(value: String): String =
    "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

extra["velnoxApiBaseUrl"] = velnoxProperty("velnox.api.baseUrl", "https://velnox-api.onrender.com")
extra["velnoxGoogleWebClientId"] = velnoxProperty("velnox.google.webClientId", "")
extra["velnoxBuildConfigString"] = ::velnoxBuildConfigString
