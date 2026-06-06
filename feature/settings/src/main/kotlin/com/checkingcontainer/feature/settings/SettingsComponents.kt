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
