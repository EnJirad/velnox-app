package com.velnox.core.data.repository

import com.velnox.core.common.coroutines.DispatcherProvider
import com.velnox.core.common.error.VelnoxResult
import com.velnox.core.common.error.map
import com.velnox.core.data.api.VelnoxCenterApi
import com.velnox.core.data.api.VelnoxCommerceApi
import com.velnox.core.data.api.VelnoxSellerApi
import com.velnox.core.data.dto.OrderStatusRequest
import com.velnox.core.data.model.Order
import com.velnox.core.data.model.toDomain
import com.velnox.core.database.dao.CachedOrderDao
import com.velnox.core.database.entity.CachedOrderEntity
import com.velnox.core.network.safeApiCall
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orders, for all three apps.
 *
 * One repository rather than three, because the same order is read by a customer in
 * Velshop, by the seller who fulfils it in Velseller, and by an operator in VelCenter.
 * Keeping a single mapping means the status badge, the money formatting and the line
 * items cannot disagree between apps — which is precisely the kind of drift that makes
 * a multi-app suite untrustworthy.
 *
 * Reads for the seller and admin scopes are cached under distinct
 * [CachedOrderDao] scopes, because a seller who also shops would otherwise have one
 * list overwrite the other.
 */
@Singleton
class OrderRepository @Inject constructor(
    private val commerceApi: VelnoxCommerceApi,
    private val sellerApi: VelnoxSellerApi,
    private val centerApi: VelnoxCenterApi,
    private val orderDao: CachedOrderDao,
    private val json: Json,
    private val dispatchers: DispatcherProvider,
) {

    /** Which list a read belongs to. */
    enum class Scope(val cacheKey: String) {
        Customer(CachedOrderDao.SCOPE_CUSTOMER),
        Seller(CachedOrderDao.SCOPE_SELLER),
    }

    suspend fun customerOrders(limit: Int = 50): VelnoxResult<List<Order>> {
        val result = safeApiCall(json) { commerceApi.orders(limit) }.map { list -> list.map { it.toDomain() } }
        if (result is VelnoxResult.Success) cache(Scope.Customer, result.data)
        return result
    }

    /**
     * Order detail.
     *
     * Never served from cache: a customer checks this page to see whether an order
     * shipped, and a stale "confirmed" would be actively misleading.
     */
    suspend fun customerOrder(orderId: String): VelnoxResult<Order> =
        safeApiCall(json) { commerceApi.order(orderId) }.map { it.toDomain() }

    /**
     * Customer cancellation.
     *
     * The backend refuses to cancel an order that has already shipped; the button is
     * therefore only offered for [Order.isCancellable] states, and a refusal is
     * surfaced verbatim rather than swallowed.
     *
     * Returns `Unit`, not the re-read order, on purpose. The cancellation is already
     * recorded server-side when this call succeeds, so a follow-up `GET` that fails
     * (offline the moment the order was cancelled, a 5xx) must not be turned into an
     * error — and must certainly not throw, which is what fetching the follow-up
     * inside the same expression used to do. The caller reloads the list, and if that
     * also fails the screen says so honestly while the order is still cancelled.
     *
     * The customer endpoint takes no reason: only the seller and admin transitions
     * record one (`OrderStatusRequest.reason`).
     */
    suspend fun cancelOrder(orderId: String): VelnoxResult<Unit> =
        safeApiCall(json) { commerceApi.cancelOrder(orderId) }.map { }

    suspend fun sellerOrders(limit: Int = 50): VelnoxResult<List<Order>> {
        val result = safeApiCall(json) { sellerApi.orders(limit) }.map { list -> list.map { it.toDomain() } }
        if (result is VelnoxResult.Success) cache(Scope.Seller, result.data)
        return result
    }

    /**
     * Seller-initiated transition.
     *
     * The caller must only offer transitions from
     * [com.velnox.core.common.domain.allowedNextStatuses]; the backend enforces the
     * same machine, so an illegal attempt is rejected rather than silently applied.
     */
    suspend fun setSellerOrderStatus(
        orderId: String,
        status: String,
        reason: String? = null,
        trackingNumber: String? = null,
        carrier: String? = null,
    ): VelnoxResult<Order> = safeApiCall(json) {
        sellerApi.setOrderStatus(orderId, OrderStatusRequest(status, reason, trackingNumber, carrier))
    }.map { it.toDomain() }

    suspend fun adminOrders(limit: Int = 100): VelnoxResult<List<Order>> =
        safeApiCall(json) { centerApi.orders(limit) }.map { list -> list.map { it.toDomain() } }

    suspend fun setAdminOrderStatus(
        orderId: String,
        status: String,
        reason: String? = null,
    ): VelnoxResult<Order> = safeApiCall(json) {
        centerApi.setOrderStatus(orderId, OrderStatusRequest(status, reason))
    }.map { it.toDomain() }

    /** Cached order summaries for instant rendering. Labelled as cached by the UI. */
    fun observeCached(scope: Scope): Flow<List<Order>> =
        orderDao.observe(scope.cacheKey).map { rows -> rows.map { it.toSummary() } }

    private suspend fun cache(scope: Scope, orders: List<Order>) = withContext(dispatchers.io) {
        orderDao.replaceAll(
            scope.cacheKey,
            orders.map { order ->
                CachedOrderEntity(
                    id = order.id,
                    scope = scope.cacheKey,
                    orderNumber = order.orderNumber,
                    status = order.status.wireValue,
                    paymentStatus = order.paymentStatus.wireValue,
                    total = order.total,
                    currency = order.currency,
                    itemCount = order.itemCount,
                    createdAtEpochMillis = order.createdAtEpochMillis ?: 0L,
                    updatedAtEpochMillis = order.updatedAtEpochMillis ?: 0L,
                    shopName = order.shopName,
                    customerName = order.customerName,
                    trackingNumber = order.trackingNumber,
                    cachedAtEpochMillis = System.currentTimeMillis(),
                )
            },
        )
    }
}

/**
 * Cached order → domain.
 *
 * Contains only summary fields, so a cached order is safe to list but must never be
 * opened as if it were the detail payload — [OrderRepository.customerOrder] always
 * re-fetches for that.
 */
private fun CachedOrderEntity.toSummary(): Order = Order(
    id = id,
    orderNumber = orderNumber,
    customerUserId = null,
    status = com.velnox.core.common.domain.OrderStatus.fromWire(status),
    paymentStatus = com.velnox.core.common.domain.PaymentStatus.fromWire(paymentStatus),
    shippingStatus = "none",
    trackingNumber = trackingNumber,
    subtotal = total,
    shippingFee = 0.0,
    discount = 0.0,
    total = total,
    currency = currency,
    note = null,
    lines = emptyList(),
    shipments = emptyList(),
    shopName = shopName,
    customerName = customerName,
    customerPhone = null,
    itemCount = itemCount,
    createdAtEpochMillis = createdAtEpochMillis.takeIf { it > 0L },
    updatedAtEpochMillis = updatedAtEpochMillis.takeIf { it > 0L },
)
