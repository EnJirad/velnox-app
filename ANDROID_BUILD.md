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

A **blank** value at any level is treated as "not provided" and falls through to the
next source, so the checked-in default always survives. That is deliberate rather than
defensive: CI always passes `-Pvelnox.google.webClientId="$VELNOX_GOOGLE_WEB_CLIENT_ID"`,
and that variable is empty whenever the optional repository variable is unset. Reading
the three sources through `project.findProperty` cannot express this — it merges them and
then a blank `-P` erases the default — so each source is read explicitly.

Only two values are ever compiled in, and both are public:

* `velnox.api.baseUrl` → `BuildConfig.VELNOX_API_BASE_URL` (`core:network`, `core:storage`)
* `velnox.google.webClientId` → `BuildConfig.VELNOX_GOOGLE_WEB_CLIENT_ID` (`core:auth`)

Both already ship in the Velnox web bundles. **No secret is compiled into an APK**, and
there is no build path that could read `JWT_SECRET`, `DATABASE_URL`, `GOOGLE_CLIENT_SECRET`
or any R2 key — none of those names exist in this repository.

`velnox.google.webClientId` is set by default, because an empty web client id is exactly
what makes the app report "sign-in is unavailable". Overriding it per environment is a
GitHub **repository variable** (`VELNOX_GOOGLE_WEB_CLIENT_ID`), not a secret: an OAuth
*web* client id is a public identifier — it is visible in the backend's own OAuth redirect
URL — and the value that must never leave the server is `GOOGLE_CLIENT_SECRET`, which
this repository forbids outright. See `ANDROID_AUTH.md` for the OAuth client registration
that goes with it.

## Signing

**Release builds are unsigned.** CI has no release keystore, and it should not have one: a
release key is a real secret, and a "signed" APK produced by CI would imply a trust it does
not have. Sign the artefact with `apksigner` and your own release key at distribution time.
Once that key exists, register its SHA-1 against the three non-`.debug` package names
(`ANDROID_AUTH.md`) — until then a release build cannot complete a Google sign-in either,
because Google has no certificate to match it against.

**Debug builds use a committed debug key**, `signing/velnox-debug.keystore`, declared in
each app's `signingConfigs` and applied to the `debug` build type. Committing a keystore is
deliberately the opposite of the rule above, so the distinction matters:

* it is **not a secret** — the store and key password are the public constant `android`, the
alias is `androiddebugkey`, and it is only ever used for `debug`;
* it cannot sign a release build, so no distribution trust depends on it;
* leaving the key to the build machine is precisely what breaks Google Sign-In. Google
authorises a Credential Manager request from the **(package name, signing certificate
SHA-1)** pair, so a key that changes per build produces a fingerprint that cannot stay
registered. That was the shipped defect: consecutive CI runs signed
`com.velnox.velshop.debug` with `21:7C:CD:93…` and then `A5:59:21:6F…`, and Google answered
the account request with no credential at all — which the app can only report as "no usable
account".

It also removes a second trap: the fingerprint is now identical on a developer machine and
on CI, so a locally built debug APK and the CI artefact work against the *same*
registration and neither replaces the other on install.

An operator who wants their own debug key can override the committed one with the
`VELNOX_DEBUG_KEYSTORE_BASE64` secret — base64 of a JKS whose alias is `androiddebugkey`
and whose store/key password is `android`. CI writes it over
`signing/velnox-debug.keystore` before the build, so there is still exactly one keystore and
one signing path. Only the *public certificate fingerprint* is ever printed; the keystore
bytes and both passwords never are.

`tools/verify-android-config.ts` keeps this honest: it asserts the keystore exists and is
really a JKS, that every app points its `debug` signing config at it, and that the workflow
neither generates a signing key nor stops reading the fingerprint back out of the signed
APKs.

For reference, if a keystore is ever placed where AGP looks by default, the location is not
`$HOME/.android`. AGP's `AndroidLocation` prefers `ANDROID_USER_HOME`, then
`XDG_CONFIG_HOME` — which a GitHub runner sets to `/home/runner/.config`, so AGP would look
in `/home/runner/.config/.android` — and only then `$HOME`. A keystore written to
`$HOME/.android` on CI is silently ignored. This is no longer load-bearing here, because the
apps declare their signing config explicitly rather than relying on AGP's debug default.

## GitHub Actions

`.github/workflows/build-android.yml` runs on push, pull request and manually, and does:

1. checkout · 2. JDK 17 · 3. Android SDK · 4. Gradle · 5. toolchain check · 6. Bun ·
7. `bun tools/verify-android-config.ts` · 8. `chmod +x ./gradlew` and `./gradlew --version` ·
9. dependency restore ·
10. configure the debug signing key (the committed one, or the `VELNOX_DEBUG_KEYSTORE_BASE64`
override), failing unless it is a readable JKS ·
11. report the SHA-1 each Android OAuth client must be registered against ·
12. `testDebugUnitTest` · 13. `lintDebug` · 14. VelShop · 15. Velseller · 16. VelCenter ·
17. verify the built `core:auth` `BuildConfig` carries a well-formed client id ·
18. verify and rename the APKs (existence **and** a minimum size) ·
19. read the certificate back out of each signed APK and fail unless all three are signed by
the key used in step 10 · 20. `velnox-android-release.zip` · 21. upload artifact
`velnox-android-release`.

Step numbers are not referenced below: the steps are named instead, so inserting a step in
this file cannot silently invalidate a pointer.

**Verify build configuration** runs before the ~40-minute Gradle work on purpose: it catches
the mistakes that are cheap to make and expensive to notice — two apps sharing an
`applicationId` (one APK silently replaces the other on install), a broken version catalog, a
backend secret name leaking into shipped source, a Google sign-in configuration that is empty
or malformed (see `ANDROID_AUTH.md`), and a debug signing key that is missing, is not a JKS,
or is no longer wired into one of the apps. Running it locally is the same command.

**Verify the build carries a Google client id** exists because it and the configuration guard
test different things. The guard checks the configuration Gradle was *given*; this step checks
what the compiler actually *produced*. A property can be read into a Gradle `extra` and still
never reach a `buildConfigField`, and the failure mode is silent — the APK builds, installs and
passes every test, then tells the user it cannot sign them in. This step reads the generated
`BuildConfig.java`, asserts the value is non-empty and well-formed, and never prints it.

**Verify the shipped APKs are signed by the expected certificate** answers the question the
reporting step cannot answer on its own: *is the fingerprint we tell you to register actually
the fingerprint of the APK in the artifact?* The reporting step reads a keystore; this one
reads the three signed APKs with `apksigner verify --print-certs` and fails unless every one
of them carries exactly that certificate — and unless all three carry the same one, since a
single registration set has to cover all three apps. Without it a misconfigured
`signingConfigs` would produce a log that is confidently wrong, and the operator would
register a certificate the shipped APK does not have.

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
