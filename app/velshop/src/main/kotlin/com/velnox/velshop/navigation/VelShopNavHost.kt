package com.velnox.velshop.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.velnox.core.ui.feature.auth.RoleRequirement
import com.velnox.core.ui.feature.auth.VelnoxSessionGate
import com.velnox.velshop.R
import com.velnox.velshop.presentation.account.AccountScreen
import com.velnox.velshop.presentation.browse.BrowseScreen
import com.velnox.velshop.presentation.cart.CartScreen
import com.velnox.velshop.presentation.checkout.CheckoutScreen
import com.velnox.velshop.presentation.orders.OrdersScreen
import com.velnox.velshop.presentation.product.ProductDetailScreen

/** Full route table. The tab destinations live in [VelShopTabRoutes]. */
object VelShopRoutes {
    const val ProductDetail = "product/{productId}"
    const val Checkout = "checkout"

    fun productDetail(productId: String) = "product/$productId"
}

/**
 * The whole graph, behind [VelnoxSessionGate].
 *
 * Velshop's catalogue is technically public on the web, but the app's primary use is
 * the signed-in journey (cart, checkout, orders) and the session cookie is what the
 * backend's rate limiter counts against. Requiring sign-in up front keeps one code
 * path instead of two versions of every screen — one that must tolerate a null user
 * and one that must not.
 */
@Composable
fun VelShopNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    VelnoxSessionGate(
        authTitle = stringResource(R.string.velshop_home_title),
        requiredRole = RoleRequirement.AnyAuthenticatedUser,
        modifier = modifier.fillMaxSize(),
    ) {
        NavHost(
            navController = navController,
            startDestination = VelShopTabRoutes.BROWSE,
            modifier = Modifier.fillMaxSize(),
        ) {
            composable(VelShopTabRoutes.BROWSE) {
                BrowseScreen(
                    onOpenProduct = { productId ->
                        navController.navigate(VelShopRoutes.productDetail(productId))
                    },
                    onNavigateTab = navController::navigateToVelShopTab,
                )
            }

            composable(
                route = VelShopRoutes.ProductDetail,
                arguments = listOf(navArgument("productId") { type = NavType.StringType }),
            ) { entry ->
                ProductDetailScreen(
                    productId = entry.arguments?.getString("productId").orEmpty(),
                    onBack = { navController.popBackStack() },
                    onNavigateTab = navController::navigateToVelShopTab,
                )
            }

            composable(VelShopTabRoutes.CART) {
                CartScreen(
                    onCheckout = { navController.navigate(VelShopRoutes.Checkout) },
                    onNavigateTab = navController::navigateToVelShopTab,
                )
            }

            composable(VelShopRoutes.Checkout) {
                CheckoutScreen(
                    onBack = { navController.popBackStack() },
                    onOrderPlaced = {
                        // Drop checkout (and the consumed cart screen) so Back from the
                        // order list cannot land on a screen for an order that already
                        // exists.
                        navController.popBackStack(VelShopTabRoutes.BROWSE, inclusive = false)
                        navController.navigate(VelShopTabRoutes.ORDERS)
                    },
                )
            }

            composable(VelShopTabRoutes.ORDERS) {
                OrdersScreen(
                    onOpenCart = { navController.navigateToVelShopTab(VelShopTabRoutes.CART) },
                    onNavigateTab = navController::navigateToVelShopTab,
                )
            }

            composable(VelShopTabRoutes.ACCOUNT) {
                AccountScreen(onNavigateTab = navController::navigateToVelShopTab)
            }
        }
    }
}
