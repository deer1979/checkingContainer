package com.example.myapplication.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.testo3.feature.login.navigation.LOGIN_ROUTE
import com.testo3.feature.settings.navigation.SETTINGS_ROUTE
import com.testo3.feature.tasks.navigation.TASKS_ROUTE

/**
 * Top-level destinations rendered as items in the bottom NavigationBar.
 * Adding a new tab is one entry in this list — the scaffold reads from here
 * and the NavHost picks the matching `xxxScreen()` extension.
 */
enum class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    Tasks(
        route = TASKS_ROUTE,
        label = "Tareas",
        icon = Icons.Outlined.Checklist,
    ),
    Settings(
        route = SETTINGS_ROUTE,
        label = "Ajustes",
        icon = Icons.Outlined.Settings,
    ),
    Login(
        route = LOGIN_ROUTE,
        label = "Cuenta",
        icon = Icons.AutoMirrored.Outlined.Login,
    );

    companion object {
        val all = entries.toList()
    }
}
