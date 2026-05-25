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
) {
    composable(route = ANNOUNCEMENTS_LIST_ROUTE) { backStackEntry ->
        // Share the same ViewModel between list and detail by scoping it to
        // the parent NavBackStackEntry (the announcements graph). Cleaner than
        // hoisting state up to the App level for a single feature.
        val parent = remember(backStackEntry) {
            navController.getBackStackEntry(ANNOUNCEMENTS_LIST_ROUTE)
        }
        val vm: AnnouncementsViewModel =
            androidx.hilt.navigation.compose.hiltViewModel(parent)
        AnnouncementsListRoute(
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = this@composable,
            onAnnouncementClick = { id ->
                navController.navigate(announcementDetailRoute(id))
            },
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
        val vm: AnnouncementsViewModel =
            androidx.hilt.navigation.compose.hiltViewModel(parent)
        AnnouncementDetailRoute(
            id = id,
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = this@composable,
            onBack = { navController.popBackStack() },
            viewModel = vm,
        )
    }
}
