package com.velnox.core.network.di

import javax.inject.Qualifier

/** OkHttp client that attaches the Velnox session and client headers. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class VelnoxHttpClient

/**
 * Plain OkHttp client with no Velnox credentials.
 *
 * Used for presigned R2 uploads and for Coil image loading. Sending the session
 * to Cloudflare would break an AWS SigV4 presigned PUT, and sending it to a CDN
 * would leak it to a third party.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class PlainHttpClient

/** Retrofit instance bound to [VelnoxHttpClient]. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class VelnoxRetrofit

/** Backend `HttpUrl`, e.g. `https://velnox-api.onrender.com/api/`. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class VelnoxBaseUrl

/** Same origin as [VelnoxBaseUrl] but without the `/api/` path — used for `/ws`. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class VelnoxApiOrigin
