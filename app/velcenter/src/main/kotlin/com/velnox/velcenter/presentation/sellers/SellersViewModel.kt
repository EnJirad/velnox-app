package com.velnox.velcenter.presentation.sellers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.velnox.core.common.error.AppError
import com.velnox.core.common.error.VelnoxResult
import com.velnox.core.data.model.ManagedSeller
import com.velnox.core.data.repository.CenterRepository
import com.velnox.core.realtime.RealtimeChannels
import com.velnox.core.realtime.VelnoxRealtimeClient
import com.velnox.core.ui.component.VelnoxScreenState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Seller approval.
 *
 * ## Approving is a real state change with real consequences
 *
 * `PATCH /api/admin/sellers/:id/status` promotes `users.role`, writes an `audit_logs`
 * row and broadcasts on `seller:updated`, all inside a transaction that locks the row.
 * So the app treats every decision as deliberate: the confirmation dialog names the
 * seller and the target status, and the three decisions the backend refuses to record
 * without a reason (`rejected`, `suspended`, `needs_correction`) cannot be confirmed
 * until one is typed. The reason ends up in the audit log and is shown to the applicant.
 *
 * ## Search is debounced
 *
 * The filter and the search term are sent to the server (`?status=&q=`), not filtered
 * locally, because the seller list is unbounded. Typing is therefore debounced so a
 * five-character shop name does not issue five requests.
 *
 * ## Realtime
 *
 * `seller:updated` re-reads the list, so a second operator's approval disappears from
 * this queue without the frame payload being trusted.
 */
@HiltViewModel
class SellersViewModel @Inject constructor(
    private val centerRepository: CenterRepository,
    realtimeClient: VelnoxRealtimeClient,
) : ViewModel() {

    /** A decision awaiting confirmation, plus the reason it may require. */
    data class Decision(
        val seller: ManagedSeller,
        val status: String,
    ) {
        val requiresReason: Boolean get() = status in REASONS_REQUIRED_FOR_SELLER
    }

    data class UiState(
        val result: VelnoxScreenState<List<ManagedSeller>> = VelnoxScreenState.Loading,
        val filter: String = STATUS_ALL,
        val query: String = "",
        val busySellerId: String? = null,
        val decision: Decision? = null,
        val notice: SellersNotice? = null,
    )

    sealed interface SellersNotice {
        data object Updated : SellersNotice
        data class Failure(val error: AppError) : SellersNotice
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var searchJob: Job? = null

    init {
        load()

        realtimeClient.subscribe(RealtimeChannels.SELLER_UPDATED)
        viewModelScope.launch {
            realtimeClient.frames
                .filter { frame -> frame.channel == RealtimeChannels.SELLER_UPDATED }
                .collect { loadQuietly() }
        }
    }

    fun refresh() = load()

    fun setFilter(status: String) {
        if (_state.value.filter == status) return
        _state.update { it.copy(filter = status) }
        load()
    }

    fun onQueryChange(value: String) {
        _state.update { it.copy(query = value) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            load()
        }
    }

    fun requestDecision(seller: ManagedSeller, status: String) {
        _state.update { it.copy(decision = Decision(seller = seller, status = status)) }
    }

    fun cancelDecision() {
        _state.update { it.copy(decision = null) }
    }

    fun confirmDecision(reason: String) {
        val decision = _state.value.decision ?: return
        val trimmedReason = reason.trim()

        // Mirror the backend's own rule so an unusable decision never leaves the device.
        if (decision.requiresReason && trimmedReason.isEmpty()) {
            _state.update {
                it.copy(
                    notice = SellersNotice.Failure(
                        AppError.Validation(serverMessage = "A reason is required for this decision."),
                    ),
                    decision = null,
                )
            }
            return
        }

        viewModelScope.launch {
            _state.update {
                it.copy(decision = null, busySellerId = decision.seller.id, notice = null)
            }

            when (
                val result = centerRepository.setSellerStatus(
                    sellerId = decision.seller.id,
                    status = decision.status,
                    reason = trimmedReason.takeIf { it.isNotEmpty() },
                )
            ) {
                is VelnoxResult.Success -> {
                    _state.update {
                        it.copy(busySellerId = null, notice = SellersNotice.Updated)
                    }
                    loadQuietly()
                }

                is VelnoxResult.Failure -> _state.update {
                    it.copy(busySellerId = null, notice = SellersNotice.Failure(result.error))
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

            val filter = _state.value.filter
            val query = _state.value.query
            val result = centerRepository.sellers(
                status = filter,
                query = query.takeIf { it.isNotBlank() },
            )

            _state.update { current ->
                when (result) {
                    is VelnoxResult.Success -> current.copy(
                        result = if (result.data.isEmpty()) {
                            VelnoxScreenState.Empty
                        } else {
                            VelnoxScreenState.Content(result.data)
                        },
                    )

                    is VelnoxResult.Failure -> when {
                        !showLoader && current.result is VelnoxScreenState.Content -> current
                        else -> current.copy(result = VelnoxScreenState.Failure(result.error))
                    }
                }
            }
        }
    }

    companion object {
        /** `all` is the value the endpoint treats as "no status filter". */
        const val STATUS_ALL = "all"

        /**
         * Decisions the backend refuses to record without a reason — the same set as
         * `REASONS_REQUIRED_FOR_SELLER` in `CenterRepository`.
         */
        val REASONS_REQUIRED_FOR_SELLER = setOf("rejected", "suspended", "needs_correction")

        private const val SEARCH_DEBOUNCE_MS = 350L
    }
}
