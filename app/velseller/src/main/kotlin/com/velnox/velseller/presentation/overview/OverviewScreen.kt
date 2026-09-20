package com.velnox.velseller.presentation.overview

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.velnox.core.common.util.VelnoxFormat
import com.velnox.core.data.model.SellerGoal
import com.velnox.core.ui.component.VelnoxBannerTone
import com.velnox.core.ui.component.VelnoxCard
import com.velnox.core.ui.component.VelnoxDivider
import com.velnox.core.ui.component.VelnoxInfoRow
import com.velnox.core.ui.component.VelnoxMessageBanner
import com.velnox.core.ui.component.VelnoxScaffold
import com.velnox.core.ui.component.VelnoxSectionHeader
import com.velnox.core.ui.component.VelnoxStateHost
import com.velnox.core.ui.component.VelnoxTextField
import com.velnox.core.ui.theme.PriceTextStyle
import com.velnox.core.ui.theme.VelnoxColors
import com.velnox.core.ui.theme.VelnoxTokens
import com.velnox.velseller.R
import com.velnox.velseller.navigation.VelSellerTabRoutes
import com.velnox.velseller.navigation.velSellerTabs

/**
 * The shop at a glance.
 *
 * Income first, because it is the question a seller opens the app with; goals second,
 * because they are a plan rather than a fact. Every money figure is the backend's own
 * aggregate — the note at the bottom of the card says so, and it is true.
 */
@Composable
fun OverviewScreen(
    onNavigateTab: (String) -> Unit,
    viewModel: OverviewViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showGoalDialog by remember { mutableStateOf(false) }

    VelnoxScaffold(
        title = stringResource(R.string.velseller_overview_title),
        tabs = velSellerTabs(),
        currentRoute = VelSellerTabRoutes.OVERVIEW,
        onNavigate = onNavigateTab,
    ) { insets ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(insets),
        ) {
            state.notice?.let { notice ->
                val (message, tone) = when (notice) {
                    OverviewViewModel.OverviewNotice.GoalCreated ->
                        stringResource(R.string.velseller_goal_created) to VelnoxBannerTone.Success

                    OverviewViewModel.OverviewNotice.GoalDeleted ->
                        stringResource(R.string.velseller_goal_deleted) to VelnoxBannerTone.Success

                    is OverviewViewModel.OverviewNotice.Failure ->
                        (notice.error.serverMessage ?: stringResource(R.string.velseller_goal_create_failed)) to
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

            Box(modifier = Modifier.weight(1f)) {
                VelnoxStateHost(
                    state = state.result,
                    onRetry = viewModel::refresh,
                ) { data, _ ->
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
                        item {
                            IncomeCard(
                                gross = data.income.grossSalesLabel,
                                commission = data.income.commissionLabel,
                                net = data.income.netEarningsLabel,
                                pending = data.income.pendingPayoutLabel,
                                orderCount = data.income.orderCount,
                                unitsSold = data.income.unitsSold,
                                average = data.income.averageOrderValue?.let { value ->
                                    VelnoxFormat.baht(value)
                                },
                            )
                        }

                        item {
                            VelnoxSectionHeader(
                                title = stringResource(R.string.velseller_goals_title),
                                actionLabel = stringResource(R.string.velseller_goal_add),
                                onAction = { showGoalDialog = true },
                            )
                        }

                        if (data.goalsUnavailable) {
                            item {
                                VelnoxMessageBanner(
                                    message = stringResource(R.string.velnox_state_error_body),
                                    tone = VelnoxBannerTone.Warning,
                                )
                            }
                        } else if (data.goals.isEmpty()) {
                            item {
                                VelnoxCard(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        text = stringResource(R.string.velseller_goals_empty),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = VelnoxColors.OnSurfaceMuted,
                                    )
                                }
                            }
                        }

                        items(items = data.goals, key = { it.id }) { goal ->
                            GoalCard(goal = goal, onDelete = { viewModel.deleteGoal(goal.id) })
                        }
                    }
                }
            }
        }
    }

    if (showGoalDialog) {
        NewGoalDialog(
            onDismiss = { showGoalDialog = false },
            onConfirm = { title, metric, target, period ->
                showGoalDialog = false
                viewModel.createGoal(title = title, metric = metric, targetText = target, period = period)
            },
        )
    }
}

