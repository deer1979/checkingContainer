package com.checkingcontainer.feature.splash

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * In-content splash that fades the brand mark in, holds briefly, then signals
 * [onFinished]. The system Splash Screen API still handles the cold-start
 * frame; this composable owns the post-startup transition into Login.
 */
@Composable
fun SplashScreen(
    onFinished: () -> Unit,
) {
    var visible by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 350, easing = LinearEasing),
        label = "splash-alpha",
    )
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.85f,
        animationSpec = tween(durationMillis = 450, easing = LinearEasing),
        label = "splash-scale",
    )

    LaunchedEffect(Unit) {
        visible = true
        delay(900)
        onFinished()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .alpha(alpha)
                    .scale(scale)
                    .background(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(28.dp),
                    )
                    .padding(28.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Bolt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
            Spacer(Modifier.height(20.dp))
            Text(
                text = "CheckingContainer",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.alpha(alpha),
            )
        }
    }
}
