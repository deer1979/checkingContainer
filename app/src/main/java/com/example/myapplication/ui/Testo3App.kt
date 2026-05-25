package com.example.myapplication.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.myapplication.navigation.Testo3NavHost
import com.example.myapplication.navigation.TopLevelDestination

@Composable
fun Testo3App() {
    val navController = rememberNavController()
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route

    Scaffold(
        bottomBar = {
            Testo3BottomBar(
                currentRoute = currentRoute,
                onSelect = { dest -> navigateToTopLevel(navController, dest) },
            )
        },
        modifier = Modifier.fillMaxSize(),
    ) { innerPadding ->
        Testo3NavHost(
            navController = navController,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        )
    }
}

private fun navigateToTopLevel(
    navController: androidx.navigation.NavHostController,
    dest: TopLevelDestination,
) {
    navController.navigate(dest.route) {
        // Pop up to the graph's start so the back stack stays a single entry
        // per top-level destination — standard Material 3 bottom-bar behavior.
        popUpTo(navController.graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}