@Composable
private fun IncomeCard(
    gross: String,
    commission: String,
    net: String,
    pending: String,
    orderCount: Int,
    unitsSold: Int,
    average: String?,
) {
    VelnoxCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.velseller_income_title),
            style = MaterialTheme.typography.titleSmall,
            color = VelnoxColors.OnSurfaceMuted,
        )
        Text(
            text = net,
            style = PriceTextStyle,
            color = VelnoxColors.EmeraldOnSurface,
            modifier = Modifier.padding(top = VelnoxTokens.spacing.gapTiny),
        )

        VelnoxDivider(modifier = Modifier.padding(vertical = VelnoxTokens.spacing.gapSmall))

        VelnoxInfoRow(label = stringResource(R.string.velseller_income_gross), value = gross)
        VelnoxInfoRow(label = stringResource(R.string.velseller_income_commission), value = commission)
        VelnoxInfoRow(label = stringResource(R.string.velseller_income_pending), value = pending)
        VelnoxInfoRow(
            label = stringResource(R.string.velseller_income_orders),
            value = orderCount.toString(),
        )
        VelnoxInfoRow(
            label = stringResource(R.string.velseller_income_units),
            value = unitsSold.toString(),
        )
        average?.let {
            VelnoxInfoRow(label = stringResource(R.string.velseller_income_average), value = it)
        }

        Text(
            text = stringResource(R.string.velseller_revenue_note),
            style = MaterialTheme.typography.bodySmall,
            color = VelnoxColors.OnSurfaceMuted,
            modifier = Modifier.padding(top = VelnoxTokens.spacing.gapSmall),
        )
    }
}

@Composable
private fun GoalCard(goal: SellerGoal, onDelete: () -> Unit) {
    val progress = goal.progress

    VelnoxCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = goal.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = VelnoxColors.OnSurface,
                )
                Text(
                    text = stringResource(
                        R.string.velseller_goal_progress,
                        formatMetric(goal.currentValue, goal.metric),
                        formatMetric(goal.targetValue, goal.metric),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = VelnoxColors.OnSurfaceMuted,
                )
                // Progress is shown as a number, not a bar: a bar would need a
                // percentage the backend does not always provide when the target is
                // zero, and the text is unambiguous either way.
                if (progress != null) {
                    Text(
                        text = "${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = VelnoxColors.EmeraldOnSurface,
                    )
                }
            }

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.velseller_goal_delete),
                    tint = VelnoxColors.OnSurfaceMuted,
                )
            }
        }
    }
}

@Composable
private fun NewGoalDialog(
    onDismiss: () -> Unit,
    onConfirm: (title: String, metric: String, target: String, period: String) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var metric by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("") }
    var period by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.velseller_goal_new_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(VelnoxTokens.spacing.gapSmall),
            ) {
                VelnoxTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = stringResource(R.string.velseller_goal_title_label),
                )
                VelnoxTextField(
                    value = metric,
                    onValueChange = { metric = it },
                    label = stringResource(R.string.velseller_goal_metric_label),
                )
                VelnoxTextField(
                    value = target,
                    onValueChange = { target = it },
                    label = stringResource(R.string.velseller_goal_target_label),
                )
                VelnoxTextField(
                    value = period,
                    onValueChange = { period = it },
                    label = stringResource(R.string.velseller_goal_period_label),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(title, metric, target, period) },
                enabled = title.isNotBlank() && target.isNotBlank(),
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

/** Money metrics are shown as baht, everything else as a plain number. */
private fun formatMetric(value: Double, metric: String): String =
    if (metric.lowercase().contains("revenue") ||
        metric.lowercase().contains("sales") ||
        metric.lowercase().contains("income")
    ) {
        VelnoxFormat.baht(value)
    } else {
        if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
    }
