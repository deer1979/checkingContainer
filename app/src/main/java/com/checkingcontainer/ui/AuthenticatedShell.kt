package com.checkingcontainer.ui

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
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
import com.checkingcontainer.core.model.User
import com.checkingcontainer.feature.admin.navigation.ADMIN_ROUTE
import com.checkingcontainer.feature.admin.navigation.adminScreen
import com.checkingcontainer.feature.announcements.navigation.ANNOUNCEMENTS_LIST_ROUTE
import com.checkingcontainer.feature.announcements.navigation.announcementsGraph
import com.checkingcontainer.feature.settings.navigation.SETTINGS_ROUTE
import com.checkingcontainer.feature.settings.navigation.settingsScreen
import com.checkingcontainer.feature.sensors.navigation.sensorsGraph
import com.checkingcontainer.feature.units.navigation.CLIENTES_LIST_ROUTE
import com.checkingcontainer.feature.units.navigation.clientesGraph
import com.checkingcontainer.feature.units.navigation.estimadosGraph
import com.checkingcontainer.feature.units.navigation.unitsGraph
import com.checkingcontainer.feature.users.navigation.USERS_LIST_ROUTE
import com.checkingcontainer.feature.users.navigation.usersGraph
import com.checkingcontainer.navigation.TopLevelDestination

/**
 * Post-login shell adaptativo:
 *  - Compact: barra inferior.
 *  - Medium/Expanded: rail lateral.
 * La navegación global solo aparece en destinos raíz.
 */
@Composable
fun AuthenticatedShell(user: User) {
    val navController = rememberNavController()
    val currentEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentEntry?.destination?.route

    val shellViewModel: ShellViewModel = hiltViewModel()
    shellViewModel.digitacionSync.collectAsStateWithLifecycle()

    val onSettingsClick = {
        navController.navigate(SETTINGS_ROUTE) {
            launchSingleTop = true
        }
    }
    val onLogout = { shellViewModel.logout() }

    val useRail = rememberUseRail()
    val topLevelRoutes = TopLevelDestination.entries.mapTo(mutableSetOf()) { it.route }

    // visibleEntries evita que la barra reaparezca antes de terminar la animación
    // de salida. Cualquier pantalla que no sea raíz oculta la navegación global.
    val visibleEntries by navController.visibleEntries.collectAsStateWithLifecycle()
    val hideGlobalNav = visibleEntries.any { entry ->
        entry.destination.route !in topLevelRoutes
    }

    if (useRail) {
        Row(Modifier.fillMaxSize()) {
            if (!hideGlobalNav) {
                ShellRail(
                    shellViewModel = shellViewModel,
                    currentRoute = currentRoute,
                    onSelect = { dest -> navigateToTopLevel(navController, dest) },
                )
            }
            Box(Modifier.fillMaxSize()) {
                ShellNavHost(
                    navController = navController,
                    user = user,
                    onSettingsClick = onSettingsClick,
                    onLogout = onLogout,
                )
            }
        }
    } else {
        Scaffold(
            bottomBar = {
                val unreadAnnouncements by shellViewModel.unreadAnnouncements.collectAsStateWithLifecycle()
                val openEstimados by shellViewModel.openEstimados.collectAsStateWithLifecycle()
                AnimatedVisibility(
                    visible = !hideGlobalNav,
                    enter = slideInVertically { it } + fadeIn(),
                    exit = ExitTransition.None,
                ) {
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
            ShellNavHost(
                navController = navController,
                user = user,
                onSettingsClick = onSettingsClick,
                onLogout = onLogout,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
private fun rememberUseRail(): Boolean {
    val activity = LocalActivity.current ?: return false
    val windowSize = calculateWindowSizeClass(activity)
    return windowSize.widthSizeClass != WindowWidthSizeClass.Compact
}

@Composable
private fun ShellRail(
    shellViewModel: ShellViewModel,
    currentRoute: String?,
    onSelect: (TopLevelDestination) -> Unit,
) {
    val unreadAnnouncements by shellViewModel.unreadAnnouncements.collectAsStateWithLifecycle()
    val openEstimados by shellViewModel.openEstimados.collectAsStateWithLifecycle()
    AppNavigationRail(
        destinations = TopLevelDestination.entries,
        currentRoute = currentRoute,
        onSelect = onSelect,
        unreadAnnouncements = unreadAnnouncements,
        openEstimados = openEstimados,
    )
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun ShellNavHost(
    navController: NavHostController,
    user: User,
    onSettingsClick: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SharedTransitionLayout(
        modifier = modifier.fillMaxSize(),
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
                onCreateAnnouncement = {
                    navController.navigate(ADMIN_ROUTE) { launchSingleTop = true }
                },
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
            estimadosGraph(
                navController = navController,
                onMeasureClick = { containerNo ->
                    navController.navigate(
                        com.checkingcontainer.feature.sensors.navigation.sensorsRoute(containerNo),
                    ) {
                        launchSingleTop = true
                    }
                },
            )
            sensorsGraph(navController = navController)
            clientesGraph(navController = navController)
            settingsScreen(
                navController = navController,
                isAdmin = user.role.isAdmin,
                onUsersClick = {
                    navController.navigate(USERS_LIST_ROUTE) {
                        launchSingleTop = true
                    }
                },
                onClientsClick = {
                    navController.navigate(CLIENTES_LIST_ROUTE) {
                        launchSingleTop = true
                    }
                },
            )
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
