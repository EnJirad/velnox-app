package com.velnox.velshop.presentation.cart

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.velnox.core.data.model.Cart
import com.velnox.core.data.model.CartLine
import com.velnox.core.ui.component.VelnoxBannerTone
import com.velnox.core.ui.component.VelnoxCard
import com.velnox.core.ui.component.VelnoxChip
import com.velnox.core.ui.component.VelnoxEmptyState
import com.velnox.core.ui.component.VelnoxImage
import com.velnox.core.ui.component.VelnoxMessageBanner
import com.velnox.core.ui.component.VelnoxPrimaryButton
import com.velnox.core.ui.component.VelnoxScaffold
import com.velnox.core.ui.component.VelnoxScreenState
import com.velnox.core.ui.component.VelnoxStateHost
import com.velnox.core.ui.theme.PriceTextStyle
import com.velnox.core.ui.theme.VelnoxColors
import com.velnox.core.ui.theme.VelnoxTokens
import com.velnox.core.ui.R as SharedR
import com.velnox.velshop.R
import com.velnox.velshop.navigation.VelShopTabRoutes
import com.velnox.velshop.navigation.velShopTabs

/**
 * The cart.
 *
 * The list shows the server's own line objects — name, variant, unit price snapshot
 * and the subtotal the backend computed — so what the user reads here is exactly what
 * checkout will charge.
 *
 * Two rules are enforced visually rather than only server-side:
 *
 *  * A line the backend reports as out of stock blocks the checkout button and says
 *    why, instead of letting the user reach checkout and be refused there.
 *  * Quantity is floored at 1. Decrementing a line of one removes it (with a notice),
 *    which is the only meaningful next step at the API's minimum quantity.
 */
@Composable
fun CartScreen(
    onCheckout: () -> Unit,
    onNavigateTab: (String) -> Unit,
    viewModel: CartViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val cartCount by viewModel.cartCount.collectAsStateWithLifecycle()

    VelnoxScaffold(
        title = stringResource(R.string.velshop_cart_title),
        tabs = velShopTabs(cartCount),
        currentRoute = VelShopTabRoutes.CART,
        onNavigate = onNavigateTab,
    ) { insets ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(insets),
        ) {
            state.notice?.let { notice ->
                val message = when (notice) {
                    CartViewModel.CartNotice.LineRemoved ->
                        stringResource(R.string.velshop_cart_item_removed)

                    is CartViewModel.CartNotice.Failure ->
                        notice.error.serverMessage ?: stringResource(SharedR.string.velnox_state_error_body)
                }
                VelnoxMessageBanner(
                    message = message,
                    tone = if (notice is CartViewModel.CartNotice.Failure) {
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
                    // An empty cart is a destination, not a failure: it gets the
                    // message *and* the way out of it.
                    is VelnoxScreenState.Empty -> VelnoxEmptyState(
                        title = stringResource(R.string.velshop_empty_cart),
                        body = stringResource(R.string.velshop_empty_cart_body),
                        actionLabel = stringResource(R.string.velshop_browse_action),
                        onAction = { onNavigateTab(VelShopTabRoutes.BROWSE) },
                    )

                    else -> VelnoxStateHost(
                        state = current,
                        onRetry = viewModel::load,
                        emptyTitle = stringResource(R.string.velshop_empty_cart),
                    ) { cart, _ ->
                        CartList(
                            cart = cart,
                            busyItemId = state.busyItemId,
                            onIncrease = viewModel::increase,
                            onDecrease = viewModel::decrease,
                            onRemove = viewModel::remove,
                        )
                    }
                }
            }

            (state.result as? VelnoxScreenState.Content<Cart>)?.let { content ->
                CartSummaryBar(
                    cart = content.value,
                    busy = state.busyItemId != null,
                    onCheckout = onCheckout,
                )
            }
        }
    }
}

