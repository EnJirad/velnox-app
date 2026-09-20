package com.velnox.velshop.presentation.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.velnox.core.data.model.Product
import com.velnox.core.ui.component.VelnoxCard
import com.velnox.core.ui.component.VelnoxChip
import com.velnox.core.ui.component.VelnoxImage
import com.velnox.core.ui.component.VelnoxLoadingState
import com.velnox.core.ui.component.VelnoxOfflineBanner
import com.velnox.core.ui.component.VelnoxScaffold
import com.velnox.core.ui.component.VelnoxSearchField
import com.velnox.core.ui.component.VelnoxStateHost
import com.velnox.core.ui.theme.PriceTextStyle
import com.velnox.core.ui.theme.VelnoxColors
import com.velnox.core.ui.theme.VelnoxTokens
import com.velnox.velshop.R
import com.velnox.velshop.navigation.VelShopTabRoutes
import com.velnox.velshop.navigation.velShopTabs

/**
 * Catalogue browsing: search, filters, paging.
 *
 * This is also the app's home screen — the product grid *is* the storefront, exactly
 * as it is on the web client, with the filter row standing in for the category rail.
 *
 * The list is a `LazyColumn` keyed by product id, and each card is a stable function
 * of an immutable [Product] — the two things that keep recomposition scoped to the
 * visible rows and stop a catalogue scroll from re-composing the whole tree.
 */
@Composable
fun BrowseScreen(
    onOpenProduct: (String) -> Unit,
    onNavigateTab: (String) -> Unit,
    viewModel: BrowseViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val cartCount by viewModel.cartCount.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()

    VelnoxScaffold(
        title = stringResource(R.string.velshop_browse_title),
        tabs = velShopTabs(cartCount),
        currentRoute = VelShopTabRoutes.BROWSE,
        onNavigate = onNavigateTab,
    ) { insets ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(insets),
        ) {
            if (!isOnline) {
                VelnoxOfflineBanner(
                    modifier = Modifier.padding(
                        horizontal = VelnoxTokens.spacing.screenHorizontal,
                        vertical = VelnoxTokens.spacing.gapSmall,
                    ),
                    message = stringResource(R.string.velshop_cached_notice),
                    onRetry = viewModel::refresh,
                )
            }

            VelnoxSearchField(
                value = query,
                onValueChange = viewModel::onQueryChange,
                placeholder = stringResource(R.string.velshop_search_placeholder),
                modifier = Modifier.padding(
                    horizontal = VelnoxTokens.spacing.screenHorizontal,
                    vertical = VelnoxTokens.spacing.gapSmall,
                ),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = VelnoxTokens.spacing.screenHorizontal),
                horizontalArrangement = Arrangement.spacedBy(VelnoxTokens.spacing.gapSmall),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(
                    selected = state.inStockOnly,
                    onClick = viewModel::toggleInStockOnly,
                    label = { Text(stringResource(R.string.velshop_in_stock_only)) },
                )
                FilterChip(
                    selected = state.verifiedOnly,
                    onClick = viewModel::toggleVerifiedOnly,
                    label = { Text(stringResource(R.string.velshop_verified_only)) },
                )
            }

            VelnoxStateHost(
                state = state.result,
                onRetry = viewModel::refresh,
                emptyTitle = stringResource(R.string.velshop_empty_products),
            ) { page, isCached ->
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = VelnoxTokens.spacing.screenHorizontal,
                        end = VelnoxTokens.spacing.screenHorizontal,
                        top = VelnoxTokens.spacing.gap,
                        bottom = VelnoxTokens.spacing.screenBottom,
                    ),
                    verticalArrangement = Arrangement.spacedBy(VelnoxTokens.spacing.gap),
                ) {
                    items(items = page.items, key = { it.id }) { product ->
                        ProductRowCard(
                            product = product,
                            isCached = isCached,
                            onClick = { onOpenProduct(product.id) },
                        )
                    }

                    if (!page.endReached) {
                        item {
                            LoadMoreRow(
                                isLoading = state.loadingMore,
                                onLoadMore = viewModel::loadMore,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Paging trigger.
 *
 * A zero-state `LaunchedEffect` inside the lazy item, so it fires when the row is
 * actually composed — i.e. when the user has scrolled near the end — instead of on
 * every recomposition of the list.
 */
@Composable
private fun LoadMoreRow(isLoading: Boolean, onLoadMore: () -> Unit) {
    LaunchedEffect(Unit) { onLoadMore() }
    if (isLoading) {
        VelnoxLoadingState(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = VelnoxTokens.spacing.gapLarge),
            label = null,
        )
    }
}

/** Catalogue card: image, name, shop, price, stock/verification chips. */
@Composable
internal fun ProductRowCard(
    product: Product,
    isCached: Boolean,
    onClick: () -> Unit,
) {
    VelnoxCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(VelnoxTokens.spacing.gap)) {
            VelnoxImage(
                url = product.primaryImageUrl,
                contentDescription = product.name,
                modifier = Modifier.size(96.dp),
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = product.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = VelnoxColors.OnSurface,
                    maxLines = 2,
                )
                product.shopName?.let { shop ->
                    Text(
                        text = shop,
                        style = MaterialTheme.typography.bodySmall,
                        color = VelnoxColors.OnSurfaceMuted,
                        maxLines = 1,
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(VelnoxTokens.spacing.gapSmall),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = product.priceLabel,
                        style = PriceTextStyle,
                        color = VelnoxColors.EmeraldOnSurface,
                    )
                    product.compareAtPriceLabel?.let { original ->
                        Text(
                            text = original,
                            style = MaterialTheme.typography.bodySmall,
                            color = VelnoxColors.OnSurfaceDisabled,
                            textDecoration = TextDecoration.LineThrough,
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(VelnoxTokens.spacing.gapTiny)) {
                    when {
                        !product.isPurchasable -> VelnoxChip(
                            text = stringResource(R.string.velnox_out_of_stock),
                            background = VelnoxColors.SurfaceMuted,
                            contentColor = VelnoxColors.OnSurfaceMuted,
                        )

                        product.isLowStock -> VelnoxChip(
                            text = stringResource(R.string.velnox_low_stock, product.availableStock),
                            background = VelnoxColors.WarningSurface,
                            contentColor = VelnoxColors.WarningOnSurface,
                        )
                    }
                    if (isCached) {
                        VelnoxChip(text = stringResource(R.string.velnox_state_cached_notice))
                    }
                }
            }
        }
    }
}
