package com.checkingcontainer.feature.units.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.checkingcontainer.feature.units.UnitDetailRoute
import com.checkingcontainer.feature.units.UnitEntryRoute
import com.checkingcontainer.feature.units.UnitListRoute

const val UNITS_ROUTE = "units"
private const val UNITS_ENTRY_ROUTE = "units/entry"
const val UNIT_DETAIL_ROUTE_PATTERN = "units/detail/{containerNo}"
internal const val UNIT_DETAIL_ARG = "containerNo"

fun unitDetailRoute(containerNo: String) = "units/detail/$containerNo"

fun NavGraphBuilder.unitsGraph(navController: NavHostController) {
    composable(route = UNITS_ROUTE) {
        UnitListRoute(
            onNewInspection = { navController.navigate(UNITS_ENTRY_ROUTE) },
            onUnitClick = { containerNo -> navController.navigate(unitDetailRoute(containerNo)) },
        )
    }
    composable(route = UNITS_ENTRY_ROUTE) {
        UnitEntryRoute(onBack = { navController.popBackStack() })
    }
    composable(
        route = UNIT_DETAIL_ROUTE_PATTERN,
        arguments = listOf(navArgument(UNIT_DETAIL_ARG) { type = NavType.StringType }),
    ) {
        UnitDetailRoute(onBack = { navController.popBackStack() })
    }
}
