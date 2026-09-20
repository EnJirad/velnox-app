package com.velnox.velcenter.presentation.moderation

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
import com.velnox.core.common.util.VelnoxFormat
import com.velnox.core.data.model.ModerationItem
import com.velnox.core.ui.component.ProductStatusBadge
import com.velnox.core.ui.component.VelnoxBannerTone
import com.velnox.core.ui.component.VelnoxCard
import com.velnox.core.ui.component.VelnoxImage
import com.velnox.core.ui.component.VelnoxMessageBanner
import com.velnox.core.ui.component.VelnoxScaffold
import com.velnox.core.ui.component.VelnoxSecondaryButton
import com.velnox.core.ui.component.VelnoxStateHost
import com.velnox.core.ui.component.VelnoxTextField
import com.velnox.core.ui.theme.PriceTextStyle
import com.velnox.core.ui.theme.VelnoxColors
import com.velnox.core.ui.theme.VelnoxTokens
import com.velnox.velcenter.R
import com.velnox.velcenter.navigation.VelCenterTabRoutes
import com.velnox.velcenter.navigation.velCenterTabs

/**
 * Product moderation.
 *
 * The queue opens on `pending_review` because that is the work; other statuses are
 * available for checking what happened to something. Approving publishes a product to
 * the live catalogue, so it takes a second tap's worth of intent (the button is
 * explicit and per row), and rejecting opens a dialog whose reason is required.
 */
@Composable
fun ModerationScreen(
    onNavigateTab: (String) -> Unit,
    viewModel: ModerationViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    VelnoxScaffold(
        title = stringResource(R.string.velcenter_moderation_title),
        tabs = velCenterTabs(),
        currentRoute = VelCenterTabRoutes.MODERATION,
        onNavigate = onNavigateTab,
    ) { insets ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(insets),
        ) {
            state.notice?.let { notice ->
                val (message, tone) = when (notice) {
                    ModerationViewModel.ModerationNotice.Updated ->
                        stringResource(R.string.velcenter_moderation_updated) to VelnoxBannerTone.Success

                    is ModerationViewModel.ModerationNotice.Failure ->
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

            StatusFilterRow(selected = state.filter, onSelect = viewModel::setFilter)

            Box(modifier = Modifier.weight(1f)) {
                VelnoxStateHost(
                    state = state.result,
                    onRetry = viewModel::refresh,
                    emptyTitle = stringResource(R.string.velcenter_moderation_empty),
                ) { queue, _ ->
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
                        items(items = queue, key = { it.id }) { item ->
                            ModerationCard(
                                item = item,
                                busy = state.busyProductId == item.id,
                                onApprove = { viewModel.approve(item) },
                                onReject = { viewModel.requestReject(item) },
                            )
                        }
                    }
                }
            }
        }
    }

    state.rejecting?.let { item ->
        RejectionDialog(
            item = item,
            onDismiss = viewModel::cancelReject,
            onConfirm = viewModel::confirmReject,
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
            label = { Text(stringResource(R.string.velcenter_moderation_all)) },
        )
        FILTER_STATUSES.forEach { status ->
            FilterChip(
                selected = selected == status,
                onClick = { onSelect(status) },
                label = { Text(productStatusLabel(status)) },
            )
        }
    }
}

@Composable
private fun ModerationCard(
    item: ModerationItem,
    busy: Boolean,
    onApprove: () -> Unit,
    onReject: () -> Unit,
) {
    VelnoxCard(modifier = Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(VelnoxTokens.spacing.gap)) {
            VelnoxImage(
                url = item.imageUrl,
                contentDescription = item.name,
                modifier = Modifier.size(72.dp),
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = VelnoxColors.OnSurface,
                    maxLines = 2,
                )
                item.shopName?.takeIf { it.isNotBlank() }?.let { shop ->
                    Text(
                        text = shop,
                        style = MaterialTheme.typography.bodySmall,
                        color = VelnoxColors.OnSurfaceMuted,
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(VelnoxTokens.spacing.gapSmall),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ProductStatusBadge(status = item.status)
                    Text(
                        text = item.priceLabel,
                        style = PriceTextStyle,
                        color = VelnoxColors.OnSurface,
                    )
                }
                item.submittedAtEpochMillis?.let { submitted ->
                    Text(
                        text = stringResource(
                            R.string.velcenter_moderation_submitted,
                            VelnoxFormat.dateTime(submitted),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = VelnoxColors.OnSurfaceMuted,
                    )
                }
            }
        }

        item.rejectionReason?.takeIf { it.isNotBlank() }?.let { reason ->
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
                .padding(top = VelnoxTokens.spacing.gap),
            horizontalArrangement = Arrangement.spacedBy(VelnoxTokens.spacing.gapSmall),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Offered only when it would change something: re-publishing a live product
            // is a no-op the backend answers with an error.
            if (item.status != ProductStatus.Published) {
                VelnoxSecondaryButton(
                    text = stringResource(R.string.velcenter_moderation_approve),
                    onClick = onApprove,
                    enabled = !busy,
                )
            }
            if (item.status != ProductStatus.Rejected) {
                VelnoxSecondaryButton(
                    text = stringResource(R.string.velcenter_moderation_reject),
                    onClick = onReject,
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

@Composable
private fun RejectionDialog(
    item: ModerationItem,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var reason by remember(item.id) { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.velcenter_moderation_reject)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(VelnoxTokens.spacing.gapSmall)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = VelnoxColors.OnSurfaceMuted,
                )
                VelnoxTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = stringResource(R.string.velcenter_moderation_reason_label),
                    singleLine = false,
                    maxLines = 3,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(reason) }, enabled = reason.isNotBlank()) {
                Text(
                    text = stringResource(R.string.velcenter_moderation_reject),
                    color = VelnoxColors.Destructive,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.velnox_action_cancel))
            }
        },
    )
}

/** Localised product status, from the shared string set. */
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

/** Queue filters: the work first, then the outcomes an operator checks. */
private val FILTER_STATUSES = listOf(
    ProductStatus.PendingReview,
    ProductStatus.Published,
    ProductStatus.Rejected,
    ProductStatus.Suspended,
)
