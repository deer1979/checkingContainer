package com.checkingcontainer.core.domain

import com.checkingcontainer.core.model.ThemeConfig
import kotlinx.coroutines.flow.Flow

interface ThemeRepository {
    val themeConfig: Flow<ThemeConfig>
    val dynamicColor: Flow<Boolean>
    suspend fun setThemeConfig(config: ThemeConfig)
    suspend fun setDynamicColor(enabled: Boolean)
}