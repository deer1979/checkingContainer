package com.checkingcontainer.ui

import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.checkingcontainer.navigation.TopLevelDestination

@Composable
fun AppBottomBar(
    destinations: Collection<TopLevelDestination>,
    currentRoute: String?,
    onSelect: (TopLevelDestination) -> Unit,
    unreadAnnouncements: Int = 0,
    openEstimados: Int = 0,
) {
    NavigationBar {
        destinations.forEach { dest ->
            val badgeCount = when (dest) {
                TopLevelDestination.Announcements -> unreadAnnouncements
                TopLevelDestination.Estimados -> openEstimados
                else -> 0
            }
            NavigationBarItem(
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
