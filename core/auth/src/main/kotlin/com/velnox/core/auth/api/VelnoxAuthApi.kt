package com.velnox.core.auth.api

import com.velnox.core.auth.dto.ChangePasswordRequest
import com.velnox.core.auth.dto.MeDataDto
import com.velnox.core.auth.dto.MemberLoginDataDto
import com.velnox.core.auth.dto.MemberLoginRequest
import com.velnox.core.auth.dto.NativeGoogleLoginRequest
import com.velnox.core.auth.dto.NativeSessionDto
import com.velnox.core.network.ApiAck
import com.velnox.core.network.ApiEnvelope
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

/**
 * Authentication endpoints, relative to the Retrofit base URL
 * (`https://<host>/api/`), so paths here start with `auth/`.
 *
 * Every one of these already exists in `backend/routes/auth.ts` except
 * `auth/native/google`, which is the single documented addition a native client
 * needs. See `ANDROID_AUTH.md`.
 */
interface VelnoxAuthApi {

    /** `GET /api/auth/me` — current identity for the presented session. */
    @GET("auth/me")
    suspend fun me(): ApiEnvelope<MeDataDto>

    /**
     * `POST /api/auth/native/google` — exchange a Google ID token for a Velnox
     * session. Same identity resolution, same JWT, same revocation semantics as
     * the browser OAuth callback.
     */
    @POST("auth/native/google")
    suspend fun signInWithGoogleIdToken(
        @Body body: NativeGoogleLoginRequest,
    ): ApiEnvelope<NativeSessionDto>

    /**
     * `POST /api/auth/member-login` — password sign-in for VelCenter staff.
     * Reachable with an employee id as well as an email address.
     */
    @POST("auth/member-login")
    suspend fun memberLogin(
        @Body body: MemberLoginRequest,
    ): ApiEnvelope<MemberLoginDataDto>

    /**
     * `POST /api/auth/logout` — revokes the JWT server-side by `jti`, so a copied
     * token stops working immediately (`revoked_tokens`).
     */
    @POST("auth/logout")
    suspend fun logout(): ApiEnvelope<ApiAck>

    /** `POST /api/auth/change-password` — authenticated self-service. */
    @POST("auth/change-password")
    suspend fun changePassword(
        @Body body: ChangePasswordRequest,
    ): ApiEnvelope<ApiAck>
}
