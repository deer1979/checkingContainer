package com.checkingcontainer.feature.units.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.checkingcontainer.feature.units.UnitEntryRoute
import com.checkingcontainer.feature.units.UnitListRoute

const val UNITS_ROUTE = "units"
private const val UNITS_ENTRY_ROUTE = "units/entry"

fun NavGraphBuilder.unitsGraph(navController: NavHostController) {
    composable(route = UNITS_ROUTE) {
        UnitListRoute(
            onNewInspection = { navController.navigate(UNITS_ENTRY_ROUTE) },
        )
    }
    composable(route = UNITS_ENTRY_ROUTE) {
        UnitEntryRoute(onBack = { navController.popBackStack() })
    }
}
