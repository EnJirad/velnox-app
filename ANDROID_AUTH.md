# ANDROID_AUTH — Native authentication for Velnox

## How Velnox web authenticates today

Verified in `EnJirad/velnox-marketplace`:

* `backend/routes/auth.ts` — `GET /auth/google` → Google consent → `/auth/google/callback`
  exchanges the code server-side, verifies the ID token's audience against
  `GOOGLE_CLIENT_ID`, resolves identity, then sets a JWT with a unique `jti` in an
  **httpOnly `velnox_session` cookie**.
* `backend/middleware/auth.ts` — `requireAuth` reads `req.cookies.velnox_session`,
  verifies the signature, and rejects any `jti` present in `revoked_tokens`.
* Identity resolution is transactional: `auth_identities (provider, provider_id)` →
  normalised `users.email` → create. One person never gets two user rows.
* `POST /api/auth/member-login` — VelCenter staff sign-in by e-mail **or** employee id
  plus password; requires `role ∈ {owner, admin, staff}` and an active account.

There is no bearer-token or refresh-token flow, and no OAuth client for Android.

## What the Android app does

### Session storage and transport

`core:storage/SecureTokenStore` keeps the same JWT the browser keeps, encrypted with an
AES-256-GCM key held in the **Android Keystore** (`EncryptedSharedPreferences`). It is
excluded from cloud backup and device transfer (`data_extraction_rules.xml`), because a
session that follows a backup silently extends the credential's reach to a device the
user never signed in on.

`core:auth/VelnoxSessionCookieJar` projects that one token into the exact cookie the
backend reads, and `AuthHeaderInterceptor` attaches both:

```
Cookie: velnox_session=<jwt>
Authorization: Bearer <jwt>
```

The cookie is what makes the app work against the **unmodified** backend, because
`requireAuth` reads it exactly as it does for a browser. It also matters for
correctness, not just compatibility:

* `backend/middleware/rate-limit.ts` keys its per-user bucket on
  `req.cookies.velnox_session` and falls back to IP without it — without the cookie every
  Android request would share the much lower anonymous IP budget.
* `backend/realtime/index.ts` authenticates the `/ws` handshake from the same cookie.

**No `Origin` header is ever sent.** `backend/middleware/origin-guard.ts` accepts requests
without an Origin as non-browser clients ("curl, Stripe webhook, **mobile app**") and
rejects anything else with 403, so a native client must not pretend to be one of the
allowlisted web origins.

### Signing in on device

Custom Tabs cannot hand cookies back to the app, and an embedded WebView is both
prohibited by this project's rules and blocked by Google. The supported native
equivalent is **Credential Manager** (`core:auth/NativeGoogleSignIn`), which returns a
Google **ID token**.

`velnox.google.webClientId` is passed as the Credential Manager `serverClientId`. That
value is the Velnox *web* client id — a public value that already ships in the web
bundles — which makes the resulting token's `aud` match the backend's existing
`GOOGLE_CLIENT_ID`, so **the backend's audience check needs no change**.

It is configured rather than guessed, and the value was read from the backend itself
(`GET /auth/google` redirects to Google with `client_id` in the URL — a public value, and
the same one the server holds in `GOOGLE_CLIENT_ID`):

| Where | What |
|-------|------|
| `gradle.properties` | Checked-in default. A blank or missing value is the bug that makes the app report "sign-in is unavailable" — it is never a fallback to a fake login. |
| `-Pvelnox.google.webClientId=…`, `local.properties` | Per-machine / per-environment override. A **blank** override falls through to the default instead of erasing it (`gradle/velnox-properties.gradle.kts`). |
| `VELNOX_GOOGLE_WEB_CLIENT_ID` | GitHub **repository variable** for CI. A variable, not a secret: an OAuth web client id is a public identifier. The value that must never leave the server is `GOOGLE_CLIENT_SECRET`, which this repository forbids. |

`tools/verify-android-config.ts` fails the build when the effective client id is empty or
malformed, and a CI step re-checks the generated `BuildConfig.java`, so a build that
cannot sign anyone in cannot pass as a successful one.

#### Registering the app with Google

A web client id is only half of the story. Google authorises a native request from the
**(package name, signing certificate SHA-1)** pair, so each app needs an *Android* OAuth
client **in the same Google Cloud project as that web client id**. Debug builds carry an
`applicationIdSuffix`, which changes the package name that is actually installed:

| App | Debug package | Release package |
|-----|---------------|-----------------|
| VelShop | `com.velnox.velshop.debug` | `com.velnox.velshop` |
| Velseller | `com.velnox.velseller.debug` | `com.velnox.velseller` |
| VelCenter | `com.velnox.velcenter.debug` | `com.velnox.velcenter` |

