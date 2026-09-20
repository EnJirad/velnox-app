package com.velnox.core.ui.component

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Badge
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import com.velnox.core.ui.theme.VelnoxColors

/**
 * One navigation item for the bottom bar.
 *
 * Mirrors the web `MobileTabBar` (`packages/shared/src/components/MobileTabBar.tsx`):
 * icon, label, and an optional badge for the cart or notification count, with the
 * emerald accent on the active item.
 */
data class VelnoxTabItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val badgeCount: Int = 0,
)

/**
 * The app bar + tab bar shell, shared so the three apps cannot drift apart visually.
 *
 * `Scaffold` is used rather than a hand-rolled layout because it applies the system
 * insets — status bar, cutout and gesture navigation — which is exactly the kind of
 * "desktop layout squeezed onto a phone" problem the brief warns about.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VelnoxScaffold(
    title: String,
    tabs: List<VelnoxTabItem>,
    currentRoute: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    topBarActions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        containerColor = VelnoxColors.Background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.Filled.ArrowBack,
                                contentDescription = null,
                                tint = VelnoxColors.OnSurface,
                            )
                        }
                    }
                },
                actions = topBarActions,
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = VelnoxColors.Surface,
                    titleContentColor = VelnoxColors.OnSurface,
                ),
            )
        },
        bottomBar = {
            if (tabs.isNotEmpty()) {
                NavigationBar(containerColor = VelnoxColors.Surface) {
                    tabs.forEach { item ->
                        NavigationBarItem(
                            selected = currentRoute == item.route,
                            onClick = { if (currentRoute != item.route) onNavigate(item.route) },
                            icon = {
                                if (item.badgeCount > 0) {
                                    Badge { Text(item.badgeCount.coerceAtMost(99).toString()) }
                                }
                                Icon(imageVector = item.icon, contentDescription = null)
                            },
                            label = { Text(item.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        )
                    }
                }
            }
        },
        content = content,
    )
}
