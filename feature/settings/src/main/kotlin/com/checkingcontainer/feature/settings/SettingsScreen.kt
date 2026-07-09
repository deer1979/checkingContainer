package com.checkingcontainer.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.checkingcontainer.core.model.ThemeConfig

@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    isAdmin: Boolean = false,
    onUsersClick: () -> Unit = {},
    onClientsClick: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SettingsScreen(
        state = state,
        onBack = onBack,
        isAdmin = isAdmin,
        onUsersClick = onUsersClick,
        onClientsClick = onClientsClick,
        onThemeChange = viewModel::onThemeChange,
        onToggleDynamicColor = viewModel::onDynamicColorChange,
        onToggleNotifications = viewModel::onToggleNotifications,
        onToggleAutoSync = viewModel::onToggleAutoSync,
        onLogout = viewModel::onLogout,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    state: SettingsUiState,
    onBack: () -> Unit,
    isAdmin: Boolean = false,
    onUsersClick: () -> Unit = {},
    onClientsClick: () -> Unit = {},
    onThemeChange: (ThemeConfig) -> Unit,
    onToggleDynamicColor: (Boolean) -> Unit,
    onToggleNotifications: (Boolean) -> Unit,
    onToggleAutoSync: (Boolean) -> Unit,
    onLogout: () -> Unit,
) {
    val context = LocalContext.current
    val versionName = remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "—"
        }.getOrDefault("—")
    }
    // Estado real de Gemini Nano (llamada async al servicio AICore del sistema).
    val aiCore by produceState(initialValue = AI_CORE_CHECKING) {
        value = aiCoreInfo()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ajustes") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Volver")
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            item { SectionHeader("Apariencia") }
            item { ThemeItem(state.theme, onThemeChange) }
            item { HorizontalDivider() }
            item {
                SettingsRow(
                    title = "Color dinamico",
                    description = "Material You — colores tomados del fondo de pantalla",
                    icon = Icons.Outlined.Palette,
                    checked = state.dynamicColor,
                    onCheckedChange = onToggleDynamicColor,
                )
            }
            item { HorizontalDivider() }

            item { SectionHeader("Sincronizacion") }
            item { CloudStatusItem(state.remoteConnected, state.remoteBackendDescription) }
            item { SyncStatusItem(state.syncStatus) }
            item { HorizontalDivider() }
            item {
                SettingsRow(
                    title = "Notificaciones",
                    description = "Avisos sobre nuevos anuncios y tareas proximas",
                    icon = Icons.Outlined.Notifications,
                    checked = state.notifications,
                    onCheckedChange = onToggleNotifications,
                )
            }
            item { HorizontalDivider() }
            item {
                SettingsRow(
                    title = "Sincronizacion automatica",
                    description = "Cuando conectemos la nube, esto la activa por defecto",
                    icon = Icons.Outlined.Sync,
                    checked = state.autoSync,
                    onCheckedChange = onToggleAutoSync,
                )
            }
            item { HorizontalDivider() }

            item { SectionHeader("Catálogos") }
            item {
                ListItem(
                    headlineContent = { Text("Clientes") },
                    supportingContent = { Text("Datos de facturación de tus clientes (SRI)") },
                    leadingContent = { Icon(Icons.Outlined.Business, contentDescription = null) },
                    modifier = Modifier.clickable(onClick = onClientsClick),
                )
            }
            item { HorizontalDivider() }

            if (isAdmin) {
                item { SectionHeader("Administración") }
                item {
                    ListItem(
                        headlineContent = { Text("Gestión de usuarios") },
                        supportingContent = { Text("Crear, editar y desactivar usuarios") },
                        leadingContent = { Icon(Icons.Outlined.Group, contentDescription = null) },
                        modifier = Modifier.clickable(onClick = onUsersClick),
                    )
                }
                item { HorizontalDivider() }
            }

            item { SectionHeader("Acerca de") }
            item {
                ListItem(
                    headlineContent = { Text("IA local (Gemini Nano)") },
                    supportingContent = { Text(aiCore.description) },
                    leadingContent = { Icon(Icons.Outlined.AutoAwesome, contentDescription = null) },
                    trailingContent = {
                        Text(
                            text = aiCore.statusLabel,
                            color = if (aiCore.available) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    },
                )
            }
            item { HorizontalDivider() }
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
                    headlineContent = { Text("Version") },
                    supportingContent = { Text(versionName) },
                    leadingContent = { Icon(Icons.Outlined.Info, contentDescription = null) },
                )
            }
            item { HorizontalDivider() }

            item { SectionHeader("Sesion") }
            item {
                ListItem(
                    headlineContent = { Text("Cerrar sesion") },
                    supportingContent = { Text("Vuelves a la pantalla de login.") },
                    leadingContent = {
                        Icon(
                            Icons.AutoMirrored.Outlined.Logout,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                        )
                    },
                    colors = ListItemDefaults.colors(headlineColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.clickable(onClick = onLogout),
                )
            }
        }
    }
}
