package com.velnox.core.data.dto

import com.velnox.core.network.serialization.FlexibleBooleanSerializer
import com.velnox.core.network.serialization.FlexibleDoubleSerializer
import com.velnox.core.network.serialization.FlexibleEpochMillisSerializer
import com.velnox.core.network.serialization.FlexibleIntSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Catalogue payloads.
 *
 * Field names are taken from the backend that produces them, not invented:
 *  * `formatProduct` / `formatCartRow` in `backend/routes/products.ts` and
 *    `backend/routes/cart.ts` for the camelCase product and cart shapes.
 *  * `packages/shared/src/lib/commerce.ts`, whose own header states it "mirrors the
 *    backend's API shapes", for products, images, variants and shops.
 *  * The raw SQL in `GET /api/categories` / `/api/categories/tree`, which returns
 *    `result.rows` directly and therefore stays **snake_case**.
 *
 * Every numeric field uses the flexible codecs because `node-postgres` returns
 * `NUMERIC` columns as strings while computed values arrive as JSON numbers.
 */

@Serializable
data class ProductImageDto(
    val id: String,
    val productId: String? = null,
    val url: String,
    val displayUrl: String? = null,
    val thumbUrl: String? = null,
    val alt: String? = null,
    @Serializable(with = FlexibleIntSerializer::class)
    val sortOrder: Int = 0,
    /** `gallery` | `detail` (migration V0030 `product_image_types`). */
    val imageType: String? = null,
    val variantId: String? = null,
    @Serializable(with = FlexibleBooleanSerializer::class)
    val isPrimary: Boolean = false,
)

@Serializable
data class InventoryDto(
    val id: String? = null,
    val productId: String? = null,
    val shopId: String? = null,
    @Serializable(with = FlexibleIntSerializer::class)
    val quantity: Int = 0,
    @Serializable(with = FlexibleIntSerializer::class)
    val reservedQuantity: Int = 0,
    /** `inventory.low_stock_threshold` on the backend; `reorderLevel` on the wire. */
    @Serializable(with = FlexibleIntSerializer::class)
    val reorderLevel: Int = 0,
    @Serializable(with = FlexibleIntSerializer::class)
    val available: Int = 0,
)

@Serializable
data class ProductVariantDto(
    val id: String,
    val productId: String? = null,
    val name: String = "",
    val sku: String? = null,
    @Serializable(with = FlexibleDoubleSerializer::class)
    val price: Double = 0.0,
    @Serializable(with = FlexibleDoubleSerializer::class)
    val compareAtPrice: Double? = null,
    @Serializable(with = FlexibleDoubleSerializer::class)
    val discountPercent: Double? = null,
    @Serializable(with = FlexibleIntSerializer::class)
    val stock: Int = 0,
    val status: String = "active",
    @Serializable(with = FlexibleIntSerializer::class)
    val sortOrder: Int = 0,
    val imageUrl: String? = null,
)

/** A single configurable axis value, e.g. "Black" under "Colour". */
@Serializable
data class ProductOptionValueDto(
    val id: String,
    val value: String = "",
    val label: String = "",
    val imageUrl: String? = null,
    @Serializable(with = FlexibleIntSerializer::class)
    val sortOrder: Int = 0,
)

@Serializable
data class ProductOptionGroupDto(
    val id: String,
    val name: String = "",
    /** `image` or `text` — controls whether the picker shows thumbnails. */
    val displayType: String = "text",
    @Serializable(with = FlexibleBooleanSerializer::class)
    val required: Boolean = false,
    @Serializable(with = FlexibleIntSerializer::class)
    val sortOrder: Int = 0,
    val values: List<ProductOptionValueDto> = emptyList(),
)

