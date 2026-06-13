package com.checkingcontainer.feature.units.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.checkingcontainer.core.model.User
import com.checkingcontainer.feature.units.EstimadoRoute
import com.checkingcontainer.feature.units.EstimadosListRoute
import com.checkingcontainer.feature.units.ReeferSearchRoute
import com.checkingcontainer.feature.units.UnitDetailRoute
import com.checkingcontainer.feature.units.UnitEntryRoute
import com.checkingcontainer.feature.units.UnitListRoute

const val UNITS_ROUTE = "units"
const val ESTIMADOS_LIST_ROUTE = "estimados"
private const val UNITS_ENTRY_BASE = "units/entry"
private const val UNITS_ENTRY_ROUTE = "$UNITS_ENTRY_BASE?unitId={unitId}"
private const val UNITS_SEARCH_ROUTE = "units/search"
internal const val UNIT_ENTRY_ID_ARG = "unitId"
const val UNIT_DETAIL_ROUTE_PATTERN = "units/detail/{containerNo}"
internal const val UNIT_DETAIL_ARG = "containerNo"
const val ESTIMADO_INSPECTION_ID_ARG = "inspectionId"
private const val ESTIMADO_ROUTE_PATTERN = "estimado/{$ESTIMADO_INSPECTION_ID_ARG}"

fun unitDetailRoute(containerNo: String) = "units/detail/$containerNo"
fun unitEntryEditRoute(unitId: Long) = "$UNITS_ENTRY_BASE?unitId=$unitId"
fun estimadoRoute(inspectionId: Long) = "estimado/$inspectionId"

fun NavGraphBuilder.unitsGraph(
    navController: NavHostController,
    user: User? = null,
    onSettingsClick: () -> Unit = {},
    onLogout: () -> Unit = {},
) {
    composable(route = UNITS_ROUTE) {
        UnitListRoute(
            onNewInspection = { navController.navigate(UNITS_ENTRY_BASE) },
            onUnitClick = { containerNo -> navController.navigate(unitDetailRoute(containerNo)) },
            onSearch = { navController.navigate(UNITS_SEARCH_ROUTE) },
            user = user,
            onSettingsClick = onSettingsClick,
            onLogout = onLogout,
        )
    }
    composable(
        route = UNITS_ENTRY_ROUTE,
        arguments = listOf(
            navArgument(UNIT_ENTRY_ID_ARG) {
                type = NavType.LongType
                defaultValue = -1L
            },
        ),
    ) {
        UnitEntryRoute(
            onBack = { navController.popBackStack() },
            onDeleted = { navController.popBackStack(UNITS_ROUTE, false) },
            onNavigateToEstimado = { inspId ->
                navController.navigate(estimadoRoute(inspId)) {
                    popUpTo(UNITS_ROUTE)
                }
            },
        )
    }
    composable(
        route = UNIT_DETAIL_ROUTE_PATTERN,
        arguments = listOf(navArgument(UNIT_DETAIL_ARG) { type = NavType.StringType }),
    ) {
        UnitDetailRoute(
            onBack = { navController.popBackStack() },
            onEdit = { unitId -> navController.navigate(unitEntryEditRoute(unitId)) },
        )
    }
    composable(route = UNITS_SEARCH_ROUTE) {
        ReeferSearchRoute(
            onBack = { navController.popBackStack() },
            onResultClick = { containerNo -> navController.navigate(unitDetailRoute(containerNo)) },
        )
    }
    composable(
        route = ESTIMADO_ROUTE_PATTERN,
        arguments = listOf(navArgument(ESTIMADO_INSPECTION_ID_ARG) { type = NavType.LongType }),
    ) {
        EstimadoRoute(
            onBack = { navController.popBackStack() },
        )
    }
}

fun NavGraphBuilder.estimadosGraph(
    navController: NavHostController,
    onMeasureClick: (String) -> Unit = {},
) {
    composable(route = ESTIMADOS_LIST_ROUTE) {
        EstimadosListRoute(
            onEstimadoClick = { inspId -> navController.navigate(estimadoRoute(inspId)) },
            onMeasureClick = onMeasureClick,
        )
    }
}
