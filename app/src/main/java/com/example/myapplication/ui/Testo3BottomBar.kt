package com.example.myapplication.ui

import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.example.myapplication.navigation.TopLevelDestination

@Composable
fun Testo3BottomBar(
    destinations: List<TopLevelDestination>,
    currentRoute: String?,
    onSelect: (TopLevelDestination) -> Unit,
) {
    NavigationBar {
        destinations.forEach { dest ->
            NavigationBarItem(
                selected = currentRoute == dest.route ||
                    currentRoute?.startsWith("${dest.route}/") == true,
                onClick = { onSelect(dest) },
                icon = { Icon(dest.icon, contentDescription = null) },
                label = { Text(dest.label) },
                alwaysShowLabel = true,
            )
        }
    }
}
