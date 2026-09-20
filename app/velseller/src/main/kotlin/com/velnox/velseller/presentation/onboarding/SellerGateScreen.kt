package com.velnox.velseller.presentation.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.velnox.core.common.domain.SellerStatus
import com.velnox.core.data.model.SellerStatusInfo
import com.velnox.core.ui.component.SellerStatusBadge
import com.velnox.core.ui.component.VerificationBadge
import com.velnox.core.ui.component.VelnoxBannerTone
import com.velnox.core.ui.component.VelnoxCard
import com.velnox.core.ui.component.VelnoxErrorState
import com.velnox.core.ui.component.VelnoxLoadingState
import com.velnox.core.ui.component.VelnoxMessageBanner
import com.velnox.core.ui.component.VelnoxPrimaryButton
import com.velnox.core.ui.component.VelnoxScaffold
import com.velnox.core.ui.component.VelnoxSecondaryButton
import com.velnox.core.ui.component.VelnoxSectionHeader
import com.velnox.core.ui.feature.auth.AuthViewModel
import com.velnox.core.ui.theme.VelnoxColors
import com.velnox.core.ui.theme.VelnoxTokens
import com.velnox.velseller.R
import com.velnox.velseller.navigation.SellerWorkspace

/**
 * Decides what a signed-in user sees: the seller workspace, the application form, or
 * the state of an application under review.
 *
 * The gate is separate from the workspace's own navigation graph, so the four
 * onboarding states cannot be reached once a seller is approved, and an approved seller
 * never sees a form they do not need.
 */
@Composable
fun SellerWorkspaceGate(
    navController: NavHostController,
    viewModel: SellerGateViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Local, not in the ViewModel: which of the two pre-approval screens is showing is
    // pure presentation, and losing it on process death simply returns the user to the
    // status screen.
    var showingApplication by remember { mutableStateOf(false) }

    when (val gate = state.gate) {
        SellerGateState.Loading -> VelnoxLoadingState(modifier = Modifier.fillMaxSize())

        is SellerGateState.Failure -> VelnoxErrorState(
            error = gate.error,
            onRetry = viewModel::refresh,
            modifier = Modifier.fillMaxSize(),
        )

        SellerGateState.Approved -> SellerWorkspace(navController = navController)

        SellerGateState.NoApplication -> if (showingApplication) {
            SellerApplicationScreen(
                onSubmitted = {
                    showingApplication = false
                    viewModel.refresh()
                },
                onBack = { showingApplication = false },
            )
        } else {
            SellerStatusScreen(
                info = null,
                canApply = true,
                onApply = { showingApplication = true },
                onRefresh = viewModel::refresh,
            )
        }

        is SellerGateState.NotApproved -> if (showingApplication) {
            SellerApplicationScreen(
                onSubmitted = {
                    showingApplication = false
                    viewModel.refresh()
                },
                onBack = { showingApplication = false },
            )
        } else {
            SellerStatusScreen(
                info = gate.info,
                // Only the states the seller lifecycle allows to resubmit; a suspended
                // seller is refused by the backend, and offering the form anyway would
                // be a lie about what is possible.
                canApply = gate.info.canReapply,
                onApply = { showingApplication = true },
                onRefresh = viewModel::refresh,
            )
        }
    }
}

/**
 * The seller application's state, told plainly.
 *
 * A rejection or correction request shows the reason the backend recorded, because the
 * applicant is entitled to it and "not approved" alone is unactionable. `null` [info]
 * means no application exists yet — that is the onboarding explanation, not an error.
 */
@Composable
private fun SellerStatusScreen(
    info: SellerStatusInfo?,
    canApply: Boolean,
    onApply: () -> Unit,
    onRefresh: () -> Unit,
    authViewModel: AuthViewModel = hiltViewModel(),
) {
    // Pre-approval there is no workspace, so this screen is the only place a pending or
    // rejected applicant can sign out from.
    val body = when (info?.status) {
        null -> stringResource(R.string.velseller_status_none_body)
        SellerStatus.Pending -> stringResource(R.string.velseller_status_pending_body)
        SellerStatus.UnderReview -> stringResource(R.string.velseller_status_under_review_body)
        SellerStatus.NeedsCorrection -> stringResource(R.string.velseller_status_needs_correction_body)
        SellerStatus.Rejected -> stringResource(R.string.velseller_status_rejected_body)
        SellerStatus.Suspended -> stringResource(R.string.velseller_status_suspended_body)
        SellerStatus.Approved -> stringResource(R.string.velseller_status_none_body)
        SellerStatus.Unknown -> stringResource(R.string.velnox_seller_unknown)
    }

    val reason = info?.correctionReason ?: info?.rejectionReason

    VelnoxScaffold(
        title = stringResource(R.string.velseller_status_title),
        tabs = emptyList(),
        currentRoute = "",
        onNavigate = {},
    ) { insets ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(insets)
                .verticalScroll(rememberScrollState())
                .padding(
                    start = VelnoxTokens.spacing.screenHorizontal,
                    end = VelnoxTokens.spacing.screenHorizontal,
                    top = VelnoxTokens.spacing.gap,
                    bottom = VelnoxTokens.spacing.screenBottom,
                ),
            verticalArrangement = Arrangement.spacedBy(VelnoxTokens.spacing.gap),
        ) {
            VelnoxCard(modifier = Modifier.fillMaxWidth()) {
                VelnoxSectionHeader(
                    title = if (info == null) {
                        stringResource(R.string.velseller_status_none_title)
                    } else {
                        stringResource(R.string.velseller_status_title)
                    },
                )

                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = VelnoxColors.OnSurfaceMuted,
                    modifier = Modifier.padding(top = VelnoxTokens.spacing.gapSmall),
                )

                if (info != null) {
                    Row(
                        modifier = Modifier.padding(top = VelnoxTokens.spacing.gapSmall),
                        horizontalArrangement = Arrangement.spacedBy(VelnoxTokens.spacing.gapSmall),
                    ) {
                        SellerStatusBadge(status = info.status)
                        VerificationBadge(status = info.verificationStatus)
                    }
                }
            }

            reason?.takeIf { it.isNotBlank() }?.let { message ->
                VelnoxMessageBanner(
                    message = stringResource(R.string.velseller_status_reason, message),
                    tone = VelnoxBannerTone.Warning,
                )
            }

            if (canApply) {
                VelnoxPrimaryButton(
                    text = if (info == null) {
                        stringResource(R.string.velseller_status_none_title)
                    } else {
                        stringResource(R.string.velseller_status_reapply)
                    },
                    onClick = onApply,
                )
            }

            VelnoxSecondaryButton(
                text = stringResource(R.string.velnox_action_refresh),
                onClick = onRefresh,
            )

            TextButton(onClick = authViewModel::signOut) {
                Text(
                    text = stringResource(R.string.velnox_action_sign_out),
                    color = VelnoxColors.OnSurfaceMuted,
                )
            }
        }
    }
}
