package com.velnox.velcenter.presentation.dashboard

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.velnox.core.common.util.VelnoxFormat
import com.velnox.core.data.model.DashboardCounts
import com.velnox.core.ui.component.VelnoxCard
import com.velnox.core.ui.component.VelnoxEmptyState
import com.velnox.core.ui.component.VelnoxScaffold
import com.velnox.core.ui.component.VelnoxScreenState
import com.velnox.core.ui.component.VelnoxStateHost
import com.velnox.core.ui.theme.VelnoxColors
import com.velnox.core.ui.theme.VelnoxTokens
import com.velnox.velcenter.R
import com.velnox.velcenter.navigation.VelCenterTabRoutes
import com.velnox.velcenter.navigation.velCenterTabs

/**
 * The operator's first screen.
 *
 * Tiles are rendered only for figures the backend actually sent; anything missing reads
 * "unavailable" rather than a confident zero, because a dashboard that invents a zero
 * tells an operator there is nothing to do when there might be.
 *
 * The layout is a two-column grid built from the data, which keeps a tile's meaning next
 * to its number and avoids a card that looks full but is blank.
 */
@Composable
fun DashboardScreen(
    onNavigateTab: (String) -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    VelnoxScaffold(
        title = stringResource(R.string.velcenter_dashboard_title),
        tabs = velCenterTabs(),
        currentRoute = VelCenterTabRoutes.DASHBOARD,
        onNavigate = onNavigateTab,
    ) { insets ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(insets),
        ) {
            when (val current = state.result) {
                is VelnoxScreenState.Content -> if (!current.value.hasAnyMetric) {
                    // A successful response with no recognisable figure is not zero
                    // everywhere — it means this build and the backend disagree about
                    // what the summary contains, and saying so is the honest report.
                    VelnoxEmptyState(
                        title = stringResource(R.string.velcenter_dashboard_no_metrics),
                        body = null,
                    )
                } else {
                    MetricGrid(counts = current.value)
                }

                else -> VelnoxStateHost(
                    state = current,
                    onRetry = viewModel::refresh,
                    emptyTitle = stringResource(R.string.velcenter_dashboard_no_metrics),
                ) { counts, _ -> MetricGrid(counts = counts) }
            }
        }
    }
}

@Composable
private fun MetricGrid(counts: DashboardCounts) {
    val unavailable = stringResource(R.string.velcenter_dashboard_unavailable)

    val tiles = listOf(
        stringResource(R.string.velcenter_dashboard_pending_sellers) to counts.pendingSellers?.toString(),
        stringResource(R.string.velcenter_dashboard_pending_products) to counts.pendingProducts?.toString(),
        stringResource(R.string.velcenter_dashboard_open_verifications) to counts.openVerifications?.toString(),
        stringResource(R.string.velcenter_dashboard_total_users) to counts.totalUsers?.toString(),
        stringResource(R.string.velcenter_dashboard_total_sellers) to counts.totalSellers?.toString(),
        stringResource(R.string.velcenter_dashboard_total_products) to counts.totalProducts?.toString(),
        stringResource(R.string.velcenter_dashboard_total_orders) to counts.totalOrders?.toString(),
        stringResource(R.string.velcenter_dashboard_gmv) to counts.grossMerchandiseValue?.let { value ->
            VelnoxFormat.baht(value)
        },
    )

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
        items(items = tiles.chunked(2)) { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(VelnoxTokens.spacing.gap),
            ) {
                row.forEach { (label, value) ->
                    MetricTile(
                        label = label,
                        value = value,
                        unavailable = unavailable,
                        modifier = Modifier.weight(1f),
                    )
                }
                // Keeps an odd tile the same width as its pair instead of stretching
                // across the row and implying extra importance.
                if (row.size == 1) {
                    Box(modifier = Modifier.weight(1f))
                }
            }
        }

        item {
            Text(
                text = stringResource(R.string.velcenter_dashboard_note),
                style = MaterialTheme.typography.bodySmall,
                color = VelnoxColors.OnSurfaceMuted,
            )
        }
    }
}

@Composable
private fun MetricTile(
    label: String,
    value: String?,
    unavailable: String,
    modifier: Modifier = Modifier,
) {
    VelnoxCard(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = VelnoxColors.OnSurfaceMuted,
        )
        Text(
            text = value ?: unavailable,
            style = MaterialTheme.typography.headlineSmall,
            color = if (value != null) VelnoxColors.OnSurface else VelnoxColors.OnSurfaceDisabled,
            modifier = Modifier.padding(top = VelnoxTokens.spacing.gapTiny),
        )
    }
}
