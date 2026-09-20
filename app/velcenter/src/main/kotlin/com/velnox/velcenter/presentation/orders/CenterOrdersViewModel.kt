package com.velnox.velcenter.presentation.orders

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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The platform-wide order list.
 *
 * An operator's transitions use the same state machine as a seller's
 * ([allowedNextStatuses]) — `PATCH /api/admin/orders/:id/status` enforces it — so the
 * buttons here are the same legal moves, offered to a role that may need to step in when
 * a shop is unresponsive.
 *
 * Cancellation still requires a reason: it is a negative outcome for the customer, and
 * the backend records the reason alongside the audit entry.
 *
 * Admin orders are intentionally **not** cached on the device. The customer and seller
 * caches exist to make those apps usable on a flaky connection; an operator acting on a
 * stale platform-wide list is a worse failure than an empty loading state, and this list
 * is read-only from the cache's point of view anyway.
 */
@HiltViewModel
class CenterOrdersViewModel @Inject constructor(
    private val orderRepository: OrderRepository,
    realtimeClient: VelnoxRealtimeClient,
) : ViewModel() {

    data class TransitionRequest(
        val order: Order,
        val next: OrderStatus,
    ) {
        val requiresReason: Boolean get() = next == OrderStatus.Cancelled
    }

    data class UiState(
        val result: VelnoxScreenState<List<Order>> = VelnoxScreenState.Loading,
        val busyOrderId: String? = null,
        val transition: TransitionRequest? = null,
        val notice: CenterOrdersNotice? = null,
    )

    sealed interface CenterOrdersNotice {
        data object Updated : CenterOrdersNotice
        data class Failure(val error: AppError) : CenterOrdersNotice
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
        if (next !in order.status.allowedNextStatuses) return
        _state.update { it.copy(transition = TransitionRequest(order = order, next = next)) }
    }

    fun cancelTransition() {
        _state.update { it.copy(transition = null) }
    }

    fun confirmTransition(reason: String) {
        val request = _state.value.transition ?: return
        val trimmed = reason.trim()

        if (request.requiresReason && trimmed.isEmpty()) {
            _state.update {
                it.copy(
                    transition = null,
                    notice = CenterOrdersNotice.Failure(
                        AppError.Validation(serverMessage = "A reason is required to cancel an order."),
                    ),
                )
            }
            return
        }

        viewModelScope.launch {
            _state.update {
                it.copy(transition = null, busyOrderId = request.order.id, notice = null)
            }

            when (
                val result = orderRepository.setAdminOrderStatus(
                    orderId = request.order.id,
                    status = request.next.wireValue,
                    reason = trimmed.takeIf { it.isNotEmpty() },
                )
            ) {
                is VelnoxResult.Success -> {
                    _state.update {
                        it.copy(busyOrderId = null, notice = CenterOrdersNotice.Updated)
                    }
                    loadQuietly()
                }

                is VelnoxResult.Failure -> _state.update {
                    it.copy(busyOrderId = null, notice = CenterOrdersNotice.Failure(result.error))
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

            when (val result = orderRepository.adminOrders()) {
                is VelnoxResult.Success -> _state.update {
                    it.copy(
                        result = if (result.data.isEmpty()) {
                            VelnoxScreenState.Empty
                        } else {
                            VelnoxScreenState.Content(result.data)
                        },
                    )
                }

                is VelnoxResult.Failure -> _state.update { current ->
                    if (!showLoader && current.result is VelnoxScreenState.Content) {
                        current
                    } else {
                        current.copy(result = VelnoxScreenState.Failure(result.error))
                    }
                }
            }
        }
    }
}
