package com.testo3.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun SettingsRoute(viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SettingsScreen(
        state = state,
        onToggleDarkMode = viewModel::onToggleDarkMode,
        onToggleDynamicColor = viewModel::onToggleDynamicColor,
        onToggleNotifications = viewModel::onToggleNotifications,
        onToggleAutoSync = viewModel::onToggleAutoSync,
        onLogout = viewModel::onLogout,
    )
}

@Composable
private fun SettingsScreen(
    state: SettingsUiState,
    onToggleDarkMode: (Boolean) -> Unit,
    onToggleDynamicColor: (Boolean) -> Unit,
    onToggleNotifications: (Boolean) -> Unit,
    onToggleAutoSync: (Boolean) -> Unit,
    onLogout: () -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item { SectionHeader("Apariencia") }

        item {
            SettingsRow(
                title = "Modo oscuro",
                description = "Override del sistema (forzar tema oscuro)",
                icon = Icons.Outlined.DarkMode,
                checked = state.darkMode,
                onCheckedChange = onToggleDarkMode,
            )
        }
        item { HorizontalDivider() }

        item {
            SettingsRow(
                title = "Color dinámico",
                description = "Material You — colores tomados del fondo de pantalla",
                icon = Icons.Outlined.Palette,
                checked = state.dynamicColor,
                onCheckedChange = onToggleDynamicColor,
            )
        }
        item { HorizontalDivider() }

        item { SectionHeader("Sincronización") }

        item {
            SettingsRow(
                title = "Notificaciones",
                description = "Avisos sobre nuevos anuncios y tareas próximas",
                icon = Icons.Outlined.Notifications,
                checked = state.notifications,
                onCheckedChange = onToggleNotifications,
            )
        }
        item { HorizontalDivider() }

        item {
            SettingsRow(
                title = "Sincronización automática",
                description = "Cuando conectemos la nube, esto la activa por defecto",
                icon = Icons.Outlined.Sync,
                checked = state.autoSync,
                onCheckedChange = onToggleAutoSync,
            )
        }
        item { HorizontalDivider() }

        item { SectionHeader("Acerca de") }

        item {
            ListItem(
                headlineContent = { Text("Privacidad") },
                supportingContent = { Text("Todo se procesa en el dispositivo. Cero red.") },
                leadingContent = { Icon(Icons.Outlined.PrivacyTip, contentDescription = null) },
            )
        }
        item { HorizontalDivider() }

        item {
            ListItem(
                headlineContent = { Text("Versión") },
                supportingContent = { Text("0.3.0 · debug build") },
                leadingContent = { Icon(Icons.Outlined.Info, contentDescription = null) },
            )
        }
        item { HorizontalDivider() }

        item { SectionHeader("Sesión") }

        item {
            ListItem(
                headlineContent = { Text("Cerrar sesión") },
                supportingContent = { Text("Vuelves a la pantalla de login.") },
                leadingContent = {
                    Icon(
                        Icons.AutoMirrored.Outlined.Logout,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                },
                colors = ListItemDefaults.colors(
                    headlineColor = MaterialTheme.colorScheme.error,
                ),
                modifier = Modifier.clickable(onClick = onLogout),
            )
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun SettingsRow(
    title: String,
    description: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(description) },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        },
    )
}
