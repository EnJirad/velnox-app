package com.velnox.core.database.di

import android.content.Context
import androidx.room.Room
import com.velnox.core.database.VelnoxDatabase
import com.velnox.core.database.dao.CachedCartDao
import com.velnox.core.database.dao.CachedCatalogDao
import com.velnox.core.database.dao.CachedCategoryDao
import com.velnox.core.database.dao.CachedOrderDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): VelnoxDatabase =
        Room.databaseBuilder(context, VelnoxDatabase::class.java, VelnoxDatabase.NAME)
            // Safe *only* because every table is a projection of data the API already
            // returned: dropping the file costs one refetch and cannot lose user data.
            // Anything that must survive an upgrade lives in core:storage instead.
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideCatalogDao(database: VelnoxDatabase): CachedCatalogDao = database.catalogDao()

    @Provides
    fun provideCategoryDao(database: VelnoxDatabase): CachedCategoryDao = database.categoryDao()

    @Provides
    fun provideCartDao(database: VelnoxDatabase): CachedCartDao = database.cartDao()

    @Provides
    fun provideOrderDao(database: VelnoxDatabase): CachedOrderDao = database.orderDao()
}
