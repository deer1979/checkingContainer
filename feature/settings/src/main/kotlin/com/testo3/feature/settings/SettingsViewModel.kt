package com.testo3.feature.settings

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@HiltViewModel
class SettingsViewModel @Inject constructor() : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    fun onToggleDarkMode(value: Boolean) =
        _state.update { it.copy(darkMode = value) }

    fun onToggleDynamicColor(value: Boolean) =
        _state.update { it.copy(dynamicColor = value) }

    fun onToggleNotifications(value: Boolean) =
        _state.update { it.copy(notifications = value) }

    fun onToggleAutoSync(value: Boolean) =
        _state.update { it.copy(autoSync = value) }
}
