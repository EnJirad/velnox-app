plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

apply(from = rootProject.file("gradle/velnox-properties.gradle.kts"))

val buildConfigString = extra["velnoxBuildConfigString"] as (String) -> String
val velnoxApiBaseUrl = extra["velnoxApiBaseUrl"] as String

android {
    namespace = "com.velnox.core.storage"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
        buildConfigField("String", "VELNOX_API_BASE_URL", buildConfigString(velnoxApiBaseUrl))
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

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.security.crypto)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotlinx.coroutines.test)
}
