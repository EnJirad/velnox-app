package com.velnox.core.data.repository

import com.velnox.core.common.error.AppError
import com.velnox.core.common.error.VelnoxResult
import com.velnox.core.data.api.UploadContentType
import com.velnox.core.data.api.UploadPurpose
import com.velnox.core.data.api.VelnoxUploadApi
import com.velnox.core.data.dto.PresignRequest
import com.velnox.core.data.dto.UploadConfirmRequest
import com.velnox.core.logging.VelnoxLog
import com.velnox.core.network.di.PlainHttpClient
import com.velnox.core.network.safeApiCall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cloudflare R2 uploads, following the documented three-step flow
 * (`docs/ai/MEDIA.md`):
 *
 * ```
 * 1. POST /api/upload/presign   → short-lived signed PUT URL
 * 2. PUT  <signed url>          → bytes go straight to Cloudflare
 * 3. POST /api/upload/confirm   → backend verifies the object and records `media`
 * ```
 *
 * ## Security properties this class is responsible for
 *
 *  * **No R2 credential exists in the app.** Signing happens server-side; the app
 *    only ever handles a time-limited URL. `R2_SECRET_ACCESS_KEY` and friends are
 *    backend environment variables and are absent from the APK by construction — the
 *    build has no code path that could read them.
 *  * **The upload uses the plain HTTP client.** [PlainHttpClient] carries no Velnox
 *    `Authorization` or session cookie: an extra header would invalidate the AWS
 *    SigV4 signature, and sending the session to Cloudflare would leak it to a third
 *    party for no reason.
 *  * **Validation happens before the request**, mirroring the backend's own rules
 *    (`ALLOWED_IMAGE_TYPES`, `MAX_IMAGE_SIZE` = 10 MB), so a user learns immediately
 *    instead of after a wasted upload.
 *
 * ## Ordering rule
 *
 * Confirm is called only after the PUT returns success. That is what stops the app
 * from telling the backend about an object that was never written — the exact failure
 * `docs/ai/MEDIA.md` warns about when it says never to delete the old object before
 * the new one is confirmed.
 */
@Singleton
class UploadRepository @Inject constructor(
    private val api: VelnoxUploadApi,
    @PlainHttpClient private val plainClient: OkHttpClient,
    private val json: Json,
) {

    /** The durable reference the backend stores and the app sends in later requests. */
    data class UploadedObject(
        val objectKey: String,
        val publicUrl: String?,
    )

    suspend fun upload(
        bytes: ByteArray,
        contentType: String,
        purpose: String,
        fileName: String? = null,
        productId: String? = null,
        variantId: String? = null,
        shopId: String? = null,
        alt: String? = null,
    ): VelnoxResult<UploadedObject> {
        validate(bytes, contentType, purpose)?.let { return VelnoxResult.Failure(it) }

        val presign = safeApiCall(json) {
            api.presign(
                PresignRequest(
                    purpose = purpose,
                    contentType = contentType,
                    fileSize = bytes.size.toLong(),
                    fileName = fileName,
                    productId = productId,
                    variantId = variantId,
                    shopId = shopId,
                ),
            )
        }

        val target = when (presign) {
            is VelnoxResult.Success -> presign.data
            is VelnoxResult.Failure -> return presign
        }

        val putFailure = putBytes(target.uploadUrl, bytes, contentType)
        if (putFailure != null) return VelnoxResult.Failure(putFailure)

        val confirm = safeApiCall(json) {
            api.confirm(
                UploadConfirmRequest(
                    objectKey = target.objectKey,
                    purpose = purpose,
                    contentType = contentType,
                    productId = productId,
                    variantId = variantId,
                    shopId = shopId,
                    alt = alt,
                ),
            )
        }

        return when (confirm) {
            is VelnoxResult.Success -> VelnoxResult.Success(
                UploadedObject(objectKey = target.objectKey, publicUrl = target.publicUrl),
            )

            is VelnoxResult.Failure -> {
                // The bytes are in R2 but unconfirmed. Reporting success here would
                // leave an orphaned object and a record that does not exist.
                VelnoxLog.w(TAG) { "Upload confirm failed for key ${target.objectKey}" }
                confirm
            }
        }
    }

    private suspend fun putBytes(
        uploadUrl: String,
        bytes: ByteArray,
        contentType: String,
    ): AppError? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(uploadUrl)
            .put(bytes.toRequestBody(contentType.toMediaType()))
            .header("Content-Type", contentType)
            .build()

        try {
            plainClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    null
                } else {
                    VelnoxLog.w(TAG) { "R2 PUT failed with HTTP ${response.code}" }
                    AppError.Server(
                        httpStatus = response.code,
                        serverCode = "UPLOAD_FAILED",
                        serverMessage = "The image could not be uploaded (HTTP ${response.code}).",
                    )
                }
            }
        } catch (io: IOException) {
            AppError.Offline(serverMessage = io.message, cause = io)
        }
    }

    private fun validate(bytes: ByteArray, contentType: String, purpose: String): AppError? = when {
        purpose !in UploadPurpose.all ->
            AppError.Validation(serverMessage = "Unsupported upload purpose.")
        contentType !in UploadContentType.all ->
            AppError.Validation(serverMessage = "Only JPEG, PNG, WebP and AVIF images are accepted.")
        bytes.isEmpty() ->
            AppError.Validation(serverMessage = "The selected file is empty.")
        bytes.size > UploadContentType.MAX_BYTES ->
            AppError.Validation(serverMessage = "Images must be 10 MB or smaller.")
        else -> null
    }

    private companion object {
        const val TAG = "UploadRepository"
    }
}
