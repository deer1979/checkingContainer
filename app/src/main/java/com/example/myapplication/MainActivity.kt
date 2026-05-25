package com.example.myapplication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.testo3.core.designsystem.theme.Testo3Theme
import com.testo3.feature.tasks.navigation.TASKS_ROUTE
import com.testo3.feature.tasks.navigation.tasksScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            Testo3Theme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    Testo3App()
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun Testo3App() {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = TASKS_ROUTE,
    ) {
        tasksScreen()
    }
}
