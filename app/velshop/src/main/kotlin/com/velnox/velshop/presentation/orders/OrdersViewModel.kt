package com.velnox.velshop.presentation.orders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.velnox.core.common.error.AppError
import com.velnox.core.common.error.VelnoxResult
import com.velnox.core.data.model.Order
import com.velnox.core.data.repository.CartRepository
import com.velnox.core.data.repository.OrderRepository
import com.velnox.core.network.connectivity.NetworkMonitor
import com.velnox.core.realtime.RealtimeChannels
import com.velnox.core.realtime.VelnoxRealtimeClient
import com.velnox.core.ui.component.VelnoxScreenState
import com.velnox.velshop.presentation.cartCountState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The customer's order list.
 *
 * ## Realtime as a hint only
 *
 * The socket is subscribed to the two order channels, but a pushed frame never
 * becomes state here: it triggers a re-read of `GET /api/customer/orders`, exactly as
 * `docs/ai/REALTIME.md` requires. A dropped socket therefore costs freshness, never
 * correctness, and the screen works from the API alone.
 *
 * ## Cancellation
 *
 * Only [Order.isCancellable] orders offer the action (the same rule the backend
 * enforces), one mutation runs at a time, and a refusal is shown with the backend's
 * own message rather than being swallowed.
 */
@HiltViewModel
class OrdersViewModel @Inject constructor(
    private val orderRepository: OrderRepository,
    private val cartRepository: CartRepository,
    private val realtimeClient: VelnoxRealtimeClient,
    networkMonitor: NetworkMonitor,
) : ViewModel() {

    data class UiState(
        val result: VelnoxScreenState<List<Order>> = VelnoxScreenState.Loading,
        /** The order currently being mutated, if any — the list stays usable. */
        val busyOrderId: String? = null,
        val notice: OrdersNotice? = null,
    )

    sealed interface OrdersNotice {
        data object Cancelled : OrdersNotice
        data class Failure(val error: AppError) : OrdersNotice
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    /** Drives the offline banner; it never blocks a request by itself. */
    val isOnline: StateFlow<Boolean> = networkMonitor.isOnline

    /** Cart badge, so the tab bar stays truthful while the user is on this screen. */
    val cartCount: StateFlow<Int> = cartRepository.cartCountState(viewModelScope)

    init {
        load()

        realtimeClient.subscribe(RealtimeChannels.ORDER_CREATED)
        realtimeClient.subscribe(RealtimeChannels.ORDER_UPDATED)
        viewModelScope.launch {
            realtimeClient.frames
                .filter { frame ->
                    frame.channel == RealtimeChannels.ORDER_CREATED ||
                        frame.channel == RealtimeChannels.ORDER_UPDATED
                }
                .collect { loadQuietly() }
        }
    }

    fun refresh() = load()

    fun cancel(order: Order) {
        if (!order.isCancellable || _state.value.busyOrderId != null) return

        viewModelScope.launch {
            _state.update { it.copy(busyOrderId = order.id, notice = null) }

            when (val result = orderRepository.cancelOrder(order.id)) {
                is VelnoxResult.Success -> {
                    _state.update { it.copy(busyOrderId = null, notice = OrdersNotice.Cancelled) }
                    // Re-read so the badge shows the server's real status rather than
                    // an optimistic local guess.
                    loadQuietly()
                }

                is VelnoxResult.Failure -> _state.update {
                    it.copy(busyOrderId = null, notice = OrdersNotice.Failure(result.error))
                }
            }
        }
    }

    fun consumeNotice() {
        _state.update { it.copy(notice = null) }
    }

    private fun load() = loadInternal(showLoader = true)

    private fun loadQuietly() = loadInternal(showLoader = false)

    private fun loadInternal(showLoader: Boolean) {
        viewModelScope.launch {
            if (showLoader) _state.update { it.copy(result = VelnoxScreenState.Loading) }

            when (val result = orderRepository.customerOrders()) {
                is VelnoxResult.Success -> _state.update {
                    it.copy(result = result.data.asListState())
                }

                is VelnoxResult.Failure -> _state.update { current ->
                    when {
                        // A background refresh that failed must not throw away orders
                        // the user is looking at.
                        !showLoader && current.result is VelnoxScreenState.Content -> current

                        else -> {
                            val cached = orderRepository
                                .observeCached(OrderRepository.Scope.Customer)
                                .first()
                            current.copy(
                                result = if (cached.isNotEmpty()) {
                                    // Labelled as cached by the screen; never passed off
                                    // as live order state.
                                    VelnoxScreenState.Content(cached, isCached = true)
                                } else {
                                    VelnoxScreenState.Failure(result.error)
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    private fun List<Order>.asListState(): VelnoxScreenState<List<Order>> =
        if (isEmpty()) VelnoxScreenState.Empty else VelnoxScreenState.Content(this)
}
