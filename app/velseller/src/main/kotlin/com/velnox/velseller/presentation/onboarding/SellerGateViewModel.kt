package com.velnox.velseller.presentation.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.velnox.core.common.error.AppError
import com.velnox.core.common.error.VelnoxResult
import com.velnox.core.data.model.SellerStatusInfo
import com.velnox.core.data.repository.SellerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * What a signed-in seller sees before the workspace.
 *
 * ## Why these states are separate and not one boolean
 *
 * `GET /api/seller/status` answers `data: null` for a user who has never applied, and
 * `docs/ai/SELLER.md` names the exact bug this causes on web: "`seller === null`
 * conflated with loading". Collapsing the cases into `isSeller: Boolean` reintroduces
 * it — an applicant whose status merely failed to load would be shown the registration
 * form again, and a rejected applicant would be shown a form with no reason.
 *
 * So the states are explicit:
 *
 *  * [SellerGateState.NoApplication] — the server said `null`: never applied.
 *  * [SellerGateState.NotApproved] — an application exists and is not approved; the
 *    reason the server recorded travels with it.
 *  * [SellerGateState.Approved] — the workspace.
 *  * [SellerGateState.Failure] — the status is *unknown*; retry, never assume.
 */
sealed interface SellerGateState {
    data object Loading : SellerGateState
    data object NoApplication : SellerGateState
    data class NotApproved(val info: SellerStatusInfo) : SellerGateState
    data object Approved : SellerGateState
    data class Failure(val error: AppError) : SellerGateState
}

data class SellerGateUiState(
    val gate: SellerGateState = SellerGateState.Loading,
)

@HiltViewModel
class SellerGateViewModel @Inject constructor(
    private val sellerRepository: SellerRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SellerGateUiState())
    val state: StateFlow<SellerGateUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    /** Re-reads the seller status. Called on entry, on retry, and after applying. */
    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(gate = SellerGateState.Loading) }

            when (val result = sellerRepository.status()) {
                is VelnoxResult.Success -> {
                    val info = result.data
                    _state.update {
                        it.copy(
                            gate = when {
                                // `data: null` — the user has never applied. This is a
                                // real answer, not a missing one.
                                info == null -> SellerGateState.NoApplication
                                info.isApproved -> SellerGateState.Approved
                                else -> SellerGateState.NotApproved(info)
                            },
                        )
                    }
                }

                is VelnoxResult.Failure -> _state.update {
                    it.copy(gate = SellerGateState.Failure(result.error))
                }
            }
        }
    }
}
