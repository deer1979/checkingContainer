package com.checkingcontainer.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.checkingcontainer.core.model.ThemeConfig

@Composable
internal fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp),
    )
}

@Composable
internal fun SettingsRow(
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
        trailingContent = { Switch(checked = checked, onCheckedChange = onCheckedChange) },
    )
}

@Composable
internal fun ThemeItem(selected: ThemeConfig, onThemeChange: (ThemeConfig) -> Unit) {
    ListItem(
        headlineContent = { Text("Tema") },
        supportingContent = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 4.dp),
            ) {
                ThemeConfig.entries.forEach { config ->
                    FilterChip(
                        selected = selected == config,
                        onClick = { onThemeChange(config) },
                        label = {
                            Text(
                                when (config) {
                                    ThemeConfig.FOLLOW_SYSTEM -> "Sistema"
                                    ThemeConfig.LIGHT -> "Claro"
                                    ThemeConfig.DARK -> "Oscuro"
                                },
                            )
                        },
                    )
                }
            }
        },
        leadingContent = { Icon(Icons.Outlined.DarkMode, contentDescription = null) },
    )
}

@Composable
internal fun SyncStatusItem(status: com.checkingcontainer.core.domain.SyncStatus?) {
    val okAt = status?.lastOkAt ?: 0L
    val pendingAt = status?.lastPendingAt ?: 0L
    val errorAt = status?.lastErrorAt ?: 0L
    val latest = maxOf(okAt, pendingAt, errorAt)
    val (title, detail, isProblem) = when {
        latest == 0L -> Triple("Sincronización", "Sin actividad registrada aún", false)
        latest == errorAt -> Triple(
            "Sincronización: error",
            status?.lastErrorMessage ?: "Error desconocido",
            true,
        )
        latest == pendingAt -> Triple(
            "Sincronización: pendiente",
            "Hay cambios en cola; se subirán al recuperar conexión",
            false,
        )
        else -> Triple(
            "Sincronización: al día",
            "Último envío ${SYNC_DATE_FORMAT.format(java.util.Date(okAt))}",
            false,
        )
    }
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(detail) },
        leadingContent = {
            Icon(
                imageVector = if (isProblem) Icons.Outlined.CloudOff else Icons.Outlined.Cloud,
                contentDescription = null,
                tint = if (isProblem) MaterialTheme.colorScheme.error
                       else MaterialTheme.colorScheme.primary,
            )
        },
    )
}

private val SYNC_DATE_FORMAT =
    java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale.getDefault())

@Composable
internal fun CloudStatusItem(connected: Boolean, description: String) {
    ListItem(
        headlineContent = {
            Text(if (connected) "Nube: conectada" else "Nube: sin conexion")
        },
        supportingContent = { Text(description) },
        leadingContent = {
            Icon(
                imageVector = if (connected) Icons.Outlined.Cloud else Icons.Outlined.CloudOff,
                contentDescription = null,
                tint = if (connected)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.error,
            )
        },
    )
}
