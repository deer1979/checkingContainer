package com.checkingcontainer.feature.announcements.navigation

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.remember
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.checkingcontainer.core.model.User
import com.checkingcontainer.feature.announcements.AnnouncementDetailRoute
import com.checkingcontainer.feature.announcements.AnnouncementsListRoute
import com.checkingcontainer.feature.announcements.AnnouncementsViewModel

const val ANNOUNCEMENTS_LIST_ROUTE = "announcements"
const val ANNOUNCEMENT_DETAIL_ROUTE = "announcements/{id}"
private const val ANNOUNCEMENT_ID_ARG = "id"

fun announcementDetailRoute(id: String): String = "announcements/$id"

@OptIn(ExperimentalSharedTransitionApi::class)
fun NavGraphBuilder.announcementsGraph(
    navController: NavHostController,
    sharedTransitionScope: SharedTransitionScope,
    isAdmin: Boolean = false,
    onCreateAnnouncement: () -> Unit = {},
    user: User? = null,
    onSettingsClick: () -> Unit = {},
    onLogout: () -> Unit = {},
) {
    composable(route = ANNOUNCEMENTS_LIST_ROUTE) { backStackEntry ->
        val parent = remember(backStackEntry) {
            navController.getBackStackEntry(ANNOUNCEMENTS_LIST_ROUTE)
        }
        val vm: AnnouncementsViewModel = hiltViewModel(parent)
        AnnouncementsListRoute(
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = this@composable,
            onAnnouncementClick = { id ->
                navController.navigate(announcementDetailRoute(id))
            },
            isAdmin = isAdmin,
            onCreateClick = onCreateAnnouncement,
            user = user,
            onSettingsClick = onSettingsClick,
            onLogout = onLogout,
            viewModel = vm,
        )
    }
    composable(
        route = ANNOUNCEMENT_DETAIL_ROUTE,
        arguments = listOf(
            navArgument(ANNOUNCEMENT_ID_ARG) { type = NavType.StringType },
        ),
    ) { backStackEntry ->
        val id = backStackEntry.arguments?.getString(ANNOUNCEMENT_ID_ARG).orEmpty()
        val parent = remember(backStackEntry) {
            navController.getBackStackEntry(ANNOUNCEMENTS_LIST_ROUTE)
        }
        val vm: AnnouncementsViewModel = hiltViewModel(parent)
        AnnouncementDetailRoute(
            id = id,
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = this@composable,
            onBack = { navController.popBackStack() },
            viewModel = vm,
        )
    }
}