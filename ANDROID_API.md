# ANDROID_API — Velnox API contract used by the Android clients

Every endpoint below exists in `EnJirad/velnox-marketplace` and was read from source
(`backend/routes/*.ts`, `packages/shared/src/lib/api-routes.ts`,
`packages/shared/src/lib/commerce.ts`) rather than inferred. The only addition is
`POST /api/auth/native/google`, documented in `ANDROID_AUTH.md`.

## Base URL and envelope

```
Base URL : {velnox.api.baseUrl}/api/
Success  : { "success": true,  "data": <payload> }
Failure  : { "success": false, "error": { "code": "...", "message": "..." } }
```

`error.message` is already localised by the backend, so the app prefers it over its own
copy. `data` may legitimately be `null` with `success: true`; that case is handled
explicitly (see `safeApiCallAllowNull` below) and is never coerced into an empty success.

Everything is reached over HTTPS; cleartext is refused app-wide by
`network_security_config.xml`.

## Catalogue (`core:data/api/VelnoxCatalogApi.kt`)

| Method | Path | Auth | Notes |
|--------|------|------|-------|
| GET | `products/catalog` | optional | Returns an **array** in `data`. Filters: `q`, `category` (slug **or** uuid — the backend normalises), `shopId`, `minPrice`, `maxPrice`, `inStock`, `verified`, `sortBy`, `limit` (≤200), `offset`. |
| GET | `products/{productId}` | optional | Published products only; 404 otherwise. Returns images, `detailImages`, variants, `optionGroups`, inventory, shop info. |
| GET | `categories?lang=` | none | Returns Postgres rows directly, so the wire names stay **snake_case** (`parent_id`, `sort_order`, `is_active`, `display_name`, `image_url`). |
| GET | `categories/tree?lang=` | none | Same rows nested under `children`. |
| GET | `shops` | none | Public shops. |
| GET | `shops/{shopId}` | none | Shop detail. |

Products are formatted by `formatProduct` in `backend/routes/products.ts`, which is the
authority for field names and for `primaryImage`, `inventory.available` and
`sellerVerificationStatus`.

`sortBy` accepts `price_asc`, `price_desc`, `popular`; anything else falls back to
`created_at DESC` server-side.

## Commerce (`core:data/api/VelnoxCommerceApi.kt`)

| Method | Path | Auth |
|--------|------|------|
| GET | `customer/cart` | ✔ |
| POST | `customer/cart/add` | ✔ |
| PUT | `customer/cart/item/{cartItemId}` | ✔ |
| DELETE | `customer/cart/item/{cartItemId}` | ✔ |
| POST | `customer/checkout` | ✔ |
| GET | `customer/orders?limit=` | ✔ |
| GET | `customer/orders/{orderId}` | ✔ |
| PATCH | `customer/orders/{orderId}/cancel` | ✔ |
| GET / PUT | `customer/profile` | ✔ |
| GET / POST | `customer/addresses` | ✔ |
| PUT / DELETE | `customer/addresses/{addressId}` | ✔ |
| GET | `customer/wishlist` | ✔ |
| POST | `customer/wishlist/toggle` | ✔ |
| GET | `customer/notifications` | ✔ |
| PATCH | `customer/notifications/{id}/read` | ✔ |
| PUT | `customer/notifications/read-all` | ✔ |

Every cart mutation returns the **whole cart** (`data.items`), so the client replaces
state instead of patching it. Cart items are the objects `formatCartRow` builds:
`priceSnapshot`, `availableStock`, `variantOptionLabels`, `productImageUrl`.

### Checkout idempotency

`POST /api/customer/checkout` is deduplicated server-side through the
`checkout_requests` table. The client sends `idempotencyKey`, derived deterministically
from a per-attempt key, so retrying the *same* attempt reuses the identical key and a new
attempt gets a new one. The HTTP layer never retries non-idempotent methods, so checkout
can only be repeated from `CartRepository.checkout` and only with the same key.

Body: `{ idempotencyKey, addressId, paymentMethod: "cod" | "card", note? }`.
`cod` creates the order directly; `card` returns a Stripe checkout URL
(`backend/routes/stripe.ts`).

## Velseller (`core:data/api/VelnoxSellerApi.kt`)

| Method | Path | Notes |
|--------|------|-------|
| GET | `seller/status` | **`data: null` means "no application"**, not an error. |
| GET | `seller/profile` | `{ seller, shops }`. |
| POST | `seller/apply` | Sends R2 **object keys** for identity evidence, never bytes. |
| PATCH | `seller/shop` / `seller/shop/{shopId}/location` | Owner-checked server-side. |
| GET / POST | `seller/products` | |
| PATCH | `seller/products/{id}` / `/status` / `/stock` | Status changes go through `product-lifecycle.ts`. |
| DELETE | `seller/products/{id}` | |
| GET | `seller/orders?limit=` | |
| PATCH | `seller/orders/{orderId}/status` | Same state machine as `allowedNextStatuses`. |
| GET | `seller/income` | Server aggregate; the app never computes revenue locally. |
| GET / POST | `seller/goals` | |
| PATCH / DELETE | `seller/goals/{goalId}` | |

