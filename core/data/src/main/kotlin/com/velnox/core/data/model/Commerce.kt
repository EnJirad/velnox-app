package com.velnox.core.data.model

import com.velnox.core.common.domain.OrderStatus
import com.velnox.core.common.domain.PaymentStatus
import com.velnox.core.common.util.VelnoxFormat
import com.velnox.core.data.dto.AddressDto
import com.velnox.core.data.dto.CartItemDto
import com.velnox.core.data.dto.CustomerProfileDto
import com.velnox.core.data.dto.NotificationDto
import com.velnox.core.data.dto.OrderDto
import com.velnox.core.data.dto.OrderItemDto
import com.velnox.core.data.dto.ShipmentDto
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** One line in the cart, exactly as `formatCartRow` serves it. */
data class CartLine(
    val cartItemId: String,
    val productId: String,
    val variantId: String?,
    val productName: String,
    val variantLabel: String?,
    val unit: String?,
    val imageUrl: String?,
    /** Price captured when the item was added — the backend's own snapshot. */
    val priceSnapshot: Double,
    val quantity: Int,
    val availableStock: Int,
    val shopName: String?,
) {
    val lineTotal: Double get() = priceSnapshot * quantity
    val lineTotalLabel: String get() = VelnoxFormat.baht(lineTotal)
    val unitPriceLabel: String get() = VelnoxFormat.baht(priceSnapshot)

    /** The cart refuses quantities above the live variant stock. */
    val canIncrease: Boolean get() = quantity < availableStock
    val canDecrease: Boolean get() = quantity > 1

    /** True when the backend reports the line is no longer satisfiable. */
    val isOutOfStock: Boolean get() = availableStock <= 0
}

data class Cart(
    val lines: List<CartLine>,
) {
    val itemCount: Int get() = lines.sumOf { it.quantity }
    val subtotal: Double get() = lines.sumOf { it.lineTotal }
    val subtotalLabel: String get() = VelnoxFormat.baht(subtotal)
    val isEmpty: Boolean get() = lines.isEmpty()

    companion object {
        val Empty = Cart(emptyList())
    }
}

data class OrderLine(
    val id: String,
    val productId: String?,
    val productName: String,
    val variantLabel: String?,
    val unitPrice: Double,
    val quantity: Int,
    val subtotal: Double,
    val imageUrl: String?,
) {
    val subtotalLabel: String get() = VelnoxFormat.baht(subtotal)
    val unitPriceLabel: String get() = VelnoxFormat.baht(unitPrice)
}

data class ShipmentEvent(
    val status: String,
    val description: String?,
    val location: String?,
    val occurredAt: String?,
)

data class Shipment(
    val id: String,
    val carrier: String,
    val trackingNumber: String?,
    val status: String,
    val events: List<ShipmentEvent>,
)

data class Order(
    val id: String,
    val orderNumber: String,
    val customerUserId: String?,
    val status: OrderStatus,
    val paymentStatus: PaymentStatus,
    val shippingStatus: String,
    val trackingNumber: String?,
    val subtotal: Double,
    val shippingFee: Double,
    val discount: Double,
    val total: Double,
    val currency: String,
    val note: String?,
    val lines: List<OrderLine>,
    val shipments: List<Shipment>,
    val shopName: String?,
    val customerName: String?,
    val customerPhone: String?,
    val itemCount: Int,
    val createdAtEpochMillis: Long?,
    val updatedAtEpochMillis: Long?,
) {
    val totalLabel: String get() = VelnoxFormat.baht(total)
    val shortNumber: String get() = VelnoxFormat.shortOrderNumber(orderNumber)

    /** Cancellable while the seller has not shipped it — mirrors the state machine. */
    val isCancellable: Boolean
        get() = status == OrderStatus.Pending || status == OrderStatus.Confirmed
}

data class Address(
    val id: String,
    val recipientName: String,
    val phone: String,
    val line1: String,
    val line2: String?,
    val subdistrict: String?,
    val district: String?,
    val city: String?,
    val state: String?,
    val postalCode: String?,
    val country: String,
    val isDefault: Boolean,
) {
    /** Single-line rendering used in the address list and the checkout summary. */
    val formatted: String get() = listOfNotNull(
        line1.takeIf { it.isNotBlank() },
        line2?.takeIf { it.isNotBlank() },
        subdistrict?.takeIf { it.isNotBlank() },
        district?.takeIf { it.isNotBlank() },
        city?.takeIf { it.isNotBlank() },
        state?.takeIf { it.isNotBlank() },
        postalCode?.takeIf { it.isNotBlank() },
    ).joinToString(", ")
}

