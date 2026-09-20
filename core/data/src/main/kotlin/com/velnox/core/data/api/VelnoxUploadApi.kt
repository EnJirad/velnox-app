package com.velnox.core.data.api

import com.velnox.core.data.dto.PresignRequest
import com.velnox.core.data.dto.PresignResponseDto
import com.velnox.core.data.dto.UploadConfirmRequest
import com.velnox.core.network.ApiAck
import com.velnox.core.network.ApiEnvelope
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Cloudflare R2 upload flow, exactly as `backend/routes/upload.ts` and
 * `docs/ai/MEDIA.md` define it:
 *
 * ```
 * POST /api/upload/presign  → signed PUT URL (short TTL)
 * PUT  <signed url>         → bytes go straight to Cloudflare, never through the API
 * POST /api/upload/confirm  → backend verifies the object and writes the media row
 * ```
 *
 * The APK holds **no** R2 credential. `R2_ACCOUNT_ID`, `R2_ACCESS_KEY_ID` and
 * `R2_SECRET_ACCESS_KEY` exist only in the backend environment, and a presigned URL
 * is time-limited, so a leaked URL from a log or proxy is of little use.
 */
interface VelnoxUploadApi {

    @POST("upload/presign")
    suspend fun presign(@Body body: PresignRequest): ApiEnvelope<PresignResponseDto>

    @POST("upload/confirm")
    suspend fun confirm(@Body body: UploadConfirmRequest): ApiEnvelope<ApiAck>
}

/**
 * Purposes the backend validates. Sending anything outside this set is rejected, so
 * they are enumerated rather than left as free-form strings.
 */
object UploadPurpose {
    /** Seller identity evidence (`backend/routes/verification.ts`). */
    const val ID_CARD = "id_card"
    const val ID_CARD_BACK = "id_card_back"
    const val SELFIE_ID = "selfie_id"

    /** Product imagery. */
    const val PRODUCT_IMAGE = "product_image"
    const val PRODUCT_DETAIL_IMAGE = "product_detail_image"
    const val VARIANT_IMAGE = "variant_image"

    /** Profile media. */
    const val CUSTOMER_AVATAR = "customer_avatar"
    const val CUSTOMER_COVER = "customer_cover"

    /** Shop media. */
    const val SHOP_LOGO = "shop_logo"
    const val SHOP_COVER = "shop_cover"

    val all: Set<String> = setOf(
        ID_CARD, ID_CARD_BACK, SELFIE_ID,
        PRODUCT_IMAGE, PRODUCT_DETAIL_IMAGE, VARIANT_IMAGE,
        CUSTOMER_AVATAR, CUSTOMER_COVER,
        SHOP_LOGO, SHOP_COVER,
    )
}

/** `Content-Type` values the backend accepts; anything else is refused at presign. */
object UploadContentType {
    const val JPEG = "image/jpeg"
    const val PNG = "image/png"
    const val WEBP = "image/webp"
    const val AVIF = "image/avif"

    val all: Set<String> = setOf(JPEG, PNG, WEBP, AVIF)

    /** Mirrors the backend's `MAX_IMAGE_SIZE` (10 MB). */
    const val MAX_BYTES: Long = 10L * 1024 * 1024
}
