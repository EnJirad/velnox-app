package com.velnox.velseller.presentation.products

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.velnox.core.common.domain.ProductStatus
import com.velnox.core.common.error.AppError
import com.velnox.core.common.error.VelnoxResult
import com.velnox.core.data.model.Product
import com.velnox.core.data.repository.SellerRepository
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
 * The seller's own products.
 *
 * ## Status changes are transitions, not field writes
 *
 * `backend/lib/product-lifecycle.ts` owns the machine, and `PATCH
 * /api/seller/products/:id/status` runs it. The seller app therefore offers exactly two
 * moves — submit a draft, and resubmit something that was sent back — because
 * publishing is a VelCenter decision. Offering "publish" here would be a button that
 * always fails.
 *
 * ## Delete keeps its intent
 *
 * Deletion is irreversible, so it is a two-step action whose state lives in the
 * ViewModel: a configuration change mid-confirmation must not silently drop the dialog
 * (which would look like the tap did nothing) or, worse, re-fire it.
 *
 * ## Realtime is a hint
 *
 * `product:updated` and `inventory:updated` frames only trigger a re-read of
 * `GET /api/seller/products`; a published price change made by VelCenter becomes visible
 * without the app trusting the frame's payload.
 */
@HiltViewModel
class ProductsViewModel @Inject constructor(
    private val sellerRepository: SellerRepository,
    realtimeClient: VelnoxRealtimeClient,
) : ViewModel() {

    data class UiState(
        val result: VelnoxScreenState<List<Product>> = VelnoxScreenState.Loading,
        /** `null` means "all statuses". */
        val filter: ProductStatus? = null,
        val busyProductId: String? = null,
        val pendingDelete: Product? = null,
        val editingStock: Product? = null,
        val notice: ProductsNotice? = null,
    ) {
        val visible: List<Product>?
            get() = (result as? VelnoxScreenState.Content<List<Product>>)?.value
                ?.filter { filter == null || it.status == filter }
    }

    sealed interface ProductsNotice {
        data object Submitted : ProductsNotice
        data object StockUpdated : ProductsNotice
        data object Deleted : ProductsNotice
        data class Failure(val error: AppError) : ProductsNotice
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        load()

        realtimeClient.subscribe(RealtimeChannels.PRODUCT_UPDATED)
        realtimeClient.subscribe(RealtimeChannels.INVENTORY_UPDATED)
        viewModelScope.launch {
            realtimeClient.frames
                .filter { frame ->
                    frame.channel == RealtimeChannels.PRODUCT_UPDATED ||
                        frame.channel == RealtimeChannels.INVENTORY_UPDATED
                }
                .collect { loadQuietly() }
        }
    }

    fun refresh() = load()

    fun setFilter(status: ProductStatus?) {
        _state.update { it.copy(filter = status) }
    }

    /** Draft → `pending_review`, or resubmit after a rejection. */
    fun submitForReview(product: Product) {
        if (_state.value.busyProductId != null) return

        viewModelScope.launch {
            _state.update { it.copy(busyProductId = product.id, notice = null) }

            when (
                val result = sellerRepository.setProductStatus(
                    productId = product.id,
                    status = ProductStatus.PendingReview.wireValue,
                )
            ) {
                is VelnoxResult.Success -> {
                    _state.update { it.copy(busyProductId = null, notice = ProductsNotice.Submitted) }
                    loadQuietly()
                }

                is VelnoxResult.Failure -> _state.update {
                    it.copy(busyProductId = null, notice = ProductsNotice.Failure(result.error))
                }
            }
        }
    }

    fun requestDelete(product: Product) {
        _state.update { it.copy(pendingDelete = product) }
    }

    fun cancelDelete() {
        _state.update { it.copy(pendingDelete = null) }
    }

    fun confirmDelete() {
        val product = _state.value.pendingDelete ?: return

        viewModelScope.launch {
            _state.update { it.copy(pendingDelete = null, busyProductId = product.id, notice = null) }

            when (val result = sellerRepository.deleteProduct(product.id)) {
                is VelnoxResult.Success -> {
                    _state.update { it.copy(busyProductId = null, notice = ProductsNotice.Deleted) }
                    loadQuietly()
                }

                is VelnoxResult.Failure -> _state.update {
                    it.copy(busyProductId = null, notice = ProductsNotice.Failure(result.error))
                }
            }
        }
    }

    fun beginStockEdit(product: Product) {
        _state.update { it.copy(editingStock = product) }
    }

    fun cancelStockEdit() {
        _state.update { it.copy(editingStock = null) }
    }

    /**
     * Applies a stock correction.
     *
     * [quantityText] is validated here rather than in the dialog so the same rule applies
     * wherever it is called from: a non-numeric or negative value never reaches the API,
     * and the backend re-validates regardless.
     */
    fun confirmStockEdit(quantityText: String) {
        val product = _state.value.editingStock ?: return
        val quantity = quantityText.trim().toIntOrNull()

        if (quantity == null || quantity < 0) {
            _state.update {
                it.copy(
                    notice = ProductsNotice.Failure(
                        AppError.Validation(serverMessage = "Stock must be a whole number of 0 or more."),
                    ),
                )
            }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(editingStock = null, busyProductId = product.id, notice = null) }

            when (val result = sellerRepository.setStock(product.id, quantity)) {
                is VelnoxResult.Success -> {
                    _state.update { it.copy(busyProductId = null, notice = ProductsNotice.StockUpdated) }
                    loadQuietly()
                }

                is VelnoxResult.Failure -> _state.update {
                    it.copy(busyProductId = null, notice = ProductsNotice.Failure(result.error))
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

            when (val result = sellerRepository.products()) {
                is VelnoxResult.Success -> _state.update {
                    it.copy(result = result.data.asListState())
                }

                is VelnoxResult.Failure -> _state.update { current ->
                    // A failed background refresh keeps what the seller is looking at.
                    if (!showLoader && current.result is VelnoxScreenState.Content) {
                        current
                    } else {
                        current.copy(result = VelnoxScreenState.Failure(result.error))
                    }
                }
            }
        }
    }

    private fun List<Product>.asListState(): VelnoxScreenState<List<Product>> =
        if (isEmpty()) VelnoxScreenState.Empty else VelnoxScreenState.Content(this)
}
