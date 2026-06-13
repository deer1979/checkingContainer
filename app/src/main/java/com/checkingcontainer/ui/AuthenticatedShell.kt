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
import com.checkingcontainer.navigation.TopLevelDestination
import com.checkingcontainer.core.model.User
import com.checkingcontainer.feature.admin.navigation.ADMIN_ROUTE
import com.checkingcontainer.feature.admin.navigation.adminScreen
import com.checkingcontainer.feature.announcements.navigation.ANNOUNCEMENTS_LIST_ROUTE
import com.checkingcontainer.feature.announcements.navigation.announcementsGraph
import com.checkingcontainer.feature.settings.navigation.SETTINGS_ROUTE
import com.checkingcontainer.feature.settings.navigation.settingsScreen
import com.checkingcontainer.feature.sensors.navigation.sensorsGraph
import com.checkingcontainer.feature.units.navigation.estimadosGraph
import com.checkingcontainer.feature.units.navigation.unitsGraph
import com.checkingcontainer.feature.users.navigation.USERS_LIST_ROUTE
import com.checkingcontainer.feature.users.navigation.usersGraph

/**
 * Post-login shell adaptativo (M3 Adaptive):
 *  - Ancho Compact (teléfono): barra inferior tipo pill ([AppBottomBar]).
 *  - Ancho Medium/Expanded (tablet de campo, plegable): rail lateral
 *    ([AppNavigationRail]) y el contenido ocupa el resto.
 * Ajustes y Usuarios se acceden desde el avatar del TopAppBar de cada raíz.
 */
@Composable
fun AuthenticatedShell(user: User) {
    val navController = rememberNavController()
    val currentEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentEntry?.destination?.route

    val shellViewModel: ShellViewModel = hiltViewModel()
    // Activa la sync de digitación solo mientras el shell está en primer plano.
    shellViewModel.digitacionSync.collectAsStateWithLifecycle()

    val onSettingsClick = { navController.navigate(SETTINGS_ROUTE) }
    val onLogout       = { shellViewModel.logout() }

    // M3 Adaptive: la clase de tamaño de ventana decide la navegación global.
    val useRail = rememberUseRail()
    // Ocultar la nav global en pantallas con su propia barra de acciones.
    // Se usa visibleEntries (no la ruta actual): al retroceder, la pantalla del
    // estimado sigue visible mientras anima su salida; mostrar la barra global
    // antes de tiempo encogía el contenido y su barra propia "saltaba" arriba
    // con un parpadeo fantasma.
    val visibleEntries by navController.visibleEntries.collectAsStateWithLifecycle()
    val hideGlobalNav = visibleEntries.any {
        it.destination.route?.startsWith("estimado/") == true
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
                // Los badges se colectan aquí dentro para que sus cambios solo
                // recompongan la barra inferior y no el shell completo.
                val unreadAnnouncements by shellViewModel.unreadAnnouncements.collectAsStateWithLifecycle()
                val openEstimados by shellViewModel.openEstimados.collectAsStateWithLifecycle()
                // Asimétrico a propósito: al IR al estimado la barra se quita
                // instantánea (animar su altura hacía que la barra propia del
                // estimado entrara "bajando" a trompicones); al VOLVER sí entra
                // deslizando desde abajo.
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

/** True si la ventana es Medium/Expanded (M3 window size class). */
@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
private fun rememberUseRail(): Boolean {
    val activity = LocalActivity.current ?: return false
    val windowSize = calculateWindowSizeClass(activity)
    return windowSize.widthSizeClass != WindowWidthSizeClass.Compact
}

/** Rail con badges; recompone solo aquí cuando cambian los contadores. */
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
            estimadosGraph(
                navController = navController,
                onMeasureClick = { containerNo ->
                    navController.navigate(com.checkingcontainer.feature.sensors.navigation.sensorsRoute(containerNo))
                },
            )
            sensorsGraph(navController = navController)
            settingsScreen(
                isAdmin = user.role.isAdmin,
                onUsersClick = { navController.navigate(USERS_LIST_ROUTE) },
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
