package com.velnox.velshop.presentation.product

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.velnox.core.common.util.VelnoxFormat
import com.velnox.core.data.model.Product
import com.velnox.core.data.model.ProductOptionGroup
import com.velnox.core.ui.component.VelnoxAddToCartButton
import com.velnox.core.ui.component.VelnoxBannerTone
import com.velnox.core.ui.component.VelnoxCard
import com.velnox.core.ui.component.VelnoxChip
import com.velnox.core.ui.component.VelnoxImage
import com.velnox.core.ui.component.VelnoxMessageBanner
import com.velnox.core.ui.component.VelnoxScaffold
import com.velnox.core.ui.component.VelnoxScreenState
import com.velnox.core.ui.component.VelnoxStateHost
import com.velnox.core.ui.theme.HeroPriceTextStyle
import com.velnox.core.ui.theme.VelnoxColors
import com.velnox.core.ui.theme.VelnoxTokens
import com.velnox.velshop.R
import com.velnox.velshop.navigation.VelShopTabRoutes
import com.velnox.velshop.navigation.velShopTabs

/**
 * Product detail.
 *
 * Mobile layout, not a shrunken desktop page: one column, a full-bleed hero image,
 * the price block directly under it, then the choices, then the description. The
 * primary action is pinned to the bottom so it is reachable without scrolling back up
 * — the reason a desktop "add to cart" in the middle of a long page does not translate
 * to a phone.
 */
@Composable
fun ProductDetailScreen(
    productId: String,
    onBack: () -> Unit,
    onNavigateTab: (String) -> Unit,
    viewModel: ProductDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val cartCount by viewModel.cartCount.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(productId) { viewModel.load(productId) }

    VelnoxScaffold(
        title = stringResource(R.string.velshop_product_title),
        tabs = velShopTabs(cartCount),
        currentRoute = "",
        onNavigate = onNavigateTab,
        onBack = onBack,
    ) { insets ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(insets),
        ) {
            state.notice?.let { notice ->
                val bannerText = when (notice) {
                    ProductDetailViewModel.ProductNotice.AddedToCart ->
                        stringResource(R.string.velshop_added_to_cart)

                    is ProductDetailViewModel.ProductNotice.Failure ->
                        notice.error.serverMessage ?: stringResource(R.string.velnox_state_error_body)
                }
                VelnoxMessageBanner(
                    message = bannerText,
                    tone = if (notice is ProductDetailViewModel.ProductNotice.Failure) {
                        VelnoxBannerTone.Error
                    } else {
                        VelnoxBannerTone.Success
                    },
                    onDismiss = viewModel::consumeNotice,
                    modifier = Modifier.padding(
                        horizontal = VelnoxTokens.spacing.screenHorizontal,
                        vertical = VelnoxTokens.spacing.gapSmall,
                    ),
                )
            }

            Box(modifier = Modifier.weight(1f)) {
                VelnoxStateHost(
                    state = state.result,
                    onRetry = viewModel::retry,
                ) { product, _ ->
                    ProductDetailContent(
                        product = product,
                        state = state,
                        onSelectVariant = viewModel::selectVariant,
                        onSelectOption = viewModel::selectOption,
                        onQuantityChange = viewModel::setQuantity,
                    )
                }
            }

            // The action bar only exists once there is a product to act on, so the
            // button is never rendered for the loading or failure state.
            (state.result as? VelnoxScreenState.Content<Product>)?.let {
                ProductActionBar(
                    state = state,
                    onAdd = viewModel::addToCart,
                    blockMessage = state.blockReason?.describe(context),
                )
            }
        }
    }
}

/**
 * Turns a blocked add-to-cart into the sentence the user sees.
 *
 * Kept as an extension on the sealed reason so every branch is resolved from a string
 * resource and a new reason cannot be added without handling it.
 */
private fun ProductDetailViewModel.AddBlockReason.describe(context: android.content.Context): String? =
    when (this) {
        ProductDetailViewModel.AddBlockReason.NotLoaded -> null
        ProductDetailViewModel.AddBlockReason.OutOfStock ->
            context.getString(R.string.velshop_add_out_of_stock)

        ProductDetailViewModel.AddBlockReason.VariantRequired ->
            context.getString(R.string.velshop_add_variant_required)

        is ProductDetailViewModel.AddBlockReason.OptionRequired ->
            context.getString(R.string.velshop_add_option_required, group.name)
    }

