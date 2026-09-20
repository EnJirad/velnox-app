package com.velnox.velshop.presentation.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.velnox.core.common.domain.CatalogSort
import com.velnox.core.common.error.VelnoxResult
import com.velnox.core.common.paging.Page
import com.velnox.core.common.paging.PageRequest
import com.velnox.core.data.dto.CatalogQuery
import com.velnox.core.data.model.Product
import com.velnox.core.data.repository.CartRepository
import com.velnox.core.data.repository.CatalogRepository
import com.velnox.core.network.connectivity.NetworkMonitor
import com.velnox.core.ui.component.VelnoxScreenState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Catalogue browse state.
 *
 * ## Paging
 *
 * [loadMore] is guarded by a single [loadJob], so a fling that crosses the trigger
 * window several times cannot fire concurrent requests — a real source of duplicate
 * rows and wasted data. The request offset comes from the loaded page's own
 * [PageRequest], not from a counter, so a failed page can never advance the cursor.
 *
 * ## Caching
 *
 * The first successful page is written to the device cache. When a later refresh
 * fails, the cached page is shown **labelled as cached** (the offline banner) instead
 * of as fresh data — the brief forbids presenting stale commerce data as current.
 *
 * ## Search
 *
 * Input is debounced before it becomes a request. Typing "หูฟัง" must not issue four
 * catalogue queries.
 */
@HiltViewModel
class BrowseViewModel @Inject constructor(
    private val catalogRepository: CatalogRepository,
    private val cartRepository: CartRepository,
    networkMonitor: NetworkMonitor,
) : ViewModel() {

    data class UiState(
        val result: VelnoxScreenState<Page<Product>> = VelnoxScreenState.Loading,
        val inStockOnly: Boolean = false,
        val verifiedOnly: Boolean = false,
        val sort: CatalogSort = CatalogSort.Newest,
        val loadingMore: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    val isOnline = networkMonitor.isOnline

    /** Cart badge count, driven by the cached cart so it survives a cold start. */
    val cartCount: StateFlow<Int> = cartRepository.observeCachedCart()
        .map { it.itemCount }
        .let { flow ->
            MutableStateFlow(0).also { holder ->
                viewModelScope.launch { flow.collect { holder.value = it } }
            }
        }
        .asStateFlow()

    private var loadJob: Job? = null
    private var searchDebounceJob: Job? = null

    init {
        loadFirstPage()
    }

    fun onQueryChange(value: String) {
        _query.value = value
        searchDebounceJob?.cancel()
        searchDebounceJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            loadFirstPage()
        }
    }

    fun toggleInStockOnly() {
        _state.update { it.copy(inStockOnly = !it.inStockOnly) }
        loadFirstPage()
    }

    fun toggleVerifiedOnly() {
        _state.update { it.copy(verifiedOnly = !it.verifiedOnly) }
        loadFirstPage()
    }

    fun refresh() = loadFirstPage()

    /**
     * Loads page zero.
     *
     * On failure the previously shown content is *replaced* by the failure state rather
     * than kept, unless a cached page exists — keeping stale rows behind an error would
     * hide the fact that the catalogue could not be refreshed.
     */
    private fun loadFirstPage() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.update { it.copy(result = VelnoxScreenState.Loading, loadingMore = false) }

            when (val result = requestPage(PageRequest(offset = 0, limit = PAGE_SIZE))) {
                is VelnoxResult.Success -> {
                    _state.update { it.copy(result = VelnoxScreenState.Content(result.data)) }
                    if (_query.value.isBlank() && !_state.value.inStockOnly && !_state.value.verifiedOnly) {
                        catalogRepository.cacheFirstPage(result.data.items)
                    }
                }

                is VelnoxResult.Failure -> {
                    val cached = catalogRepository.cachedProducts()
                    _state.update {
                        it.copy(
                            result = if (cached.isNotEmpty() && _query.value.isBlank()) {
                                VelnoxScreenState.Content(
                                    Page(items = cached, request = PageRequest(0, cached.size)),
                                    isCached = true,
                                )
                            } else {
                                VelnoxScreenState.Failure(result.error)
                            },
                        )
                    }
                }
            }
        }
    }

    fun loadMore() {
        val current = _state.value
        val page = (current.result as? VelnoxScreenState.Content<Page<Product>>)?.value ?: return
        // Cached content has no server cursor to continue from.
        if (current.result.isCached || current.loadingMore || page.endReached) return

        loadJob = viewModelScope.launch {
            _state.update { it.copy(loadingMore = true) }
            val next = page.request.next

            when (val result = requestPage(next)) {
                is VelnoxResult.Success -> _state.update { state ->
                    val existing =
                        (state.result as? VelnoxScreenState.Content<Page<Product>>)?.value ?: page
                    val merged = Page(
                        items = existing.items + result.data.items,
                        request = next,
                        total = null,
                        hasMore = result.data.hasMore,
                    )
                    state.copy(result = VelnoxScreenState.Content(merged), loadingMore = false)
                }

                is VelnoxResult.Failure -> {
                    // A failed page must not advance the cursor, so the next attempt
                    // asks for the same offset again.
                    _state.update { it.copy(loadingMore = false) }
                }
            }
        }
    }

    private suspend fun requestPage(page: PageRequest): VelnoxResult<Page<Product>> =
        catalogRepository.catalog(
            query = CatalogQuery(
                q = _query.value.takeIf { it.isNotBlank() },
                inStock = _state.value.inStockOnly.takeIf { it },
                verified = _state.value.verifiedOnly.takeIf { it },
                sortBy = _state.value.sort.wireValue,
            ),
            page = page,
        )

    private companion object {
        const val PAGE_SIZE = 20
        const val SEARCH_DEBOUNCE_MS = 350L
    }
}
