package com.checkingcontainer.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Group
import androidx.compose.ui.graphics.vector.ImageVector
import com.checkingcontainer.core.model.UserRole
import com.checkingcontainer.feature.announcements.navigation.ANNOUNCEMENTS_LIST_ROUTE
import com.checkingcontainer.feature.units.navigation.UNITS_ROUTE
import com.checkingcontainer.feature.users.navigation.USERS_LIST_ROUTE

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
    Units(
        route = UNITS_ROUTE,
        label = "Unidades",
        icon = Icons.Outlined.AcUnit,
    ),
    Users(
        route = USERS_LIST_ROUTE,
        label = "Usuarios",
        icon = Icons.Outlined.Group,
        adminOnly = true,
    );

    companion object {
        fun forRole(role: UserRole): List<TopLevelDestination> =
            if (role.isAdmin) listOf(Announcements, Units, Users)
            else listOf(Announcements, Units)
    }
}