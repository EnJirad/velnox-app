package com.velnox.velshop.presentation.orders

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import com.velnox.core.common.util.VelnoxFormat
import com.velnox.core.data.model.Order
import com.velnox.core.ui.component.OrderStatusBadge
import com.velnox.core.ui.component.PaymentStatusBadge
import com.velnox.core.ui.component.VelnoxBannerTone
import com.velnox.core.ui.component.VelnoxCard
import com.velnox.core.ui.component.VelnoxEmptyState
import com.velnox.core.ui.component.VelnoxMessageBanner
import com.velnox.core.ui.component.VelnoxOfflineBanner
import com.velnox.core.ui.component.VelnoxScaffold
import com.velnox.core.ui.component.VelnoxScreenState
import com.velnox.core.ui.component.VelnoxSecondaryButton
import com.velnox.core.ui.component.VelnoxStateHost
import com.velnox.core.ui.theme.PriceTextStyle
import com.velnox.core.ui.theme.VelnoxColors
import com.velnox.core.ui.theme.VelnoxTokens
import com.velnox.velshop.R
import com.velnox.velshop.navigation.VelShopTabRoutes
import com.velnox.velshop.navigation.velShopTabs

/**
 * The customer's orders.
 *
 * Each row answers the three questions a buyer actually has: what did I order, where is
 * it, and how much did it cost. Cancellation is offered only while the seller has not
 * shipped (the backend's own rule) and always asks for confirmation first, because it
 * is irreversible.
 */
@Composable
fun OrdersScreen(
    onOpenCart: () -> Unit,
    onNavigateTab: (String) -> Unit,
    viewModel: OrdersViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    val cartCount by viewModel.cartCount.collectAsStateWithLifecycle()

    var orderPendingCancellation by remember { mutableStateOf<Order?>(null) }

    VelnoxScaffold(
        title = stringResource(R.string.velshop_orders_title),
        tabs = velShopTabs(cartCount),
        currentRoute = VelShopTabRoutes.ORDERS,
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

            state.notice?.let { notice ->
                val message = when (notice) {
                    OrdersViewModel.OrdersNotice.Cancelled ->
                        stringResource(R.string.velshop_order_cancelled_notice)

                    is OrdersViewModel.OrdersNotice.Failure ->
                        notice.error.serverMessage ?: stringResource(R.string.velnox_state_error_body)
                }
                VelnoxMessageBanner(
                    message = message,
                    tone = if (notice is OrdersViewModel.OrdersNotice.Failure) {
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
                when (val current = state.result) {
                    is VelnoxScreenState.Empty -> VelnoxEmptyState(
                        title = stringResource(R.string.velshop_empty_orders),
                        body = stringResource(R.string.velshop_empty_orders_body),
                        actionLabel = stringResource(R.string.velshop_browse_action),
                        onAction = { onNavigateTab(VelShopTabRoutes.BROWSE) },
                    )

                    else -> VelnoxStateHost(
                        state = current,
                        onRetry = viewModel::refresh,
                        emptyTitle = stringResource(R.string.velshop_empty_orders),
                    ) { orders, _ ->
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
                            items(items = orders, key = { it.id }) { order ->
                                OrderCard(
                                    order = order,
                                    busy = state.busyOrderId == order.id,
                                    onCancel = { orderPendingCancellation = order },
                                    onOpenCart = onOpenCart,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    orderPendingCancellation?.let { order ->
        AlertDialog(
            onDismissRequest = { orderPendingCancellation = null },
            title = { Text(stringResource(R.string.velshop_order_cancel_confirm_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.velshop_order_cancel_confirm_body,
                        order.shortNumber,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        orderPendingCancellation = null
                        viewModel.cancel(order)
                    },
                ) {
                    Text(
                        text = stringResource(R.string.velshop_order_cancel),
                        color = VelnoxColors.Destructive,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { orderPendingCancellation = null }) {
                    Text(stringResource(R.string.velnox_action_cancel))
                }
            },
        )
    }
}

@Composable
private fun OrderCard(
    order: Order,
    busy: Boolean,
    onCancel: () -> Unit,
    onOpenCart: () -> Unit,
) {
    VelnoxCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.velshop_order_number, order.shortNumber),
                style = MaterialTheme.typography.titleSmall,
                color = VelnoxColors.OnSurface,
                modifier = Modifier.weight(1f),
            )
            OrderStatusBadge(status = order.status)
        }

        Text(
            text = VelnoxFormat.dateTime(order.createdAtEpochMillis),
            style = MaterialTheme.typography.bodySmall,
            color = VelnoxColors.OnSurfaceMuted,
        )

        order.lines.take(ORDER_PREVIEW_LINES).forEach { line ->
            Text(
                text = "${line.productName} ×${line.quantity}",
                style = MaterialTheme.typography.bodyMedium,
                color = VelnoxColors.OnSurfaceSecondary,
                maxLines = 1,
            )
        }
        if (order.lines.size > ORDER_PREVIEW_LINES) {
            Text(
                text = stringResource(R.string.velshop_order_more_items, order.lines.size - ORDER_PREVIEW_LINES),
                style = MaterialTheme.typography.bodySmall,
                color = VelnoxColors.OnSurfaceMuted,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = VelnoxTokens.spacing.gapSmall),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(VelnoxTokens.spacing.gapSmall),
        ) {
            PaymentStatusBadge(status = order.paymentStatus)
            Text(
                text = stringResource(R.string.velshop_order_items, order.itemCount),
                style = MaterialTheme.typography.bodySmall,
                color = VelnoxColors.OnSurfaceMuted,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = order.totalLabel,
                style = PriceTextStyle,
                color = VelnoxColors.OnSurface,
            )
        }

        order.trackingNumber?.let { tracking ->
            Text(
                text = stringResource(R.string.velshop_tracking, tracking),
                style = MaterialTheme.typography.bodySmall,
                color = VelnoxColors.InfoOnSurface,
                modifier = Modifier.padding(top = VelnoxTokens.spacing.gapTiny),
            )
        }

        if (order.isCancellable) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = VelnoxTokens.spacing.gap),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(VelnoxTokens.spacing.gapSmall),
            ) {
                VelnoxSecondaryButton(
                    text = stringResource(R.string.velshop_order_cancel),
                    onClick = onCancel,
                    enabled = !busy,
                )
                TextButton(onClick = onOpenCart) {
                    Text(
                        text = stringResource(R.string.velshop_order_reorder),
                        style = MaterialTheme.typography.labelMedium,
                        color = VelnoxColors.EmeraldOnSurface,
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
}

/** How many product lines a collapsed order row shows before summarising the rest. */
private const val ORDER_PREVIEW_LINES = 3
