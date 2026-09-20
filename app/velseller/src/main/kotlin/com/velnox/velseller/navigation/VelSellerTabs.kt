package com.velnox.velseller.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import com.velnox.core.ui.component.VelnoxTabItem
import com.velnox.velseller.R

/** The seller workspace's four destinations. */
object VelSellerTabRoutes {
    const val OVERVIEW = "overview"
    const val PRODUCTS = "products"
    const val ORDERS = "orders"
    const val ACCOUNT = "account"
}

/**
 * The one tab list for Velseller.
 *
 * Defined once so the tabs cannot drift: a screen that built its own list is exactly
 * how a tab ends up present in the bar with no destination.
 *
 * There is deliberately **no badge** on the orders tab. A badge needs a count that is
 * authoritative for the whole seller, and the only endpoint that provides one is the
 * order list itself — duplicating it into a second flow would let the badge disagree
 * with the list it points at. The orders screen shows the same information where it
 * can be trusted.
 */
@Composable
fun velSellerTabs(): List<VelnoxTabItem> = listOf(
    VelnoxTabItem(
        route = VelSellerTabRoutes.OVERVIEW,
        label = stringResource(R.string.velseller_tab_overview),
        icon = Icons.Filled.Dashboard,
    ),
    VelnoxTabItem(
        route = VelSellerTabRoutes.PRODUCTS,
        label = stringResource(R.string.velseller_tab_products),
        icon = Icons.Outlined.Inventory2,
    ),
    VelnoxTabItem(
        route = VelSellerTabRoutes.ORDERS,
        label = stringResource(R.string.velseller_tab_orders),
        icon = Icons.Outlined.ReceiptLong,
    ),
    VelnoxTabItem(
        route = VelSellerTabRoutes.ACCOUNT,
        label = stringResource(R.string.velseller_tab_account),
        icon = Icons.Outlined.Person,
    ),
)

/**
 * Switches tabs without growing the back stack.
 *
 * Tapping the same tab twice must not push a second copy, and returning to a visited
 * tab must not recreate it (which would discard its scroll position and re-issue its
 * requests). Only the arg-free overloads of `navigate`/`popBackStack` are used, so the
 * call site never depends on a particular navigation version's option builder.
 */
fun NavHostController.navigateToVelSellerTab(route: String) {
    if (currentDestination?.route == route) return
    if (!popBackStack(route, inclusive = false)) {
        navigate(route)
    }
}
