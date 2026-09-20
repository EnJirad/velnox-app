package com.velnox.velseller.presentation.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.velnox.core.common.error.VelnoxResult
import com.velnox.core.data.model.SellerProfile
import com.velnox.core.data.repository.SellerRepository
import com.velnox.core.ui.component.VelnoxScreenState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The seller's own shop record.
 *
 * `GET /api/seller/profile` resolves the seller from the session, so the app never sends
 * a seller id — a seller cannot request another seller's shop even by mistake. Shop name,
 * logo and address are edited on the web (where the media upload and address picker
 * already exist); this screen reports them and states clearly where they are changed,
 * rather than offering a half-featured editor.
 */
@HiltViewModel
class ShopAccountViewModel @Inject constructor(
    private val sellerRepository: SellerRepository,
) : ViewModel() {

    data class UiState(
        val result: VelnoxScreenState<SellerProfile> = VelnoxScreenState.Loading,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(result = VelnoxScreenState.Loading) }

            _state.update { current ->
                when (val result = sellerRepository.profile()) {
                    is VelnoxResult.Success -> current.copy(result = VelnoxScreenState.Content(result.data))
                    is VelnoxResult.Failure -> current.copy(result = VelnoxScreenState.Failure(result.error))
                }
            }
        }
    }
}
