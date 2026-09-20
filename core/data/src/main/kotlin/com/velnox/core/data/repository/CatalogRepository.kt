package com.velnox.core.data.repository

import com.velnox.core.common.coroutines.DispatcherProvider
import com.velnox.core.common.error.VelnoxResult
import com.velnox.core.common.error.map
import com.velnox.core.common.paging.Page
import com.velnox.core.common.domain.ProductStatus
import com.velnox.core.common.domain.VerificationStatus
import com.velnox.core.common.paging.PageRequest
import com.velnox.core.data.api.VelnoxCatalogApi
import com.velnox.core.data.dto.CatalogQuery
import com.velnox.core.data.model.Category
import com.velnox.core.data.model.Product
import com.velnox.core.data.model.Shop
import com.velnox.core.data.model.toCategoryTree
import com.velnox.core.data.model.toDomain
import com.velnox.core.database.dao.CachedCatalogDao
import com.velnox.core.database.dao.CachedCategoryDao
import com.velnox.core.database.entity.CachedCategoryEntity
import com.velnox.core.database.entity.CachedProductEntity
import com.velnox.core.network.safeApiCall
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Catalogue reads, with a write-through cache in `core:database`.
 *
 * ## Why the cache is write-through and never read-first
 *
 * Price, stock and moderation status are the exact values that must not be stale, so
 * a screen that is online always gets the API answer, and the cache is only a
 * fallback the UI labels as cached ([CachedResult]). Nothing in this class ever
 * returns cached rows as if they were fresh, which is what the brief means by
 * "must not show fake data when the API fails".
 *
 * ## Categories are cached unconditionally
 *
 * The category tree is platform-owned, changes rarely, and is needed by the catalogue
 * filter, the Velseller product form and VelCenter. Serving it from cache while the
 * network is down is safe because it carries no prices.
 */
@Singleton
class CatalogRepository @Inject constructor(
    private val api: VelnoxCatalogApi,
    private val catalogDao: CachedCatalogDao,
    private val categoryDao: CachedCategoryDao,
    private val json: Json,
    private val dispatchers: DispatcherProvider,
) {

    /** A value plus whether it came from the device cache rather than the API. */
    data class CachedResult<T>(val value: T, val fromCache: Boolean)

    suspend fun catalog(
        query: CatalogQuery,
        page: PageRequest = PageRequest(),
    ): VelnoxResult<Page<Product>> {
        val request = query.copy(limit = page.limit, offset = page.offset)
        val result = safeApiCall(json) { api.catalog(request.toQueryMap()) }

        return result.map { dtos ->
            val products = dtos.map { it.toDomain() }
            Page(
                items = products,
                request = page,
                // The catalog endpoint returns a bare array with no total, so the
                // only honest signal is "was this page full".
                total = null,
                hasMore = dtos.size >= page.limit,
            )
        }
    }

    /**
     * Writes the first catalogue page into the cache.
     *
     * Called by Velshop after a successful home load. Only a first page is written,
     * so the cache can never be a mix of pages from two different sync points.
     */
    suspend fun cacheFirstPage(products: List<Product>) = withContext(dispatchers.io) {
        if (products.isEmpty()) return@withContext
        val now = System.currentTimeMillis()
        catalogDao.replaceAll(products.map { it.toEntity(now) })
    }

    /**
     * Cached catalogue rows.
     *
     * The caller must present these as cached (Velshop shows a banner with
     * [latestCachedAt]); they are never a substitute for a live price or stock check.
     */
    suspend fun cachedProducts(): List<Product> = withContext(dispatchers.io) {
        cachedRows().map { it.toDomain() }
    }

    private suspend fun cachedRows(): List<CachedProductEntity> =
        catalogDao.firstPage(limit = CACHE_PAGE_SIZE)

    suspend fun latestCachedAt(): Long? = withContext(dispatchers.io) { catalogDao.latestCachedAt() }

    /**
     * `GET /api/products/:id`.
     *
     * Never served from cache: this is the screen where a user decides to buy, and a
     * stale price or stock figure would be a correctness bug with money attached.
     */
    suspend fun product(productId: String): VelnoxResult<Product> =
        safeApiCall(json) { api.product(productId) }.map { it.toDomain() }

    /** Category tree, falling back to the cached copy when the request fails. */
    suspend fun categoryTree(language: String = "th"): VelnoxResult<CachedResult<List<Category>>> {
        val remote = safeApiCall(json) { api.categoryTree(language) }
        return when (remote) {
            is VelnoxResult.Success -> {
                val tree = remote.data.toCategoryTree()
                withContext(dispatchers.io) { cacheCategories(remote.data, tree) }
                VelnoxResult.Success(CachedResult(tree, fromCache = false))
            }

            is VelnoxResult.Failure -> {
                val cached = withContext(dispatchers.io) { categoryDao.getActive() }
                if (cached.isEmpty()) {
                    remote
                } else {
                    VelnoxResult.Success(CachedResult(cached.toCategoryTree(), fromCache = true))
                }
            }
        }
    }

    fun observeCachedCategories(): Flow<List<Category>> =
        categoryDao.observeActive().map { it.toCategoryTree() }

    suspend fun shops(): VelnoxResult<List<Shop>> =
        safeApiCall(json) { api.shops() }.map { list -> list.map { it.toDomain() } }

    suspend fun shop(shopId: String): VelnoxResult<Shop> =
        safeApiCall(json) { api.shop(shopId) }.map { it.toDomain() }

    private suspend fun cacheCategories(
        dtos: List<com.velnox.core.data.dto.CategoryDto>,
        tree: List<Category>,
    ) {
        // Store flattenable rows: `id`, `slug`, localised name, parent and order.
        val rows = dtos.map { dto ->
            CachedCategoryEntity(
                id = dto.id,
                slug = dto.slug,
                parentId = dto.parentId,
                sortOrder = dto.sortOrder,
                isActive = dto.isActive,
                name = dto.displayName?.takeIf { it.isNotBlank() } ?: dto.name,
                description = dto.displayDescription,
                imageUrl = dto.imageUrl,
                productCount = dto.productCount,
                cachedAtEpochMillis = System.currentTimeMillis(),
            )
        }
        if (rows.isNotEmpty()) categoryDao.upsert(rows)
    }

    private companion object {
        /** Only the first catalogue page is cached, so the cache is one coherent snapshot. */
        const val CACHE_PAGE_SIZE = 24
    }
}

