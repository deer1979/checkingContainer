package com.testo3.feature.admin.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.testo3.feature.admin.AdminRoute

const val ADMIN_ROUTE = "admin"

fun NavGraphBuilder.adminScreen() {
    composable(route = ADMIN_ROUTE) {
        AdminRoute()
    }
}
