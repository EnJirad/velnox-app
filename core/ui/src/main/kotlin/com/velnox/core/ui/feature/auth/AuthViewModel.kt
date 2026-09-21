package com.velnox.core.ui.feature.auth

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.velnox.core.auth.model.AuthState
import com.velnox.core.auth.repository.AuthRepository
import com.velnox.core.auth.signin.GoogleSignInOutcome
import com.velnox.core.auth.signin.NativeGoogleSignIn
import com.velnox.core.common.error.AppError
import com.velnox.core.common.error.VelnoxResult
import com.velnox.core.logging.VelnoxLog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Sign-in state for all three apps, plus VelCenter's staff password flow.
 *
 * The ViewModel owns no business state of its own: [AuthState] comes straight from
 * [AuthRepository], which is the single source of truth for who is signed in. This
 * class only translates user intent (tap, submit) into repository calls and turns the
 * result into a one-shot message for the screen.
 */
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val nativeGoogleSignIn: NativeGoogleSignIn,
) : ViewModel() {

    /**
     * Transient messages, consumed once by the UI.
     *
     * One case per distinguishable outcome. A single "Google failed" message would be
     * wrong for at least two of them, and the two it would conflate — no credential
     * versus no provider — have nothing in common: one is fixed in the Google Cloud
     * project, the other on the device.
     */
    sealed interface Message {
        data object GoogleSignInCancelled : Message
        data object GoogleNotConfigured : Message
        data object GoogleNoCredential : Message
        data object GoogleProviderUnavailable : Message
        data object GoogleTokenUnusable : Message
        data class Failure(val error: AppError) : Message
    }

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _message = MutableStateFlow<Message?>(null)
    val message: StateFlow<Message?> = _message.asStateFlow()

    /** `true` when this build can offer Google sign-in at all. */
    val isGoogleSignInConfigured: Boolean get() = nativeGoogleSignIn.isConfigured

    val authState: StateFlow<AuthState> = authRepository.authState

    /**
     * Runs the native Google flow and exchanges the ID token with the backend.
     *
     * An [Activity] is required because Credential Manager renders a system sheet.
     * Nothing about the resulting session is decided on device: the token is only
     * accepted if the backend verified it and issued a session.
     */
    fun signInWithGoogle(activity: Activity) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            try {
                when (val outcome = nativeGoogleSignIn.requestIdToken(activity)) {
                    is GoogleSignInOutcome.IdTokenObtained -> {
                        // The nonce goes with the token: the backend rejects a token whose
                        // `nonce` claim is not this attempt's value.
                        val result = authRepository.signInWithGoogleIdToken(
                            idToken = outcome.idToken,
                            nonce = outcome.nonce,
                        )
                        when (result) {
                            is VelnoxResult.Success -> Unit // authState now emits Authenticated
                            is VelnoxResult.Failure -> _message.value = Message.Failure(result.error)
                        }
                    }

                    GoogleSignInOutcome.Cancelled -> _message.value = Message.GoogleSignInCancelled
                    GoogleSignInOutcome.NotConfigured -> _message.value = Message.GoogleNotConfigured
                    GoogleSignInOutcome.NoCredentialAvailable ->
                        _message.value = Message.GoogleNoCredential
                    GoogleSignInOutcome.ProviderUnavailable ->
                        _message.value = Message.GoogleProviderUnavailable
                    is GoogleSignInOutcome.TokenUnusable ->
                        _message.value = Message.GoogleTokenUnusable
                    is GoogleSignInOutcome.Failed -> _message.value = Message.Failure(outcome.error)
                }
            } finally {
                _busy.value = false
            }
        }
    }

    /** VelCenter staff sign-in with an e-mail address or employee id. */
    fun signInStaff(identifier: String, password: String) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            try {
                val result = authRepository.signInStaff(identifier, password)
                if (result is VelnoxResult.Failure) _message.value = Message.Failure(result.error)
            } finally {
                _busy.value = false
            }
        }
    }

    /** Retry from the "could not verify session" screen. */
    fun retrySession() {
        viewModelScope.launch {
            VelnoxLog.i(TAG) { "Retrying session validation" }
            authRepository.restoreSession()
        }
    }

    fun signOut() {
        viewModelScope.launch { authRepository.signOut() }
    }

    fun consumeMessage() {
        _message.value = null
    }

    private companion object {
        const val TAG = "AuthViewModel"
    }
}
