package com.checkingcontainer.feature.settings.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.checkingcontainer.feature.settings.SettingsRoute

const val SETTINGS_ROUTE = "settings"

fun NavGraphBuilder.settingsScreen(
    navController: NavHostController,
    isAdmin: Boolean = false,
    onUsersClick: () -> Unit,
    onClientsClick: () -> Unit,
) {
    composable(route = SETTINGS_ROUTE) {
        SettingsRoute(
            onBack = { navController.popBackStack() },
            isAdmin = isAdmin,
            onUsersClick = onUsersClick,
            onClientsClick = onClientsClick,
        )
    }
}
