package com.velnox.core.common.domain

/**
 * Commerce status vocabulary shared by all three apps.
 *
 * These live in `core:common` (not `core:data`) because they are pure domain
 * vocabulary with no infrastructure dependency: the design system needs to render
 * an order badge, the data layer needs to parse the wire value, and neither should
 * depend on the other to do it.
 *
 * Every value here is transcribed from the real system, not invented:
 * `packages/shared/src/lib/commerce.ts` (`StoreOrderStatus`, `StorePaymentStatus`,
 * `StoreProductStatus`, `VerificationStatus`), `docs/ai/SELLER.md` for seller
 * statuses, and `db/schema.sql` for the check constraints.
 */
enum class OrderStatus(val wireValue: String) {
    /** `pending` — the seller has not confirmed yet. */
    Pending("pending"),
    Confirmed("confirmed"),
    Shipped("shipped"),
    Delivered("delivered"),
    Completed("completed"),
    Cancelled("cancelled"),

    /** A status this build does not know. Rendered verbatim rather than hidden. */
    Unknown("");

    companion object {
        fun fromWire(value: String?): OrderStatus =
            entries.firstOrNull { it.wireValue == value?.lowercase() } ?: Unknown
    }
}

/**
 * The order state machine, mirroring `NEXT_ORDER_STATUSES` on web.
 *
 * Kept client-side so Velseller can offer only legal transitions. The backend
 * enforces the same rules — this list is a usability aid, never the boundary.
 */
val OrderStatus.allowedNextStatuses: List<OrderStatus>
    get() = when (this) {
        OrderStatus.Pending -> listOf(OrderStatus.Confirmed, OrderStatus.Cancelled)
        OrderStatus.Confirmed -> listOf(OrderStatus.Shipped, OrderStatus.Cancelled)
        OrderStatus.Shipped -> listOf(OrderStatus.Delivered)
        OrderStatus.Delivered -> listOf(OrderStatus.Completed)
        OrderStatus.Completed, OrderStatus.Cancelled, OrderStatus.Unknown -> emptyList()
    }

enum class PaymentStatus(val wireValue: String) {
    Unpaid("unpaid"),
    Pending("pending"),
    Paid("paid"),
    PartiallyRefunded("partially_refunded"),
    Refunded("refunded"),
    Failed("failed"),
    Unknown("");

    companion object {
        fun fromWire(value: String?): PaymentStatus =
            entries.firstOrNull { it.wireValue == value?.lowercase() } ?: Unknown
    }
}

/**
 * Product lifecycle. Transitions are owned by `backend/lib/product-lifecycle.ts`;
 * this enum only names the states.
 */
enum class ProductStatus(val wireValue: String) {
    /** Saved but not submitted for moderation. */
    Draft("draft"),

    /** Submitted and waiting for VelCenter moderation. */
    PendingReview("pending_review"),

    /** Visible in the catalogue. */
    Published("published"),

    Rejected("rejected"),
    Suspended("suspended"),
    Archived("archived"),
    Unknown("");

    /** Only published products are purchasable. */
    val isPurchasable: Boolean get() = this == Published

    /** True while the product occupies a moderation slot. */
    val isAwaitingModeration: Boolean get() = this == PendingReview

    companion object {
        fun fromWire(value: String?): ProductStatus =
            entries.firstOrNull { it.wireValue == value?.lowercase() } ?: Unknown
    }
}

/**
 * Seller application lifecycle — `docs/ai/SELLER.md`:
 * "Canonical statuses: pending|approved|rejected|suspended", plus the
 * `under_review` and `needs_correction` states `RequireRole.tsx` switches on.
 */
enum class SellerStatus(val wireValue: String) {
    Pending("pending"),
    UnderReview("under_review"),
    NeedsCorrection("needs_correction"),
    Approved("approved"),
    Rejected("rejected"),
    Suspended("suspended"),
    Unknown("");

    /** Only an approved seller gets the Velseller workspace. */
    val isApproved: Boolean get() = this == Approved

    /** States that permit resubmitting the application. */
    val canReapply: Boolean
        get() = this == Rejected || this == NeedsCorrection || this == Unknown

    companion object {
        fun fromWire(value: String?): SellerStatus =
            entries.firstOrNull { it.wireValue == value?.lowercase() } ?: Unknown
    }
}

/**
 * Verification status. Velnox has exactly **one** verification system — the
 * seller/shop identity check (`docs/ai/CATEGORIES.md`, `RequireRole.tsx`). There is
 * no product-level verification flow to model.
 */
enum class VerificationStatus(val wireValue: String) {
    Unverified("unverified"),
    Pending("pending"),
    Verified("verified"),
    Rejected("rejected"),
    Suspended("suspended"),
    Unknown("");

    /** Drives the V badge. */
    val isVerified: Boolean get() = this == Verified

    companion object {
        fun fromWire(value: String?): VerificationStatus =
            entries.firstOrNull { it.wireValue == value?.lowercase() } ?: Unknown
    }
}

/** Sort options the catalogue endpoint actually supports (`sortBy`). */
enum class CatalogSort(val wireValue: String) {
    Newest("newest"),
    PriceAsc("price_asc"),
    PriceDesc("price_desc"),
    Popular("popular"),
    Rating("rating"),
}
