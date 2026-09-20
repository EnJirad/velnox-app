package com.velnox.core.data.api

import com.velnox.core.data.dto.CategoryDto
import com.velnox.core.data.dto.ProductDto
import com.velnox.core.data.dto.ShopDto
import com.velnox.core.network.ApiEnvelope
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.QueryMap

/**
 * Public catalogue endpoints. Paths are relative to `…/api/`.
 *
 * All of these are `optionalAuth` server-side, so they work signed out; when signed
 * in they additionally carry the session, which the backend uses for behavioural
 * tracking.
 */
interface VelnoxCatalogApi {

    /**
     * `GET /api/products/catalog` — returns an array directly in `data`.
     * Pagination is `offset`/`limit` (max `limit` is 200 server-side).
     */
    @GET("products/catalog")
    suspend fun catalog(@QueryMap filters: Map<String, String>): ApiEnvelope<List<ProductDto>>

    /** `GET /api/products/:productId` — published products only; 404 otherwise. */
    @GET("products/{productId}")
    suspend fun product(@Path("productId") productId: String): ApiEnvelope<ProductDto>

    /** `GET /api/categories?lang=` — flat, active categories with localised names. */
    @GET("categories")
    suspend fun categories(@Query("lang") lang: String? = null): ApiEnvelope<List<CategoryDto>>

    /** `GET /api/categories/tree?lang=` — roots with nested `children`. */
    @GET("categories/tree")
    suspend fun categoryTree(@Query("lang") lang: String? = null): ApiEnvelope<List<CategoryDto>>

    /** `GET /api/shops` — public shops. */
    @GET("shops")
    suspend fun shops(): ApiEnvelope<List<ShopDto>>

    /** `GET /api/shops/:shopId` — shop detail. */
    @GET("shops/{shopId}")
    suspend fun shop(@Path("shopId") shopId: String): ApiEnvelope<ShopDto>
}
