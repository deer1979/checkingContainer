package com.checkingcontainer.feature.users.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.checkingcontainer.feature.users.UserFormRoute
import com.checkingcontainer.feature.users.UserManagementRoute

const val USERS_LIST_ROUTE = "users"
const val USER_FORM_ROUTE_PATTERN = "users/form?userId={userId}"
internal const val USER_ID_ARG = "userId"

private const val NEW_USER_ID = -1L

fun userFormRoute(userId: Long? = null): String =
    "users/form?userId=${userId ?: NEW_USER_ID}"

fun NavGraphBuilder.usersGraph(
    navController: NavHostController,
    onNavigateToSettings: () -> Unit,
) {
    composable(route = USERS_LIST_ROUTE) {
        UserManagementRoute(
            onAddUser = { navController.navigate(userFormRoute()) },
            onEditUser = { id -> navController.navigate(userFormRoute(id)) },
            onNavigateToSettings = onNavigateToSettings,
        )
    }
    composable(
        route = USER_FORM_ROUTE_PATTERN,
        arguments = listOf(
            navArgument(USER_ID_ARG) {
                type = NavType.LongType
                defaultValue = NEW_USER_ID
            },
        ),
    ) {
        UserFormRoute(onBack = { navController.popBackStack() })
    }
}