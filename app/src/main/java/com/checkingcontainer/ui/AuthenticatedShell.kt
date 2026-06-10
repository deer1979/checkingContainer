package com.checkingcontainer.ui

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.checkingcontainer.navigation.TopLevelDestination
import com.checkingcontainer.core.model.User
import com.checkingcontainer.feature.admin.navigation.ADMIN_ROUTE
import com.checkingcontainer.feature.admin.navigation.adminScreen
import com.checkingcontainer.feature.announcements.navigation.ANNOUNCEMENTS_LIST_ROUTE
import com.checkingcontainer.feature.announcements.navigation.announcementsGraph
import com.checkingcontainer.feature.settings.navigation.SETTINGS_ROUTE
import com.checkingcontainer.feature.settings.navigation.settingsScreen
import com.checkingcontainer.feature.units.navigation.estimadosGraph
import com.checkingcontainer.feature.units.navigation.unitsGraph
import com.checkingcontainer.feature.users.navigation.USERS_LIST_ROUTE
import com.checkingcontainer.feature.users.navigation.usersGraph

/**
 * Post-login shell. Barra inferior con Anuncios y Unidades. Ajustes y Usuarios
 * se acceden desde el avatar del usuario en el TopAppBar de cada pantalla raíz.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun AuthenticatedShell(user: User) {
    val navController = rememberNavController()
    val currentEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentEntry?.destination?.route

    val shellViewModel: ShellViewModel = hiltViewModel()
    val unreadAnnouncements by shellViewModel.unreadAnnouncements.collectAsStateWithLifecycle()
    val openEstimados by shellViewModel.openEstimados.collectAsStateWithLifecycle()

    val onSettingsClick = { navController.navigate(SETTINGS_ROUTE) }
    val onLogout       = { shellViewModel.logout() }

    Scaffold(
        bottomBar = {
            // Ocultar la nav global en pantallas con su propia barra de acciones
            val hideBottomNav = currentRoute?.startsWith("estimado/") == true
            if (!hideBottomNav) {
                AppBottomBar(
                    destinations = TopLevelDestination.entries,
                    currentRoute = currentRoute,
                    onSelect = { dest -> navigateToTopLevel(navController, dest) },
                    unreadAnnouncements = unreadAnnouncements,
                    openEstimados = openEstimados,
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
                    isAdmin = user.role.isAdmin,
                    onCreateAnnouncement = { navController.navigate(ADMIN_ROUTE) },
                    user = user,
                    onSettingsClick = onSettingsClick,
                    onLogout = onLogout,
                )
                usersGraph(navController = navController)
                adminScreen(
                    onBack = { navController.popBackStack() },
                    onPublished = { navController.popBackStack() },
                )
                unitsGraph(
                    navController = navController,
                    user = user,
                    onSettingsClick = onSettingsClick,
                    onLogout = onLogout,
                )
                estimadosGraph(navController = navController)
                settingsScreen(
                    isAdmin = user.role.isAdmin,
                    onUsersClick = { navController.navigate(USERS_LIST_ROUTE) },
                )
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
