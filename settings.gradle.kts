pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "velnox-mobile"

// ─── Shared core ─────────────────────────────────────────────────────────────
// Every module here is a plain Android library consumed by all three apps.
include(":core:common")
include(":core:logging")
include(":core:network")
include(":core:storage")
include(":core:database")
include(":core:auth")
include(":core:realtime")
include(":core:ui")
include(":core:data")

// ─── Applications ────────────────────────────────────────────────────────────
// Each app has a unique applicationId (com.velnox.velshop / .velseller / .velcenter)
// so all three install side by side on one device.
//
// All three are listed together on purpose: the CI contract is that a failure in ANY
// app fails the whole build, which only holds if every app is always part of the
// configuration. Commenting one out for a while would silently turn "all three" into
// "the two that still compile".
include(":app:velshop")
include(":app:velseller")
include(":app:velcenter")
