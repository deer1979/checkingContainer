package com.example.myapplication.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.testo3.core.model.UserRole
import com.testo3.feature.admin.navigation.ADMIN_ROUTE
import com.testo3.feature.announcements.navigation.ANNOUNCEMENTS_LIST_ROUTE
import com.testo3.feature.settings.navigation.SETTINGS_ROUTE
import com.testo3.feature.tasks.navigation.TASKS_ROUTE

/**
 * Tabs in the authenticated bottom NavigationBar. The Admin tab only appears
 * for [UserRole.Admin] users; the public flow never reaches this list.
 */
enum class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val adminOnly: Boolean = false,
) {
    Announcements(
        route = ANNOUNCEMENTS_LIST_ROUTE,
        label = "Anuncios",
        icon = Icons.Outlined.Campaign,
    ),
    Admin(
        route = ADMIN_ROUTE,
        label = "Admin",
        icon = Icons.Outlined.AdminPanelSettings,
        adminOnly = true,
    ),
    Tasks(
        route = TASKS_ROUTE,
        label = "Tareas",
        icon = Icons.Outlined.Checklist,
    ),
    Settings(
        route = SETTINGS_ROUTE,
        label = "Ajustes",
        icon = Icons.Outlined.Settings,
    );

    companion object {
        fun forRole(role: UserRole): List<TopLevelDestination> = entries.filter {
            !it.adminOnly || role == UserRole.Admin
        }
    }
}
