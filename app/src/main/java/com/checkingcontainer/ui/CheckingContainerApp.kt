package com.checkingcontainer.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.checkingcontainer.MainViewModel
import com.checkingcontainer.core.domain.AuthState

/**
 * App entry. Observes the auth state at the activity scope and crossfades
 * between two completely separate compositions:
 *   - [PublicShell] â†’ splash + login. No Scaffold, no bottom bar.
 *   - [AuthenticatedShell] â†’ Scaffold + bottom NavigationBar + per-role start
 *     destination (Admin â†’ admin panel, Normal â†’ announcements).
 */
@Composable
fun CheckingContainerApp(viewModel: MainViewModel = hiltViewModel()) {
    val authState by viewModel.authState.collectAsStateWithLifecycle()

    AnimatedContent(
        targetState = authState,
        label = "auth-state",
        transitionSpec = {
            fadeIn(tween(durationMillis = 250)) togetherWith fadeOut(tween(durationMillis = 200))
        },
        contentKey = {
            when (it) {
                AuthState.Loading,
                AuthState.Unauthenticated -> "public"
                is AuthState.Authenticated -> "auth"
            }
        },
    ) { state ->
        when (state) {
            AuthState.Loading,
            AuthState.Unauthenticated -> PublicShell()
            is AuthState.Authenticated -> AuthenticatedShell(user = state.user)
        }
    }
}
