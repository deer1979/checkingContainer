package com.example.myapplication.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.testo3.core.model.UserRole
import com.testo3.feature.admin.navigation.ADMIN_ROUTE
import com.testo3.feature.announcements.navigation.ANNOUNCEMENTS_LIST_ROUTE
import com.testo3.feature.settings.navigation.SETTINGS_ROUTE
import com.testo3.feature.tasks.navigation.TASKS_ROUTE
import com.testo3.feature.units.navigation.UNITS_ROUTE
import com.testo3.feature.users.navigation.USERS_LIST_ROUTE

/**
 * Tabs in the authenticated bottom NavigationBar. Admin-only tabs are
 * filtered out for non-admin roles. The selection of which tab a tap lands
 * on is driven by [route]; nested screens (e.g. announcement detail, user
 * form) keep the tab highlighted via prefix-matching in the bottom bar.
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
    Users(
        route = USERS_LIST_ROUTE,
        label = "Usuarios",
        icon = Icons.Outlined.Group,
        adminOnly = true,
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
    Units(
        route = UNITS_ROUTE,
        label = "Unidades",
        icon = Icons.Outlined.AcUnit,
    ),
    Settings(
        route = SETTINGS_ROUTE,
        label = "Ajustes",
        icon = Icons.Outlined.Settings,
    );

    companion object {
        fun forRole(role: UserRole): List<TopLevelDestination> = entries.filter {
            !it.adminOnly || role.isAdmin
        }
    }
}
