package com.checkingcontainer.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.checkingcontainer.feature.login.LoginRoute
import com.checkingcontainer.feature.splash.SplashScreen

/**
 * Pre-authentication flow. Renders a splash, then transitions to the login
 * card. Intentionally has no Scaffold / NavigationBar — the bottom bar only
 * exists in the authenticated shell.
 */
@Composable
fun PublicShell(modifier: Modifier = Modifier) {
    var stage by rememberSaveable { mutableStateOf(PublicStage.Splash) }

    Surface(modifier = modifier.fillMaxSize()) {
        Crossfade(
            targetState = stage,
            animationSpec = tween(durationMillis = 300),
            label = "public-stage",
        ) { current ->
            when (current) {
                PublicStage.Splash -> SplashScreen(
                    onFinished = { stage = PublicStage.Login },
                )
                PublicStage.Login -> LoginRoute()
            }
        }
    }
}

private enum class PublicStage { Splash, Login }