data class CustomerProfile(
    val userId: String?,
    val firstName: String?,
    val lastName: String?,
    val phone: String?,
    val preferredLanguage: String?,
) {
    val fullName: String
        get() = listOfNotNull(firstName?.takeIf { it.isNotBlank() }, lastName?.takeIf { it.isNotBlank() })
            .joinToString(" ")
}

data class NotificationItem(
    val id: String,
    val type: String,
    val title: String,
    val message: String,
    val isRead: Boolean,
    val createdAtEpochMillis: Long?,
)

data class WishlistEntry(
    val productId: String,
    val product: Product?,
    val addedAtEpochMillis: Long?,
)

// ─── Mappers ─────────────────────────────────────────────────────────────────

/**
 * Renders `variantOptionLabels`.
 *
 * The backend builds this from variant option values and does not document its exact
 * shape, so the raw JSON is read defensively: an array of primitives or an array of
 * objects with a label-ish field both render, and anything else yields `null` rather
 * than a crash. [fallback] (the variant name) is preferred when present.
 */
internal fun variantLabel(json: kotlinx.serialization.json.JsonElement?, fallback: String?): String? {
    fallback?.takeIf { it.isNotBlank() }?.let { return it }
    val array = json as? JsonArray ?: return null
    val parts = array.mapNotNull { element ->
        when (element) {
            is JsonPrimitive -> element.contentOrNull
            is JsonObject -> (element["label"] ?: element["value"] ?: element["valueLabel"])
                ?.let { (it as? JsonPrimitive)?.contentOrNull }
            else -> null
        }
    }.filter { it.isNotBlank() }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" / ")
}

fun CartItemDto.toDomain(): CartLine = CartLine(
    cartItemId = id,
    productId = productId,
    variantId = variantId,
    productName = productName,
    variantLabel = variantLabel(variantOptionLabels, variantName),
    unit = unit,
    imageUrl = productImageUrl?.takeIf { it.isNotBlank() },
    priceSnapshot = priceSnapshot,
    quantity = quantity,
    availableStock = availableStock,
    shopName = shopName?.takeIf { it.isNotBlank() },
)

fun OrderItemDto.toDomain(): OrderLine = OrderLine(
    id = id,
    productId = productId,
    productName = productName,
    variantLabel = variantName?.takeIf { it.isNotBlank() },
    unitPrice = unitPrice,
    quantity = quantity,
    subtotal = subtotal,
    imageUrl = imageUrl?.takeIf { it.isNotBlank() },
)

fun ShipmentDto.toDomain(): Shipment = Shipment(
    id = id,
    carrier = carrier,
    trackingNumber = trackingNumber?.takeIf { it.isNotBlank() },
    status = status,
    events = events.map {
        ShipmentEvent(
            status = it.status,
            description = it.description,
            location = it.location,
            occurredAt = it.occurredAt,
        )
    },
)

fun OrderDto.toDomain(): Order = Order(
    id = id,
    orderNumber = orderNumber,
    customerUserId = customerUserId,
    status = OrderStatus.fromWire(status),
    paymentStatus = PaymentStatus.fromWire(paymentStatus),
    shippingStatus = shippingStatus,
    trackingNumber = trackingNumber?.takeIf { it.isNotBlank() },
    subtotal = subtotal,
    shippingFee = shippingFee,
    discount = discount,
    total = total,
    currency = currency,
    note = note?.takeIf { it.isNotBlank() },
    lines = items.map { it.toDomain() },
    shipments = shipments.map { it.toDomain() },
    shopName = shopName?.takeIf { it.isNotBlank() },
    customerName = customerName?.takeIf { it.isNotBlank() },
    customerPhone = customerPhone?.takeIf { it.isNotBlank() },
    // Fall back to the real line count so a list row never shows "0 items" while
    // the order detail shows three.
    itemCount = if (itemCount > 0) itemCount else items.sumOf { it.quantity },
    createdAtEpochMillis = createdAt.takeIf { it > 0L },
    updatedAtEpochMillis = updatedAt.takeIf { it > 0L },
)

fun AddressDto.toDomain(): Address = Address(
    id = id,
    recipientName = recipientName,
    phone = phone,
    line1 = line1,
    line2 = line2,
    subdistrict = subdistrict,
    district = district,
    city = city,
    state = state,
    postalCode = postalCode,
    country = country,
    isDefault = isDefault,
)

fun CustomerProfileDto.toDomain(): CustomerProfile = CustomerProfile(
    userId = userId ?: id,
    firstName = firstName,
    lastName = lastName,
    phone = phone,
    preferredLanguage = preferredLanguage,
)

fun NotificationDto.toDomain(): NotificationItem = NotificationItem(
    id = id,
    type = type,
    title = title,
    message = message,
    isRead = isRead,
    createdAtEpochMillis = createdAt.takeIf { it > 0L },
)
