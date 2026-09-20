package com.velnox.velcenter.presentation.sellers

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
import com.velnox.core.common.domain.SellerStatus
import com.velnox.core.data.model.ManagedSeller
import com.velnox.core.ui.component.SellerStatusBadge
import com.velnox.core.ui.component.VerificationBadge
import com.velnox.core.ui.component.VelnoxBannerTone
import com.velnox.core.ui.component.VelnoxCard
import com.velnox.core.ui.component.VelnoxMessageBanner
import com.velnox.core.ui.component.VelnoxScaffold
import com.velnox.core.ui.component.VelnoxScreenState
import com.velnox.core.ui.component.VelnoxSearchField
import com.velnox.core.ui.component.VelnoxSecondaryButton
import com.velnox.core.ui.component.VelnoxStateHost
import com.velnox.core.ui.component.VelnoxTextField
import com.velnox.core.ui.theme.VelnoxColors
import com.velnox.core.ui.theme.VelnoxTokens
import com.velnox.core.ui.R as SharedR
import com.velnox.velcenter.R
import com.velnox.velcenter.navigation.VelCenterTabRoutes
import com.velnox.velcenter.navigation.velCenterTabs

/**
 * Seller approvals.
 *
 * The queue is server-filtered (`?status=&q=`) because it is unbounded, and every
 * decision goes through a confirmation dialog that names the seller and the target
 * status. Approving promotes a real account, and the three negative decisions require a
 * reason the applicant will read — so none of them can happen from a stray tap.
 */
@Composable
fun SellersScreen(
    onNavigateTab: (String) -> Unit,
    viewModel: SellersViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    VelnoxScaffold(
        title = stringResource(R.string.velcenter_sellers_title),
        tabs = velCenterTabs(),
        currentRoute = VelCenterTabRoutes.SELLERS,
        onNavigate = onNavigateTab,
    ) { insets ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(insets),
        ) {
            state.notice?.let { notice ->
                val (message, tone) = when (notice) {
                    SellersViewModel.SellersNotice.Updated ->
                        stringResource(R.string.velcenter_seller_updated) to VelnoxBannerTone.Success

                    is SellersViewModel.SellersNotice.Failure ->
                        (notice.error.serverMessage ?: stringResource(SharedR.string.velnox_state_error_body)) to
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

            VelnoxSearchField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                placeholder = stringResource(R.string.velcenter_sellers_search),
                modifier = Modifier.padding(
                    horizontal = VelnoxTokens.spacing.screenHorizontal,
                    vertical = VelnoxTokens.spacing.gapSmall,
                ),
            )

            StatusFilterRow(selected = state.filter, onSelect = viewModel::setFilter)

            Box(modifier = Modifier.weight(1f)) {
                VelnoxStateHost(
                    state = state.result,
                    onRetry = viewModel::refresh,
                    emptyTitle = stringResource(R.string.velcenter_sellers_empty),
                ) { sellers, _ ->
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
                        items(items = sellers, key = { it.id }) { seller ->
                            SellerCard(
                                seller = seller,
                                busy = state.busySellerId == seller.id,
                                onDecision = { status -> viewModel.requestDecision(seller, status) },
                            )
                        }
                    }
                }
            }
        }
    }

    state.decision?.let { decision ->
        DecisionDialog(
            decision = decision,
            onDismiss = viewModel::cancelDecision,
            onConfirm = viewModel::confirmDecision,
        )
    }
}

@Composable
private fun StatusFilterRow(
    selected: String,
    onSelect: (String) -> Unit,
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
            selected = selected == SellersViewModel.STATUS_ALL,
            onClick = { onSelect(SellersViewModel.STATUS_ALL) },
            label = { Text(stringResource(R.string.velcenter_sellers_all)) },
        )
        FILTER_STATUSES.forEach { status ->
            FilterChip(
                selected = selected == status.wireValue,
                onClick = { onSelect(status.wireValue) },
                label = { Text(sellerStatusLabel(status)) },
            )
        }
    }
}

