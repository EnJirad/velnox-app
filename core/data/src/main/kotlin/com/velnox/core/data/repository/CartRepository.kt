package com.velnox.core.data.repository

import com.velnox.core.common.coroutines.DispatcherProvider
import com.velnox.core.common.error.AppError
import com.velnox.core.common.error.VelnoxResult
import com.velnox.core.data.api.VelnoxCommerceApi
import com.velnox.core.data.dto.AddToCartRequest
import com.velnox.core.data.dto.CartDto
import com.velnox.core.data.dto.CheckoutRequest
import com.velnox.core.data.dto.CheckoutResultDto
import com.velnox.core.data.dto.UpdateCartItemRequest
import com.velnox.core.data.model.Cart
import com.velnox.core.data.model.CartLine
import com.velnox.core.data.model.toDomain
import com.velnox.core.database.dao.CachedCartDao
import com.velnox.core.database.entity.CachedCartItemEntity
import com.velnox.core.logging.VelnoxLog
import com.velnox.core.network.safeApiCall
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cart and checkout.
 *
 * ## Cart
 *
 * One cart per user server-side (`ensureCart`). Every mutation returns the complete
 * updated cart, so the client **replaces** its state rather than patching it, which
 * removes a whole class of "my cart shows 3, the server thinks 2" bugs.
 *
 * ## Checkout and idempotency
 *
 * Checkout is a mutation that must never be retried blindly, and the brief requires a
 * real idempotency strategy instead of hopeful retries. The strategy is the backend's
 * own: `POST /api/customer/checkout` deduplicates through the `checkout_requests`
 * table keyed on an idempotency key. This repository derives that key
 * deterministically from the caller's **attempt key**, so:
 *
 *  * retrying the same attempt (the user pressed the button again after a timeout)
 *    reuses the identical key and cannot create a second order;
 *  * a new attempt produces a new key.
 *
 * The HTTP layer refuses to retry any non-idempotent method, so this class is the
 * only place a checkout request can ever be repeated — and only with the same key.
 */
@Singleton
class CartRepository @Inject constructor(
    private val api: VelnoxCommerceApi,
    private val cartDao: CachedCartDao,
    private val json: Json,
    private val dispatchers: DispatcherProvider,
) {

    suspend fun cart(): VelnoxResult<Cart> = applyCart(safeApiCall(json) { api.cart() })

    suspend fun addToCart(productId: String, quantity: Int, variantId: String?): VelnoxResult<Cart> {
        if (productId.isBlank()) {
            return VelnoxResult.Failure(AppError.Validation(serverMessage = "Missing product."))
        }
        if (quantity < 1) {
            return VelnoxResult.Failure(AppError.Validation(serverMessage = "Quantity must be at least 1."))
        }
        return applyCart(
            safeApiCall(json) {
                api.addToCart(AddToCartRequest(productId = productId, quantity = quantity, variantId = variantId))
            },
        )
    }

    suspend fun updateQuantity(cartItemId: String, quantity: Int): VelnoxResult<Cart> {
        if (quantity < 1) {
            // 1 is the floor; emptying a line is an explicit delete, so a mis-tap on
            // minus can never silently drop an item.
            return VelnoxResult.Failure(AppError.Validation(serverMessage = "Quantity must be at least 1."))
        }
        return applyCart(
            safeApiCall(json) { api.updateCartItem(cartItemId, UpdateCartItemRequest(quantity = quantity)) },
        )
    }

    suspend fun removeLine(cartItemId: String): VelnoxResult<Cart> =
        applyCart(safeApiCall(json) { api.removeCartItem(cartItemId) })

    /** Cached cart for an instant cold-start render. Never authoritative. */
    fun observeCachedCart(): Flow<Cart> = cartDao.observe().map { rows -> Cart(rows.map { it.toLine() }) }

    suspend fun clearCache() = withContext(dispatchers.io) { cartDao.clear() }

    /**
     * Places the order.
     *
     * @param attemptKey stable identifier for *this* user attempt. Callers reuse it
     *   when retrying the same attempt and use a new one for a new attempt.
     */
    suspend fun checkout(
        attemptKey: String,
        addressId: String,
        paymentMethod: String = PAYMENT_COD,
        note: String? = null,
    ): VelnoxResult<CheckoutResultDto> {
        if (addressId.isBlank()) {
            return VelnoxResult.Failure(
                AppError.Validation(serverMessage = "A delivery address is required before checkout."),
            )
        }

        val idempotencyKey = UUID.nameUUIDFromBytes(attemptKey.toByteArray()).toString()

        val result = safeApiCall(json) {
            api.checkout(
                CheckoutRequest(
                    idempotencyKey = idempotencyKey,
                    addressId = addressId,
                    paymentMethod = paymentMethod,
                    note = note,
                ),
            )
        }

        if (result is VelnoxResult.Success) {
            // The cart is consumed server-side; the local cache must not keep showing
            // items that no longer exist.
            clearCache()
        } else if (result is VelnoxResult.Failure && !result.error.isRetryable) {
            VelnoxLog.w(TAG) { "Checkout rejected: ${result.error.serverCode}" }
        }

        return result
    }

    /** Replace-state semantics plus write-through caching for every cart response. */
    private suspend fun applyCart(result: VelnoxResult<CartDto>): VelnoxResult<Cart> = when (result) {
        is VelnoxResult.Success -> {
            val cart = Cart(result.data.items.map { it.toDomain() })
            cache(cart)
            VelnoxResult.Success(cart)
        }

        is VelnoxResult.Failure -> result
    }

    private suspend fun cache(cart: Cart) = withContext(dispatchers.io) {
        cartDao.replaceAll(
            cart.lines.map { line ->
                CachedCartItemEntity(
                    cartItemId = line.cartItemId,
                    productId = line.productId,
                    variantId = line.variantId,
                    productName = line.productName,
                    variantName = line.variantLabel,
                    imageUrl = line.imageUrl,
                    unitPrice = line.priceSnapshot,
                    quantity = line.quantity,
                    availableStock = line.availableStock,
                    currency = CURRENCY,
                    shopId = null,
                    shopName = line.shopName,
                    cachedAtEpochMillis = System.currentTimeMillis(),
                )
            },
        )
    }

    companion object {
        private const val TAG = "CartRepository"

        /** The single Velnox currency. */
        const val CURRENCY = "THB"

        /** Cash on delivery — the method the backend can create without Stripe. */
        const val PAYMENT_COD = "cod"

        /** Stripe redirect flow (`backend/routes/stripe.ts`). */
        const val PAYMENT_CARD = "card"
    }
}

private fun CachedCartItemEntity.toLine(): CartLine = CartLine(
    cartItemId = cartItemId,
    productId = productId,
    variantId = variantId,
    productName = productName,
    variantLabel = variantName,
    unit = null,
    imageUrl = imageUrl,
    priceSnapshot = unitPrice,
    quantity = quantity,
    availableStock = availableStock,
    shopName = shopName,
)
