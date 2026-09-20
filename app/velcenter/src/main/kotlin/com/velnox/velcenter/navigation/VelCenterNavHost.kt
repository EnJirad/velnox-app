package com.velnox.velcenter.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.velnox.core.ui.feature.auth.RoleRequirement
import com.velnox.core.ui.feature.auth.VelnoxSessionGate
import com.velnox.velcenter.R
import com.velnox.velcenter.presentation.account.StaffAccountScreen
import com.velnox.velcenter.presentation.dashboard.DashboardScreen
import com.velnox.velcenter.presentation.moderation.ModerationScreen
import com.velnox.velcenter.presentation.orders.CenterOrdersScreen
import com.velnox.velcenter.presentation.sellers.SellersScreen

/**
 * VelCenter's graph.
 *
 * Unlike Velseller, this app **does** gate on the role: staff accounts are created by an
 * owner (`POST /api/admin/employees`), never self-service, so "no centre role" means
 * "this app is not for you" and the gate says so explicitly instead of showing a broken
 * screen.
 *
 * `allowStaffSignIn = true` enables the password form, because an operator cannot use
 * the Google button — their account has a password hash from the employee flow.
 * `POST /api/auth/member-login` stays authoritative about who may sign in, and the
 * client adds no bypass.
 */
@Composable
fun VelCenterNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    VelnoxSessionGate(
        authTitle = stringResource(R.string.velcenter_auth_title),
        allowStaffSignIn = true,
        requiredRole = RoleRequirement.CenterStaff,
        modifier = modifier.fillMaxSize(),
    ) {
        NavHost(
            navController = navController,
            startDestination = VelCenterTabRoutes.DASHBOARD,
            modifier = Modifier.fillMaxSize(),
        ) {
            composable(VelCenterTabRoutes.DASHBOARD) {
                DashboardScreen(onNavigateTab = navController::navigateToVelCenterTab)
            }

            composable(VelCenterTabRoutes.SELLERS) {
                SellersScreen(onNavigateTab = navController::navigateToVelCenterTab)
            }

            composable(VelCenterTabRoutes.MODERATION) {
                ModerationScreen(onNavigateTab = navController::navigateToVelCenterTab)
            }

            composable(VelCenterTabRoutes.ORDERS) {
                CenterOrdersScreen(onNavigateTab = navController::navigateToVelCenterTab)
            }

            composable(VelCenterTabRoutes.ACCOUNT) {
                StaffAccountScreen(onNavigateTab = navController::navigateToVelCenterTab)
            }
        }
    }
}
