package com.velnox.core.network.serialization

import kotlinx.serialization.json.Json

/**
 * The single JSON configuration used for every Velnox API payload.
 *
 *  * `ignoreUnknownKeys` — the backend adds fields without a mobile release;
 *    an unknown key must never break a screen.
 *  * `explicitNulls = false` — absent and null are treated identically, matching
 *    the backend's `undefined` optional fields in JSON.
 *  * `coerceInputValues` — a `null` where a non-null default exists falls back to
 *    the declared default instead of throwing.
 *  * `isLenient = false` — the backend emits strict JSON; a malformed body should
 *    surface as [com.velnox.core.common.error.AppError.Serialization], not be
 *    silently half-parsed.
 */
val VelnoxJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
    isLenient = false
    encodeDefaults = true
    allowStructuredMapKeys = false
}
