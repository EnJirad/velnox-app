package com.velnox.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.velnox.core.database.dao.CachedCartDao
import com.velnox.core.database.dao.CachedCatalogDao
import com.velnox.core.database.dao.CachedCategoryDao
import com.velnox.core.database.dao.CachedOrderDao
import com.velnox.core.database.entity.CachedCartItemEntity
import com.velnox.core.database.entity.CachedCategoryEntity
import com.velnox.core.database.entity.CachedOrderEntity
import com.velnox.core.database.entity.CachedProductEntity

/**
 * On-device **read-through cache** for Neon.
 *
 * ## What this database is not
 *
 * It is not a second source of truth and it holds no commerce state of its own.
 * Per `docs/ai/ARCHITECTURE.md` ("Neon is the only commerce source of truth"),
 * every table here is a projection of a response that the API already returned,
 * stamped with `cachedAtEpochMillis` so the UI can label it as cached.
 *
 * ## No write queue, on purpose
 *
 * There is deliberately no outbox / pending-mutation table. Checkout, order status
 * changes, product writes and cart mutations are **never** queued for later
 * replay: `POST /api/customer/checkout` is idempotent server-side through the
 * `checkout_requests` table and an idempotency key, and a mobile client that
 * replays writes after a flaky connection would be inventing a second, weaker
 * idempotency story. When the network is gone, mutations fail honestly and the user
 * retries deliberately.
 *
 * ## Migration policy
 *
 * `fallbackToDestructiveMigration` is acceptable *here and only here*, because the
 * database is a cache: dropping it costs one refetch and cannot lose user data.
 * `core:storage` owns everything that must survive an upgrade.
 */
@Database(
    entities = [
        CachedProductEntity::class,
        CachedCategoryEntity::class,
        CachedCartItemEntity::class,
        CachedOrderEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class VelnoxDatabase : RoomDatabase() {

    abstract fun catalogDao(): CachedCatalogDao

    abstract fun categoryDao(): CachedCategoryDao

    abstract fun cartDao(): CachedCartDao

    abstract fun orderDao(): CachedOrderDao

    companion object {
        const val NAME = "velnox-cache.db"
    }
}
