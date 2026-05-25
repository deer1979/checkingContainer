package com.checkingcontainer.feature.settings

import com.checkingcontainer.core.model.ThemeConfig

data class SettingsUiState(
    val theme: ThemeConfig = ThemeConfig.FOLLOW_SYSTEM,
    val dynamicColor: Boolean = true,
    val notifications: Boolean = true,
    val autoSync: Boolean = true,
)