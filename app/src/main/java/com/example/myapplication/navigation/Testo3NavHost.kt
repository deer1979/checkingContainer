package com.example.myapplication.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import com.testo3.feature.login.navigation.LOGIN_ROUTE
import com.testo3.feature.login.navigation.loginScreen
import com.testo3.feature.settings.navigation.settingsScreen
import com.testo3.feature.tasks.navigation.tasksScreen

@Composable
fun Testo3NavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = LOGIN_ROUTE,
        modifier = modifier,
    ) {
        loginScreen()
        tasksScreen()
        settingsScreen()
    }
}
