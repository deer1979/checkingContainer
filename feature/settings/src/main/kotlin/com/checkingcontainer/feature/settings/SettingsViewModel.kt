package com.checkingcontainer.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.checkingcontainer.core.domain.AuthRepository
import com.checkingcontainer.core.domain.ThemeRepository
import com.checkingcontainer.core.model.ThemeConfig
import com.checkingcontainer.core.network.RemoteDataSource
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val themeRepository: ThemeRepository,
    private val remoteDataSource: RemoteDataSource,
) : ViewModel() {

    private val _state = MutableStateFlow(
        SettingsUiState(
            remoteConnected = remoteDataSource.isConnected,
            remoteBackendDescription = remoteDataSource.backendDescription,
        )
    )
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            themeRepository.themeConfig.collect { config ->
                _state.update { it.copy(theme = config) }
            }
        }
        viewModelScope.launch {
            themeRepository.dynamicColor.collect { enabled ->
                _state.update { it.copy(dynamicColor = enabled) }
            }
        }
    }

    fun onThemeChange(config: ThemeConfig) {
        viewModelScope.launch { themeRepository.setThemeConfig(config) }
    }

    fun onDynamicColorChange(enabled: Boolean) {
        viewModelScope.launch { themeRepository.setDynamicColor(enabled) }
    }

    fun onToggleNotifications(value: Boolean) = _state.update { it.copy(notifications = value) }
    fun onToggleAutoSync(value: Boolean)       = _state.update { it.copy(autoSync = value) }

    fun onLogout() {
        viewModelScope.launch { authRepository.logout() }
    }
}
