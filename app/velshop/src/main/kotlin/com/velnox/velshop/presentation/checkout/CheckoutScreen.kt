package com.velnox.velshop.presentation.checkout

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.velnox.core.data.model.Address
import com.velnox.core.data.repository.CartRepository
import com.velnox.core.ui.component.VelnoxBannerTone
import com.velnox.core.ui.component.VelnoxCard
import com.velnox.core.ui.component.VelnoxDivider
import com.velnox.core.ui.component.VelnoxInfoRow
import com.velnox.core.ui.component.VelnoxMessageBanner
import com.velnox.core.ui.component.VelnoxPrimaryButton
import com.velnox.core.ui.component.VelnoxScaffold
import com.velnox.core.ui.component.VelnoxScreenState
import com.velnox.core.ui.component.VelnoxSectionHeader
import com.velnox.core.ui.component.VelnoxStateHost
import com.velnox.core.ui.component.VelnoxTextField
import com.velnox.core.ui.theme.PriceTextStyle
import com.velnox.core.ui.theme.VelnoxColors
import com.velnox.core.ui.theme.VelnoxTokens
import com.velnox.velshop.R

/**
 * Checkout.
 *
 * Mobile layout: the address choice, the payment method, an optional note and the
 * order summary are one vertical flow, with the confirm action pinned to the bottom of
 * the screen so it is always reachable. Nothing is collapsed behind a disclosure — the
 * user must be able to see the total and the address they are about to commit to
 * without extra taps.
 *
 * ## Card payments
 *
 * `paymentMethod = "card"` returns a Stripe checkout URL instead of creating the order
 * immediately (`backend/routes/stripe.ts`). The app opens that URL in the user's
 * browser — a native browser session, not an embedded WebView, which the project's
 * rules forbid and which Stripe would refuse anyway. The order then appears in the
 * order list once the backend confirms payment.
 */
@Composable
fun CheckoutScreen(
    onBack: () -> Unit,
    onOrderPlaced: () -> Unit,
    viewModel: CheckoutViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // One-shot: whatever the outcome of this submission, the checkout screen is done.
    LaunchedEffect(state.placed) {
        val placed = state.placed ?: return@LaunchedEffect
        placed.checkoutUrl?.takeIf { it.isNotBlank() }?.let { url ->
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }
        }
        onOrderPlaced()
    }

    VelnoxScaffold(
        title = stringResource(R.string.velshop_checkout_title),
        tabs = emptyList(),
        currentRoute = "",
        onNavigate = {},
        onBack = onBack,
    ) { insets ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(insets),
        ) {
            state.error?.let { error ->
                VelnoxMessageBanner(
                    message = error.serverMessage ?: stringResource(R.string.velnox_state_error_body),
                    tone = VelnoxBannerTone.Error,
                    onDismiss = viewModel::consumeError,
                    modifier = Modifier.padding(
                        horizontal = VelnoxTokens.spacing.screenHorizontal,
                        vertical = VelnoxTokens.spacing.gapSmall,
                    ),
                )
            }

            Box(modifier = Modifier.weight(1f)) {
                VelnoxStateHost(
                    state = state.result,
                    onRetry = viewModel::load,
                    emptyTitle = stringResource(R.string.velshop_empty_cart),
                    emptyBody = stringResource(R.string.velshop_empty_cart_body),
                ) { data, _ ->
                    CheckoutContent(
                        data = data,
                        state = state,
                        onSelectAddress = viewModel::selectAddress,
                        onSelectPaymentMethod = viewModel::selectPaymentMethod,
                        onNoteChange = viewModel::onNoteChange,
                    )
                }
            }

            (state.result as? VelnoxScreenState.Content<CheckoutViewModel.CheckoutData>)?.let { content ->
                CheckoutActionBar(
                    total = content.value.cart.subtotalLabel,
                    submitting = state.submitting,
                    enabled = state.canSubmit,
                    onConfirm = viewModel::submit,
                )
            }
        }
    }
}

