package com.velnox.core.data.dto

import com.velnox.core.network.serialization.FlexibleBooleanSerializer
import com.velnox.core.network.serialization.FlexibleDoubleSerializer
import com.velnox.core.network.serialization.FlexibleEpochMillisSerializer
import com.velnox.core.network.serialization.FlexibleIntSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Cart payloads.
 *
 * `GET /api/customer/cart` answers `{ success: true, data: { items: [...] } }`, and
 * each item is exactly the object `formatCartRow` builds in
 * `backend/routes/cart.ts` — including `priceSnapshot` (the price captured when the
 * item was added) and `availableStock` (variant stock when a variant is selected).
 */
@Serializable
data class CartItemDto(
    val id: String,
    val productId: String,
    val variantId: String? = null,
    val variantName: String? = null,
    val variantSku: String? = null,
    /**
     * Kept as raw JSON: the backend builds this from variant option values and its
     * exact shape is not part of the documented contract, so mapping it into a typed
     * class would be guesswork. [com.velnox.core.data.mapper.variantLabel] renders it.
     */
    val variantOptionLabels: JsonElement? = null,
    val productName: String = "",
    val unit: String? = null,
    @Serializable(with = FlexibleIntSerializer::class)
    val quantity: Int = 0,
    @Serializable(with = FlexibleDoubleSerializer::class)
    val priceSnapshot: Double = 0.0,
    @Serializable(with = FlexibleIntSerializer::class)
    val availableStock: Int = 0,
    val shopName: String? = null,
    val productImageUrl: String? = null,
    val addedAt: String? = null,
)

@Serializable
data class CartDto(val items: List<CartItemDto> = emptyList())

/** `POST /api/customer/cart/add`. */
@Serializable
data class AddToCartRequest(
    val productId: String,
    val quantity: Int = 1,
    val variantId: String? = null,
)

/** `PUT /api/customer/cart/item/:cartItemId`. */
@Serializable
data class UpdateCartItemRequest(
    val quantity: Int,
)

/**
 * Order and order-item payloads.
 *
 * Names come from `packages/shared/src/lib/commerce.ts` (`StoreOrder`,
 * `StoreOrderItem`, `StoreAddressSnapshot`), which documents itself as mirroring the
 * backend API shapes.
 */
@Serializable
data class OrderItemDto(
    val id: String,
    val orderId: String? = null,
    val productId: String? = null,
    val shopId: String? = null,
    val variantId: String? = null,
    val productName: String = "",
    val variantName: String? = null,
    val unit: String? = null,
    @Serializable(with = FlexibleDoubleSerializer::class)
    val unitPrice: Double = 0.0,
    @Serializable(with = FlexibleIntSerializer::class)
    val quantity: Int = 0,
    @Serializable(with = FlexibleDoubleSerializer::class)
    val subtotal: Double = 0.0,
    val imageUrl: String? = null,
    val productStatus: String? = null,
)

@Serializable
data class ShipmentEventDto(
    val id: String,
    val status: String = "",
    val description: String? = null,
    val location: String? = null,
    /** ISO-8601 string, unlike most other timestamps in this API. */
    val occurredAt: String? = null,
)

@Serializable
data class ShipmentDto(
    val id: String,
    val carrier: String = "",
    val trackingNumber: String? = null,
    val status: String = "",
    val estimatedDeliveryDate: String? = null,
    val events: List<ShipmentEventDto> = emptyList(),
)

@Serializable
data class OrderDto(
    val id: String,
    val orderNumber: String = "",
    val customerUserId: String? = null,
    val status: String = "pending",
    val paymentStatus: String = "unpaid",
    val shippingStatus: String = "none",
    val shippingMethod: String? = null,
    val trackingNumber: String? = null,
    @Serializable(with = FlexibleDoubleSerializer::class)
    val subtotal: Double = 0.0,
    @Serializable(with = FlexibleDoubleSerializer::class)
    val discount: Double = 0.0,
    @Serializable(with = FlexibleDoubleSerializer::class)
    val shippingFee: Double = 0.0,
    @Serializable(with = FlexibleDoubleSerializer::class)
    val total: Double = 0.0,
    val currency: String = "THB",
    val note: String? = null,
    @Serializable(with = FlexibleEpochMillisSerializer::class)
    val createdAt: Long = 0L,
    @Serializable(with = FlexibleEpochMillisSerializer::class)
    val updatedAt: Long = 0L,
    val items: List<OrderItemDto> = emptyList(),
    val shipments: List<ShipmentDto> = emptyList(),
    val shopId: String? = null,
    val shopName: String? = null,
    val customerName: String? = null,
    val customerPhone: String? = null,
    @Serializable(with = FlexibleIntSerializer::class)
    val itemCount: Int = 0,
)

