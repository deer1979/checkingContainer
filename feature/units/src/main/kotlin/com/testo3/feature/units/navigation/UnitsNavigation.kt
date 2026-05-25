package com.testo3.feature.units.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.testo3.feature.units.UnitEntryRoute

const val UNITS_ROUTE = "units"

fun NavGraphBuilder.unitsGraph(navController: NavHostController) {
    composable(route = UNITS_ROUTE) {
        UnitEntryRoute(onBack = { navController.popBackStack() })
    }
}