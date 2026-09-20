package com.velnox.core.ui.di

import android.content.Context
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import com.velnox.core.network.di.PlainHttpClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Singleton

/**
 * The one Coil image loader for all three apps.
 *
 * ## Why the plain HTTP client
 *
 * Images are served from Cloudflare R2's public domain. Sending the Velnox session
 * cookie or `Authorization` header to a third-party host would leak the credential,
 * and a presigned URL would additionally reject the unexpected header. The
 * [PlainHttpClient] carries no Velnox headers at all.
 *
 * ## Caching is configured explicitly
 *
 * The brief requires caching for images, and it is also the single largest lever on
 * perceived performance in a catalogue: without a disk cache, scrolling back up
 * re-downloads every photo. Both caches are bounded and sized for a phone.
 */
@Module
@InstallIn(SingletonComponent::class)
object ImageLoaderModule {

    @Provides
    @Singleton
    fun provideImageLoader(
        @ApplicationContext context: Context,
        @PlainHttpClient httpClient: OkHttpClient,
    ): ImageLoader = ImageLoader.Builder(context)
        .okHttpClient(httpClient)
        .memoryCache {
            MemoryCache.Builder(context)
                .maxSizePercent(MEMORY_CACHE_FRACTION)
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(context.cacheDir.resolve("velnox-images"))
                .maxSizeBytes(DISK_CACHE_BYTES)
                .build()
        }
        // Cached images are fine for a catalogue. R2 keys for profile and shop media
        // are fixed (`shop/{id}/logo.webp`), so the web clients already cache-bust
        // with `?v=`; the app relies on the same query string.
        .memoryCachePolicy(CachePolicy.ENABLED)
        .diskCachePolicy(CachePolicy.ENABLED)
        .crossfade(true)
        .build()

    /** A quarter of the process memory budget is generous but bounded. */
    private const val MEMORY_CACHE_FRACTION = 0.25

    private const val DISK_CACHE_BYTES = 64L * 1024 * 1024
}
