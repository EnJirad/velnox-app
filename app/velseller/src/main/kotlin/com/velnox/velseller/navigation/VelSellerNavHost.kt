package com.velnox.velseller.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.velnox.core.ui.feature.auth.RoleRequirement
import com.velnox.core.ui.feature.auth.VelnoxSessionGate
import com.velnox.velseller.R
import com.velnox.velseller.presentation.account.ShopAccountScreen
import com.velnox.velseller.presentation.onboarding.SellerWorkspaceGate
import com.velnox.velseller.presentation.orders.SellerOrdersScreen
import com.velnox.velseller.presentation.overview.OverviewScreen
import com.velnox.velseller.presentation.products.ProductsScreen

/**
 * Velseller's entry point: the session gate, then the seller-status gate, then the
 * workspace.
 *
 * ## Why the role requirement is "any authenticated user"
 *
 * Velseller's front door is the **seller application**, which any signed-in Velnox user
 * may submit — a customer becomes a seller by applying, so gating the app on the
 * `seller` role would make applying impossible. The role check that matters is
 * server-side: every `/api/seller/...` endpoint resolves `user → seller → shop` and
 * refuses a non-seller, and [SellerWorkspaceGate] only shows the workspace when
 * `GET /api/seller/status` reports an approved seller.
 *
 * VelCenter is different: staff accounts are created by an owner, so that app does gate
 * on the role.
 */
@Composable
fun VelSellerNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    VelnoxSessionGate(
        authTitle = stringResource(R.string.velseller_auth_title),
        requiredRole = RoleRequirement.AnyAuthenticatedUser,
        modifier = modifier.fillMaxSize(),
    ) {
        SellerWorkspaceGate(navController = navController)
    }
}

/**
 * The approved-seller workspace.
 *
 * Only reachable through [SellerWorkspaceGate], so no screen here has to consider an
 * unapproved seller.
 */
@Composable
fun SellerWorkspace(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = VelSellerTabRoutes.OVERVIEW,
        modifier = modifier.fillMaxSize(),
    ) {
        composable(VelSellerTabRoutes.OVERVIEW) {
            OverviewScreen(onNavigateTab = navController::navigateToVelSellerTab)
        }

        composable(VelSellerTabRoutes.PRODUCTS) {
            ProductsScreen(onNavigateTab = navController::navigateToVelSellerTab)
        }

        composable(VelSellerTabRoutes.ORDERS) {
            SellerOrdersScreen(onNavigateTab = navController::navigateToVelSellerTab)
        }

        composable(VelSellerTabRoutes.ACCOUNT) {
            ShopAccountScreen(onNavigateTab = navController::navigateToVelSellerTab)
        }
    }
}