private fun List<CachedCategoryEntity>.toCategoryTree(): List<Category> {
    val nodes = associate { entity ->
        entity.id to Category(
            id = entity.id,
            slug = entity.slug,
            name = entity.name,
            parentId = entity.parentId,
            sortOrder = entity.sortOrder,
            imageUrl = entity.imageUrl,
            productCount = entity.productCount,
            children = emptyList(),
        )
    }
    val childrenByParent = groupBy { it.parentId }
    fun build(entity: CachedCategoryEntity): Category {
        val node = nodes.getValue(entity.id)
        return node.copy(
            children = childrenByParent[entity.id].orEmpty()
                .sortedBy { it.sortOrder }
                .map(::build),
        )
    }
    return filter { it.parentId == null || !nodes.containsKey(it.parentId) }
        .sortedBy { it.sortOrder }
        .map(::build)
}

/**
 * Cached row → domain.
 *
 * Deliberately lossy: a cached row has no variants, no option groups and no detail
 * images, so it reconstructs exactly what a catalogue card needs and nothing that
 * could be mistaken for live purchasable state.
 */
private fun CachedProductEntity.toDomain(): Product = Product(
    id = id,
    shopId = shopId,
    sellerId = null,
    name = name,
    description = description,
    categorySlug = categorySlug,
    unit = unit,
    price = price,
    compareAtPrice = compareAtPrice,
    currency = currency,
    status = ProductStatus.fromWire(status),
    rejectionReason = null,
    images = emptyList(),
    primaryImageUrl = primaryImageUrl,
    detailImages = emptyList(),
    variants = emptyList(),
    optionGroups = emptyList(),
    availableStock = totalStock,
    reorderLevel = 0,
    shopName = shopName,
    soldCount = soldCount,
    rating = rating,
    reviewCount = reviewCount,
    verificationStatus = VerificationStatus.fromWire(sellerVerificationStatus),
    createdAtEpochMillis = null,
    updatedAtEpochMillis = null,
)

private fun Product.toEntity(now: Long): CachedProductEntity = CachedProductEntity(
    id = id,
    name = name,
    description = description,
    price = price,
    compareAtPrice = compareAtPrice,
    currency = currency,
    unit = unit,
    primaryImageUrl = primaryImageUrl,
    imageUrlsJson = images.joinToString(separator = ",") { it.url },
    categorySlug = categorySlug,
    categoryName = null,
    shopId = shopId,
    shopName = shopName,
    shopSlug = null,
    status = status.wireValue,
    soldCount = soldCount,
    rating = rating,
    reviewCount = reviewCount,
    sellerVerificationStatus = verificationStatus.wireValue,
    totalStock = availableStock,
    hasVariants = variants.isNotEmpty(),
    vrepeatEnabled = false,
    cachedAtEpochMillis = now,
)
