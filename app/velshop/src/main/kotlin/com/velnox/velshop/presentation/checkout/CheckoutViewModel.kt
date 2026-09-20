package com.velnox.velshop.presentation.checkout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.velnox.core.common.error.AppError
import com.velnox.core.common.error.VelnoxResult
import com.velnox.core.data.model.Address
import com.velnox.core.data.model.Cart
import com.velnox.core.data.repository.CartRepository
import com.velnox.core.data.repository.CustomerRepository
import com.velnox.core.ui.component.VelnoxScreenState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * Checkout: address, payment method, note, and the one mutation that must never be
 * duplicated.
 *
 * ## The idempotency contract
 *
 * `POST /api/customer/checkout` is deduplicated server-side through
 * `checkout_requests`, keyed on the `idempotencyKey` this client sends.
 * [attemptKey] is that key's source, and its lifecycle is deliberately narrow:
 *
 *  * it is created on the first submit of an attempt,
 *  * **kept** when the attempt fails, so pressing the button again after a timeout
 *    sends the identical key and cannot create a second order,
 *  * cleared only when the order is actually placed, so the next purchase is a
 *    genuinely new attempt with a new key.
 *
 * The HTTP layer never retries a `POST`, so this class is the only place a checkout
 * request can be repeated at all — and only ever with the same key.
 *
 * ## Both reads are required
 *
 * The addresses and the cart are fetched together. A cart that changed since the user
 * opened it, or an address list that failed to load, must be visible *before*
 * committing: showing a summary built from half a load is how a user confirms an
 * order they did not intend.
 */
@HiltViewModel
class CheckoutViewModel @Inject constructor(
    private val cartRepository: CartRepository,
    private val customerRepository: CustomerRepository,
) : ViewModel() {

    data class CheckoutData(
        val addresses: List<Address>,
        val cart: Cart,
    )

    /** A placed order. [checkoutUrl] is present only for the Stripe card flow. */
    data class PlacedOrder(
        val orderNumber: String?,
        val checkoutUrl: String?,
    )

    data class UiState(
        val result: VelnoxScreenState<CheckoutData> = VelnoxScreenState.Loading,
        val selectedAddressId: String? = null,
        val paymentMethod: String = CartRepository.PAYMENT_COD,
        val note: String = "",
        val submitting: Boolean = false,
        val error: AppError? = null,
        val placed: PlacedOrder? = null,
    ) {
        val data: CheckoutData? get() = (result as? VelnoxScreenState.Content<CheckoutData>)?.value

        val selectedAddress: Address?
            get() = data?.addresses?.firstOrNull { it.id == selectedAddressId }

        /** The backend refuses a checkout without an address, so the button waits. */
        val canSubmit: Boolean
            get() = !submitting && selectedAddress != null && data?.cart?.isEmpty == false
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    /** Stable per-attempt key — see the class documentation. */
    private var attemptKey: String? = null

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(result = VelnoxScreenState.Loading, error = null) }

            // Both calls are needed to render one complete summary, so they are made
            // concurrently and both must succeed for the screen to be usable.
            val (addressResult, cartResult) = coroutineScope {
                val addresses = async { customerRepository.addresses() }
                val cart = async { cartRepository.cart() }
                addresses.await() to cart.await()
            }

            when {
                addressResult is VelnoxResult.Failure ->
                    _state.update { it.copy(result = VelnoxScreenState.Failure(addressResult.error)) }

                cartResult is VelnoxResult.Failure ->
                    _state.update { it.copy(result = VelnoxScreenState.Failure(cartResult.error)) }

                cartResult is VelnoxResult.Success && cartResult.data.isEmpty ->
                    _state.update { it.copy(result = VelnoxScreenState.Empty) }

                addressResult is VelnoxResult.Success && cartResult is VelnoxResult.Success -> {
                    val addresses = addressResult.data
                    _state.update { current ->
                        current.copy(
                            result = VelnoxScreenState.Content(
                                CheckoutData(addresses = addresses, cart = cartResult.data),
                            ),
                            // Preselect the default address, else the first one. An
                            // explicit choice always wins over both.
                            selectedAddressId = current.selectedAddressId
                                ?: addresses.firstOrNull { it.isDefault }?.id
                                ?: addresses.firstOrNull()?.id,
                        )
                    }
                }
            }
        }
    }

    fun selectAddress(addressId: String) {
        _state.update { it.copy(selectedAddressId = addressId) }
    }

    fun selectPaymentMethod(method: String) {
        _state.update { it.copy(paymentMethod = method) }
    }

    fun onNoteChange(value: String) {
        _state.update { it.copy(note = value) }
    }

    fun consumeError() {
        _state.update { it.copy(error = null) }
    }

    /** Places the order. Safe to call again after a failure — see the class docs. */
    fun submit() {
        val current = _state.value
        val address = current.selectedAddress ?: return
        if (!current.canSubmit) return

        val key = attemptKey ?: UUID.randomUUID().toString().also { attemptKey = it }

        viewModelScope.launch {
            _state.update { it.copy(submitting = true, error = null) }

            val result = cartRepository.checkout(
                attemptKey = key,
                addressId = address.id,
                paymentMethod = current.paymentMethod,
                note = current.note.takeIf { it.isNotBlank() },
            )

            when (result) {
                is VelnoxResult.Success -> {
                    // The attempt is finished; a future checkout is a new one.
                    attemptKey = null
                    _state.update {
                        it.copy(
                            submitting = false,
                            placed = PlacedOrder(
                                orderNumber = result.data.orderNumber,
                                checkoutUrl = result.data.checkoutUrl,
                            ),
                        )
                    }
                }

                is VelnoxResult.Failure -> _state.update {
                    // The key is intentionally NOT cleared: retrying this same attempt
                    // must reuse it, or a slow-but-successful checkout could be
                    // replayed as a second order.
                    it.copy(submitting = false, error = result.error)
                }
            }
        }
    }
}
