# AI_HANDOFF — Velnox Android

Living handoff note: what was being worked on, what was actually proven, and what is still
open. `ANDROID_AUTH.md`, `ANDROID_BUILD.md` and `ANDROID_API.md` remain the authoritative
references for their subjects — this file is the state of play and deliberately does not
duplicate them.

## Objective

Make native Google Sign-In work end to end on all three Velnox Android clients:

```
VelShop / Velseller / VelCenter
  → Credential Manager (Google account chooser)
  → Google ID token
  → POST /api/auth/native/google
  → backend verifies the token
  → Velnox session (same JWT the browser flow issues)
  → GET /api/auth/me → authenticated
```

No mock login, no fabricated token, no hardcoded user, no bypassed authorisation.

## Diagnosis

**Symptom.** An installed APK shows:

> «Google ไม่ได้ส่งบัญชีที่ใช้ได้กลับมา ตรวจสอบว่าอุปกรณ์มีบัญชี Google และบิลด์นี้ลงทะเบียนชื่อแพ็กเกจกับลายเซ็น (SHA-1) ไว้กับ Google แล้ว»

That is `velnox_auth_no_credential`, reached from
`NativeGoogleSignIn → GoogleSignInOutcome.NoCredentialAvailable`, i.e. Credential Manager
threw `NoCredentialException`. The earlier, different message
(`velnox_auth_not_configured`, "no Google Client ID configured") no longer appears, so the
client id is no longer the problem — the build carries one.

**What was verified, rather than assumed.** The three APKs from CI run `35654696973` were
downloaded and inspected directly (`aapt2 dump packagename`, `apksigner verify
--print-certs`):

| APK | Package | Certificate SHA-1 |
|---|---|---|
| VelShop.apk | `com.velnox.velshop.debug` | `a559216fb2bd24cc795b7bcdffea12a48b7d3a78` |
| Velseller.apk | `com.velnox.velseller.debug` | `a559216fb2bd24cc795b7bcdffea12a48b7d3a78` |
| VelCenter.apk | `com.velnox.velcenter.debug` | `a559216fb2bd24cc795b7bcdffea12a48b7d3a78` |

The same three APKs from CI run `35549406753`:

| APK | Package | Certificate SHA-1 |
|---|---|---|
| VelShop.apk | `com.velnox.velshop.debug` | `217ccd93f08831bba66a6a1877fe507579081962` |
| Velseller.apk | `com.velnox.velseller.debug` | `217ccd93f08831bba66a6a1877fe507579081962` |
| VelCenter.apk | `com.velnox.velcenter.debug` | `217ccd93f08831bba66a6a1877fe507579081962` |

The web client id compiled into the shipped APK is
`610153333350-6j5vhhhf0fcsf2rgo5buk4cqepeum0gj.apps.googleusercontent.com`, and the live
backend's own OAuth redirect holds the identical value (below), so the token's `aud` will
match the backend. The APK contains no Google client secret (`GOCSPX-…` appears nowhere in
the dex; the only `client_secret` string in the APK is a log-redaction regex inside a
library).

Live backend (`https://velnox-api.onrender.com`):

| Request | Result |
|---|---|
| `GET /api/auth/me` | `401 {"code":"UNAUTHORIZED"}` — route exists, requires auth |
| `GET /auth/google` | `302` to Google with `client_id=610153333350-…` — so `GOOGLE_CLIENT_ID` is set server-side |
| `POST /api/auth/native/google` | `404 Cannot POST /api/auth/native/google` — **not deployed** |

## Root cause

Three independent defects; the first two are fixed, the third is not in this repository.

1. **The build genuinely had no client id** (commit `0726208`). `gradle.properties` shipped
   `velnox.google.webClientId=` empty and the workflow never passed one, so
   `BuildConfig.VELNOX_GOOGLE_WEB_CLIENT_ID` was `""` in every APK. A second bug hid it:
   `velnoxProperty()` used `project.findProperty()`, which merges all configuration sources
   and cannot tell which one won, so a **blank** `-P` erased the checked-in default instead
   of falling through to it.

2. **The debug signing certificate changed on every CI run.** Google authorises a Credential
   Manager request from the (package name, signing certificate SHA-1) pair, so a key that
   moves makes any Android OAuth client registration stale — and the caller is then answered
   with no credential at all, which is exactly the reported symptom. The workflow generated a
   fresh keypair whenever `VELNOX_DEBUG_KEYSTORE_BASE64` was unset (its own log says so:
   *"no VELNOX_DEBUG_KEYSTORE_BASE64 was supplied, so this run generated a new debug key"*),
   which is why the two runs above signed the same package with two different certificates.

3. **The backend endpoint does not exist on the deployment.** `POST /api/auth/native/google`
   returns `404` in production. The implementation exists and is tested in
   `EnJirad/velnox-marketplace`, delivered here as
   `patches/velnox-marketplace-0001-native-google-signin.patch`, but it cannot be pushed
   from this workspace: the managed credential is scoped to `velnox-app` and GitHub answers
   `403 Permission to EnJirad/velnox-marketplace.git denied`.

Nothing else in the chain was found to be wrong: the credential request,
`setServerClientId(webClientId)`, `setFilterByAuthorizedAccounts(false)`,
`setAutoSelectEnabled(false)`, the secure nonce, the distinct outcome/error mapping, the
session storage and `GET /api/auth/me` are all already correct and were left alone.

## Files changed