The debug certificate is committed at `signing/velnox-debug.keystore` and used by every
debug build, on CI and on a developer machine alike, so the fingerprint does not move and the
registration below is a **one-time** action. (`ANDROID_BUILD.md` § Signing explains why a
*debug* key is committed while a release key must never be.) Read it from the artefact rather
than from memory:

```bash
./gradlew :app:velshop:assembleDebug
"${ANDROID_HOME}/build-tools/35.0.0/apksigner" verify --print-certs \
  app/velshop/build/outputs/apk/debug/velshop-debug.apk
```

The three clients to create in the Velnox Google Cloud project — one per app, because the
package name differs even though all three share the committed key:

| Package name | SHA-1 certificate fingerprint |
|---|---|
| `com.velnox.velshop.debug` | `13:83:2C:F0:C2:04:D5:FC:8E:CF:AE:52:BD:4D:E9:8C:3C:2A:76:8C` |
| `com.velnox.velseller.debug` | `13:83:2C:F0:C2:04:D5:FC:8E:CF:AE:52:BD:4D:E9:8C:3C:2A:76:8C` |
| `com.velnox.velcenter.debug` | `13:83:2C:F0:C2:04:D5:FC:8E:CF:AE:52:BD:4D:E9:8C:3C:2A:76:8C` |

and the matching SHA-256, should the console ask for it:

```
B7:2C:6E:FF:9E:BB:E6:ED:56:8B:5D:9F:8F:C6:A2:DC:58:98:ED:D0:7D:33:BF:CC:22:D1:25:71:03:D1:CC:0A
```

There are **no release clients to register yet**: release builds are produced unsigned, so
they have no certificate to declare. Add the three non-`.debug` entries once you sign a
release build with a release key you control — that key must be a different one, and the
debug key must never be used to sign anything distributed.

CI reports these same values (`Report the Google Sign-In registration values`) and then
proves them against the artefacts (`Verify the shipped APKs are signed by the expected
certificate`), so the table above can be re-derived from any workflow log instead of being
trusted. Without these clients Google issues no credential to the app at all, and Credential
Manager reports that refusal exactly the way it reports a device with no account — which is
why this step is not optional, and why the app's message names both possibilities rather
than guessing one.

### The one backend addition required

**Status: implemented in source, not deployed.** Verified against the live backend rather
than assumed — the endpoint answers `404` on the live deployment, so it starts working
only once the backend change below is deployed. It exists and passes `tsc --noEmit` and
`bun test backend/tests` in `EnJirad/velnox-marketplace`, delivered as:

```
patches/velnox-marketplace-0001-native-google-signin.patch
```

The patch could not be pushed from this workspace — the managed GitHub credential is
scoped to `velnox-app` and GitHub answered `403 Permission to EnJirad/velnox-marketplace
.git denied`. Apply it with:

```bash
git -C <velnox-marketplace> am patches/velnox-marketplace-0001-native-google-signin.patch
git -C <velnox-marketplace> push origin main
```

The contract it implements, and the claim checks that make it safe:

| | |
|---|---|
| Request | `POST {base}/api/auth/native/google` · body `{ idToken, nonce, platform? }` |
| `nonce` | **Required.** The value the app requested; Google echoes it into the token's `nonce` claim. A missing or mismatched nonce is refused. |
| Response | `{ success: true, data: { token, expiresInSeconds, user } }` — `token` is the same JWT the browser receives in the `velnox_session` cookie, which the endpoint also sets. |
| Rejections | `400 VALIDATION_ERROR` · `401 INVALID_GOOGLE_TOKEN` / `GOOGLE_AUDIENCE_MISMATCH` / `GOOGLE_ISSUER_MISMATCH` / `GOOGLE_TOKEN_EXPIRED` / `GOOGLE_NONCE_MISMATCH` · `403 ACCOUNT_DISABLED` · `500 DB_ERROR` |