@Composable
private fun CartList(
    cart: Cart,
    busyItemId: String?,
    onIncrease: (CartLine) -> Unit,
    onDecrease: (CartLine) -> Unit,
    onRemove: (CartLine) -> Unit,
) {
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
        items(items = cart.lines, key = { it.cartItemId }) { line ->
            CartLineCard(
                line = line,
                busy = busyItemId == line.cartItemId,
                onIncrease = { onIncrease(line) },
                onDecrease = { onDecrease(line) },
                onRemove = { onRemove(line) },
            )
        }
    }
}

@Composable
private fun CartLineCard(
    line: CartLine,
    busy: Boolean,
    onIncrease: () -> Unit,
    onDecrease: () -> Unit,
    onRemove: () -> Unit,
) {
    VelnoxCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(VelnoxTokens.spacing.gap),
            verticalAlignment = Alignment.Top,
        ) {
            VelnoxImage(
                url = line.imageUrl,
                contentDescription = line.productName,
                modifier = Modifier.size(72.dp),
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = line.productName,
                    style = MaterialTheme.typography.titleSmall,
                    color = VelnoxColors.OnSurface,
                    maxLines = 2,
                )
                line.variantLabel?.let { variant ->
                    Text(
                        text = variant,
                        style = MaterialTheme.typography.bodySmall,
                        color = VelnoxColors.OnSurfaceMuted,
                        maxLines = 1,
                    )
                }
                line.shopName?.let { shop ->
                    Text(
                        text = shop,
                        style = MaterialTheme.typography.bodySmall,
                        color = VelnoxColors.OnSurfaceMuted,
                        maxLines = 1,
                    )
                }

                if (line.isOutOfStock) {
                    VelnoxChip(
                        text = stringResource(SharedR.string.velnox_out_of_stock),
                        background = VelnoxColors.DestructiveSurface,
                        contentColor = VelnoxColors.Destructive,
                        modifier = Modifier.padding(top = VelnoxTokens.spacing.gapTiny),
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = VelnoxTokens.spacing.gapSmall),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedIconButton(onClick = onDecrease, enabled = !busy) {
                        Icon(
                            imageVector = Icons.Filled.Remove,
                            contentDescription = stringResource(R.string.velshop_cart_decrease),
                        )
                    }

                    Text(
                        text = line.quantity.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        color = VelnoxColors.OnSurface,
                        modifier = Modifier.padding(horizontal = VelnoxTokens.spacing.gap),
                    )

                    OutlinedIconButton(
                        onClick = onIncrease,
                        enabled = !busy && line.canIncrease,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = stringResource(R.string.velshop_cart_increase),
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    if (busy) {
                        CircularProgressIndicator(
                            color = VelnoxColors.Emerald,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp),
                        )
                    } else {
                        Text(
                            text = line.lineTotalLabel,
                            style = PriceTextStyle,
                            color = VelnoxColors.EmeraldOnSurface,
                        )
                    }
                }
            }

            IconButton(onClick = onRemove, enabled = !busy) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.velshop_cart_remove),
                    tint = VelnoxColors.OnSurfaceMuted,
                )
            }
        }
    }
}

/** Pinned summary: the server's subtotal plus the one action that matters. */
@Composable
private fun CartSummaryBar(
    cart: Cart,
    busy: Boolean,
    onCheckout: () -> Unit,
) {
    val blockedLine = cart.lines.any { it.isOutOfStock }

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
            verticalArrangement = Arrangement.spacedBy(VelnoxTokens.spacing.gapSmall),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.velshop_subtotal),
                    style = MaterialTheme.typography.bodyMedium,
                    color = VelnoxColors.OnSurfaceMuted,
                )
                Spacer(modifier = Modifier.width(VelnoxTokens.spacing.gap))
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = cart.subtotalLabel,
                    style = PriceTextStyle,
                    color = VelnoxColors.OnSurface,
                )
            }

            if (blockedLine) {
                Text(
                    text = stringResource(R.string.velshop_cart_out_of_stock_block),
                    style = MaterialTheme.typography.bodySmall,
                    color = VelnoxColors.Destructive,
                )
            }

            VelnoxPrimaryButton(
                text = stringResource(R.string.velshop_checkout_action),
                onClick = onCheckout,
                enabled = !cart.isEmpty && !blockedLine,
                loading = busy,
            )
        }
    }
}