/**
 * `POST /api/customer/checkout`.
 *
 * Checkout is finance: it must never be retried blindly. The backend deduplicates
 * through the `checkout_requests` table, and [idempotencyKey] is this client's side
 * of that contract — one key per user-initiated checkout attempt, reused only for
 * retries of that same attempt.
 */
@Serializable
data class CheckoutRequest(
    val idempotencyKey: String,
    val addressId: String,
    val paymentMethod: String = "cod",
    val note: String? = null,
    val shippingMethod: String? = null,
)

@Serializable
data class CheckoutResultDto(
    val orderId: String? = null,
    val orderNumber: String? = null,
    /** Present when the backend redirected to Stripe instead of creating a COD order. */
    val checkoutUrl: String? = null,
    val sessionId: String? = null,
)

// ─── Customer profile ────────────────────────────────────────────────────────

@Serializable
data class CustomerProfileDto(
    val id: String? = null,
    val userId: String? = null,
    val firstName: String? = null,
    val lastName: String? = null,
    val phone: String? = null,
    val dateOfBirth: String? = null,
    val preferredLanguage: String? = null,
    val defaultAddressId: String? = null,
)

@Serializable
data class AddressDto(
    val id: String,
    val recipientName: String = "",
    val phone: String = "",
    val line1: String = "",
    val line2: String? = null,
    val subdistrict: String? = null,
    val district: String? = null,
    val city: String? = null,
    val state: String? = null,
    val postalCode: String? = null,
    val country: String = "TH",
    @Serializable(with = FlexibleBooleanSerializer::class)
    val isDefault: Boolean = false,
    /** Map pin, when the user placed one with the address picker. */
    @Serializable(with = FlexibleDoubleSerializer::class)
    val latitude: Double? = null,
    @Serializable(with = FlexibleDoubleSerializer::class)
    val longitude: Double? = null,
)

/** `POST`/`PUT /api/customer/addresses`. */
@Serializable
data class AddressUpsertRequest(
    val recipientName: String,
    val phone: String,
    val line1: String,
    val line2: String? = null,
    val subdistrict: String? = null,
    val district: String? = null,
    val city: String? = null,
    val state: String? = null,
    val postalCode: String? = null,
    val country: String = "TH",
    val isDefault: Boolean = false,
    val latitude: Double? = null,
    val longitude: Double? = null,
)

@Serializable
data class WishlistItemDto(
    val id: String? = null,
    val productId: String,
    val product: ProductDto? = null,
    @Serializable(with = FlexibleEpochMillisSerializer::class)
    val createdAt: Long = 0L,
)

/**
 * `PATCH /api/seller/orders/:orderId/status` and
 * `PATCH /api/admin/orders/:orderId/status`.
 *
 * [reason] is required by the backend for some transitions (cancellation), which is
 * why it is carried on the request rather than inferred.
 */
@Serializable
data class OrderStatusRequest(
    val status: String,
    val reason: String? = null,
    val trackingNumber: String? = null,
    val carrier: String? = null,
)

@Serializable
data class WishlistToggleRequest(val productId: String)

@Serializable
data class NotificationDto(
    val id: String,
    val type: String = "",
    val title: String = "",
    val message: String = "",
    @Serializable(with = FlexibleBooleanSerializer::class)
    val isRead: Boolean = false,
    @Serializable(with = FlexibleEpochMillisSerializer::class)
    val createdAt: Long = 0L,
)

// ─── Uploads (Cloudflare R2, presigned by the backend) ───────────────────────

/**
 * `POST /api/upload/presign`.
 *
 * The APK never holds an R2 credential: the backend signs a short-lived URL and the
 * app PUTs the bytes directly to Cloudflare, then confirms so the backend can record
 * the `media` row.
 */
@Serializable
data class PresignRequest(
    val purpose: String,
    val contentType: String,
    val fileSize: Long,
    val fileName: String? = null,
    val productId: String? = null,
    val variantId: String? = null,
    val shopId: String? = null,
)

@Serializable
data class PresignResponseDto(
    val uploadUrl: String,
    val objectKey: String,
    val publicUrl: String? = null,
    val expiresInSeconds: Long? = null,
)

@Serializable
data class UploadConfirmRequest(
    val objectKey: String,
    val purpose: String,
    val contentType: String? = null,
    val productId: String? = null,
    val variantId: String? = null,
    val shopId: String? = null,
    val alt: String? = null,
)
