package com.testo3.feature.tasks.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.testo3.feature.tasks.TasksRoute

const val TASKS_ROUTE = "tasks"

fun NavGraphBuilder.tasksScreen() {
    composable(route = TASKS_ROUTE) {
        TasksRoute()
    }
}
