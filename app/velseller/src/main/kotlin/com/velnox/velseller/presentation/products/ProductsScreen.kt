package com.velnox.velseller.presentation.products

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.velnox.core.common.domain.ProductStatus
import com.velnox.core.data.model.Product
import com.velnox.core.ui.component.ProductStatusBadge
import com.velnox.core.ui.component.VelnoxBannerTone
import com.velnox.core.ui.component.VelnoxCard
import com.velnox.core.ui.component.VelnoxEmptyState
import com.velnox.core.ui.component.VelnoxImage
import com.velnox.core.ui.component.VelnoxMessageBanner
import com.velnox.core.ui.component.VelnoxScaffold
import com.velnox.core.ui.component.VelnoxScreenState
import com.velnox.core.ui.component.VelnoxSecondaryButton
import com.velnox.core.ui.component.VelnoxStateHost
import com.velnox.core.ui.component.VelnoxTextField
import com.velnox.core.ui.theme.PriceTextStyle
import com.velnox.core.ui.theme.VelnoxColors
import com.velnox.core.ui.theme.VelnoxTokens
import com.velnox.velseller.R
import com.velnox.velseller.navigation.VelSellerTabRoutes
import com.velnox.velseller.navigation.velSellerTabs

/**
 * The seller's products.
 *
 * The filter row is client-side over one `GET /api/seller/products` response rather than
 * a request per status: a seller's catalogue is small, and re-requesting on every chip
 * tap would trade a real round trip for nothing. The actions offered per row come from
 * the product's own lifecycle state, so no button here can be one the backend refuses.
 */
