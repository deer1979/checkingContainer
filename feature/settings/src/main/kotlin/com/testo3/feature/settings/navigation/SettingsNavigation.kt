package com.testo3.feature.settings.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.testo3.feature.settings.SettingsRoute

const val SETTINGS_ROUTE = "settings"

fun NavGraphBuilder.settingsScreen() {
    composable(route = SETTINGS_ROUTE) {
        SettingsRoute()
    }
}