@Composable
private fun SellerCard(
    seller: ManagedSeller,
    busy: Boolean,
    onDecision: (String) -> Unit,
) {
    VelnoxCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = seller.name?.takeIf { it.isNotBlank() }
                        ?: seller.email.orEmpty(),
                    style = MaterialTheme.typography.titleSmall,
                    color = VelnoxColors.OnSurface,
                )
                seller.email?.let { email ->
                    Text(
                        text = email,
                        style = MaterialTheme.typography.bodySmall,
                        color = VelnoxColors.OnSurfaceMuted,
                    )
                }
                seller.phone?.takeIf { it.isNotBlank() }?.let { phone ->
                    Text(
                        text = phone,
                        style = MaterialTheme.typography.bodySmall,
                        color = VelnoxColors.OnSurfaceMuted,
                    )
                }
            }
            SellerStatusBadge(status = seller.status)
        }

        seller.shopName?.takeIf { it.isNotBlank() }?.let { shop ->
            Text(
                text = stringResource(R.string.velcenter_seller_shop, shop),
                style = MaterialTheme.typography.bodyMedium,
                color = VelnoxColors.OnSurfaceSecondary,
                modifier = Modifier.padding(top = VelnoxTokens.spacing.gapSmall),
            )
        }

        Row(
            modifier = Modifier.padding(top = VelnoxTokens.spacing.gapTiny),
            horizontalArrangement = Arrangement.spacedBy(VelnoxTokens.spacing.gapSmall),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            VerificationBadge(status = seller.verificationStatus)
            seller.productCount?.let { count ->
                Text(
                    text = stringResource(R.string.velcenter_seller_products, count),
                    style = MaterialTheme.typography.bodySmall,
                    color = VelnoxColors.OnSurfaceMuted,
                )
            }
        }

        seller.rejectionReason?.takeIf { it.isNotBlank() }?.let { reason ->
            Text(
                text = reason,
                style = MaterialTheme.typography.bodySmall,
                color = VelnoxColors.Destructive,
                modifier = Modifier.padding(top = VelnoxTokens.spacing.gapSmall),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(top = VelnoxTokens.spacing.gap),
            horizontalArrangement = Arrangement.spacedBy(VelnoxTokens.spacing.gapSmall),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DECISION_ORDER.forEach { (labelRes, status) ->
                // Setting a seller to the status they already hold would be a request the
                // backend answers with an error, so it is not offered.
                if (seller.status != status) {
                    VelnoxSecondaryButton(
                        text = stringResource(labelRes),
                        onClick = { onDecision(status.wireValue) },
                        enabled = !busy,
                    )
                }
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
private fun DecisionDialog(
    decision: SellersViewModel.Decision,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var reason by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.velcenter_seller_decision_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(VelnoxTokens.spacing.gapSmall)) {
                Text(
                    text = stringResource(
                        R.string.velcenter_seller_decision_body,
                        decision.seller.shopName ?: decision.seller.name.orEmpty(),
                        sellerStatusText(decision.status),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = VelnoxColors.OnSurfaceMuted,
                )

                if (decision.requiresReason) {
                    VelnoxTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        label = stringResource(R.string.velcenter_seller_reason_label),
                        supportingText = stringResource(R.string.velcenter_seller_reason_required),
                        singleLine = false,
                        maxLines = 3,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(reason) },
                enabled = !decision.requiresReason || reason.isNotBlank(),
            ) {
                Text(stringResource(SharedR.string.velnox_action_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(SharedR.string.velnox_action_cancel))
            }
        },
    )
}

/** Localised seller status, from the shared string set so all three apps agree. */
@Composable
private fun sellerStatusLabel(status: SellerStatus): String = when (status) {
    SellerStatus.Pending -> stringResource(SharedR.string.velnox_seller_pending)
    SellerStatus.UnderReview -> stringResource(SharedR.string.velnox_seller_under_review)
    SellerStatus.NeedsCorrection -> stringResource(SharedR.string.velnox_seller_needs_correction)
    SellerStatus.Approved -> stringResource(SharedR.string.velnox_seller_approved)
    SellerStatus.Rejected -> stringResource(SharedR.string.velnox_seller_rejected)
    SellerStatus.Suspended -> stringResource(SharedR.string.velnox_seller_suspended)
    SellerStatus.Unknown -> stringResource(SharedR.string.velnox_seller_unknown)
}

/** Localised label for a wire status value coming back from a decision. */
@Composable
private fun sellerStatusText(wireValue: String): String =
    sellerStatusLabel(SellerStatus.fromWire(wireValue))

/** The statuses worth filtering by, in lifecycle order. */
private val FILTER_STATUSES = listOf(
    SellerStatus.Pending,
    SellerStatus.UnderReview,
    SellerStatus.NeedsCorrection,
    SellerStatus.Approved,
    SellerStatus.Rejected,
    SellerStatus.Suspended,
)

/** The four decisions, in the order they are offered. */
private val DECISION_ORDER = listOf(
    R.string.velcenter_seller_approve to SellerStatus.Approved,
    R.string.velcenter_seller_needs_correction to SellerStatus.NeedsCorrection,
    R.string.velcenter_seller_reject to SellerStatus.Rejected,
    R.string.velcenter_seller_suspend to SellerStatus.Suspended,
)
