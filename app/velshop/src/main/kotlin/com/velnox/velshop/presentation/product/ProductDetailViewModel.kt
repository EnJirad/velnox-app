package com.velnox.velshop.presentation.product

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.velnox.core.common.error.AppError
import com.velnox.core.common.error.VelnoxResult
import com.velnox.core.data.model.Product
import com.velnox.core.data.model.ProductOptionGroup
import com.velnox.core.data.model.ProductVariant
import com.velnox.core.data.repository.CartRepository
import com.velnox.core.data.repository.CatalogRepository
import com.velnox.core.ui.component.VelnoxScreenState
import com.velnox.velshop.presentation.cartCountState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Product detail: variant/option selection, quantity and add-to-cart.
 *
 * ## Why the selection rules live here and not in the screen
 *
 * The web product page refuses to add an item until every `required` option group has
 * a value and, when the product has variants, a variant is chosen. Reproducing that
 * rule in the composable would make it possible for the button's enabled state and the
 * request's validity to disagree. Both are derived from one place — the sealed
 * [AddBlockReason] — so "the button was enabled but the add failed validation" cannot
 * happen.
 *
 * ## Stock
 *
 * The ceiling is the *selected variant's* stock when a variant exists, otherwise the
 * product's available stock. The backend re-checks at checkout; this only avoids
 * offering a quantity the user cannot buy.
 */
@HiltViewModel
class ProductDetailViewModel @Inject constructor(
    private val catalogRepository: CatalogRepository,
    private val cartRepository: CartRepository,
) : ViewModel() {

    data class UiState(
        val result: VelnoxScreenState<Product> = VelnoxScreenState.Loading,
        val selectedVariantId: String? = null,
        /** `optionGroupId` → `optionValueId`. */
        val selectedOptions: Map<String, String> = emptyMap(),
        val quantity: Int = 1,
        val adding: Boolean = false,
        val notice: ProductNotice? = null,
    ) {
        val product: Product? get() = (result as? VelnoxScreenState.Content<Product>)?.value

        val selectedVariant: ProductVariant?
            get() = product?.variants?.firstOrNull { it.id == selectedVariantId }

        /** Unit price actually charged: the variant's when one is chosen. */
        val unitPrice: Double get() = selectedVariant?.price ?: product?.price ?: 0.0

        val compareAtPrice: Double?
            get() = selectedVariant?.compareAtPrice ?: product?.compareAtPrice

        val maxQuantity: Int
            get() = (selectedVariant?.stock ?: product?.availableStock ?: 0).coerceAtLeast(0)

        /** Reasons the primary action must stay disabled, in the order they matter. */
        val blockReason: AddBlockReason?
            get() {
                val current = product ?: return AddBlockReason.NotLoaded
                if (!current.isPurchasable) return AddBlockReason.OutOfStock
                if (current.variants.isNotEmpty() && selectedVariantId == null) {
                    return AddBlockReason.VariantRequired
                }
                val missing = current.optionGroups
                    .filter { it.required }
                    .firstOrNull { !selectedOptions.containsKey(it.id) }
                if (missing != null) return AddBlockReason.OptionRequired(missing)
                if (maxQuantity <= 0) return AddBlockReason.OutOfStock
                return null
            }

        val canAddToCart: Boolean get() = blockReason == null && !adding
    }

    /** Why "add to cart" is unavailable. Rendered from string resources, never raw. */
    sealed interface AddBlockReason {
        data object NotLoaded : AddBlockReason
        data object OutOfStock : AddBlockReason
        data object VariantRequired : AddBlockReason
        data class OptionRequired(val group: ProductOptionGroup) : AddBlockReason
    }

    /** One-shot feedback for the screen. */
    sealed interface ProductNotice {
        data object AddedToCart : ProductNotice
        data class Failure(val error: AppError) : ProductNotice
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    val cartCount: StateFlow<Int> = cartRepository.cartCountState(viewModelScope)

    private var loadedProductId: String? = null

    /**
     * Loads the product.
     *
     * Never served from the device cache: this is the screen where a user decides to
     * buy, so a stale price or stock figure would be a correctness bug with money
     * attached (the catalogue list is the one that may fall back to cached rows).
     *
     * A re-entry for the same id (configuration change, back navigation) is ignored so
     * a rotation does not discard the user's variant selection.
     */
    fun load(productId: String) {
        if (productId.isBlank()) {
            _state.update { it.copy(result = VelnoxScreenState.Failure(AppError.NotFound())) }
            return
        }
        if (loadedProductId == productId && _state.value.result is VelnoxScreenState.Content) return

        loadedProductId = productId
        viewModelScope.launch {
            _state.update { it.copy(result = VelnoxScreenState.Loading) }
            when (val result = catalogRepository.product(productId)) {
                is VelnoxResult.Success -> _state.update { state ->
                    state.copy(
                        result = VelnoxScreenState.Content(result.data),
                        // Default to the only variant when there is exactly one — the
                        // web page preselects it too, and it saves a tap on the
                        // commonest catalogue shape.
                        selectedVariantId = state.selectedVariantId
                            ?: result.data.variants.singleOrNull()?.id,
                    )
                }

                is VelnoxResult.Failure ->
                    _state.update { it.copy(result = VelnoxScreenState.Failure(result.error)) }
            }
        }
    }

    fun retry() {
        val productId = loadedProductId ?: return
        loadedProductId = null
        load(productId)
    }

    fun selectVariant(variantId: String) {
        _state.update { it.copy(selectedVariantId = variantId).clamped() }
    }

    fun selectOption(groupId: String, valueId: String) {
        _state.update { state ->
            state.copy(selectedOptions = state.selectedOptions + (groupId to valueId)).clamped()
        }
    }

    fun setQuantity(quantity: Int) {
        _state.update { it.copy(quantity = quantity.coerceIn(1, it.maxQuantity.coerceAtLeast(1))) }
    }

    fun addToCart() {
        val current = _state.value
        val product = current.product ?: return
        if (current.blockReason != null || current.adding) return

        viewModelScope.launch {
            _state.update { it.copy(adding = true) }
            val result = cartRepository.addToCart(
                productId = product.id,
                quantity = current.quantity,
                variantId = current.selectedVariantId,
            )
            _state.update { state ->
                when (result) {
                    is VelnoxResult.Success -> state.copy(adding = false, notice = ProductNotice.AddedToCart)
                    is VelnoxResult.Failure -> state.copy(adding = false, notice = ProductNotice.Failure(result.error))
                }
            }
        }
    }

    fun consumeNotice() {
        _state.update { it.copy(notice = null) }
    }

    /** Keeps the quantity legal after the selection changes the stock ceiling. */
    private fun UiState.clamped(): UiState =
        copy(quantity = quantity.coerceIn(1, maxQuantity.coerceAtLeast(1)))
}