@Serializable
data class ProductDto(
    val id: String,
    val shopId: String? = null,
    val sellerId: String? = null,
    val name: String = "",
    val description: String? = null,
    /** Raw stored value (`products.category_id`, a slug for canonical rows). */
    val category: String? = null,
    val categorySlug: String? = null,
    val unit: String? = null,
    @Serializable(with = FlexibleDoubleSerializer::class)
    val price: Double = 0.0,
    @Serializable(with = FlexibleDoubleSerializer::class)
    val compareAtPrice: Double? = null,
    val currency: String = "THB",
    val status: String = "draft",
    val rejectionReason: String? = null,
    val supplier: String? = null,
    @Serializable(with = FlexibleEpochMillisSerializer::class)
    val createdAt: Long = 0L,
    @Serializable(with = FlexibleEpochMillisSerializer::class)
    val updatedAt: Long = 0L,
    val images: List<ProductImageDto> = emptyList(),
    val primaryImage: ProductImageDto? = null,
    val detailImages: List<ProductImageDto> = emptyList(),
    val inventory: InventoryDto? = null,
    val variants: List<ProductVariantDto> = emptyList(),
    val featuredVariant: ProductVariantDto? = null,
    val optionGroups: List<ProductOptionGroupDto> = emptyList(),
    val shopName: String? = null,
    val shopSlug: String? = null,
    @Serializable(with = FlexibleIntSerializer::class)
    val soldCount: Int = 0,
    @Serializable(with = FlexibleDoubleSerializer::class)
    val rating: Double? = null,
    @Serializable(with = FlexibleIntSerializer::class)
    val reviewCount: Int = 0,
    /** Seller/shop verification status — drives the V badge. */
    val sellerVerificationStatus: String? = null,
    @Serializable(with = FlexibleBooleanSerializer::class)
    val isVerifiedProduct: Boolean = false,
    /** Present on catalog rows; used by Velseller's reorder intelligence. */
    @Serializable(with = FlexibleIntSerializer::class)
    val currentStock: Int? = null,
    @Serializable(with = FlexibleIntSerializer::class)
    val reorderLevel: Int? = null,
    @Serializable(with = FlexibleIntSerializer::class)
    val purchaseCount: Int? = null,
    @Serializable(with = FlexibleEpochMillisSerializer::class)
    val lastOrderedAt: Long? = null,
    @Serializable(with = FlexibleDoubleSerializer::class)
    val avgCycleDays: Double? = null,
    // VelRepeat configuration (migrations V0025).
    @Serializable(with = FlexibleBooleanSerializer::class)
    val vrepeatEnabled: Boolean = false,
    @Serializable(with = FlexibleBooleanSerializer::class)
    val vrepeatWeeklyEnabled: Boolean = false,
    @Serializable(with = FlexibleBooleanSerializer::class)
    val vrepeatMonthlyEnabled: Boolean = false,
    @Serializable(with = FlexibleDoubleSerializer::class)
    val vrepeatWeeklyPrice: Double? = null,
    @Serializable(with = FlexibleDoubleSerializer::class)
    val vrepeatMonthlyPrice: Double? = null,
)

/**
 * Category.
 *
 * **Snake_case on purpose.** `GET /api/categories` and `/api/categories/tree` return
 * `result.rows` straight from Postgres, so the wire names are the column names:
 * `parent_id`, `sort_order`, `is_active`, `display_name`, `display_description`,
 * `image_url`, `product_count`. The localised name arrives in `display_name`,
 * resolved server-side with `COALESCE(names->>$lang, name)`.
 */
@Serializable
data class CategoryDto(
    val id: String,
    val name: String = "",
    val slug: String = "",
    val icon: String? = null,
    @SerialName("parent_id") val parentId: String? = null,
    @SerialName("sort_order") @Serializable(with = FlexibleIntSerializer::class)
    val sortOrder: Int = 0,
    @SerialName("is_active") @Serializable(with = FlexibleBooleanSerializer::class)
    val isActive: Boolean = true,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("display_description") val displayDescription: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("product_count") @Serializable(with = FlexibleIntSerializer::class)
    val productCount: Int = 0,
    /** Only present on `/categories/tree`. */
    val children: List<CategoryDto> = emptyList(),
)

@Serializable
data class ShopDto(
    val id: String,
    val sellerId: String? = null,
    val name: String = "",
    val slug: String? = null,
    val description: String? = null,
    /** The shops table column is `logo`; `imageUrl` is the frontend alias. */
    val logo: String? = null,
    val cover: String? = null,
    val imageUrl: String? = null,
    val phone: String? = null,
    val address: String? = null,
    val announcement: String? = null,
    val status: String = "active",
    @Serializable(with = FlexibleDoubleSerializer::class)
    val commissionRate: Double? = null,
    val currency: String = "THB",
    @Serializable(with = FlexibleDoubleSerializer::class)
    val rating: Double? = null,
    @Serializable(with = FlexibleIntSerializer::class)
    val productCount: Int = 0,
    val verificationStatus: String? = null,
    @Serializable(with = FlexibleEpochMillisSerializer::class)
    val createdAt: Long = 0L,
)

/** `GET /api/products/catalog` returns the array directly in `data`. */
@Serializable
data class CatalogQuery(
    val q: String? = null,
    val category: String? = null,
    val shopId: String? = null,
    @Serializable(with = FlexibleDoubleSerializer::class) val minPrice: Double? = null,
    @Serializable(with = FlexibleDoubleSerializer::class) val maxPrice: Double? = null,
    val inStock: Boolean? = null,
    val verified: Boolean? = null,
    val sortBy: String? = null,
    val limit: Int? = null,
    val offset: Int? = null,
) {
    /** Only non-null values become query parameters — the backend treats `""` as a filter. */
    fun toQueryMap(): Map<String, String> = buildMap {
        q?.takeIf { it.isNotBlank() }?.let { put("q", it) }
        category?.takeIf { it.isNotBlank() }?.let { put("category", it) }
        shopId?.takeIf { it.isNotBlank() }?.let { put("shopId", it) }
        minPrice?.let { put("minPrice", it.toString()) }
        maxPrice?.let { put("maxPrice", it.toString()) }
        if (inStock == true) put("inStock", "true")
        if (verified == true) put("verified", "true")
        sortBy?.takeIf { it.isNotBlank() }?.let { put("sortBy", it) }
        limit?.let { put("limit", it.toString()) }
        offset?.let { put("offset", it.toString()) }
    }
}
