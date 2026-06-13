package com.checkingcontainer.feature.sensors.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.checkingcontainer.feature.sensors.SensorsRoute

const val SENSORS_CONTAINER_ARG = "containerNo"
private const val SENSORS_ROUTE_PATTERN = "sensors/{$SENSORS_CONTAINER_ARG}"

fun sensorsRoute(containerNo: String) = "sensors/$containerNo"

fun NavGraphBuilder.sensorsGraph(navController: NavHostController) {
    composable(
        route = SENSORS_ROUTE_PATTERN,
        arguments = listOf(navArgument(SENSORS_CONTAINER_ARG) { type = NavType.StringType }),
    ) {
        SensorsRoute(onBack = { navController.popBackStack() })
    }
}