@Composable
private fun ProductDetailContent(
    product: Product,
    state: ProductDetailViewModel.UiState,
    onSelectVariant: (String) -> Unit,
    onSelectOption: (String, String) -> Unit,
    onQuantityChange: (Int) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = VelnoxTokens.spacing.gapLarge),
        verticalArrangement = Arrangement.spacedBy(VelnoxTokens.spacing.gap),
    ) {
        item {
            VelnoxImage(
                url = product.primaryImageUrl,
                contentDescription = product.name,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp),
                cornerRadius = 0.dp,
            )
        }

        item {
            Column(modifier = Modifier.padding(horizontal = VelnoxTokens.spacing.screenHorizontal)) {
                Text(
                    text = product.name,
                    style = MaterialTheme.typography.titleLarge,
                    color = VelnoxColors.OnSurface,
                )
                product.shopName?.let { shop ->
                    Text(
                        text = shop,
                        style = MaterialTheme.typography.bodySmall,
                        color = VelnoxColors.OnSurfaceMuted,
                    )
                }

                Row(
                    modifier = Modifier.padding(top = VelnoxTokens.spacing.gapSmall),
                    horizontalArrangement = Arrangement.spacedBy(VelnoxTokens.spacing.gapSmall),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = VelnoxFormat.baht(state.unitPrice),
                        style = HeroPriceTextStyle,
                        color = VelnoxColors.EmeraldOnSurface,
                    )
                    state.compareAtPrice?.let { original ->
                        Text(
                            text = VelnoxFormat.baht(original),
                            style = MaterialTheme.typography.bodySmall,
                            color = VelnoxColors.OnSurfaceDisabled,
                            textDecoration = TextDecoration.LineThrough,
                        )
                    }
                    VelnoxFormat.discountPercent(state.unitPrice, state.compareAtPrice)?.let { percent ->
                        VelnoxChip(
                            text = stringResource(R.string.velshop_discount_percent, percent),
                            background = VelnoxColors.DestructiveSurface,
                            contentColor = VelnoxColors.Destructive,
                        )
                    }
                }

                Row(
                    modifier = Modifier.padding(top = VelnoxTokens.spacing.gapTiny),
                    horizontalArrangement = Arrangement.spacedBy(VelnoxTokens.spacing.gapSmall),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (product.verificationStatus.isVerified) {
                        VelnoxChip(
                            text = stringResource(R.string.velnox_verified),
                            background = VelnoxColors.EmeraldSurface,
                            contentColor = VelnoxColors.EmeraldOnSurface,
                        )
                    }
                    if (product.soldCount > 0) {
                        Text(
                            text = stringResource(R.string.velshop_sold_count, product.soldCount),
                            style = MaterialTheme.typography.bodySmall,
                            color = VelnoxColors.OnSurfaceMuted,
                        )
                    }
                    Text(
                        text = stringResource(R.string.velshop_stock_remaining, state.maxQuantity),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (state.maxQuantity > 0) {
                            VelnoxColors.OnSurfaceMuted
                        } else {
                            VelnoxColors.Destructive
                        },
                    )
                }
            }
        }

        if (product.variants.isNotEmpty()) {
            item {
                ChipRow(
                    label = stringResource(R.string.velshop_select_variant),
                    values = product.variants.map { it.id to it.name },
                    selectedId = state.selectedVariantId,
                    onSelect = onSelectVariant,
                )
            }
        }

        product.optionGroups.forEach { group ->
            item(key = group.id) {
                ChipRow(
                    label = if (group.required) {
                        stringResource(R.string.velshop_option_required_label, group.name)
                    } else {
                        group.name
                    },
                    values = group.values.map { it.id to it.label },
                    selectedId = state.selectedOptions[group.id],
                    onSelect = { valueId -> onSelectOption(group.id, valueId) },
                )
            }
        }

        product.description?.takeIf { it.isNotBlank() }?.let { description ->
            item {
                VelnoxCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = VelnoxTokens.spacing.screenHorizontal),
                ) {
                    Text(
                        text = stringResource(R.string.velshop_description),
                        style = MaterialTheme.typography.titleSmall,
                        color = VelnoxColors.OnSurface,
                    )
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = VelnoxColors.OnSurfaceMuted,
                        modifier = Modifier.padding(top = VelnoxTokens.spacing.gapTiny),
                    )
                }
            }
        }

        item {
            QuantityRow(
                quantity = state.quantity,
                max = state.maxQuantity,
                enabled = state.maxQuantity > 0,
                onChange = onQuantityChange,
            )
        }
    }
}

/** A labelled row of single-choice chips, horizontally scrollable on a phone. */
@Composable
private fun ChipRow(
    label: String,
    values: List<Pair<String, String>>,
    selectedId: String?,
    onSelect: (String) -> Unit,
) {
    if (values.isEmpty()) return

    Column(modifier = Modifier.padding(horizontal = VelnoxTokens.spacing.screenHorizontal)) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = VelnoxColors.OnSurface,
        )
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(top = VelnoxTokens.spacing.gapTiny),
            horizontalArrangement = Arrangement.spacedBy(VelnoxTokens.spacing.gapSmall),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            values.forEach { (id, text) ->
                FilterChip(
                    selected = id == selectedId,
                    onClick = { onSelect(id) },
                    label = { Text(text) },
                )
            }
        }
    }
}

@Composable
private fun QuantityRow(
    quantity: Int,
    max: Int,
    enabled: Boolean,
    onChange: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = VelnoxTokens.spacing.screenHorizontal),
        horizontalArrangement = Arrangement.spacedBy(VelnoxTokens.spacing.gap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.velshop_quantity),
            style = MaterialTheme.typography.titleSmall,
            color = VelnoxColors.OnSurface,
            modifier = Modifier.weight(1f),
        )
        OutlinedIconButton(
            onClick = { onChange(quantity - 1) },
            enabled = enabled && quantity > 1,
        ) {
            Icon(imageVector = Icons.Filled.Remove, contentDescription = null)
        }
        Text(
            text = quantity.toString(),
            style = MaterialTheme.typography.titleMedium,
            color = VelnoxColors.OnSurface,
        )
        OutlinedIconButton(
            onClick = { onChange(quantity + 1) },
            enabled = enabled && quantity < max,
        ) {
            Icon(imageVector = Icons.Filled.Add, contentDescription = null)
        }
    }
}

/** Pinned bottom action, so the buy action is always reachable. */
@Composable
private fun ProductActionBar(
    state: ProductDetailViewModel.UiState,
    onAdd: () -> Unit,
    blockMessage: String?,
) {
    Surface(
        color = VelnoxColors.Surface,
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = VelnoxTokens.spacing.screenHorizontal,
                vertical = VelnoxTokens.spacing.gap,
            ),
            verticalArrangement = Arrangement.spacedBy(VelnoxTokens.spacing.gapTiny),
        ) {
            blockMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = VelnoxColors.OnSurfaceMuted,
                )
            }
            VelnoxAddToCartButton(
                onClick = onAdd,
                enabled = state.canAddToCart,
                loading = state.adding,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
