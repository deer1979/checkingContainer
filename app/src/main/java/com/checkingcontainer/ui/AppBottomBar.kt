package com.checkingcontainer.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import com.checkingcontainer.navigation.TopLevelDestination

@Composable
fun AppBottomBar(
    destinations: Collection<TopLevelDestination>,
    currentRoute: String?,
    onSelect: (TopLevelDestination) -> Unit,
    unreadAnnouncements: Int = 0,
    openEstimados: Int = 0,
) {
    NavigationBar(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .shadow(elevation = 8.dp, shape = RoundedCornerShape(28.dp))
            .clip(RoundedCornerShape(28.dp)),
        windowInsets = WindowInsets(0),
    ) {
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
