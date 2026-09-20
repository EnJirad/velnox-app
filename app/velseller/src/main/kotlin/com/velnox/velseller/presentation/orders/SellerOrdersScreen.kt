package com.velnox.velseller.presentation.orders

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
import androidx.compose.foundation.verticalScroll
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
import com.velnox.core.common.domain.OrderStatus
import com.velnox.core.common.domain.allowedNextStatuses
import com.velnox.core.common.util.VelnoxFormat
import com.velnox.core.data.model.Order
import com.velnox.core.ui.component.OrderStatusBadge
import com.velnox.core.ui.component.PaymentStatusBadge
import com.velnox.core.ui.component.VelnoxBannerTone
import com.velnox.core.ui.component.VelnoxCard
import com.velnox.core.ui.component.VelnoxEmptyState
import com.velnox.core.ui.component.VelnoxInfoRow
import com.velnox.core.ui.component.VelnoxMessageBanner
import com.velnox.core.ui.component.VelnoxOfflineBanner
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
 * Orders awaiting this seller.
 *
 * Each card shows what the seller must act on — the items, the customer, the money — and
 * then exactly the transitions the order state machine allows. Cancelling asks for a
 * reason and shipping offers a tracking number, because those are the two transitions
 * where a bare status change loses information the customer depends on.
 */
@Composable
fun SellerOrdersScreen(
    onNavigateTab: (String) -> Unit,
    viewModel: SellerOrdersViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    VelnoxScaffold(
        title = stringResource(R.string.velseller_orders_title),
        tabs = velSellerTabs(),
        currentRoute = VelSellerTabRoutes.ORDERS,
        onNavigate = onNavigateTab,
    ) { insets ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(insets),
        ) {
            state.notice?.let { notice ->
                val (message, tone) = when (notice) {
                    SellerOrdersViewModel.SellerOrdersNotice.Updated ->
                        stringResource(R.string.velseller_order_updated) to VelnoxBannerTone.Success

                    is SellerOrdersViewModel.SellerOrdersNotice.Failure ->
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

            (state.result as? VelnoxScreenState.Content<List<Order>>)?.takeIf { it.isCached }?.let {
                VelnoxOfflineBanner(
                    modifier = Modifier.padding(
                        horizontal = VelnoxTokens.spacing.screenHorizontal,
                        vertical = VelnoxTokens.spacing.gapSmall,
                    ),
                    message = stringResource(R.string.velnox_state_cached_notice),
                    onRetry = viewModel::refresh,
                )
            }

            Box(modifier = Modifier.weight(1f)) {
                if (state.result is VelnoxScreenState.Empty) {
                    VelnoxEmptyState(
                        title = stringResource(R.string.velseller_orders_empty),
                        body = stringResource(R.string.velseller_orders_empty_body),
                    )
                } else {
                    VelnoxStateHost(
                        state = state.result,
                        onRetry = viewModel::refresh,
                        emptyTitle = stringResource(R.string.velseller_orders_empty),
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
                                SellerOrderCard(
                                    order = order,
                                    busy = state.busyOrderId == order.id,
                                    onTransition = { next -> viewModel.requestTransition(order, next) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    state.transition?.let { request ->
        TransitionDialog(
            request = request,
            onDismiss = viewModel::cancelTransition,
            onConfirm = viewModel::confirmTransition,
        )
    }
}

@Composable
private fun SellerOrderCard(
    order: Order,
    busy: Boolean,
    onTransition: (OrderStatus) -> Unit,
) {
    VelnoxCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.velnox_order_number, order.shortNumber),
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

        order.lines.forEach { line ->
            Text(
                text = "${line.productName} ×${line.quantity}",
                style = MaterialTheme.typography.bodyMedium,
                color = VelnoxColors.OnSurfaceSecondary,
                maxLines = 2,
            )
        }

        VelnoxInfoRow(
            label = stringResource(R.string.velnox_order_items_label),
            value = stringResource(R.string.velnox_order_items, order.itemCount),
        )
        order.customerName?.let { customer ->
            VelnoxInfoRow(
                label = stringResource(R.string.velseller_order_customer, customer),
                value = order.customerPhone.orEmpty(),
            )
        }
        order.trackingNumber?.let { tracking ->
            Text(
                text = stringResource(R.string.velnox_order_tracking, tracking),
                style = MaterialTheme.typography.bodySmall,
                color = VelnoxColors.InfoOnSurface,
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
                text = order.totalLabel,
                style = PriceTextStyle,
                color = VelnoxColors.OnSurface,
                modifier = Modifier.weight(1f),
            )
        }

        val nextStatuses = order.status.allowedNextStatuses
        if (nextStatuses.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = VelnoxTokens.spacing.gap),
                horizontalArrangement = Arrangement.spacedBy(VelnoxTokens.spacing.gapSmall),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                nextStatuses.forEach { next ->
                    VelnoxSecondaryButton(
                        text = stringResource(R.string.velseller_order_action, orderStatusLabel(next)),
                        onClick = { onTransition(next) },
                        enabled = !busy,
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

@Composable
private fun TransitionDialog(
    request: SellerOrdersViewModel.TransitionRequest,
    onDismiss: () -> Unit,
    onConfirm: (reason: String, tracking: String, carrier: String) -> Unit,
) {
    var reason by remember { mutableStateOf("") }
    var tracking by remember { mutableStateOf("") }
    var carrier by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.velseller_order_transition_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(VelnoxTokens.spacing.gapSmall),
            ) {
                Text(
                    text = stringResource(
                        R.string.velseller_order_transition_body,
                        request.order.shortNumber,
                        orderStatusLabel(request.next),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = VelnoxColors.OnSurfaceMuted,
                )

                if (request.needsTracking) {
                    VelnoxTextField(
                        value = tracking,
                        onValueChange = { tracking = it },
                        label = stringResource(R.string.velseller_order_tracking_label),
                    )
                    VelnoxTextField(
                        value = carrier,
                        onValueChange = { carrier = it },
                        label = stringResource(R.string.velseller_order_carrier_label),
                    )
                }

                if (request.needsReason) {
                    VelnoxTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        label = stringResource(R.string.velseller_order_reason_label),
                        singleLine = false,
                        maxLines = 3,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(reason, tracking, carrier) },
                // A cancellation without a reason is refused by the backend, so the
                // dialog does not let the user send one.
                enabled = !request.needsReason || reason.isNotBlank(),
            ) {
                Text(stringResource(R.string.velnox_action_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.velnox_action_cancel))
            }
        },
    )
}

/** Localised order status, from the shared string set so all three apps agree. */
@Composable
private fun orderStatusLabel(status: OrderStatus): String = when (status) {
    OrderStatus.Pending -> stringResource(R.string.velnox_order_pending)
    OrderStatus.Confirmed -> stringResource(R.string.velnox_order_confirmed)
    OrderStatus.Shipped -> stringResource(R.string.velnox_order_shipped)
    OrderStatus.Delivered -> stringResource(R.string.velnox_order_delivered)
    OrderStatus.Completed -> stringResource(R.string.velnox_order_completed)
    OrderStatus.Cancelled -> stringResource(R.string.velnox_order_cancelled)
    OrderStatus.Unknown -> stringResource(R.string.velnox_order_unknown)
}
