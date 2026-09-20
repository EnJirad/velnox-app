package com.velnox.velshop.presentation.account

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
import com.velnox.core.auth.model.VelnoxRole
import com.velnox.core.common.util.VelnoxFormat
import com.velnox.core.data.model.CustomerProfile
import com.velnox.core.ui.component.VelnoxAvatar
import com.velnox.core.ui.component.VelnoxCard
import com.velnox.core.ui.component.VelnoxChip
import com.velnox.core.ui.component.VelnoxDivider
import com.velnox.core.ui.component.VelnoxInfoRow
import com.velnox.core.ui.component.VelnoxScaffold
import com.velnox.core.ui.component.VelnoxSecondaryButton
import com.velnox.core.ui.component.VelnoxSectionHeader
import com.velnox.core.ui.component.VelnoxStateHost
import com.velnox.core.ui.feature.auth.AuthViewModel
import com.velnox.core.ui.theme.VelnoxColors
import com.velnox.core.ui.theme.VelnoxTokens
import com.velnox.core.ui.R as SharedR
import com.velnox.velshop.R
import com.velnox.velshop.navigation.VelShopTabRoutes
import com.velnox.velshop.navigation.velShopTabs

/**
 * Account.
 *
 * The identity shown here is the one the backend confirmed (`GET /api/auth/me` via
 * [AuthViewModel]); the profile details come from `GET /api/customer/profile`. Neither
 * is cached into local state, and sign-out is confirmed first because it revokes the
 * session server-side as well as locally.
 */
@Composable
fun AccountScreen(
    onNavigateTab: (String) -> Unit,
    authViewModel: AuthViewModel = hiltViewModel(),
    viewModel: AccountViewModel = hiltViewModel(),
) {
    val authState by authViewModel.authState.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val cartCount by viewModel.cartCount.collectAsStateWithLifecycle()

    var confirmSignOut by remember { mutableStateOf(false) }

    val user = (authState as? AuthState.Authenticated)?.user

    VelnoxScaffold(
        title = stringResource(R.string.velshop_account_title),
        tabs = velShopTabs(cartCount),
        currentRoute = VelShopTabRoutes.ACCOUNT,
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
                        // Identity card: verified name, e-mail and role from the session.
                        VelnoxCard(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(VelnoxTokens.spacing.gap),
                            ) {
                                VelnoxAvatar(
                                    url = user?.avatarUrl,
                                    displayName = user?.displayName.orEmpty(),
                                    initials = user?.initials.orEmpty(),
                                    size = 56.dp,
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = user?.displayName.orEmpty(),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = VelnoxColors.OnSurface,
                                    )
                                    Text(
                                        text = user?.email.orEmpty(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = VelnoxColors.OnSurfaceMuted,
                                    )
                                    user?.role?.let { role ->
                                        VelnoxChip(
                                            text = roleLabel(role),
                                            background = VelnoxColors.EmeraldSurface,
                                            contentColor = VelnoxColors.EmeraldOnSurface,
                                            modifier = Modifier.padding(top = VelnoxTokens.spacing.gapTiny),
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item {
                        VelnoxCard(modifier = Modifier.fillMaxWidth()) {
                            VelnoxSectionHeader(title = stringResource(R.string.velshop_account_profile))
                            ProfileRows(profile = profile)
                        }
                    }

                    item {
                        VelnoxCard(modifier = Modifier.fillMaxWidth()) {
                            VelnoxSectionHeader(title = stringResource(R.string.velshop_account_summary))
                            VelnoxInfoRow(
                                label = stringResource(R.string.velshop_account_addresses),
                                value = state.addressCount?.toString() ?: VelnoxFormat.PLACEHOLDER,
                            )
                            VelnoxInfoRow(
                                label = stringResource(R.string.velshop_account_wishlist),
                                value = state.wishlistCount?.toString() ?: VelnoxFormat.PLACEHOLDER,
                            )
                            VelnoxDivider(modifier = Modifier.padding(top = VelnoxTokens.spacing.gapSmall))
                            Text(
                                text = stringResource(R.string.velshop_account_manage_on_web),
                                style = MaterialTheme.typography.bodySmall,
                                color = VelnoxColors.OnSurfaceMuted,
                                modifier = Modifier.padding(top = VelnoxTokens.spacing.gapSmall),
                            )
                        }
                    }

                    item {
                        VelnoxSecondaryButton(
                            text = stringResource(SharedR.string.velnox_action_sign_out),
                            onClick = { confirmSignOut = true },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    item {
                        Text(
                            text = stringResource(R.string.velshop_account_session_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = VelnoxColors.OnSurfaceMuted,
                        )
                    }
                }
            }
        }
    }

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text(stringResource(R.string.velshop_account_sign_out_title)) },
            text = { Text(stringResource(R.string.velshop_account_sign_out_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmSignOut = false
                        authViewModel.signOut()
                    },
                ) {
                    Text(
                        text = stringResource(SharedR.string.velnox_action_sign_out),
                        color = VelnoxColors.Destructive,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmSignOut = false }) {
                    Text(stringResource(SharedR.string.velnox_action_cancel))
                }
            },
        )
    }
}

@Composable
private fun ProfileRows(profile: CustomerProfile) {
    val missing = stringResource(R.string.velshop_account_not_set)

    VelnoxInfoRow(
        label = stringResource(R.string.velshop_account_name),
        value = profile.fullName.ifBlank { missing },
    )
    VelnoxInfoRow(
        label = stringResource(R.string.velshop_account_phone),
        value = profile.phone?.takeIf { it.isNotBlank() } ?: missing,
    )
    VelnoxInfoRow(
        label = stringResource(R.string.velshop_account_language),
        value = profile.preferredLanguage?.takeIf { it.isNotBlank() } ?: missing,
    )
}

/** Role label from the real `users.role` vocabulary. */
@Composable
private fun roleLabel(role: VelnoxRole): String = when (role) {
    VelnoxRole.Customer -> stringResource(SharedR.string.velnox_role_customer)
    VelnoxRole.Seller -> stringResource(SharedR.string.velnox_role_seller)
    VelnoxRole.Admin -> stringResource(SharedR.string.velnox_role_admin)
    VelnoxRole.Owner -> stringResource(SharedR.string.velnox_role_owner)
    VelnoxRole.Staff -> stringResource(SharedR.string.velnox_role_staff)
    VelnoxRole.Unknown -> stringResource(SharedR.string.velnox_role_unknown)
}
