package com.checkingcontainer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.checkingcontainer.core.domain.AuthRepository
import com.checkingcontainer.core.domain.AuthState
import com.checkingcontainer.core.domain.ThemeRepository
import com.checkingcontainer.core.model.ThemeConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class MainViewModel @Inject constructor(
    authRepository: AuthRepository,
    themeRepository: ThemeRepository,
) : ViewModel() {

    val authState: StateFlow<AuthState> = authRepository.state.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AuthState.Loading,
    )

    val themeConfig: StateFlow<ThemeConfig> = themeRepository.themeConfig.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ThemeConfig.FOLLOW_SYSTEM,
    )

    val dynamicColor: StateFlow<Boolean> = themeRepository.dynamicColor.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = true,
    )
}