package com.velnox.velcenter.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.velnox.core.common.error.VelnoxResult
import com.velnox.core.data.model.DashboardCounts
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
 * Platform counts for the operator's dashboard.
 *
 * ## A missing count is not a zero
 *
 * `GET /api/admin/dashboard/counts` has grown over time and every figure is optional in
 * the payload. `DashboardCounts` therefore keeps them nullable and the screen renders an
 * absent figure as unavailable. Showing "0 pending sellers" because a field was missing
 * would be a false all-clear on the one screen whose job is to tell an operator there is
 * work waiting.
 *
 * ## Realtime
 *
 * `seller:updated`, `product:updated` and `audit:created` frames trigger a re-read of the
 * counts endpoint. Approving on one device therefore updates another operator's tiles —
 * but through the REST call, never from the frame payload.
 */
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val centerRepository: CenterRepository,
    realtimeClient: VelnoxRealtimeClient,
) : ViewModel() {

    data class UiState(
        val result: VelnoxScreenState<DashboardCounts> = VelnoxScreenState.Loading,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        load()

        realtimeClient.subscribe(RealtimeChannels.SELLER_UPDATED)
        realtimeClient.subscribe(RealtimeChannels.PRODUCT_UPDATED)
        realtimeClient.subscribe(RealtimeChannels.AUDIT_CREATED)
        viewModelScope.launch {
            realtimeClient.frames
                .filter { frame ->
                    frame.channel == RealtimeChannels.SELLER_UPDATED ||
                        frame.channel == RealtimeChannels.PRODUCT_UPDATED ||
                        frame.channel == RealtimeChannels.AUDIT_CREATED
                }
                .collect { loadQuietly() }
        }
    }

    fun refresh() = load()

    private fun load() = loadInternal(showLoader = true)

    private fun loadQuietly() = loadInternal(showLoader = false)

    private fun loadInternal(showLoader: Boolean) {
        viewModelScope.launch {
            if (showLoader) _state.update { it.copy(result = VelnoxScreenState.Loading) }

            _state.update { current ->
                when (val result = centerRepository.dashboardCounts()) {
                    is VelnoxResult.Success -> current.copy(result = VelnoxScreenState.Content(result.data))

                    is VelnoxResult.Failure -> when {
                        // Keep the tiles the operator is reading if a background refresh
                        // failed; only a foreground load may replace them with an error.
                        !showLoader && current.result is VelnoxScreenState.Content -> current
                        else -> current.copy(result = VelnoxScreenState.Failure(result.error))
                    }
                }
            }
        }
    }
}
