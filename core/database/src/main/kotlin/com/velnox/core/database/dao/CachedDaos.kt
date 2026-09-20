package com.velnox.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.velnox.core.database.entity.CachedCartItemEntity
import com.velnox.core.database.entity.CachedCategoryEntity
import com.velnox.core.database.entity.CachedOrderEntity
import com.velnox.core.database.entity.CachedProductEntity
import kotlinx.coroutines.flow.Flow

/**
 * Catalogue cache.
 *
 * Listing reads are keyed on the *filter signature* so a cached page is only
 * replayed for the exact query the user made. Showing the "electronics" page for a
 * "shoes" search would be worse than showing nothing.
 */
@Dao
interface CachedCatalogDao {

    /** Insert or replace a freshly fetched page. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProducts(products: List<CachedProductEntity>)

    @Query("SELECT * FROM cached_products WHERE id = :productId LIMIT 1")
    suspend fun findProduct(productId: String): CachedProductEntity?

    /**
     * Replaces the whole cached catalogue.
     *
     * Called only with a full first page, so the cache can never contain a mix of
     * pages from two different sync points.
     */
    @Transaction
    suspend fun replaceAll(products: List<CachedProductEntity>) {
        clearProducts()
        upsertProducts(products)
    }

    @Query("DELETE FROM cached_products")
    suspend fun clearProducts()

    @Query("DELETE FROM cached_products WHERE cachedAtEpochMillis < :olderThan")
    suspend fun evictOlderThan(olderThan: Long)

    @Query("SELECT COUNT(*) FROM cached_products")
    suspend fun count(): Int

    /**
     * The cached catalogue snapshot.
     *
     * Only a single page is ever stored (see [replaceAll]), so an ordered read is
     * already one coherent snapshot — there is no page mixing to guard against.
     */
    @Query("SELECT * FROM cached_products ORDER BY cachedAtEpochMillis DESC LIMIT :limit")
    suspend fun firstPage(limit: Int): List<CachedProductEntity>

    /** Newest cache timestamp, used to label content as "cached" honestly. */
    @Query("SELECT MAX(cachedAtEpochMillis) FROM cached_products")
    suspend fun latestCachedAt(): Long?
}

@Dao
interface CachedCategoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(categories: List<CachedCategoryEntity>)

    @Query("SELECT * FROM cached_categories WHERE isActive = 1 ORDER BY sortOrder ASC, name ASC")
    fun observeActive(): Flow<List<CachedCategoryEntity>>

    @Query("SELECT * FROM cached_categories WHERE isActive = 1 ORDER BY sortOrder ASC, name ASC")
    suspend fun getActive(): List<CachedCategoryEntity>

    @Query("DELETE FROM cached_categories")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM cached_categories")
    suspend fun count(): Int
}

@Dao
interface CachedCartDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(items: List<CachedCartItemEntity>)

    @Query("SELECT * FROM cached_cart_items ORDER BY productName ASC")
    fun observe(): Flow<List<CachedCartItemEntity>>

    @Transaction
    suspend fun replaceAll(items: List<CachedCartItemEntity>) {
        clear()
        upsert(items)
    }

    @Query("DELETE FROM cached_cart_items")
    suspend fun clear()

    @Query("SELECT * FROM cached_cart_items WHERE cartItemId = :cartItemId LIMIT 1")
    suspend fun find(cartItemId: String): CachedCartItemEntity?
}

@Dao
interface CachedOrderDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(orders: List<CachedOrderEntity>)

    @Query("SELECT * FROM cached_orders WHERE scope = :scope ORDER BY createdAtEpochMillis DESC")
    fun observe(scope: String): Flow<List<CachedOrderEntity>>

    @Query("SELECT * FROM cached_orders WHERE scope = :scope AND id = :orderId LIMIT 1")
    suspend fun find(scope: String, orderId: String): CachedOrderEntity?

    @Transaction
    suspend fun replaceAll(scope: String, orders: List<CachedOrderEntity>) {
        clear(scope)
        upsert(orders)
    }

    @Query("DELETE FROM cached_orders WHERE scope = :scope")
    suspend fun clear(scope: String)

    @Query("SELECT COUNT(*) FROM cached_orders WHERE scope = :scope")
    suspend fun count(scope: String): Int

    companion object {
        /** Scope keys — a seller browsing their shop sees a different list. */
        const val SCOPE_CUSTOMER = "customer"
        const val SCOPE_SELLER = "seller"
    }
}
