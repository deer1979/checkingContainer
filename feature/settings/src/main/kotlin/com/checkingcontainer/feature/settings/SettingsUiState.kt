package com.checkingcontainer.feature.settings

/**
 * In-memory state. When we add Preferences DataStore later, this becomes the
 * mapped view of the persisted preferences flow — the screen doesn't change.
 */
data class SettingsUiState(
    val darkMode: Boolean = false,
    val dynamicColor: Boolean = true,
    val notifications: Boolean = true,
    val autoSync: Boolean = true,
)
