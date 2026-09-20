package com.velnox.velseller.presentation.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
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
import com.velnox.core.auth.model.AuthState
import com.velnox.core.data.model.SellerProfile
import com.velnox.core.ui.component.SellerStatusBadge
import com.velnox.core.ui.component.VerificationBadge
import com.velnox.core.ui.component.VelnoxAvatar
import com.velnox.core.ui.component.VelnoxCard
import com.velnox.core.ui.component.VelnoxDivider
import com.velnox.core.ui.component.VelnoxInfoRow
import com.velnox.core.ui.component.VelnoxScaffold
import com.velnox.core.ui.component.VelnoxSecondaryButton
import com.velnox.core.ui.component.VelnoxSectionHeader
import com.velnox.core.ui.component.VelnoxStateHost
import com.velnox.core.ui.feature.auth.AuthViewModel
import com.velnox.core.ui.theme.VelnoxColors
import com.velnox.core.ui.theme.VelnoxTokens
import com.velnox.velseller.R
import com.velnox.velseller.navigation.VelSellerTabRoutes
import com.velnox.velseller.navigation.velSellerTabs

/**
 * The seller's shop account.
 *
 * Signed-in identity comes from the session (`/api/auth/me`); the shop record comes from
 * `/api/seller/profile`. Sign-out is confirmed because it revokes the session
 * server-side, and it matters more here than in VelShop: a shared device left signed in
 * is a shop left open.
 */
@Composable
fun ShopAccountScreen(
    onNavigateTab: (String) -> Unit,
    authViewModel: AuthViewModel = hiltViewModel(),
    viewModel: ShopAccountViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val authState by authViewModel.authState.collectAsStateWithLifecycle()
    var confirmSignOut by remember { mutableStateOf(false) }

    val user = (authState as? AuthState.Authenticated)?.user

    VelnoxScaffold(
        title = stringResource(R.string.velseller_account_title),
        tabs = velSellerTabs(),
        currentRoute = VelSellerTabRoutes.ACCOUNT,
        onNavigate = onNavigateTab,
    ) { insets ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(insets),
        ) {
            VelnoxStateHost(
                state = state.result,
                onRetry = viewModel::load,
            ) { profile, _ ->
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
                        VelnoxCard(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(VelnoxTokens.spacing.gap),
                            ) {
                                VelnoxAvatar(
                                    url = profile.primaryShop?.logoUrl,
                                    displayName = shopTitle(profile, fallback = user?.displayName.orEmpty()),
                                    initials = user?.initials.orEmpty(),
                                    size = 56.dp,
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = shopTitle(profile, fallback = user?.displayName.orEmpty()),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = VelnoxColors.OnSurface,
                                    )
                                    Text(
                                        text = user?.email.orEmpty(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = VelnoxColors.OnSurfaceMuted,
                                    )
                                }
                            }
                        }
                    }

                    item {
                        VelnoxCard(modifier = Modifier.fillMaxWidth()) {
                            VelnoxSectionHeader(title = stringResource(R.string.velseller_account_shop))

                            VelnoxInfoRow(
                                label = stringResource(R.string.velseller_account_shop_name),
                                value = profile.primaryShop?.name
                                    ?: profile.seller?.name
                                    ?: stringResource(R.string.velseller_account_not_set),
                            )
                            VelnoxInfoRow(
                                label = stringResource(R.string.velseller_account_shop_phone),
                                value = profile.primaryShop?.phone?.takeIf { it.isNotBlank() }
                                    ?: stringResource(R.string.velseller_account_not_set),
                            )
                            VelnoxInfoRow(
                                label = stringResource(R.string.velseller_account_address),
                                value = profile.primaryShop?.address?.takeIf { it.isNotBlank() }
                                    ?: stringResource(R.string.velseller_account_not_set),
                            )

                            VelnoxDivider(modifier = Modifier.padding(vertical = VelnoxTokens.spacing.gapSmall))

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(VelnoxTokens.spacing.gapSmall),
                            ) {
                                profile.seller?.status?.let { status -> SellerStatusBadge(status = status) }
                                profile.seller?.verificationStatus?.let { verification ->
                                    VerificationBadge(status = verification)
                                }
                            }

                            Text(
                                text = stringResource(R.string.velseller_account_manage_on_web),
                                style = MaterialTheme.typography.bodySmall,
                                color = VelnoxColors.OnSurfaceMuted,
                                modifier = Modifier.padding(top = VelnoxTokens.spacing.gapSmall),
                            )
                        }
                    }

                    item {
                        VelnoxSecondaryButton(
                            text = stringResource(R.string.velnox_action_sign_out),
                            onClick = { confirmSignOut = true },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text(stringResource(R.string.velseller_sign_out_title)) },
            text = { Text(stringResource(R.string.velseller_sign_out_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmSignOut = false
                        authViewModel.signOut()
                    },
                ) {
                    Text(
                        text = stringResource(R.string.velnox_action_sign_out),
                        color = VelnoxColors.Destructive,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmSignOut = false }) {
                    Text(stringResource(R.string.velnox_action_cancel))
                }
            },
        )
    }
}

/** The shop name, falling back to the signed-in name so the header is never blank. */
private fun shopTitle(profile: SellerProfile, fallback: String): String =
    profile.primaryShop?.name?.takeIf { it.isNotBlank() }
        ?: profile.seller?.name?.takeIf { it.isNotBlank() }
        ?: fallback
