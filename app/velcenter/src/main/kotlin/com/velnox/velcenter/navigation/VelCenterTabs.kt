package com.velnox.velcenter.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SpaceDashboard
import androidx.compose.material.icons.outlined.FactCheck
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import com.velnox.core.ui.component.VelnoxTabItem
import com.velnox.velcenter.R

/** VelCenter's five destinations — the four queues plus the staff account. */
object VelCenterTabRoutes {
    const val DASHBOARD = "dashboard"
    const val SELLERS = "sellers"
    const val MODERATION = "moderation"
    const val ORDERS = "orders"
    const val ACCOUNT = "account"
}

/**
 * The one tab list for VelCenter.
 *
 * Five items is the maximum a bottom bar should carry, which is exactly why the
 * remaining operational surfaces (users, employees, categories, settings, audit log)
 * are deliberately **not** tabs: they are lower-frequency, and cramming them into the
 * bar would make the four things an operator does every day harder to hit. They remain
 * available on the web client, which is the right shape for them.
 *
 * No badges: control-room counts belong on the dashboard, where they are read with
 * context, not on a tab that cannot explain what it is counting.
 */
@Composable
fun velCenterTabs(): List<VelnoxTabItem> = listOf(
    VelnoxTabItem(
        route = VelCenterTabRoutes.DASHBOARD,
        label = stringResource(R.string.velcenter_tab_dashboard),
        icon = Icons.Filled.SpaceDashboard,
    ),
    VelnoxTabItem(
        route = VelCenterTabRoutes.SELLERS,
        label = stringResource(R.string.velcenter_tab_sellers),
        icon = Icons.Outlined.Groups,
    ),
    VelnoxTabItem(
        route = VelCenterTabRoutes.MODERATION,
        label = stringResource(R.string.velcenter_tab_moderation),
        icon = Icons.Outlined.FactCheck,
    ),
    VelnoxTabItem(
        route = VelCenterTabRoutes.ORDERS,
        label = stringResource(R.string.velcenter_tab_orders),
        icon = Icons.Outlined.ReceiptLong,
    ),
    VelnoxTabItem(
        route = VelCenterTabRoutes.ACCOUNT,
        label = stringResource(R.string.velcenter_tab_account),
        icon = Icons.Outlined.Person,
    ),
)

/** Switches tabs without growing the back stack — see the VelShop equivalent. */
fun NavHostController.navigateToVelCenterTab(route: String) {
    if (currentDestination?.route == route) return
    if (!popBackStack(route, inclusive = false)) {
        navigate(route)
    }
}
