plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.velnox.core.ui"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
    }

    buildFeatures {
        compose = true
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
    // `core:ui` owns the design system *and* the screens that are literally shared by
    // all three apps (sign-in, session gate). Those screens need the auth and data
    // layers; the dependency stays one-way because neither of those depends on UI.
    api(project(":core:common"))
    api(project(":core:auth"))
    api(project(":core:data"))

    api(platform(libs.compose.bom))
    api(libs.compose.ui)
    api(libs.compose.ui.graphics)
    api(libs.compose.foundation)
    api(libs.compose.material3)
    api(libs.compose.material.icons.extended)
    api(libs.compose.ui.tooling.preview)
    api(libs.androidx.lifecycle.runtime.compose)

    api(libs.coil.compose)
    api(libs.androidx.navigation.compose)
    api(libs.androidx.lifecycle.viewmodel.compose)
    api(libs.hilt.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    debugApi(libs.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotest.assertions.core)
}
