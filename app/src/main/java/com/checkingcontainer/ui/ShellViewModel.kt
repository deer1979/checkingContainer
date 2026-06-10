package com.checkingcontainer.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.checkingcontainer.core.domain.AnnouncementsRepository
import com.checkingcontainer.core.domain.AuthRepository
import com.checkingcontainer.core.domain.AuthState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

/**
 * Estado a nivel del shell autenticado. Por ahora expone el número de anuncios
 * sin leer del usuario actual para el badge de la barra de navegación inferior.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ShellViewModel @Inject constructor(
    private val auth: AuthRepository,
    announcements: AnnouncementsRepository,
) : ViewModel() {

    fun logout() { viewModelScope.launch { auth.logout() } }

    val unreadAnnouncements: StateFlow<Int> = auth.state
        .flatMapLatest { state ->
            if (state is AuthState.Authenticated) announcements.unreadCount(state.user.id)
            else flowOf(0)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
}
