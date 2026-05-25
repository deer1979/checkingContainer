package com.testo3.feature.login.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.testo3.feature.login.LoginRoute

const val LOGIN_ROUTE = "login"

fun NavGraphBuilder.loginScreen() {
    composable(route = LOGIN_ROUTE) {
        LoginRoute()
    }
}
