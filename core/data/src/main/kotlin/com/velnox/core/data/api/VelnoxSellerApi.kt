package com.velnox.core.data.api

import com.velnox.core.data.dto.GoalCreateRequest
import com.velnox.core.data.dto.OrderDto
import com.velnox.core.data.dto.ProductDto
import com.velnox.core.data.dto.ProductStatusRequest
import com.velnox.core.data.dto.SellerApplicationRequest
import com.velnox.core.data.dto.SellerGoalDto
import com.velnox.core.data.dto.SellerIncomeDto
import com.velnox.core.data.dto.SellerProfileEnvelopeDto
import com.velnox.core.data.dto.SellerStatusDto
import com.velnox.core.data.dto.ShopAddressDto
import com.velnox.core.data.dto.ShopDto
import com.velnox.core.data.dto.ShopUpdateRequest
import com.velnox.core.data.dto.StockUpdateRequest
import com.velnox.core.network.ApiAck
import com.velnox.core.network.ApiEnvelope
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Velseller endpoints.
 *
 * The server checks ownership on every one of these (`user → seller → shop`), so the
 * app never has to pass a seller id — the session decides whose data is returned.
 */
interface VelnoxSellerApi {

    /**
     * `GET /api/seller/status`.
     * Answers `data: null` for a signed-in user with no application — call it with
     * `safeApiCallAllowNull`, never with `safeApiCall`.
     */
    @GET("seller/status")
    suspend fun status(): ApiEnvelope<SellerStatusDto?>

    @GET("seller/profile")
    suspend fun profile(): ApiEnvelope<SellerProfileEnvelopeDto>

    /** `POST /api/seller/apply` — creates the pending seller + shop record. */
    @POST("seller/apply")
    suspend fun apply(@Body body: SellerApplicationRequest): ApiEnvelope<SellerStatusDto>

    @PATCH("seller/shop")
    suspend fun updateShop(@Body body: ShopUpdateRequest): ApiEnvelope<ShopDto>

    @PATCH("seller/shop/{shopId}/location")
    suspend fun updateShopLocation(
        @Path("shopId") shopId: String,
        @Body body: ShopAddressDto,
    ): ApiEnvelope<ApiAck>

    // ─── Products ────────────────────────────────────────────────────────────
    @GET("seller/products")
    suspend fun products(): ApiEnvelope<List<ProductDto>>

    @POST("seller/products")
    suspend fun createProduct(@Body body: ProductDto): ApiEnvelope<ProductDto>

    @PATCH("seller/products/{productId}")
    suspend fun updateProduct(
        @Path("productId") productId: String,
        @Body body: ProductDto,
    ): ApiEnvelope<ProductDto>

    /**
     * Status changes go through the lifecycle machine in
     * `backend/lib/product-lifecycle.ts`. Submitting a draft for review is a
     * transition to `pending_review`, not a plain field write.
     */
    @PATCH("seller/products/{productId}/status")
    suspend fun setProductStatus(
        @Path("productId") productId: String,
        @Body body: ProductStatusRequest,
    ): ApiEnvelope<ProductDto>

    @PATCH("seller/products/{productId}/stock")
    suspend fun setStock(
        @Path("productId") productId: String,
        @Body body: StockUpdateRequest,
    ): ApiEnvelope<ApiAck>

    @DELETE("seller/products/{productId}")
    suspend fun deleteProduct(@Path("productId") productId: String): ApiEnvelope<ApiAck>

    // ─── Orders ──────────────────────────────────────────────────────────────
    @GET("seller/orders")
    suspend fun orders(@Query("limit") limit: Int = 50): ApiEnvelope<List<OrderDto>>

    /**
     * Transitions an order. The backend enforces the same state machine the app
     * shows in [com.velnox.core.common.domain.allowedNextStatuses].
     */
    @PATCH("seller/orders/{orderId}/status")
    suspend fun setOrderStatus(
        @Path("orderId") orderId: String,
        @Body body: com.velnox.core.data.dto.OrderStatusRequest,
    ): ApiEnvelope<OrderDto>

    // ─── Intelligence ────────────────────────────────────────────────────────
    @GET("seller/income")
    suspend fun income(): ApiEnvelope<SellerIncomeDto>

    @GET("seller/goals")
    suspend fun goals(): ApiEnvelope<List<SellerGoalDto>>

    @POST("seller/goals")
    suspend fun createGoal(@Body body: GoalCreateRequest): ApiEnvelope<SellerGoalDto>

    @PATCH("seller/goals/{goalId}")
    suspend fun updateGoal(
        @Path("goalId") goalId: String,
        @Body body: GoalCreateRequest,
    ): ApiEnvelope<SellerGoalDto>

    @DELETE("seller/goals/{goalId}")
    suspend fun deleteGoal(@Path("goalId") goalId: String): ApiEnvelope<ApiAck>
}
