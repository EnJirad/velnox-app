package com.velnox.velcenter.presentation.moderation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.velnox.core.common.domain.ProductStatus
import com.velnox.core.common.error.AppError
import com.velnox.core.common.error.VelnoxResult
import com.velnox.core.data.model.ModerationItem
import com.velnox.core.data.repository.CenterRepository
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
 * The product moderation queue.
 *
 * ## Publishing is VelCenter's decision, not the seller's
 *
 * `PATCH /api/admin/products/:id/moderation` runs the same lifecycle machine the seller
 * app's submit action feeds into: a seller moves a draft to `pending_review`, and only a
 * moderator moves it to `published`. That is why the approve action here is the only
 * path to a live product — and why it is not offered twice for something already
 * published.
 *
 * ## A rejection always carries a reason
 *
 * The reason is stored, shown to the seller and required by the backend. The dialog
 * therefore cannot be confirmed empty, so an operator never has to guess what "rejected"
 * without a reason would even mean to the person reading it.
 *
 * ## Realtime
 *
 * `product:updated` re-reads the queue, so a colleague's decision removes the item from
 * this list rather than leaving a stale row that fails when acted on.
 */
@HiltViewModel
class ModerationViewModel @Inject constructor(
    private val centerRepository: CenterRepository,
    realtimeClient: VelnoxRealtimeClient,
) : ViewModel() {

    data class UiState(
        val result: VelnoxScreenState<List<ModerationItem>> = VelnoxScreenState.Loading,
        /** Defaults to the queue that needs work rather than to everything. */
        val filter: ProductStatus? = ProductStatus.PendingReview,
        val busyProductId: String? = null,
        val rejecting: ModerationItem? = null,
        val notice: ModerationNotice? = null,
    )

    sealed interface ModerationNotice {
        data object Updated : ModerationNotice
        data class Failure(val error: AppError) : ModerationNotice
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        load()

        realtimeClient.subscribe(RealtimeChannels.PRODUCT_UPDATED)
        viewModelScope.launch {
            realtimeClient.frames
                .filter { frame -> frame.channel == RealtimeChannels.PRODUCT_UPDATED }
                .collect { loadQuietly() }
        }
    }

    fun refresh() = load()

    fun setFilter(status: ProductStatus?) {
        if (_state.value.filter == status) return
        _state.update { it.copy(filter = status) }
        load()
    }

    fun approve(item: ModerationItem) {
        decide(item = item, status = ProductStatus.Published.wireValue, reason = null)
    }

    fun requestReject(item: ModerationItem) {
        _state.update { it.copy(rejecting = item) }
    }

    fun cancelReject() {
        _state.update { it.copy(rejecting = null) }
    }

    fun confirmReject(reason: String) {
        val item = _state.value.rejecting ?: return
        val trimmed = reason.trim()

        if (trimmed.isEmpty()) {
            _state.update {
                it.copy(
                    rejecting = null,
                    notice = ModerationNotice.Failure(
                        AppError.Validation(serverMessage = "A rejection reason is required."),
                    ),
                )
            }
            return
        }

        _state.update { it.copy(rejecting = null) }
        decide(item = item, status = ProductStatus.Rejected.wireValue, reason = trimmed)
    }

    fun consumeNotice() {
        _state.update { it.copy(notice = null) }
    }

    private fun decide(item: ModerationItem, status: String, reason: String?) {
        if (_state.value.busyProductId != null) return

        viewModelScope.launch {
            _state.update { it.copy(busyProductId = item.id, notice = null) }

            when (
                val result = centerRepository.moderateProduct(
                    productId = item.id,
                    status = status,
                    reason = reason,
                )
            ) {
                is VelnoxResult.Success -> {
                    _state.update { it.copy(busyProductId = null, notice = ModerationNotice.Updated) }
                    loadQuietly()
                }

                is VelnoxResult.Failure -> _state.update {
                    it.copy(busyProductId = null, notice = ModerationNotice.Failure(result.error))
                }
            }
        }
    }

    private fun load() = loadInternal(showLoader = true)

    private fun loadQuietly() = loadInternal(showLoader = false)

    private fun loadInternal(showLoader: Boolean) {
        viewModelScope.launch {
            if (showLoader) _state.update { it.copy(result = VelnoxScreenState.Loading) }

            val status = _state.value.filter?.wireValue
            val result = centerRepository.moderationQueue(status = status)

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
}
