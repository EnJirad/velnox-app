package com.velnox.core.realtime

import com.velnox.core.auth.repository.AuthRepository
import com.velnox.core.auth.session.SessionManager
import com.velnox.core.common.di.ApplicationScope
import com.velnox.core.logging.VelnoxLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wires authentication and app lifecycle to the socket.
 *
 * Keeping this separate from [VelnoxRealtimeClient] leaves the client as plain
 * transport with no knowledge of auth or Android lifecycle, so the policy —
 * "connected only while signed in and in the foreground" — lives in one readable
 * place.
 *
 * Note the deliberate absence of any "fall back to polling" behaviour: when the
 * socket is down every screen still works, because they read from the API. Realtime
 * is an optimisation, never a dependency.
 */
@Singleton
class RealtimeLifecycleController @Inject constructor(
    private val client: VelnoxRealtimeClient,
    private val authRepository: AuthRepository,
    private val foregroundTracker: AppForegroundTracker,
    private val sessionManager: SessionManager,
    @ApplicationScope private val scope: CoroutineScope,
) {

    private var started = false

    /** Call once from `Application.onCreate`. */
    fun start() {
        if (started) return
        started = true

        foregroundTracker.start()

        scope.launch {
            combine(
                authRepository.authState.map { it.userOrNull?.id }.distinctUntilChanged(),
                // No distinctUntilChanged() here: `isForeground` is a StateFlow, which
                // already never re-emits an equal value, and calling the operator on a
                // StateFlow is a hard deprecation error (it is a documented no-op).
                foregroundTracker.isForeground,
            ) { userId, isForeground -> userId to isForeground }
                .collect { (userId, isForeground) -> applyState(userId, isForeground) }
        }

        // A revoked or expired session must take the socket down immediately instead
        // of waiting for the server's 30-second revocation sweep.
        sessionManager.onSessionRejected {
            VelnoxLog.i(TAG) { "Session rejected — closing realtime" }
            client.disconnect()
        }
    }

    private fun applyState(userId: String?, isForeground: Boolean) {
        when {
            userId == null -> client.disconnect()
            isForeground -> {
                client.resume()
                client.connect(userId)
            }
            else -> client.pause()
        }
    }

    private companion object {
        const val TAG = "RealtimeLifecycle"
    }
}
