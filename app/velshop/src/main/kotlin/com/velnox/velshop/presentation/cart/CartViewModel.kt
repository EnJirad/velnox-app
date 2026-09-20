package com.velnox.velshop.presentation.cart

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.velnox.core.common.error.AppError
import com.velnox.core.common.error.VelnoxResult
import com.velnox.core.data.model.Cart
import com.velnox.core.data.model.CartLine
import com.velnox.core.data.repository.CartRepository
import com.velnox.core.ui.component.VelnoxScreenState
import com.velnox.velshop.presentation.cartCountState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The cart.
 *
 * ## Replace, never patch
 *
 * Every mutation endpoint returns the complete updated cart, so this ViewModel
 * *replaces* its state with the server's answer instead of adjusting a local count.
 * That removes the whole class of "the badge says 3 but the server thinks 2" bugs, and
 * it means the displayed subtotal is always the server's own arithmetic.
 *
 * ## One mutation at a time
 *
 * Quantity taps are serialised through [UiState.busyItemId]: a double-tap on "+"
 * previously (in the obvious implementation) issues two `PUT`s whose responses can
 * arrive out of order, leaving the cart showing the older of the two quantities.
 * Ignoring a tap while a mutation is in flight is the honest fix — the button also
 * shows a progress indicator, so nothing looks dropped.
 */
@HiltViewModel
class CartViewModel @Inject constructor(
    private val cartRepository: CartRepository,
) : ViewModel() {

    data class UiState(
        val result: VelnoxScreenState<Cart> = VelnoxScreenState.Loading,
        /** The line currently being mutated, if any. */
        val busyItemId: String? = null,
        val notice: CartNotice? = null,
    )

    /** One-shot feedback. Text is resolved from string resources by the screen. */
    sealed interface CartNotice {
        data object LineRemoved : CartNotice
        data class Failure(val error: AppError) : CartNotice
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    val cartCount: StateFlow<Int> = cartRepository.cartCountState(viewModelScope)

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(result = VelnoxScreenState.Loading) }
            _state.update { current ->
                when (val result = cartRepository.cart()) {
                    is VelnoxResult.Success -> current.copy(result = result.data.asListState())
                    is VelnoxResult.Failure -> current.copy(result = VelnoxScreenState.Failure(result.error))
                }
            }
        }
    }

    fun increase(line: CartLine) {
        // The API's stock ceiling; the backend refuses beyond it and returns the
        // unchanged cart, so blocking here avoids a pointless round trip.
        if (!line.canIncrease) return
        mutate(line.cartItemId) { cartRepository.updateQuantity(line.cartItemId, line.quantity + 1) }
    }

    /**
     * Decrease.
     *
     * At quantity 1 the line is removed instead of being decremented, matching the web
     * cart: the API floor is 1, so the only meaningful next step is removal — and the
     * user is told that is what happened rather than seeing the row vanish silently.
     */
    fun decrease(line: CartLine) {
        if (!line.canDecrease) {
            remove(line)
            return
        }
        mutate(line.cartItemId) { cartRepository.updateQuantity(line.cartItemId, line.quantity - 1) }
    }

    fun remove(line: CartLine) {
        mutate(line.cartItemId, removed = true) { cartRepository.removeLine(line.cartItemId) }
    }

    fun consumeNotice() {
        _state.update { it.copy(notice = null) }
    }

    private fun mutate(
        itemId: String,
        removed: Boolean = false,
        block: suspend () -> VelnoxResult<Cart>,
    ) {
        if (_state.value.busyItemId != null) return

        viewModelScope.launch {
            _state.update { it.copy(busyItemId = itemId, notice = null) }
            when (val result = block()) {
                is VelnoxResult.Success -> _state.update {
                    it.copy(
                        result = result.data.asListState(),
                        busyItemId = null,
                        notice = if (removed) CartNotice.LineRemoved else null,
                    )
                }

                is VelnoxResult.Failure -> _state.update {
                    // The cart is left as it was: the server rejected the change, so
                    // showing a modified local cart would misrepresent it.
                    it.copy(busyItemId = null, notice = CartNotice.Failure(result.error))
                }
            }
        }
    }

    private fun Cart.asListState(): VelnoxScreenState<Cart> =
        if (isEmpty) VelnoxScreenState.Empty else VelnoxScreenState.Content(this)
}
