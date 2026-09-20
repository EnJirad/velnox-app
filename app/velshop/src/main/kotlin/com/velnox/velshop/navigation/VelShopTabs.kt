package com.velnox.velshop.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import com.velnox.core.ui.component.VelnoxTabItem
import com.velnox.velshop.R

/** Bottom-tab destinations. */
object VelShopTabRoutes {
    const val BROWSE = "browse"
    const val CART = "cart"
    const val ORDERS = "orders"
    const val ACCOUNT = "account"
}

/**
 * The one tab list for the whole app.
 *
 * Defined once instead of per screen so the four tabs cannot drift: a screen that
 * built its own list is exactly how the cart badge ends up missing on three of four
 * screens, and how a tab ends up present in the bar but with no destination.
 */
@Composable
fun velShopTabs(cartCount: Int): List<VelnoxTabItem> = listOf(
    VelnoxTabItem(
        route = VelShopTabRoutes.BROWSE,
        label = stringResource(R.string.velshop_tab_home),
        icon = Icons.Outlined.Storefront,
    ),
    VelnoxTabItem(
        route = VelShopTabRoutes.CART,
        label = stringResource(R.string.velshop_tab_cart),
        icon = Icons.Filled.ShoppingCart,
        badgeCount = cartCount,
    ),
    VelnoxTabItem(
        route = VelShopTabRoutes.ORDERS,
        label = stringResource(R.string.velshop_tab_orders),
        icon = Icons.Outlined.Receipt,
    ),
    VelnoxTabItem(
        route = VelShopTabRoutes.ACCOUNT,
        label = stringResource(R.string.velshop_tab_account),
        icon = Icons.Outlined.Person,
    ),
)

/**
 * Switches tabs without growing the back stack.
 *
 * Tapping the same tab twice must not push a second copy of it, and returning to a
 * tab the user already visited must not re-create its screen from scratch (which
 * would throw away its scroll position and re-issue its requests). So the existing
 * entry is popped back to when present, and only otherwise is a new one pushed.
 *
 * Uses only the arg-free/route overloads of `navigate`/`popBackStack` deliberately:
 * they are stable members of `NavController`, so the call site never depends on which
 * `NavOptions` builder DSL the resolved navigation version exposes.
 */
fun NavHostController.navigateToVelShopTab(route: String) {
    if (currentDestination?.route == route) return
    if (!popBackStack(route, inclusive = false)) {
        navigate(route)
    }
}
