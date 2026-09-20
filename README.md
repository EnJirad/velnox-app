# velnox-mobile

Native Android clients for the **Velnox** marketplace (`EnJirad/velnox-marketplace`),
written in Kotlin with Jetpack Compose. Three separate applications, one repository, one
Gradle build.

| App | Application ID | Who it is for |
|-----|----------------|---------------|
| **VelShop** | `com.velnox.velshop` | Customers — browse, cart, checkout, orders |
| **Velseller** | `com.velnox.velseller` | Shops — apply, sell, fulfil orders, track income |
| **VelCenter** | `com.velnox.velcenter` | Velnox staff — seller approvals, moderation, orders |

The three ids are distinct, so all three install side by side on one device.

## What this is not

This is not a WebView shell. There is no Capacitor, Cordova, React Native, Flutter or
embedded web bundle: every screen is Compose, talking to the existing Velnox backend over
HTTPS. The web apps remain the design and behaviour reference, and the backend and Neon
database are unchanged — this repository adds a client, not a second backend.

## Getting the APKs

Push to `main`, open a pull request, or run the workflow manually. GitHub Actions builds
all three apps in one run and publishes:

```
velnox-android-release.zip
├── VelShop.apk
├── Velseller.apk
└── VelCenter.apk
```

Download it from the **Artifacts** section of the workflow run (`velnox-android-release`).
A failure in any one app fails the whole run — there is no partial success and no
placeholder APK. See `ANDROID_BUILD.md` for the failure semantics and the signing policy
(CI produces **unsigned** APKs; signing is a deliberate distribution-time step).

## Building locally

```bash
cp local.properties.example local.properties   # set sdk.dir=…
./gradlew :app:velshop:assembleDebug :app:velseller:assembleDebug :app:velcenter:assembleDebug
```

Requires JDK 17 and Android SDK 35. Full toolchain table in `ANDROID_BUILD.md`.

## Repository layout

```
core/common      error model, paging, status machines, formatting
core/network     Retrofit + OkHttp, response envelope, retry, connectivity
core/auth        session owner, Google sign-in, auth repository
core/storage     Keystore-backed session token, DataStore preferences
core/database    Room read-through cache (projections, never a source of truth)
core/realtime    WebSocket client for live updates
core/ui          design system + the screens shared by all three apps
core/data        DTOs, API surfaces, domain models, repositories
app/velshop      ┐
app/velseller    ├ screens, navigation and ViewModels for each application
app/velcenter    ┘
tools/           build-configuration guard used locally and in CI
```

## Documentation

| File | Contents |
|------|----------|
| `MOBILE_ARCHITECTURE.md` | module graph, layering, state management, offline behaviour |
| `ANDROID_API.md` | every endpoint used, the response envelope, error mapping |
| `ANDROID_AUTH.md` | the authentication flow and the one backend addition it needs |
| `ANDROID_BUILD.md` | toolchain, local build, CI contract, signing |

## Before you commit

```bash
bun tools/verify-android-config.ts   # unique app ids, resolvable version catalog, no secrets
```

The same check runs as the first step of CI. It exists because two apps accidentally
sharing an `applicationId` makes one APK silently replace another on install — a mistake
that otherwise only shows up after a forty-minute build.

No credential of any kind belongs in this repository: no `DATABASE_URL`, no
`JWT_SECRET`, no OAuth client secret, no R2 key. The APK contains only the public backend
base URL and the public Google web client id.
