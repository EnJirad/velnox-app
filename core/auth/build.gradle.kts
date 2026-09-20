plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

apply(from = rootProject.file("gradle/velnox-properties.gradle.kts"))

val buildConfigString = extra["velnoxBuildConfigString"] as (String) -> String
val velnoxGoogleWebClientId = extra["velnoxGoogleWebClientId"] as String

android {
    namespace = "com.velnox.core.auth"
    compileSdk = 35

    defaultConfig {
        minSdk = 24

        // PUBLIC value (it already ships in the Velnox web bundles). It is used as
        // the Credential Manager `serverClientId` so Google issues an ID token whose
        // `aud` matches the backend's existing GOOGLE_CLIENT_ID audience check.
        // Deliberately never a secret: no GOOGLE_CLIENT_SECRET, JWT_SECRET or R2 key
        // may exist in this project.
        buildConfigField("String", "VELNOX_GOOGLE_WEB_CLIENT_ID", buildConfigString(velnoxGoogleWebClientId))
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    api(project(":core:common"))
    api(project(":core:network"))
    api(project(":core:storage"))
    api(project(":core:logging"))

    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.googleid)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
