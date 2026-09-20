package com.velnox.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A product row as it appeared in `GET /api/products/catalog`.
 *
 * Only the fields the list and detail screens render are stored; the detail screen
 * always re-fetches `GET /api/products/:id` when online, because price, stock and
 * moderation status are exactly the values that must not be stale.
 */
@Entity(
    tableName = "cached_products",
    indices = [
        Index("categorySlug"),
        Index("shopId"),
        Index("cachedAtEpochMillis"),
    ],
)
data class CachedProductEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String?,
    val price: Double,
    val compareAtPrice: Double?,
    val currency: String,
    val unit: String,
    val primaryImageUrl: String?,
    val imageUrlsJson: String,
    val categorySlug: String?,
    val categoryName: String?,
    val shopId: String?,
    val shopName: String?,
    val shopSlug: String?,
    val status: String,
    val soldCount: Int,
    val rating: Double?,
    val reviewCount: Int,
    /** Seller/shop verification status — drives the V badge in the catalogue. */
    val sellerVerificationStatus: String?,
    val totalStock: Int,
    val hasVariants: Boolean,
    val vrepeatEnabled: Boolean,
    val cachedAtEpochMillis: Long,
)

/** `categories` row projected from `GET /api/categories/tree`. */
@Entity(
    tableName = "cached_categories",
    indices = [Index("parentId"), Index("sortOrder")],
)
data class CachedCategoryEntity(
    @PrimaryKey val id: String,
    val slug: String,
    val parentId: String?,
    val sortOrder: Int,
    val isActive: Boolean,
    /** Localised name already resolved by the backend for the requested `lang`. */
    val name: String,
    val description: String?,
    val imageUrl: String?,
    val productCount: Int,
    val cachedAtEpochMillis: Long,
)

/**
 * The cart as last read from `GET /api/customer/cart`.
 *
 * Read-only: it exists so the cart tab can render instantly and so an offline user
 * can see what they had. Quantities here are never authoritative — any change goes
 * through the cart endpoints.
 */
@Entity(tableName = "cached_cart_items", indices = [Index("shopId")])
data class CachedCartItemEntity(
    @PrimaryKey val cartItemId: String,
    val productId: String,
    val variantId: String?,
    val productName: String,
    val variantName: String?,
    val imageUrl: String?,
    val unitPrice: Double,
    val quantity: Int,
    val availableStock: Int,
    val currency: String,
    val shopId: String?,
    val shopName: String?,
    val cachedAtEpochMillis: Long,
)

/** Order summary from `GET /api/customer/orders` / `GET /api/seller/orders`. */
@Entity(
    tableName = "cached_orders",
    indices = [Index("status"), Index("cachedAtEpochMillis"), Index("scope")],
)
data class CachedOrderEntity(
    @PrimaryKey val id: String,
    /**
     * Which list this row came from: `customer` or `seller`.
     *
     * A seller can also be a customer, so the same order id could legitimately
     * appear in both lists with different fields populated. Keying only on the id
     * would let one overwrite the other.
     */
    val scope: String,
    val orderNumber: String,
    val status: String,
    val paymentStatus: String,
    val total: Double,
    val currency: String,
    val itemCount: Int,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val shopName: String?,
    val customerName: String?,
    val trackingNumber: String?,
    val cachedAtEpochMillis: Long,
)