Verified on every request: signature (`tokeninfo`, against Google's published keys),
audience (`aud === GOOGLE_CLIENT_ID`), issuer (`accounts.google.com`), expiry (explicitly,
not inferred from `tokeninfo`'s status code) and the nonce. Identity is then resolved by
the same transactional `resolveUser`, so account linking is unchanged.

| Request | Live result |
|---------|-------------|
| `POST {base}/api/auth/native/google` | `404 Cannot POST /api/auth/native/google` |
| `GET {base}/auth/google` | `302` to `accounts.google.com` with `client_id=…` — so `GOOGLE_CLIENT_ID` **is** configured server-side |
| `GET {base}/api/auth/me` | `401` — the auth surface itself is up |

The browser flow finishes server-side because the *code* exchange needs
`GOOGLE_CLIENT_SECRET`. A native client has an ID token, not a code, so the backend must
accept it. `verifyGoogleIdentity` (tokeninfo + `claims.aud === GOOGLE_CLIENT_ID`) and
`resolveUser` (identity resolution) already do exactly the right thing, so the addition
reuses both and cannot create a second kind of account. The sketch below is the original
shape of the addition; the shipped patch is stricter than it (it adds the issuer, expiry
and nonce checks, the coded `GoogleTokenError`, and the required `nonce` field) and is the
one to apply. For reference, it sits next to the existing routes in
`backend/routes/auth.ts`:

```ts
/**
 * POST /api/auth/native/google
 * Body: { idToken: string }
 * Exchanges a Google ID token obtained on-device for a Velnox session.
 * Reuses the SAME identity resolution and the SAME JWT as the browser flow.
 */
app.post("/api/auth/native/google", async (req, res) => {
  const idToken = typeof req.body?.idToken === "string" ? req.body.idToken : "";
  if (!idToken) {
    res.status(400).json({ success: false, error: { code: "VALIDATION_ERROR", message: "idToken is required" } });
    return;
  }

  // Reuses verifyGoogleIdentity: tokeninfo + `claims.aud === GOOGLE_CLIENT_ID`.
  const googleUser = await verifyGoogleIdentity(idToken);

  // Same transactional resolution as the OAuth callback — one person, one user row.
  const { userId } = await resolveUser(googleUser);

  const userRow = await query(
    "SELECT id, email, name, avatar, role, status, department, must_change_password FROM users WHERE id = $1",
    [userId],
  );
  const u = userRow.rows[0];
  if (!u) {
    res.status(404).json({ success: false, error: { code: "NOT_FOUND", message: "User not found" } });
    return;
  }
  if (u.status !== "active") {
    res.status(403).json({ success: false, error: { code: "ACCOUNT_DISABLED", message: "Account is disabled" } });
    return;
  }

  const token = createSessionToken(u.id, u.email);
  setSessionCookie(res, token); // web clients keep working unchanged

  res.json({
    success: true,
    data: {
      token,
      expiresInSeconds: 7 * 24 * 60 * 60,
      user: { id: u.id, email: u.email, name: u.name, avatar: u.avatar, role: u.role,
              status: u.status, department: u.department ?? null,
              mustChangePassword: u.must_change_password === true },
    },
  });
});
```

Also return the token from `member-login` so VelCenter can sign in natively — the
existing endpoint already builds one, it just does not put it in the body:

```ts
// inside POST /api/auth/member-login, replacing the final res.json({ ... })
res.json({
  success: true,
  data: { userId: userRow.id, email: userRow.email, name: userRow.name,
          role: userRow.role, department: userRow.department, token: sessionToken },
});
```

Both additions are additive: the cookie is still set, the web flows are untouched, no
token gets a longer lifetime, and revocation through `revoked_tokens` applies identically.

**Until that patch is deployed, native sign-in stops at the backend and reports a
configuration error.** The app never falls back to a locally minted credential, a
hardcoded user, or a bypass — the brief forbids all three, and a client that mints its
own session is not an authentication system. The user-visible outcomes are therefore:

| State | What the user sees |
|-------|--------------------|
| No client id in the build | "This build has no Google Client ID configured, so sign-in is unavailable" — a build problem, fixed by configuration, not by code |
| Client id present, backend endpoint absent (`404`) | The account sheet completes, then a normal error message: the server build cannot issue a native session |
| Client id present, Android OAuth client not registered for this package + SHA-1 | Credential Manager refuses; no token is ever produced |
| All three in place | Google ID token → backend verifies it → Velnox session |

## Session lifecycle in the app

`AuthRepository` exposes `AuthState`, and `VelnoxSessionGate` is the single component that
decides what an app shows for it:

| State | Meaning | UI |
|-------|---------|----|
| `Restoring` | A persisted token is being validated against `GET /api/auth/me`. | Loader — **not** the sign-in screen. |
| `Unverified` | A token exists but `/me` was unreachable (offline, timeout, 5xx). | Retry screen. The token is **kept**; a tunnel must not sign anyone out. |
| `Unauthenticated` | No token, or the server answered 401/404 for it. | Sign-in screen. |
| `Authenticated(user)` | Identity confirmed by the backend. | The app. |

`requireAuth` answering 401 triggers `AuthProvider.onSessionRejected()`, which clears the
token, drops the realtime socket and returns the app to `Unauthenticated`. Sign-out calls
`POST /api/auth/logout` (which revokes the `jti` server-side and closes that user's live
sockets) and clears local state **even if that call fails** — the local token is
discarded either way, and the failure is surfaced so the user knows the session may
linger elsewhere until it expires.

## What is banned here

No fake login. No mock authentication. No bypassed authorization. No hardcoded user or
token. No `DATABASE_URL`, `JWT_SECRET`, `GOOGLE_CLIENT_SECRET`, `R2_*`, or
`BOOTSTRAP_OWNER_SECRET` anywhere in this repository or in a built APK. The only
secret-adjacent value handled on device is the operator-entered bootstrap code, which is
never stored, and `VelnoxLog`'s redactor masks it by pattern regardless.
