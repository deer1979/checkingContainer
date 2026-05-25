package com.checkingcontainer.feature.units.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.checkingcontainer.feature.units.UnitEntryRoute

const val UNITS_ROUTE = "units"

fun NavGraphBuilder.unitsGraph(
    navController: NavHostController,
    onNavigateToSettings: () -> Unit,
) {
    composable(route = UNITS_ROUTE) {
        UnitEntryRoute(
            onBack = { navController.popBackStack() },
            onNavigateToSettings = onNavigateToSettings,
        )
    }
}