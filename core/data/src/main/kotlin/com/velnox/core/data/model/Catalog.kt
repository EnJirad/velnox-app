package com.velnox.core.data.model

import com.velnox.core.common.domain.ProductStatus
import com.velnox.core.common.domain.VerificationStatus
import com.velnox.core.common.util.VelnoxFormat
import com.velnox.core.data.dto.CategoryDto
import com.velnox.core.data.dto.ProductDto
import com.velnox.core.data.dto.ProductImageDto
import com.velnox.core.data.dto.ProductOptionGroupDto
import com.velnox.core.data.dto.ProductOptionValueDto
import com.velnox.core.data.dto.ProductVariantDto
import com.velnox.core.data.dto.ShopDto

/**
 * Catalogue domain models.
 *
 * These are what the UI consumes: enums instead of wire strings, `null` instead of
 * sentinel zeros, and pre-computed display values (discount percent, stock label)
 * that the web clients compute identically. Keeping that logic here rather than in
 * three separate view models is what stops Velshop, Velseller and VelCenter from
 * disagreeing about what "out of stock" means.
 */

data class ProductImage(
    val id: String,
    val url: String,
    val alt: String,
    val sortOrder: Int,
    val isPrimary: Boolean,
    val isDetail: Boolean,
)

data class ProductVariant(
    val id: String,
    val name: String,
    val price: Double,
    val compareAtPrice: Double?,
    val stock: Int,
    val isActive: Boolean,
    val imageUrl: String?,
) {
    val discountPercent: Int? get() = VelnoxFormat.discountPercent(price, compareAtPrice)
    val isPurchasable: Boolean get() = isActive && stock > 0
}

data class ProductOptionValue(
    val id: String,
    val label: String,
    val imageUrl: String?,
)

data class ProductOptionGroup(
    val id: String,
    val name: String,
    /** `true` when values carry thumbnails; the picker then shows image chips. */
    val usesImages: Boolean,
    val required: Boolean,
    val values: List<ProductOptionValue>,
)

data class Product(
    val id: String,
    val shopId: String?,
    val sellerId: String?,
    val name: String,
    val description: String?,
    val categorySlug: String?,
    val unit: String,
    val price: Double,
    val compareAtPrice: Double?,
    val currency: String,
    val status: ProductStatus,
    val rejectionReason: String?,
    val images: List<ProductImage>,
    val primaryImageUrl: String?,
    val detailImages: List<ProductImage>,
    val variants: List<ProductVariant>,
    val optionGroups: List<ProductOptionGroup>,
    val availableStock: Int,
    val reorderLevel: Int,
    val shopName: String?,
    val soldCount: Int,
    val rating: Double?,
    val reviewCount: Int,
    val verificationStatus: VerificationStatus,
    val createdAtEpochMillis: Long?,
    val updatedAtEpochMillis: Long?,
) {

    /** Buy Now / Add to Cart eligibility — the single definition used everywhere. */
    val isPurchasable: Boolean get() = status.isPurchasable && availableStock > 0

    val discountPercent: Int? get() = VelnoxFormat.discountPercent(price, compareAtPrice)

    /** Max quantity the cart will accept for this product. */
    val maxOrderQuantity: Int get() = availableStock.coerceAtLeast(0)

    /** Low-stock hint threshold, mirroring `inventory.low_stock_threshold` semantics. */
    val isLowStock: Boolean get() = availableStock in 1..reorderLevel.coerceAtLeast(1)

    val priceLabel: String get() = VelnoxFormat.baht(price)

    val compareAtPriceLabel: String? get() = compareAtPrice?.let { VelnoxFormat.baht(it) }
}

data class Category(
    val id: String,
    val slug: String,
    val name: String,
    val parentId: String?,
    val sortOrder: Int,
    val imageUrl: String?,
    val productCount: Int,
    val children: List<Category>,
) {
    /** Root categories only carry a meaningful product count of their own. */
    val hasChildren: Boolean get() = children.isNotEmpty()

    /** Depth-first flatten, used by the pickers in Velseller and VelCenter. */
    fun flatten(depth: Int = 0): List<Pair<Category, Int>> =
        listOf(this to depth) + children.flatMap { it.flatten(depth + 1) }
}

data class Shop(
    val id: String,
    val sellerId: String?,
    val name: String,
    val slug: String?,
    val description: String?,
    val logoUrl: String?,
    val coverUrl: String?,
    val phone: String?,
    val address: String?,
    val announcement: String?,
    val rating: Double?,
    val productCount: Int,
    val verificationStatus: VerificationStatus,
    val isActive: Boolean,
)

// ─── Mappers ─────────────────────────────────────────────────────────────────

