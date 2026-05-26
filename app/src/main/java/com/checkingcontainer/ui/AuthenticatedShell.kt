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
import com.checkingcontainer.feature.settings.navigation.settingsScreen
import com.checkingcontainer.feature.units.navigation.unitsGraph
import com.checkingcontainer.feature.users.navigation.USERS_LIST_ROUTE
import com.checkingcontainer.feature.users.navigation.usersGraph

/**
 * Post-login shell. Scaffold + bottom NavigationBar wrap an inner NavHost
 * holding all authenticated destinations. Start destination is role-based:
 * admins land on user management; everyone else lands on announcements.
 *
 * Wrapped in [SharedTransitionLayout] so list → detail flows (announcements)
 * can share visual elements across destinations.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun AuthenticatedShell(user: User) {
    val navController = rememberNavController()
    val currentEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentEntry?.destination?.route
    val destinations = remember(user.role) { TopLevelDestination.forRole(user.role) }
    val startDestination = if (user.role.isAdmin) USERS_LIST_ROUTE else ANNOUNCEMENTS_LIST_ROUTE

    Scaffold(
        bottomBar = {
            AppBottomBar(
                destinations = destinations,
                currentRoute = currentRoute,
                onSelect = { dest -> navigateToTopLevel(navController, dest) },
            )
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
                startDestination = startDestination,
                modifier = Modifier.fillMaxSize(),
            ) {
                announcementsGraph(
                    navController = navController,
                    sharedTransitionScope = this@SharedTransitionLayout,
                )
                usersGraph(navController = navController)
                adminScreen()
                unitsGraph(navController = navController)
                settingsScreen()
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
