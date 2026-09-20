package com.velnox.core.data.di

import com.velnox.core.data.api.VelnoxCatalogApi
import com.velnox.core.data.api.VelnoxCenterApi
import com.velnox.core.data.api.VelnoxCommerceApi
import com.velnox.core.data.api.VelnoxSellerApi
import com.velnox.core.data.api.VelnoxUploadApi
import com.velnox.core.network.di.VelnoxRetrofit
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import retrofit2.create
import javax.inject.Singleton

/**
 * Binds the five API surfaces onto the single authenticated Retrofit instance.
 *
 * All of them share that instance on purpose: they must share the cookie jar, the
 * auth interceptor, the retry policy and the timeouts, or a screen could behave
 * differently depending on which endpoint it happened to call.
 */
@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideCatalogApi(@VelnoxRetrofit retrofit: Retrofit): VelnoxCatalogApi = retrofit.create()

    @Provides
    @Singleton
    fun provideCommerceApi(@VelnoxRetrofit retrofit: Retrofit): VelnoxCommerceApi = retrofit.create()

    @Provides
    @Singleton
    fun provideSellerApi(@VelnoxRetrofit retrofit: Retrofit): VelnoxSellerApi = retrofit.create()

    @Provides
    @Singleton
    fun provideCenterApi(@VelnoxRetrofit retrofit: Retrofit): VelnoxCenterApi = retrofit.create()

    @Provides
    @Singleton
    fun provideUploadApi(@VelnoxRetrofit retrofit: Retrofit): VelnoxUploadApi = retrofit.create()
}