@Composable
fun ProductsScreen(
    onNavigateTab: (String) -> Unit,
    viewModel: ProductsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    VelnoxScaffold(
        title = stringResource(R.string.velseller_products_title),
        tabs = velSellerTabs(),
        currentRoute = VelSellerTabRoutes.PRODUCTS,
        onNavigate = onNavigateTab,
    ) { insets ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(insets),
        ) {
            state.notice?.let { notice ->
                val (message, tone) = when (notice) {
                    ProductsViewModel.ProductsNotice.Submitted ->
                        stringResource(R.string.velseller_product_submitted) to VelnoxBannerTone.Success

                    ProductsViewModel.ProductsNotice.StockUpdated ->
                        stringResource(R.string.velseller_product_stock_updated) to VelnoxBannerTone.Success

                    ProductsViewModel.ProductsNotice.Deleted ->
                        stringResource(R.string.velseller_product_deleted) to VelnoxBannerTone.Success

                    is ProductsViewModel.ProductsNotice.Failure ->
                        (notice.error.serverMessage ?: stringResource(R.string.velnox_state_error_body)) to
                            VelnoxBannerTone.Error
                }
                VelnoxMessageBanner(
                    message = message,
                    tone = tone,
                    onDismiss = viewModel::consumeNotice,
                    modifier = Modifier.padding(
                        horizontal = VelnoxTokens.spacing.screenHorizontal,
                        vertical = VelnoxTokens.spacing.gapSmall,
                    ),
                )
            }

            StatusFilterRow(
                selected = state.filter,
                onSelect = viewModel::setFilter,
            )

            Box(modifier = Modifier.weight(1f)) {
                val visible = state.visible

                when {
                    state.result is VelnoxScreenState.Empty || visible?.isEmpty() == true -> VelnoxEmptyState(
                        title = stringResource(R.string.velseller_products_empty),
                        body = stringResource(R.string.velseller_products_empty_body),
                    )

                    else -> VelnoxStateHost(
                        state = state.result,
                        onRetry = viewModel::refresh,
                        emptyTitle = stringResource(R.string.velseller_products_empty),
                    ) { _, _ ->
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
                            items(items = visible.orEmpty(), key = { it.id }) { product ->
                                ProductCard(
                                    product = product,
                                    busy = state.busyProductId == product.id,
                                    onSubmit = { viewModel.submitForReview(product) },
                                    onEditStock = { viewModel.beginStockEdit(product) },
                                    onDelete = { viewModel.requestDelete(product) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    state.pendingDelete?.let { product ->
        AlertDialog(
            onDismissRequest = viewModel::cancelDelete,
            title = { Text(stringResource(R.string.velseller_product_delete_confirm_title)) },
            text = { Text(stringResource(R.string.velseller_product_delete_confirm_body, product.name)) },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDelete) {
                    Text(
                        text = stringResource(R.string.velseller_product_delete),
                        color = VelnoxColors.Destructive,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelDelete) {
                    Text(stringResource(R.string.velnox_action_cancel))
                }
            },
        )
    }

    state.editingStock?.let { product ->
        StockDialog(
            product = product,
            onDismiss = viewModel::cancelStockEdit,
            onConfirm = viewModel::confirmStockEdit,
        )
    }
}

@Composable
private fun StatusFilterRow(
    selected: ProductStatus?,
    onSelect: (ProductStatus?) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = VelnoxTokens.spacing.screenHorizontal),
        horizontalArrangement = Arrangement.spacedBy(VelnoxTokens.spacing.gapSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(
            selected = selected == null,
            onClick = { onSelect(null) },
            label = { Text(stringResource(R.string.velseller_products_all)) },
        )
        FILTERABLE_STATUSES.forEach { status ->
            FilterChip(
                selected = selected == status,
                onClick = { onSelect(status) },
                label = { Text(productStatusLabel(status)) },
            )
        }
    }
}

@Composable
private fun ProductCard(
    product: Product,
    busy: Boolean,
    onSubmit: () -> Unit,
    onEditStock: () -> Unit,
    onDelete: () -> Unit,
) {
    VelnoxCard(modifier = Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(VelnoxTokens.spacing.gap)) {
            VelnoxImage(
                url = product.primaryImageUrl,
                contentDescription = product.name,
                modifier = Modifier.size(72.dp),
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = product.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = VelnoxColors.OnSurface,
                    maxLines = 2,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(VelnoxTokens.spacing.gapSmall),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ProductStatusBadge(status = product.status)
                    Text(
                        text = product.priceLabel,
                        style = PriceTextStyle,
                        color = VelnoxColors.OnSurface,
                    )
                }
                Text(
                    text = stringResource(
                        R.string.velseller_product_stock,
                        product.availableStock,
                        product.unit,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (product.availableStock > 0) {
                        VelnoxColors.OnSurfaceMuted
                    } else {
                        VelnoxColors.Destructive
                    },
                )
            }
        }

        product.rejectionReason?.takeIf { it.isNotBlank() }?.let { reason ->
            Text(
                text = stringResource(R.string.velseller_product_rejection_reason, reason),
                style = MaterialTheme.typography.bodySmall,
                color = VelnoxColors.Destructive,
                modifier = Modifier.padding(top = VelnoxTokens.spacing.gapSmall),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = VelnoxTokens.spacing.gap),
            horizontalArrangement = Arrangement.spacedBy(VelnoxTokens.spacing.gapSmall),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Only the two transitions the seller lifecycle actually allows.
            if (product.status == ProductStatus.Draft || product.status == ProductStatus.Rejected) {
                VelnoxSecondaryButton(
                    text = stringResource(
                        if (product.status == ProductStatus.Draft) {
                            R.string.velseller_product_submit
                        } else {
                            R.string.velseller_product_resubmit
                        },
                    ),
                    onClick = onSubmit,
                    enabled = !busy,
                )
            }

            VelnoxSecondaryButton(
                text = stringResource(R.string.velseller_product_edit_stock),
                onClick = onEditStock,
                enabled = !busy,
            )

            TextButton(onClick = onDelete, enabled = !busy) {
                Text(
                    text = stringResource(R.string.velseller_product_delete),
                    style = MaterialTheme.typography.labelMedium,
                    color = VelnoxColors.Destructive,
                )
            }

            if (busy) {
                CircularProgressIndicator(
                    color = VelnoxColors.Emerald,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun StockDialog(
    product: Product,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    // Seeded from the live value so the seller edits a real number instead of an empty
    // box, and never silently overwrites stock with a blank submission.
    var quantity by remember(product.id) { mutableStateOf(product.availableStock.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.velseller_stock_dialog_title)) },
        text = {
            VelnoxTextField(
                value = quantity,
                onValueChange = { quantity = it },
                label = stringResource(R.string.velseller_stock_label),
                supportingText = product.name,
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(quantity) }) {
                Text(stringResource(R.string.velnox_action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.velnox_action_cancel))
            }
        },
    )
}

/** Statuses a seller can usefully filter by; the order mirrors the lifecycle. */
private val FILTERABLE_STATUSES = listOf(
    ProductStatus.Draft,
    ProductStatus.PendingReview,
    ProductStatus.Published,
    ProductStatus.Rejected,
    ProductStatus.Suspended,
)

/** Localised label for a product status, from the shared string set. */
@Composable
private fun productStatusLabel(status: ProductStatus): String = when (status) {
    ProductStatus.Draft -> stringResource(R.string.velnox_product_draft)
    ProductStatus.PendingReview -> stringResource(R.string.velnox_product_pending_review)
    ProductStatus.Published -> stringResource(R.string.velnox_product_published)
    ProductStatus.Rejected -> stringResource(R.string.velnox_product_rejected)
    ProductStatus.Suspended -> stringResource(R.string.velnox_product_suspended)
    ProductStatus.Archived -> stringResource(R.string.velnox_product_archived)
    ProductStatus.Unknown -> stringResource(R.string.velnox_product_unknown)
}
