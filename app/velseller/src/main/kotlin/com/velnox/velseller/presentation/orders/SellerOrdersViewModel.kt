package com.velnox.velseller.presentation.orders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.velnox.core.common.domain.OrderStatus
import com.velnox.core.common.domain.allowedNextStatuses
import com.velnox.core.common.error.AppError
import com.velnox.core.common.error.VelnoxResult
import com.velnox.core.data.model.Order
import com.velnox.core.data.repository.OrderRepository
import com.velnox.core.realtime.RealtimeChannels
import com.velnox.core.realtime.VelnoxRealtimeClient
import com.velnox.core.ui.component.VelnoxScreenState
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
 * Orders that belong to this seller.
 *
 * ## The state machine is the UI
 *
 * The buttons a seller sees are derived from
 * [com.velnox.core.common.domain.allowedNextStatuses] — the same machine
 * `backend/lib/seller-orders.ts` enforces — so there is no button that always fails and
 * no legal move that is missing.
 *
 * ## Transitions that need more than a status
 *
 * Cancelling an order requires a reason and shipping one usually carries a tracking
 * number, so those two transitions open a form first ([TransitionRequest]). Sending a
 * bare status change for them would be refused by the backend, or worse, recorded
 * without the information the customer needs.
 *
 * ## Realtime
 *
 * `order:created` / `order:updated` frames trigger a re-read of the REST list; the frame
 * payload is never treated as state.
 */
@HiltViewModel
class SellerOrdersViewModel @Inject constructor(
    private val orderRepository: OrderRepository,
    realtimeClient: VelnoxRealtimeClient,
) : ViewModel() {

    /** A transition the user asked for, pending any extra detail it needs. */
    data class TransitionRequest(
        val order: Order,
        val next: OrderStatus,
    ) {
        val needsReason: Boolean get() = next == OrderStatus.Cancelled
        val needsTracking: Boolean get() = next == OrderStatus.Shipped
    }

    data class UiState(
        val result: VelnoxScreenState<List<Order>> = VelnoxScreenState.Loading,
        val busyOrderId: String? = null,
        val transition: TransitionRequest? = null,
        val notice: SellerOrdersNotice? = null,
    )

    sealed interface SellerOrdersNotice {
        data object Updated : SellerOrdersNotice
        data class Failure(val error: AppError) : SellerOrdersNotice
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

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

    fun requestTransition(order: Order, next: OrderStatus) {
        // Refuse a transition the machine does not allow, even if some caller offers it.
        if (next !in order.status.allowedNextStatuses) return
        _state.update { it.copy(transition = TransitionRequest(order = order, next = next)) }
    }

    fun cancelTransition() {
        _state.update { it.copy(transition = null) }
    }

    fun confirmTransition(reason: String, trackingNumber: String, carrier: String) {
        val request = _state.value.transition ?: return

        viewModelScope.launch {
            _state.update {
                it.copy(transition = null, busyOrderId = request.order.id, notice = null)
            }

            when (
                val result = orderRepository.setSellerOrderStatus(
                    orderId = request.order.id,
                    status = request.next.wireValue,
                    reason = reason.trim().takeIf { it.isNotEmpty() },
                    trackingNumber = trackingNumber.trim().takeIf { it.isNotEmpty() },
                    carrier = carrier.trim().takeIf { it.isNotEmpty() },
                )
            ) {
                is VelnoxResult.Success -> {
                    _state.update {
                        it.copy(busyOrderId = null, notice = SellerOrdersNotice.Updated)
                    }
                    loadQuietly()
                }

                is VelnoxResult.Failure -> _state.update {
                    it.copy(busyOrderId = null, notice = SellerOrdersNotice.Failure(result.error))
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

            when (val result = orderRepository.sellerOrders()) {
                is VelnoxResult.Success -> _state.update {
                    it.copy(result = result.data.asListState())
                }

                is VelnoxResult.Failure -> _state.update { current ->
                    when {
                        !showLoader && current.result is VelnoxScreenState.Content -> current

                        else -> {
                            // Seller and customer scopes are cached separately, so this
                            // fallback can never show a seller their shopping list.
                            val cached = orderRepository
                                .observeCached(OrderRepository.Scope.Seller)
                                .first()
                            current.copy(
                                result = if (cached.isNotEmpty()) {
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
