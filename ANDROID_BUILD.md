# ANDROID_BUILD — Building Velnox Android

## Toolchain

| Component | Version |
|-----------|---------|
| JDK | 17 (Temurin) |
| Gradle | 8.9 (wrapper) |
| Android Gradle Plugin | 8.7.3 |
| Kotlin | 2.0.21 (Compose compiler plugin) |
| compileSdk / targetSdk | 35 |
| minSdk | 24 |
| Compose BOM | 2024.10.01 |

Android SDK packages required: `platforms;android-35`, `build-tools;35.0.0`,
`platform-tools`.

## Local build

```bash
# 1. Point at your SDK and (optionally) override the backend
cp local.properties.example local.properties
#    sdk.dir=/path/to/Android/sdk
#    velnox.api.baseUrl=https://velnox-api.onrender.com
#    velnox.google.webClientId=<web client id>   # enables native sign-in

# 2. Build one app
./gradlew :app:velshop:assembleDebug

# 3. Build all three and verify the artefacts
./gradlew :app:velshop:assembleDebug :app:velseller:assembleDebug :app:velcenter:assembleDebug
ls app/*/build/outputs/apk/debug/
```

Expected APKs:

```
app/velshop/build/outputs/apk/debug/velshop-debug.apk
app/velseller/build/outputs/apk/debug/velseller-debug.apk
app/velcenter/build/outputs/apk/debug/velcenter-debug.apk
```

Other useful tasks:

```bash
./gradlew testDebugUnitTest        # unit tests
./gradlew lintDebug                # static analysis
./gradlew :core:data:dependencies  # what a module actually resolves
```

## Configuration resolution

Three sources, first match wins, resolved once at configuration time in
`gradle/velnox-properties.gradle.kts`:

1. `local.properties` (git-ignored, machine-local)
2. `-Pvelnox.api.baseUrl=…` / `-Pvelnox.google.webClientId=…` (what CI passes)
3. `gradle.properties` (checked-in default)

Only two values are ever compiled in, and both are public:

* `velnox.api.baseUrl` → `BuildConfig.VELNOX_API_BASE_URL` (`core:network`, `core:storage`)
* `velnox.google.webClientId` → `BuildConfig.VELNOX_GOOGLE_WEB_CLIENT_ID` (`core:auth`)

Both already ship in the Velnox web bundles. **No secret is compiled into an APK**, and
there is no build path that could read `JWT_SECRET`, `DATABASE_URL`, `GOOGLE_CLIENT_SECRET`
or any R2 key — none of those names exist in this repository.

## Signing

Release builds are produced **unsigned**. CI intentionally has no keystore: a keystore
committed to a repository is a leaked secret, and a "signed" APK from CI would imply a
trust it does not have. Sign the artefact with `apksigner` (or add a properly stored
GitHub secret and a `signingConfigs` block) at distribution time.

## GitHub Actions

`.github/workflows/build-android.yml` runs on push, pull request and manually, and does:

1. checkout · 2. JDK 17 · 3. Android SDK · 4. Gradle · 5. Bun ·
6. `bun tools/verify-android-config.ts` · 7. dependency restore ·
8. `testDebugUnitTest` · 9. `lintDebug` · 10. VelShop · 11. Velseller · 12. VelCenter ·
13. APK verification (existence **and** a minimum size) · 14. rename to
`VelShop.apk` / `Velseller.apk` / `VelCenter.apk` · 15. `velnox-android-release.zip` ·
16. upload artifact `velnox-android-release`.

Step 6 runs before the ~40-minute Gradle work on purpose: it catches the three mistakes
that are cheap to make and expensive to notice — two apps sharing an `applicationId` (one
APK silently replaces the other on install), a broken version catalog, and a backend
secret name leaking into shipped source. Running it locally is the same command.

### Failure semantics

The requirement is that a failure in any single app fails the run. That is achieved by
*not* masking anything:

* No `continue-on-error`, so a step that exits non-zero fails the job.
* `set -euo pipefail` in every shell step.
* Each app has its own `assembleDebug` step, so the log names the app that broke.
* The verification step fails when an APK is missing, is smaller than 100 000 bytes, or
  when the count in `release/` is not exactly three.

Resulting behaviour, as required:

```
VelShop PASS   VelShop FAIL
Velseller FAIL   → run FAILED      Velseller PASS   → run FAILED
VelCenter PASS   VelCenter PASS
```

No placeholder APK is ever produced, and a failed app is never skipped.

## Build hygiene notes

* `org.gradle.configuration-cache` is left **off** in CI: it is a per-machine speed-up,
  and enabling it there only adds failure modes.
* `android.nonTransitiveRClass=true` and `nonFinalResIds=true` are set project-wide.
* Room's schema JSON is written to `core/database/schemas` so a cache-schema change shows
  up in review.
* `consumer-rules.pro` in `core:network` keeps kotlinx.serialization serializers and
  Retrofit metadata under R8; release builds enable minification and resource shrinking.
* Android lint is set to abort on error in CI, so a lint regression fails the build
  rather than being printed and ignored.
