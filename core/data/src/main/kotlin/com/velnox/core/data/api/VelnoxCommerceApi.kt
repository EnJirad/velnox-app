package com.velnox.core.data.api

import com.velnox.core.data.dto.AddToCartRequest
import com.velnox.core.data.dto.AddressDto
import com.velnox.core.data.dto.AddressUpsertRequest
import com.velnox.core.data.dto.CartDto
import com.velnox.core.data.dto.CheckoutRequest
import com.velnox.core.data.dto.CheckoutResultDto
import com.velnox.core.data.dto.CustomerProfileDto
import com.velnox.core.data.dto.NotificationDto
import com.velnox.core.data.dto.OrderDto
import com.velnox.core.data.dto.UpdateCartItemRequest
import com.velnox.core.data.dto.WishlistItemDto
import com.velnox.core.data.dto.WishlistToggleRequest
import com.velnox.core.network.ApiAck
import com.velnox.core.network.ApiEnvelope
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/** Customer-side commerce: cart, checkout, orders, profile, addresses, wishlist. */
interface VelnoxCommerceApi {

    // ─── Cart ────────────────────────────────────────────────────────────────
    @GET("customer/cart")
    suspend fun cart(): ApiEnvelope<CartDto>

    @POST("customer/cart/add")
    suspend fun addToCart(@Body body: AddToCartRequest): ApiEnvelope<CartDto>

    @PUT("customer/cart/item/{cartItemId}")
    suspend fun updateCartItem(
        @Path("cartItemId") cartItemId: String,
        @Body body: UpdateCartItemRequest,
    ): ApiEnvelope<CartDto>

    @DELETE("customer/cart/item/{cartItemId}")
    suspend fun removeCartItem(@Path("cartItemId") cartItemId: String): ApiEnvelope<CartDto>

    /**
     * Checkout. Idempotent server-side through `checkout_requests`; the request
     * carries the matching idempotency key so a genuine retry cannot double-charge.
     */
    @POST("customer/checkout")
    suspend fun checkout(@Body body: CheckoutRequest): ApiEnvelope<CheckoutResultDto>

    // ─── Orders ──────────────────────────────────────────────────────────────
    @GET("customer/orders")
    suspend fun orders(@Query("limit") limit: Int = 50): ApiEnvelope<List<OrderDto>>

    @GET("customer/orders/{orderId}")
    suspend fun order(@Path("orderId") orderId: String): ApiEnvelope<OrderDto>

    @PATCH("customer/orders/{orderId}/cancel")
    suspend fun cancelOrder(@Path("orderId") orderId: String): ApiEnvelope<ApiAck>

    // ─── Profile ─────────────────────────────────────────────────────────────
    @GET("customer/profile")
    suspend fun profile(): ApiEnvelope<CustomerProfileDto>

    @PUT("customer/profile")
    suspend fun updateProfile(@Body body: CustomerProfileDto): ApiEnvelope<CustomerProfileDto>

    // ─── Addresses ───────────────────────────────────────────────────────────
    @GET("customer/addresses")
    suspend fun addresses(): ApiEnvelope<List<AddressDto>>

    @POST("customer/addresses")
    suspend fun createAddress(@Body body: AddressUpsertRequest): ApiEnvelope<AddressDto>

    @PUT("customer/addresses/{addressId}")
    suspend fun updateAddress(
        @Path("addressId") addressId: String,
        @Body body: AddressUpsertRequest,
    ): ApiEnvelope<AddressDto>

    @DELETE("customer/addresses/{addressId}")
    suspend fun deleteAddress(@Path("addressId") addressId: String): ApiEnvelope<ApiAck>

    // ─── Wishlist ────────────────────────────────────────────────────────────
    @GET("customer/wishlist")
    suspend fun wishlist(): ApiEnvelope<List<WishlistItemDto>>

    @POST("customer/wishlist/toggle")
    suspend fun toggleWishlist(@Body body: WishlistToggleRequest): ApiEnvelope<ApiAck>

    // ─── Notifications ───────────────────────────────────────────────────────
    @GET("customer/notifications")
    suspend fun notifications(): ApiEnvelope<List<NotificationDto>>

    @PATCH("customer/notifications/{notificationId}/read")
    suspend fun markNotificationRead(@Path("notificationId") notificationId: String): ApiEnvelope<ApiAck>

    @PUT("customer/notifications/read-all")
    suspend fun markAllNotificationsRead(): ApiEnvelope<ApiAck>
}
