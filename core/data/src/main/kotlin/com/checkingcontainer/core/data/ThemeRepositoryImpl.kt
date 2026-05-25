package com.checkingcontainer.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.checkingcontainer.core.domain.ThemeRepository
import com.checkingcontainer.core.model.ThemeConfig
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class ThemeRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : ThemeRepository {

    override val themeConfig: Flow<ThemeConfig> = dataStore.data.map { prefs ->
        runCatching { ThemeConfig.valueOf(prefs[KEY_THEME] ?: "") }
            .getOrDefault(ThemeConfig.FOLLOW_SYSTEM)
    }

    override val dynamicColor: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_DYNAMIC_COLOR] ?: true
    }

    override suspend fun setThemeConfig(config: ThemeConfig) {
        dataStore.edit { it[KEY_THEME] = config.name }
    }

    override suspend fun setDynamicColor(enabled: Boolean) {
        dataStore.edit { it[KEY_DYNAMIC_COLOR] = enabled }
    }

    companion object {
        val KEY_THEME = stringPreferencesKey("theme_config")
        val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
    }
}