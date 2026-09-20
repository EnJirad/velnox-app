# MOBILE_ARCHITECTURE — Velnox Native Android

Native Android clients for the Velnox marketplace, replacing nothing: the web apps in
`EnJirad/velnox-marketplace` remain the reference and the backend is unchanged.

```
                        Velnox Web (reference)
                                │
                        Velnox Mobile
                                │
        ┌───────────────────────┼───────────────────────┐
        ▼                       ▼                       ▼
     VelShop                Velseller                VelCenter
   com.velnox.velshop   com.velnox.velseller     com.velnox.velcenter
        │                       │                       │
        └───────────────────────┼───────────────────────┘
                                │  HTTPS + velnox_session
                                ▼
                       Velnox Backend API
                                │
                     ┌──────────┴──────────┐
                     ▼                     ▼
                 Neon DB            Cloudflare R2
```

## Hard rules this architecture encodes

| Rule | Where it is enforced |
|------|----------------------|
| No direct database access from the app | There is no Postgres driver in any module's dependencies. |
| No secret in the APK | Only `velnox.api.baseUrl` and `velnox.google.webClientId` are compiled in, both public values that already ship in the web bundles. |
| Neon is the only commerce source of truth | `core:data` holds no state; `core:database` tables are labelled projections with a `cachedAtEpochMillis` stamp. |
| UI is never a security boundary | Role/permission checks exist in the UI only to hide controls; every endpoint re-checks server-side. |
| No fabricated data on failure | `safeApiCall` treats a missing payload as an error, and `VelnoxScreenState` has no way to render content from a failure. |

## Module graph

```
core/common      error model, paging, dispatchers, commerce enums, formatting
core/logging     redacting logger
core/network     Retrofit/OkHttp, envelope, error mapping, retry, connectivity
core/storage     Keystore session token, DataStore preferences
core/database    Room read-through cache
core/auth        session owner, Google sign-in, AuthRepository
core/realtime    WebSocket client, lifecycle controller
core/ui          design system + the screens shared by all three apps
core/data        DTOs, API surfaces, domain models, repositories
app/velshop      com.velnox.velshop
app/velseller    com.velnox.velseller
app/velcenter    com.velnox.velcenter
```

Dependencies only ever point one way:

```
app/*  →  core/ui → core/data → core/{auth,realtime} → core/{network,storage,database} → core/{common,logging}
```

`core:ui` depends on `core:auth` and `core:data` because it also owns the sign-in screen
and the session gate, which are literally shared by all three apps. Nothing depends on
`core:ui`, so the graph stays acyclic.

## Clean Architecture + MVVM mapping

The brief asks for `presentation / domain / data` inside each app plus a shared `core/`.
Duplicating DTOs, mappers and repositories three times would be the "duplicate business
logic without reason" the brief forbids, so the layering is split by ownership:

| Layer | Lives in | Contains |
|-------|----------|----------|
| Presentation | `app/*/presentation/<feature>/` | Screen composable + its `ViewModel`, one file per feature |
| Domain (models & rules) | `core:common` (enums, ordering rules) and `core/data/model` (entities) | Status machines, purchasability rules, formatting |
| Data (remote) | `core:data/{api,dto,repository}` | Retrofit surfaces, DTOs, mappers, repositories |
| Data (local) | `core:database`, `core:storage` | Cache projections, encrypted token, preferences |
| Domain (use cases) | `app/*/presentation/<feature>/*ViewModel` methods | Orchestration specific to that app's flow |

Each app still has `domain`-level logic where it is genuinely app-specific (Velshop's
cart arithmetic, Velseller's lifecycle transitions, VelCenter's decision reasons), but a
shared mapping means an order badge cannot disagree between the three apps.

### Why screen and ViewModel share a file

One feature = one file (`BrowseScreen.kt` contains `BrowseScreen` + `BrowseViewModel`).
This is deliberate: the two are always changed together, the ViewModel is never reused
across screens, and it keeps "where is the state for this screen?" answerable without a
search. Genuinely shared logic is pushed down into `core:data`, never into a shared
ViewModel.

## State management

* `ViewModel` + `StateFlow` only. **No mutable business state in an Activity or
  Composable.**
* Screen state is a single immutable `UiState` data class per feature, updated with
  `MutableStateFlow.update`, so a recomposition can never observe a half-applied change.
* `VelnoxScreenState<T>` (`Loading` / `Empty` / `Content` / `Failure`) makes the mandatory
  Loading, Error, Retry, Offline, Empty and Success states structural rather than
  something a screen author has to remember.
* No global mutable singletons. The only process-wide state is `SessionManager`
  (deliberate, see ANDROID_AUTH.md) and the Room cache.

## Offline behaviour

| Data | Offline behaviour |
|------|-------------------|
| Category tree | Served from cache, labelled as cached (no prices involved). |
| Catalogue first page | Served from cache **with an offline banner**; never presented as live. |
| Cart contents | Read from cache; every mutation requires the network and fails honestly. |
| Order lists | Read from cache under a scope key; order **detail** always re-fetches. |
| Checkout, order status, product writes | Never queued. No outbox exists on purpose — see `core:database/VelnoxDatabase`. |

## Realtime

`core:realtime` is an optimisation, never a dependency. Frames are delivered to whoever
subscribes, and the documented reaction is to **re-read the REST endpoint**, because
WebSocket state is not persistent state. The socket is connected only while signed in and
in the foreground, and stops permanently on the server's `4001 Session revoked` close
code instead of reconnecting against a dead session.

## Performance decisions

* `LazyColumn` with `key = { it.id }` everywhere a list exists.
* Catalogue paging uses the API's own `offset`/`limit`; a single in-flight page request
  is enforced so a fling cannot fire a burst.
* Search input is debounced (350 ms) before it becomes a request.
* Images go through one shared Coil loader with a bounded memory cache (25 % of the
  process budget) and a 64 MB disk cache.
* The socket is closed in the background rather than kept alive.
* No animated placeholders in lists: the jank cost is not worth the decoration.

## Current state

Every module listed above exists and every screen below is implemented. There are no
placeholder screens and no route that resolves to a stub: a route that exists in a
`*NavHost` has a real screen behind it, because a half-wired route is worse than an
absent one — it looks finished and fails on the tap.

| App | Screens |
|-----|---------|
| VelShop | auth, browse (paging + filters + search), product detail, cart, checkout, orders, account |
| Velseller | auth, seller gate (apply / pending / rejected / suspended), application form with R2 document upload, overview (income + goals), products, orders, account |
| VelCenter | auth, dashboard, seller approvals, product moderation, orders, account |

Screens that only these apps need live in the app module; anything that two apps would
duplicate (the sign-in screen, the session gate, the design system, profile and address
editing) lives in `core:ui` / `core:data`.

### Order of work, if this is extended

1. Instrumented tests for the three auth flows (the one thing a JVM test cannot cover).
2. Release signing through a GitHub-stored keystore, or an `apksigner` step in CI.
3. Push notifications (FCM) layered on top of the existing realtime client — the socket
   only delivers while the app is foregrounded, so notifications are additive.

## Build entry points

```bash
./gradlew :app:velshop:assembleDebug :app:velseller:assembleDebug :app:velcenter:assembleDebug
bun tools/verify-android-config.ts   # ids unique, catalog resolves, no secret names
```

See `ANDROID_BUILD.md` for the toolchain and the CI contract, and `ANDROID_API.md` for the
endpoint surface each repository talks to.
