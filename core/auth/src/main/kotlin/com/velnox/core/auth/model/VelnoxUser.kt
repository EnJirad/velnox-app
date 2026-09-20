package com.velnox.core.auth.model

import com.velnox.core.common.error.AppError

/**
 * Role as stored in `users.role` by the Velnox backend.
 *
 * Values and their meaning come from the real system, not from guesswork:
 * `docs/ai/AUTH.md` ("Roles: customer, seller, admin/owner/staff") and
 * `packages/shared/src/components/RequireRole.tsx`
 * (`canCenter = owner || admin || staff`).
 */
enum class VelnoxRole(val wireValue: String) {
    Customer("customer"),
    Seller("seller"),
    Admin("admin"),
    Owner("owner"),
    Staff("staff"),

    /**
     * A role the backend introduced after this build shipped.
     *
     * Kept explicit rather than defaulting to [Customer]: silently showing a
     * customer-shaped UI to an unknown privileged role is a worse failure than an
     * explicit "unsupported account" screen.
     */
    Unknown("unknown"),
    ;

    /** VelCenter access — mirrors `RequireRole role="center"` on web. */
    val canAccessCenter: Boolean get() = this == Owner || this == Admin || this == Staff

    /** Velseller access — mirrors `RequireRole role="seller"`. */
    val canAccessSellerWorkspace: Boolean get() = this == Seller || canAccessCenter

    companion object {
        fun fromWire(value: String?): VelnoxRole = when (value?.lowercase()) {
            "customer" -> Customer
            "seller" -> Seller
            "admin" -> Admin
            "owner" -> Owner
            "staff" -> Staff
            else -> Unknown
        }
    }
}

/** `users.status` — a non-active user is refused by `/api/auth/member-login`. */
enum class VelnoxUserStatus {
    Active,
    Disabled,
    Unknown,
    ;

    companion object {
        fun fromWire(value: String?): VelnoxUserStatus = when (value?.lowercase()) {
            "active" -> Active
            "disabled", "suspended", "inactive" -> Disabled
            else -> Unknown
        }
    }
}

/**
 * The authenticated identity, shaped exactly like the `user` object returned by
 * `GET /api/auth/me` (`backend/routes/auth.ts`).
 *
 * [permissions] are the effective codes resolved server-side by
 * `resolvePermissions`. VelCenter uses them to hide what the user cannot do — and
 * never as a security boundary, because every endpoint re-checks.
 */
data class VelnoxUser(
    val id: String,
    val email: String,
    val name: String,
    val avatarUrl: String?,
    val coverUrl: String?,
    val role: VelnoxRole,
    val status: VelnoxUserStatus,
    val department: String?,
    val mustChangePassword: Boolean,
    val permissions: Set<String>,
    val createdAtEpochMillis: Long?,
    val updatedAtEpochMillis: Long?,
) {
    /** Display fallback so the UI never renders an empty name. */
    val displayName: String get() = name.ifBlank { email.substringBefore('@') }

    val initials: String
        get() = displayName
            .split(' ')
            .filter { it.isNotBlank() }
            .take(2)
            .map { it.first().uppercaseChar() }
            .joinToString("")
            .ifEmpty { "?" }

    val isOwner: Boolean get() = role == VelnoxRole.Owner

    fun hasPermission(code: String): Boolean = isOwner || code in permissions
}

/**
 * Authentication lifecycle for the whole app.
 *
 * Each state is real, not a spinner detail:
 *
 *  * [Restoring] — the app is deciding whether a persisted session is still valid
 *    by calling `/api/auth/me`. Rendering the sign-in screen here would flash a
 *    login form at an already signed-in user.
 *  * [Unverified] — a session token exists but could not be validated because the
 *    request failed (offline, timeout, 5xx). The token is deliberately **kept**:
 *    a network blip must not sign the user out, and the app must not claim an
 *    identity it has not confirmed. The UI shows a retry screen.
 *  * [Unauthenticated] — no usable session. Only reached when there is no token,
 *    or the backend answered 401/404 for it.
 */
sealed interface AuthState {
    data object Restoring : AuthState
    data class Unverified(val error: AppError) : AuthState
    data object Unauthenticated : AuthState
    data class Authenticated(val user: VelnoxUser) : AuthState

    val userOrNull: VelnoxUser? get() = (this as? Authenticated)?.user
}
