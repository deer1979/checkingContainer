package com.checkingcontainer.ui

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.checkingcontainer.navigation.TopLevelDestination

/**
 * M3 Adaptive: navegación lateral para anchos Medium/Expanded (tablets de
 * campo, plegables abiertos). Es el equivalente del [AppBottomBar] pill que
 * se usa en teléfonos — mismos destinos y badges.
 */
@Composable
fun AppNavigationRail(
    destinations: Collection<TopLevelDestination>,
    currentRoute: String?,
    onSelect: (TopLevelDestination) -> Unit,
    unreadAnnouncements: Int = 0,
    openEstimados: Int = 0,
) {
    NavigationRail(
        modifier = Modifier
            .fillMaxHeight()
            .statusBarsPadding(),
    ) {
        destinations.forEach { dest ->
            val badgeCount = when (dest) {
                TopLevelDestination.Announcements -> unreadAnnouncements
                TopLevelDestination.Estimados -> openEstimados
                else -> 0
            }
            NavigationRailItem(
                selected = currentRoute == dest.route ||
                    currentRoute?.startsWith("${dest.route}/") == true,
                onClick = { onSelect(dest) },
                icon = {
                    if (badgeCount > 0) {
                        BadgedBox(
                            badge = { Badge { Text(if (badgeCount > 99) "99+" else "$badgeCount") } },
                        ) {
                            Icon(dest.icon, contentDescription = null)
                        }
                    } else {
                        Icon(dest.icon, contentDescription = null)
                    }
                },
                label = { Text(dest.label) },
                alwaysShowLabel = true,
            )
        }
    }
}
