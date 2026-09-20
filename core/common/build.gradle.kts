plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    // This module owns the Hilt modules every app depends on (CoroutinesModule,
    // ApplicationScopeModule). Without KSP + the Hilt plugin here those @Module classes
    // are never processed, and every consuming app fails at hiltJavaCompile with
    // "[Dagger/MissingBinding] DispatcherProvider cannot be provided". Every other
    // module that declares Hilt bindings applies both plugins; this one was the outlier.
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
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
    ksp(libs.hilt.compiler)

    // The domain vocabulary and the paging/formatting rules in this module are pure
    // Kotlin, so they are covered by plain JVM tests — no Robolectric, no emulator.
    testImplementation(libs.junit)
    testImplementation(libs.kotest.assertions.core)
}
