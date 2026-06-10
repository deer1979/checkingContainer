package com.checkingcontainer.feature.settings.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.checkingcontainer.feature.settings.SettingsRoute

const val SETTINGS_ROUTE = "settings"

fun NavGraphBuilder.settingsScreen(
    isAdmin: Boolean = false,
    onUsersClick: () -> Unit = {},
) {
    composable(route = SETTINGS_ROUTE) {
        SettingsRoute(
            onBack = {},
            isAdmin = isAdmin,
            onUsersClick = onUsersClick,
        )
    }
}