## VelCenter (`core:data/api/VelnoxCenterApi.kt`)

| Method | Path | Notes |
|--------|------|-------|
| GET | `admin/dashboard/counts` | Counts are nullable; a missing tile is rendered as unavailable, never as a confident zero. |
| GET | `admin/sellers?status=&q=` | |
| PATCH | `admin/sellers/{id}/status` | Approval promotes `users.role`, writes `audit_logs`, broadcasts `seller:updated`; `rejected`/`suspended`/`needs_correction` require a reason. |
| GET | `admin/products/moderation?status=&q=&shopId=` | |
| PATCH | `admin/products/{id}/moderation` | Rejection requires a reason. |
| GET | `admin/users?segment=` · PATCH `admin/users/{id}/access` | Partial update — only changed fields are sent. |
| GET / POST | `admin/employees` · POST `/employees/{id}/reset-password` · PATCH `/employees/{id}/active` | |
| GET | `admin/permissions` · PATCH `admin/staff` | Mirrors the permission codes the endpoints enforce. |
| GET | `admin/orders?limit=` · PATCH `admin/orders/{id}/status` | |
| GET | `admin/audit-logs?limit=&offset=&action=&entityType=&q=` | |
| GET / POST / PATCH / DELETE | `admin/categories` | Delete is refused while products or children reference the category. |
| GET / PATCH | `admin/settings` | |
| GET | `admin/bootstrap-status` · POST `admin/claim-owner` | The bootstrap secret is operator-entered, never stored. |

## Media — Cloudflare R2 (`core:data/api/VelnoxUploadApi.kt`)

```
POST /api/upload/presign  → { uploadUrl, objectKey, publicUrl }
PUT  <uploadUrl>          → bytes go straight to Cloudflare's storage
POST /api/upload/confirm  → backend verifies the object and writes the `media` row
```

Allowed `Content-Type`: `image/jpeg`, `image/png`, `image/webp`, `image/avif`.
Maximum size: 10 MB. Purposes are enumerated in `UploadPurpose`; the backend validates
them, so they are constants rather than free-form strings.

The `PUT` uses `@PlainHttpClient` — **no Velnox session is sent to Cloudflare**. Sending
the cookie or `Authorization` header would leak the session to a third party and would
break the AWS SigV4 signature on the presigned PUT. Confirm is only called after the PUT
succeeds, so the backend is never told about an object that was not written.

**The APK contains no R2 credential.** Signing happens server-side; the app only ever
handles a short-lived URL. Images are then loaded by Coil from the public domain, again
over the plain client (no Velnox headers).

## Realtime

```
wss://{host}/ws
→ { "type": "subscribe", "channel": "user:<userId>" }
← { "type": "subscribed", "channel": "..." }
← { "type": "<event>", "channel": "...", "data": {...}, "timestamp": "..." }
```

The handshake is authenticated from the `velnox_session` cookie. Public channels:
`cart:updated`, `order:created`, `order:updated`, `product:updated`,
`inventory:updated`, `seller:updated`, `audit:created`, `notification:created`. The only
private channel a client may subscribe to is its own `user:<userId>`; anything else is
refused with `{ "type": "error", "code": "FORBIDDEN" }`.

Per-connection limits: frames larger than 4 KB close with `1009`; more than 120 frames
per 10 s closes with `1008`. Close code `4001` means the session was revoked — the app
stops reconnecting and signs out.

Realtime is delivery only. Every consumer reacts by re-reading the corresponding REST
endpoint, because Neon is the source of truth.

## Error mapping

`safeApiCall` is the single place transport outcomes become domain failures:

| Condition | `AppError` |
|-----------|------------|
| HTTP 400 / 422 | `Validation` |
| HTTP 401 | `Unauthorized` → clears the session |
| HTTP 403 | `Forbidden` → no retry offered |
| HTTP 404 | `NotFound` → no retry offered |
| HTTP 409 | `Conflict` |
| HTTP 429 | `RateLimited` |
| Other 4xx/5xx | `Server(status, code, message)` |
| `UnknownHost` / `Connect` / SSL / plain `IOException` | `Offline` |
| `SocketTimeout` | `Timeout` |
| `SerializationException` | `Serialization` |
| `success: true` with no `data` | `Serialization` (contract violation) |

Retry applies to `GET`/`HEAD` only, and only to `429`/`502`/`503`/`504` or a transport
failure, with exponential backoff plus jitter (2 attempts, ≤5 s, `Retry-After` honoured).
`POST`, `PUT`, `PATCH` and `DELETE` are never retried — that is the rule that keeps a
flaky connection from replaying an order or a payment without an idempotency key.

## Field-shape tolerance

The backend sends timestamps as **either** ISO strings or epoch milliseconds
(`commerce.ts` documents this explicitly), and `node-postgres` returns `NUMERIC` columns
as **strings** while computed values arrive as numbers. `core:network/serialization`
handles both with `FlexibleEpochMillisSerializer`, `FlexibleDoubleSerializer`,
`FlexibleIntSerializer` and `FlexibleBooleanSerializer`, so a backend response can change
shape without breaking a screen. JSON is read with `ignoreUnknownKeys = true` so a new
backend field never requires an app release.