fun ProductImageDto.toDomain(): ProductImage = ProductImage(
    id = id,
    url = displayUrl?.takeIf { it.isNotBlank() } ?: url,
    alt = alt.orEmpty(),
    sortOrder = sortOrder,
    isPrimary = isPrimary,
    isDetail = imageType == "detail",
)

fun ProductVariantDto.toDomain(): ProductVariant = ProductVariant(
    id = id,
    name = name,
    price = price,
    compareAtPrice = compareAtPrice,
    stock = stock,
    isActive = status.equals("active", ignoreCase = true),
    imageUrl = imageUrl?.takeIf { it.isNotBlank() },
)

fun ProductOptionValueDto.toDomain(): ProductOptionValue = ProductOptionValue(
    id = id,
    label = label.ifBlank { value },
    imageUrl = imageUrl?.takeIf { it.isNotBlank() },
)

fun ProductOptionGroupDto.toDomain(): ProductOptionGroup = ProductOptionGroup(
    id = id,
    name = name,
    usesImages = displayType.equals("image", ignoreCase = true),
    required = required,
    values = values.sortedBy { it.sortOrder }.map { it.toDomain() },
)

fun ProductDto.toDomain(): Product {
    val variantStock = variants.sumOf { it.stock }
    val inventoryAvailable = inventory?.available ?: 0
    // Prefer real inventory; fall back to variant stock only when there is no
    // inventory row at all, which is the same precedence `applyVariantStock` uses
    // on the backend.
    val available = when {
        inventory != null -> inventoryAvailable
        variants.isNotEmpty() -> variantStock
        else -> 0
    }

    val gallery = images.filterNot { it.imageType == "detail" }
    val primary = primaryImage?.toDomain()
        ?: gallery.firstOrNull { it.isPrimary }
        ?: gallery.firstOrNull()

    return Product(
        id = id,
        shopId = shopId,
        sellerId = sellerId,
        name = name,
        description = description,
        categorySlug = categorySlug?.takeIf { it.isNotBlank() } ?: category?.takeIf { it.isNotBlank() },
        unit = unit?.takeIf { it.isNotBlank() } ?: "ชิ้น",
        price = price,
        compareAtPrice = compareAtPrice,
        currency = currency,
        status = ProductStatus.fromWire(status),
        rejectionReason = rejectionReason?.takeIf { it.isNotBlank() },
        images = gallery.sortedBy { it.sortOrder }.map { it.toDomain() },
        primaryImageUrl = primary?.url,
        detailImages = detailImages.map { it.toDomain() }.sortedBy { it.sortOrder },
        variants = variants.map { it.toDomain() },
        optionGroups = optionGroups.map { it.toDomain() }.sortedBy { it.sortOrder },
        availableStock = available.coerceAtLeast(0),
        reorderLevel = inventory?.reorderLevel ?: reorderLevel ?: 0,
        shopName = shopName?.takeIf { it.isNotBlank() },
        soldCount = soldCount,
        rating = rating,
        reviewCount = reviewCount,
        verificationStatus = VerificationStatus.fromWire(sellerVerificationStatus),
        createdAtEpochMillis = createdAt.takeIf { it > 0L },
        updatedAtEpochMillis = updatedAt.takeIf { it > 0L },
    )
}

fun CategoryDto.toDomain(): Category = Category(
    id = id,
    slug = slug,
    name = displayName?.takeIf { it.isNotBlank() } ?: name,
    parentId = parentId,
    sortOrder = sortOrder,
    imageUrl = imageUrl?.takeIf { it.isNotBlank() },
    productCount = productCount,
    children = children.map { it.toDomain() },
)

fun ShopDto.toDomain(): Shop = Shop(
    id = id,
    sellerId = sellerId,
    name = name,
    slug = slug?.takeIf { it.isNotBlank() },
    description = description,
    logoUrl = logo?.takeIf { it.isNotBlank() } ?: imageUrl?.takeIf { it.isNotBlank() },
    coverUrl = cover?.takeIf { it.isNotBlank() },
    phone = phone,
    address = address,
    announcement = announcement,
    rating = rating,
    productCount = productCount,
    verificationStatus = VerificationStatus.fromWire(verificationStatus),
    isActive = status.equals("active", ignoreCase = true),
)

/** Builds the root tree when the API returned a flat list. */
fun List<CategoryDto>.toCategoryTree(): List<Category> {
    val byId = associateBy { it.id }
    val childrenOf = groupBy { it.parentId }
    fun build(dto: CategoryDto): Category = dto.toDomain().copy(
        children = childrenOf[dto.id].orEmpty().sortedBy { it.sortOrder }.map(::build),
    )
    return filter { it.parentId == null || byId[it.parentId] == null }
        .sortedBy { it.sortOrder }
        .map(::build)
}
