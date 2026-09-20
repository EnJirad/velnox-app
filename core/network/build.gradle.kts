plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

apply(from = rootProject.file("gradle/velnox-properties.gradle.kts"))

val buildConfigString = extra["velnoxBuildConfigString"] as (String) -> String
val velnoxApiBaseUrl = extra["velnoxApiBaseUrl"] as String

android {
    namespace = "com.velnox.core.network"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")

        buildConfigField("String", "VELNOX_API_BASE_URL", buildConfigString(velnoxApiBaseUrl))
        // Sent as X-Velnox-Client so the backend can tell native traffic apart.
        buildConfigField("String", "VELNOX_CLIENT_PLATFORM", buildConfigString("android"))
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
    api(project(":core:logging"))

    api(libs.retrofit)
    api(libs.okhttp)
    api(libs.kotlinx.serialization.json)

    implementation(libs.retrofit.serialization.converter)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
}
