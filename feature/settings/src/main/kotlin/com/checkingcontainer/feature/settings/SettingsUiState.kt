package com.checkingcontainer.feature.settings

import androidx.compose.runtime.Immutable
import com.checkingcontainer.core.model.ThemeConfig

@Immutable
data class SettingsUiState(
    val theme: ThemeConfig = ThemeConfig.FOLLOW_SYSTEM,
    val dynamicColor: Boolean = true,
    val notifications: Boolean = true,
    val autoSync: Boolean = true,
    /** true si el backend remoto está configurado y conectado. */
    val remoteConnected: Boolean = false,
    /** Descripción del backend remoto para mostrar en la UI. */
    val remoteBackendDescription: String = "Sin backend remoto configurado",
)
