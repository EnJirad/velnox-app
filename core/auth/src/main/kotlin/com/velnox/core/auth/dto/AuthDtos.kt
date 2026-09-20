package com.velnox.core.auth.dto

import com.velnox.core.network.serialization.FlexibleEpochMillisSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `data.user` of `GET /api/auth/me`.
 * Field names match `backend/routes/auth.ts` exactly (camelCase in JSON).
 */
@Serializable
data class UserDto(
    val id: String,
    val email: String = "",
    val name: String = "",
    val avatar: String? = null,
    val coverUrl: String? = null,
    val role: String = "customer",
    val status: String = "active",
    val department: String? = null,
    val mustChangePassword: Boolean = false,
    val permissions: List<String> = emptyList(),
    @Serializable(with = FlexibleEpochMillisSerializer::class)
    val createdAt: Long = 0L,
    @Serializable(with = FlexibleEpochMillisSerializer::class)
    val updatedAt: Long = 0L,
)

@Serializable
data class MeDataDto(val user: UserDto)

/**
 * Body of `POST /api/auth/native/google`.
 *
 * The Google **ID token** obtained on-device by Credential Manager. It is a
 * short-lived, audience-restricted assertion for this OAuth client — not a
 * session and not a client secret. The backend verifies it with Google and
 * performs the same identity resolution as the browser flow.
 */
@Serializable
data class NativeGoogleLoginRequest(
    val idToken: String,
    /** Optional convenience hint; the backend resolves identity itself. */
    val platform: String = "android",
)

/**
 * `data` of `POST /api/auth/native/google`.
 *
 * `token` is the same JWT the browser receives in the `velnox_session` cookie.
 * It is absent on an unpatched backend, which is surfaced as an explicit
 * configuration error rather than a silent fallback (see `AuthRepository`).
 */
@Serializable
data class NativeSessionDto(
    val token: String? = null,
    val user: UserDto? = null,
    val expiresInSeconds: Long? = null,
)

/** Body of `POST /api/auth/member-login` (VelCenter staff password sign-in). */
@Serializable
data class MemberLoginRequest(
    /** E-mail address or employee id — the backend accepts either. */
    val identifier: String,
    val password: String,
)

/**
 * `data` of `POST /api/auth/member-login`.
 *
 * The backend already returns the identity fields; `token` is added by the native
 * support patch and is `null` against an unpatched deployment.
 */
@Serializable
data class MemberLoginDataDto(
    val userId: String,
    val email: String = "",
    val name: String = "",
    val role: String = "staff",
    val department: String? = null,
    val token: String? = null,
)

@Serializable
data class ChangePasswordRequest(
    @SerialName("currentPassword") val currentPassword: String,
    @SerialName("newPassword") val newPassword: String,
)
