package com.checkingcontainer.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.ui.graphics.vector.ImageVector
import com.checkingcontainer.feature.announcements.navigation.ANNOUNCEMENTS_LIST_ROUTE
import com.checkingcontainer.feature.units.navigation.ESTIMADOS_LIST_ROUTE
import com.checkingcontainer.feature.units.navigation.UNITS_ROUTE

enum class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
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
    Estimados(
        route = ESTIMADOS_LIST_ROUTE,
        label = "Estimados",
        icon = Icons.Outlined.Assignment,
    );
}
