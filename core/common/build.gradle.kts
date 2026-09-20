plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.velnox.core.common"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
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
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.hilt.android)

    // The domain vocabulary and the paging/formatting rules in this module are pure
    // Kotlin, so they are covered by plain JVM tests — no Robolectric, no emulator.
    testImplementation(libs.junit)
    testImplementation(libs.kotest.assertions.core)
}
