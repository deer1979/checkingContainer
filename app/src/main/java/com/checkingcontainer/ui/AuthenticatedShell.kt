package com.checkingcontainer.ui

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.checkingcontainer.navigation.TopLevelDestination
import com.checkingcontainer.core.model.User
import com.checkingcontainer.feature.admin.navigation.adminScreen
import com.checkingcontainer.feature.announcements.navigation.ANNOUNCEMENTS_LIST_ROUTE
import com.checkingcontainer.feature.announcements.navigation.announcementsGraph
import com.checkingcontainer.feature.settings.navigation.SETTINGS_ROUTE
import com.checkingcontainer.feature.settings.navigation.settingsScreen
import com.checkingcontainer.feature.tasks.navigation.tasksScreen
import com.checkingcontainer.feature.units.navigation.unitsGraph
import com.checkingcontainer.feature.users.navigation.usersGraph

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun AuthenticatedShell(user: User) {
    val navController = rememberNavController()
    val currentEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentEntry?.destination?.route
    val destinations = remember(user.role) { TopLevelDestination.forRole(user.role) }

    val onNavigateToSettings: () -> Unit = { navController.navigate(SETTINGS_ROUTE) }

    Scaffold(
        bottomBar = {
            if (currentRoute != SETTINGS_ROUTE) {
                AppBottomBar(
                    destinations = destinations,
                    currentRoute = currentRoute,
                    onSelect = { dest -> navigateToTopLevel(navController, dest) },
                )
            }
        },
        modifier = Modifier.fillMaxSize(),
    ) { innerPadding ->
        SharedTransitionLayout(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            NavHost(
                navController = navController,
                startDestination = ANNOUNCEMENTS_LIST_ROUTE,
                modifier = Modifier.fillMaxSize(),
            ) {
                announcementsGraph(
                    navController = navController,
                    sharedTransitionScope = this@SharedTransitionLayout,
                    onNavigateToSettings = onNavigateToSettings,
                )
                usersGraph(
                    navController = navController,
                    onNavigateToSettings = onNavigateToSettings,
                )
                adminScreen()
                tasksScreen()
                unitsGraph(
                    navController = navController,
                    onNavigateToSettings = onNavigateToSettings,
                )
                settingsScreen(navController = navController)
            }
        }
    }
}

private fun navigateToTopLevel(
    navController: NavHostController,
    dest: TopLevelDestination,
) {
    navController.navigate(dest.route) {
        popUpTo(navController.graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}