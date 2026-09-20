package com.velnox.velshop.presentation.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.velnox.core.common.error.VelnoxResult
import com.velnox.core.data.model.CustomerProfile
import com.velnox.core.data.repository.CartRepository
import com.velnox.core.data.repository.CustomerRepository
import com.velnox.core.ui.component.VelnoxScreenState
import com.velnox.velshop.presentation.cartCountState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The account tab's data.
 *
 * The profile is required — it is the whole point of the screen — while the address
 * and wishlist counts are optional summaries: when one of those calls fails its tile
 * is rendered as unavailable rather than as a confident zero. That distinction is the
 * same one the VelCenter dashboard makes, and it is what stops "0 addresses" from
 * being shown to a user who actually has three.
 */
@HiltViewModel
class AccountViewModel @Inject constructor(
    private val customerRepository: CustomerRepository,
    cartRepository: CartRepository,
) : ViewModel() {

    data class UiState(
        val result: VelnoxScreenState<CustomerProfile> = VelnoxScreenState.Loading,
        /** `null` means "could not be read", never "zero". */
        val addressCount: Int? = null,
        val wishlistCount: Int? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    /** Cart badge for the tab bar; the badge must not vanish on this screen. */
    val cartCount: StateFlow<Int> = cartRepository.cartCountState(viewModelScope)

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(result = VelnoxScreenState.Loading) }

            // Three independent reads: the summaries must not be able to fail the
            // profile, and the profile must not be delayed by them.
            val (profile, addresses, wishlist) = coroutineScope {
                val profileCall = async { customerRepository.profile() }
                val addressesCall = async { customerRepository.addresses() }
                val wishlistCall = async { customerRepository.wishlist() }
                Triple(profileCall.await(), addressesCall.await(), wishlistCall.await())
            }

            val profileState = when (profile) {
                is VelnoxResult.Success -> VelnoxScreenState.Content(profile.data)
                is VelnoxResult.Failure -> VelnoxScreenState.Failure(profile.error)
            }

            _state.update { current ->
                current.copy(
                    result = profileState,
                    // A failed count is null; the screen renders it as unavailable.
                    addressCount = when (addresses) {
                        is VelnoxResult.Success -> addresses.data.size
                        is VelnoxResult.Failure -> null
                    },
                    wishlistCount = when (wishlist) {
                        is VelnoxResult.Success -> wishlist.data.size
                        is VelnoxResult.Failure -> null
                    },
                )
            }
        }
    }
}