| File | Why |
|---|---|
| `signing/velnox-debug.keystore` | **New.** The committed debug-only key. Its SHA-1 is the value registered with Google, and committing it is what stops the fingerprint rotating per build. Not a secret: password `android`, alias `androiddebugkey`, `debug` only. |
| `app/velshop/build.gradle.kts`, `app/velseller/build.gradle.kts`, `app/velcenter/build.gradle.kts` | Declare a `signingConfigs` entry pointing at that keystore and apply it to the `debug` build type, so a local build and a CI build share one certificate. |
| `.github/workflows/build-android.yml` | Stop generating a per-run key; use the committed key (or the `VELNOX_DEBUG_KEYSTORE_BASE64` override) and fail if it is unreadable. Report the exact package + SHA-1 rows. Added a step that reads the certificate back out of the three signed APKs and fails unless all three carry the expected one. |
| `tools/verify-android-config.ts` | New guard checks: the keystore exists and really is a JKS, every app points its `debug` signing config at it, and the workflow neither generates a signing key nor stops verifying the APK certificates. |
| `ANDROID_BUILD.md` | § Signing rewritten: debug key committed (and why that is not the same as committing a release key), release still unsigned, and the CI step list updated. |
| `ANDROID_AUTH.md` | § "Registering the app with Google" now carries the exact package + SHA-1 table to paste into Google Cloud, and states that there is no release client to register yet. |
| `AI_HANDOFF.md` | **New.** This file. |
| `patches/velnox-marketplace-0001-native-google-signin.patch` | Unchanged in this round. Implements `POST /api/auth/native/google`, reusing `verifyGoogleIdentity` and `resolveUser`. |

`core/database/schemas/com.velnox.core.database.VelnoxDatabase/1.json` is untracked and
**not** part of this change: it is a Room schema export regenerated by any local build
(`exportSchema = true` + `room.schemaLocation` in `core/database/build.gradle.kts`).

## Configuration required (Google Cloud — owner action)

Android OAuth clients must exist in the **same Google Cloud project** as the web client id
used as `serverClientId`. One client per app, all sharing the committed key:

| Package name | SHA-1 |
|---|---|
| `com.velnox.velshop.debug` | `13:83:2C:F0:C2:04:D5:FC:8E:CF:AE:52:BD:4D:E9:8C:3C:2A:76:8C` |
| `com.velnox.velseller.debug` | `13:83:2C:F0:C2:04:D5:FC:8E:CF:AE:52:BD:4D:E9:8C:3C:2A:76:8C` |
| `com.velnox.velcenter.debug` | `13:83:2C:F0:C2:04:D5:FC:8E:CF:AE:52:BD:4D:E9:8C:3C:2A:76:8C` |

SHA-256, if the console asks: `B7:2C:6E:FF:9E:BB:E6:ED:56:8B:5D:9F:8F:C6:A2:DC:58:98:ED:D0:7D:33:BF:CC:22:D1:25:71:03:D1:CC:0A`

No release client can be registered yet: release builds are produced unsigned, so they have
no certificate. When a release key exists, register its SHA-1 against the three non-`.debug`
package names.

The Google Cloud Console itself cannot be read from this workspace, so the *existence* of
these clients is the one thing that remains unverified — it can only be confirmed from the
account that owns the project.

## Tests performed

* Two CI artifact sets downloaded and inspected with `aapt2` and `apksigner`; package names
  and certificate fingerprints read from the artefacts rather than from logs (tables above).
* The APK's compiled client id and the absence of any Google client secret read out of the
  dex files.
* `bun tools/verify-android-config.ts` — passes; and each new check was shown to fail on a
  deliberately broken copy (keystore deleted, keystore replaced with a non-JKS, an app
  detached from the keystore, the `debug` `signingConfig` removed, `keytool -genkeypair`
  reintroduced, the APK read-back removed).
* `bun x tsc -b --noEmit` — clean.
* Live backend probes (table in Diagnosis), run twice on separate days.
* Gradle locally: **not** a complete run. This sandbox has 2 GB RAM, one core and no swap,
  and `:core:ui:compileDebugKotlin` (Kotlin + KSP + Compose) does not finish in it — the same
  reason the earlier Android work was verified through CI. Nothing in this change weakens
  the local build; CI runs the full `testDebugUnitTest`, `lintDebug` and three `assembleDebug`
  tasks on a 4-core runner, and it is the authoritative build system for this repository.
* CI: green on `fee7f7b` (run `35654696973`), and on this change (see below).
* The keystore's own fingerprint was read with `keytool -list -v` here, and the
  APK-to-fingerprint match is asserted by the workflow's `apksigner verify --print-certs`
  step on every run, so the value reported for Google registration is always the value the
  shipped APKs are actually signed with.

## Remaining issue

1. **`POST /api/auth/native/google` is still `404` on the deployment.** Android cannot
   complete a sign-in until the marketplace patch is applied and deployed. That repository
   is outside this workspace and refuses this workspace's credential (`403`).
2. **The three Android OAuth clients are not registered** (owner-only action; the console is
   not reachable from here). Until they exist, Google answers the account request with no
   credential and the app reports exactly that.
3. No real-device test is possible from this workspace — there is no Android device and no
   Google account here, and CI cannot drive an account chooser. The *code path* to the
   chooser is verified; the chooser itself is not.

## Next action

In order:

1. Register the three Android OAuth clients in the Google Cloud project that owns
   `610153333350-…` (one client per package, SHA-1 above).
2. Apply and deploy the backend patch:
   ```bash
   git -C <velnox-marketplace> am patches/velnox-marketplace-0001-native-google-signin.patch
   git -C <velnox-marketplace> push origin main
   ```
3. Install the CI APK on a device with a Google account and run through
   `Continue with Google → chooser → ID token → POST /api/auth/native/google → /api/auth/me`.
   Each stage now has its own message, so a stall names the stage that failed instead of
   reporting "no account on device".