@Composable
private fun CheckoutContent(
    data: CheckoutViewModel.CheckoutData,
    state: CheckoutViewModel.UiState,
    onSelectAddress: (String) -> Unit,
    onSelectPaymentMethod: (String) -> Unit,
    onNoteChange: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = VelnoxTokens.spacing.screenHorizontal,
            end = VelnoxTokens.spacing.screenHorizontal,
            top = VelnoxTokens.spacing.gap,
            bottom = VelnoxTokens.spacing.screenBottom,
        ),
        verticalArrangement = Arrangement.spacedBy(VelnoxTokens.spacing.gapSmall),
    ) {
        item {
            VelnoxSectionHeader(title = stringResource(R.string.velshop_select_address))
        }

        if (data.addresses.isEmpty()) {
            // No address means checkout cannot proceed. The message names the real
            // remedy instead of failing at the last tap.
            item {
                VelnoxMessageBanner(
                    message = stringResource(R.string.velshop_no_address),
                    tone = VelnoxBannerTone.Warning,
                )
            }
        }

        items(items = data.addresses, key = { it.id }) { address ->
            AddressCard(
                address = address,
                selected = address.id == state.selectedAddressId,
                onSelect = { onSelectAddress(address.id) },
            )
        }

        item {
            VelnoxSectionHeader(
                title = stringResource(R.string.velshop_checkout_payment_section),
                modifier = Modifier.padding(top = VelnoxTokens.spacing.gap),
            )
        }

        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(VelnoxTokens.spacing.gapSmall),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(
                    selected = state.paymentMethod == CartRepository.PAYMENT_COD,
                    onClick = { onSelectPaymentMethod(CartRepository.PAYMENT_COD) },
                    label = { Text(stringResource(R.string.velshop_payment_cod)) },
                )
                FilterChip(
                    selected = state.paymentMethod == CartRepository.PAYMENT_CARD,
                    onClick = { onSelectPaymentMethod(CartRepository.PAYMENT_CARD) },
                    label = { Text(stringResource(R.string.velshop_payment_card)) },
                )
            }
        }

        item {
            VelnoxTextField(
                value = state.note,
                onValueChange = onNoteChange,
                label = stringResource(R.string.velshop_checkout_note_label),
                singleLine = false,
                maxLines = 3,
                modifier = Modifier.padding(top = VelnoxTokens.spacing.gapSmall),
            )
        }

        item {
            VelnoxSectionHeader(
                title = stringResource(R.string.velshop_checkout_summary),
                modifier = Modifier.padding(top = VelnoxTokens.spacing.gap),
            )
        }

        item {
            VelnoxCard(modifier = Modifier.fillMaxWidth()) {
                data.cart.lines.forEach { line ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = VelnoxTokens.spacing.gapTiny),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = line.productName,
                                style = MaterialTheme.typography.bodyMedium,
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
                        }
                        Text(
                            text = "×${line.quantity}",
                            style = MaterialTheme.typography.bodySmall,
                            color = VelnoxColors.OnSurfaceMuted,
                            modifier = Modifier.padding(horizontal = VelnoxTokens.spacing.gapSmall),
                        )
                        Text(
                            text = line.lineTotalLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            color = VelnoxColors.OnSurfaceSecondary,
                        )
                    }
                }

                VelnoxDivider(modifier = Modifier.padding(vertical = VelnoxTokens.spacing.gapSmall))

                VelnoxInfoRow(
                    label = stringResource(R.string.velshop_order_items, data.cart.itemCount),
                    value = data.cart.subtotalLabel,
                )
                VelnoxInfoRow(
                    label = stringResource(R.string.velshop_shipping_fee),
                    value = stringResource(R.string.velshop_shipping_calculated_at_delivery),
                )
            }
        }
    }
}

@Composable
private fun AddressCard(
    address: Address,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    VelnoxCard(onClick = onSelect, modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(VelnoxTokens.spacing.gapSmall),
        ) {
            // A radio, not a checkbox: exactly one address is used per order.
            RadioButton(selected = selected, onClick = onSelect)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = address.recipientName,
                    style = MaterialTheme.typography.titleSmall,
                    color = VelnoxColors.OnSurface,
                )
                Text(
                    text = address.phone,
                    style = MaterialTheme.typography.bodySmall,
                    color = VelnoxColors.OnSurfaceMuted,
                )
                Text(
                    text = address.formatted,
                    style = MaterialTheme.typography.bodySmall,
                    color = VelnoxColors.OnSurfaceMuted,
                )
            }
        }
    }
}

@Composable
private fun CheckoutActionBar(
    total: String,
    submitting: Boolean,
    enabled: Boolean,
    onConfirm: () -> Unit,
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
            verticalArrangement = Arrangement.spacedBy(VelnoxTokens.spacing.gapSmall),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.velshop_total),
                    style = MaterialTheme.typography.bodyMedium,
                    color = VelnoxColors.OnSurfaceMuted,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = total,
                    style = PriceTextStyle,
                    color = VelnoxColors.OnSurface,
                )
            }

            VelnoxPrimaryButton(
                text = stringResource(R.string.velshop_checkout_confirm),
                onClick = onConfirm,
                enabled = enabled,
                loading = submitting,
            )
        }
    }
}
