package com.velnox.velshop.presentation

import com.velnox.core.data.repository.CartRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Cart badge count as UI state.
 *
 * The count comes from the cached cart rather than a live request, so it survives a
 * cold start and a flaky network; the next successful cart call replaces it. Shared by
 * every VelShop ViewModel so the badge cannot disagree with the cart screen.
 */
fun CartRepository.cartCountState(scope: CoroutineScope): StateFlow<Int> =
    observeCachedCart()
        .map { it.itemCount }
        .stateIn(scope, SharingStarted.Eagerly, 0)
