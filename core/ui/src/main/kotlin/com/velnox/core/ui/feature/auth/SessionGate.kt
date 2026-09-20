package com.velnox.core.ui.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.velnox.core.auth.model.AuthState
import com.velnox.core.auth.model.VelnoxRole
import com.velnox.core.auth.model.VelnoxUser
import com.velnox.core.ui.R
import com.velnox.core.ui.component.VelnoxErrorState
import com.velnox.core.ui.component.VelnoxLoadingState
import com.velnox.core.ui.theme.VelnoxColors
import com.velnox.core.ui.theme.VelnoxTokens

/**
 * The one place an app decides whether to show its product or a sign-in screen.
 *
 * Using a single gate is what guarantees the three apps cannot disagree about what
 * "signed in" means, and it is what makes the four [AuthState] cases impossible to
 * collapse by accident:
 *
 *  * [AuthState.Restoring] → a loader, **not** the sign-in screen. Showing the login
 *    form while a valid session is still being validated is the flashing-login bug.
 *  * [AuthState.Unverified] → an offline-style retry screen. The session token is
 *    deliberately retained, so this is not a sign-out.
 *  * [AuthState.Unauthenticated] → the sign-in screen.
 *  * [AuthState.Authenticated] → the app, after passing the optional role check.
 *
 * The role check mirrors the web `RequireRole` wrapper: a customer who somehow reaches
 * Velseller or VelCenter sees an explicit "not permitted" message rather than a broken
 * screen, and every protected endpoint re-checks server-side anyway.
 */
@Composable
fun VelnoxSessionGate(
    authTitle: String,
    modifier: Modifier = Modifier,
    allowStaffSignIn: Boolean = false,
    requiredRole: RoleRequirement = RoleRequirement.AnyAuthenticatedUser,
    // Declared before `content` so the trailing lambda at every call site binds to
    // `content`. With the view model last, `VelnoxSessionGate(...) { … }` binds the
    // lambda to `viewModel` instead and no call site compiles.
    viewModel: AuthViewModel = hiltViewModel(),
    content: @Composable (VelnoxUser) -> Unit,
) {
    val state by viewModel.authState.collectAsStateWithLifecycle()

    when (val current = state) {
        AuthState.Restoring -> VelnoxLoadingState(modifier = modifier.fillMaxSize())

        is AuthState.Unverified -> VelnoxErrorState(
            error = current.error,
            onRetry = viewModel::retrySession,
            modifier = modifier.fillMaxSize(),
            fallbackMessage = null,
        )

        AuthState.Unauthenticated -> VelnoxAuthScreen(
            title = authTitle,
            modifier = modifier,
            allowStaffSignIn = allowStaffSignIn,
            viewModel = viewModel,
        )

        is AuthState.Authenticated -> {
            if (requiredRole.isSatisfiedBy(current.user)) {
                content(current.user)
            } else {
                RoleMismatchScreen(role = current.user.role)
            }
        }
    }
}

/** Which roles an app admits. */
sealed interface RoleRequirement {
    data object AnyAuthenticatedUser : RoleRequirement
    data object Seller : RoleRequirement
    data object CenterStaff : RoleRequirement

    fun isSatisfiedBy(user: VelnoxUser): Boolean = when (this) {
        AnyAuthenticatedUser -> true
        Seller -> user.role.canAccessSellerWorkspace
        CenterStaff -> user.role.canAccessCenter
    }
}

@Composable
private fun RoleMismatchScreen(role: VelnoxRole) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(VelnoxTokens.spacing.screenHorizontal),
        verticalArrangement = Arrangement.spacedBy(VelnoxTokens.spacing.gap, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.velnox_state_forbidden_title),
            style = MaterialTheme.typography.titleMedium,
            color = VelnoxColors.OnSurface,
        )
        Text(
            text = stringResource(R.string.velnox_state_forbidden_body) + " (${role.wireValue})",
            style = MaterialTheme.typography.bodyMedium,
            color = VelnoxColors.OnSurfaceMuted,
            textAlign = TextAlign.Center,
        )
    }
}
