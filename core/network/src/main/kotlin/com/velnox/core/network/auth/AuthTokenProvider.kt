package com.velnox.core.network.auth

/**
 * The network layer's view of "who am I".
 *
 * Declared in `core:network` (not `core:auth`) so the dependency direction stays
 * one-way: `core:auth -> core:network`. `core:auth` binds its session manager to
 * this interface through Hilt.
 */
interface AuthTokenProvider {

    /**
     * Current session token, or `null` when signed out.
     *
     * This is a blocking, in-memory read: interceptors run on the OkHttp thread
     * and must not suspend or hit disk.
     */
    fun currentSessionToken(): String?

    /**
     * Called when the backend rejected the session (HTTP 401).
     *
     * Implementations must be idempotent and must not block — the call happens
     * on the OkHttp dispatcher thread. The session reset itself is asynchronous.
     */
    fun onSessionRejected()
}
