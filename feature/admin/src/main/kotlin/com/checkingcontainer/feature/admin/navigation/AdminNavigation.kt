package com.checkingcontainer.feature.admin.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.checkingcontainer.feature.admin.AdminRoute

const val ADMIN_ROUTE = "admin"

fun NavGraphBuilder.adminScreen(
    onBack: () -> Unit,
    onPublished: () -> Unit,
) {
    composable(route = ADMIN_ROUTE) {
        AdminRoute(onBack = onBack, onPublished = onPublished)
    }
}
