package com.checkingcontainer.feature.login.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.checkingcontainer.feature.login.LoginRoute

const val LOGIN_ROUTE = "login"

fun NavGraphBuilder.loginScreen() {
    composable(route = LOGIN_ROUTE) {
        LoginRoute()
    }
}
