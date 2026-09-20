package com.velnox.velcenter.presentation.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.velnox.core.ui.component.VelnoxAvatar
import com.velnox.core.ui.component.VelnoxCard
import com.velnox.core.ui.component.VelnoxChip
import com.velnox.core.ui.component.VelnoxDivider
import com.velnox.core.ui.component.VelnoxInfoRow
import com.velnox.core.ui.component.VelnoxScaffold
import com.velnox.core.ui.component.VelnoxSecondaryButton
import com.velnox.core.ui.component.VelnoxSectionHeader
import com.velnox.core.ui.feature.auth.AuthViewModel
import com.velnox.core.ui.theme.VelnoxColors
import com.velnox.core.ui.theme.VelnoxTokens
import com.velnox.core.ui.R as SharedR
import com.velnox.velcenter.R
import com.velnox.velcenter.navigation.VelCenterTabRoutes
import com.velnox.velcenter.navigation.velCenterTabs

/**
 * The signed-in operator.
 *
 * The identity and the permission set shown here come from the backend's own
 * `/api/auth/me` response — the app has no second source of truth for "what may I do".
 * The note on the card states the rule the whole suite follows: hiding a control is a
 * usability choice, never a security boundary, because every `/api/admin/...` route
 * re-resolves the caller's role and permission code server-side.
 */
@Composable
fun StaffAccountScreen(
    onNavigateTab: (String) -> Unit,
    authViewModel: AuthViewModel = hiltViewModel(),
) {
    val authState by authViewModel.authState.collectAsStateWithLifecycle()
    var confirmSignOut by remember { mutableStateOf(false) }

    val user = (authState as? AuthState.Authenticated)?.user

    VelnoxScaffold(
        title = stringResource(R.string.velcenter_account_title),
        tabs = velCenterTabs(),
        currentRoute = VelCenterTabRoutes.ACCOUNT,
        onNavigate = onNavigateTab,
    ) { insets ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(insets)
                .padding(
                    start = VelnoxTokens.spacing.screenHorizontal,
                    end = VelnoxTokens.spacing.screenHorizontal,
                    top = VelnoxTokens.spacing.gap,
                    bottom = VelnoxTokens.spacing.screenBottom,
                ),
            verticalArrangement = Arrangement.spacedBy(VelnoxTokens.spacing.gap),
        ) {
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

            val notSet = stringResource(R.string.velcenter_account_not_set)

            VelnoxCard(modifier = Modifier.fillMaxWidth()) {
                VelnoxSectionHeader(title = stringResource(R.string.velcenter_account_role))
                VelnoxInfoRow(
                    label = stringResource(R.string.velcenter_account_role),
                    value = user?.role?.let { roleLabel(it) } ?: notSet,
                )
                VelnoxInfoRow(
                    label = stringResource(R.string.velcenter_account_department),
                    value = user?.department?.takeIf { it.isNotBlank() } ?: notSet,
                )
                VelnoxInfoRow(
                    label = stringResource(R.string.velcenter_account_permissions),
                    // The effective set the backend resolved, which the owner role also
                    // flows through (`resolvePermissions`).
                    value = (user?.permissions?.size ?: 0).toString(),
                )

                VelnoxDivider(modifier = Modifier.padding(vertical = VelnoxTokens.spacing.gapSmall))

                Text(
                    text = stringResource(R.string.velcenter_account_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = VelnoxColors.OnSurfaceMuted,
                )
            }

            VelnoxSecondaryButton(
                text = stringResource(SharedR.string.velnox_action_sign_out),
                onClick = { confirmSignOut = true },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text(stringResource(R.string.velcenter_sign_out_title)) },
            text = { Text(stringResource(R.string.velcenter_sign_out_body)) },
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

/** Localised role, from the shared string set. */
@Composable
private fun roleLabel(role: VelnoxRole): String = when (role) {
    VelnoxRole.Customer -> stringResource(SharedR.string.velnox_role_customer)
    VelnoxRole.Seller -> stringResource(SharedR.string.velnox_role_seller)
    VelnoxRole.Admin -> stringResource(SharedR.string.velnox_role_admin)
    VelnoxRole.Owner -> stringResource(SharedR.string.velnox_role_owner)
    VelnoxRole.Staff -> stringResource(SharedR.string.velnox_role_staff)
    VelnoxRole.Unknown -> stringResource(SharedR.string.velnox_role_unknown)
}

