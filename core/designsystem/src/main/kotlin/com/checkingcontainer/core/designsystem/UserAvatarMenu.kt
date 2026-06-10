package com.checkingcontainer.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.checkingcontainer.core.model.User

/**
 * Botón circular con iniciales del usuario en el TopAppBar.
 * Al tocarlo muestra un menú con nombre, cargo, Ajustes y Cerrar sesión.
 */
@Composable
fun UserAvatarMenu(
    user: User,
    onSettingsClick: () -> Unit,
    onLogout: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    val initials = buildString {
        user.firstName.firstOrNull()?.uppercaseChar()?.let(::append)
        user.lastName.firstOrNull()?.uppercaseChar()?.let(::append)
    }.ifEmpty { "?" }

    Box {
        Box(
            modifier = Modifier
                .padding(end = 8.dp)
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .clickable { expanded = true },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = initials,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            // Encabezado: nombre y cargo (no clickeable)
            DropdownMenuItem(
                text = {
                    Column {
                        Text(user.fullName, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = user.jobTitle.display,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                onClick = {},
                enabled = false,
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Ajustes") },
                leadingIcon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                onClick = {
                    expanded = false
                    onSettingsClick()
                },
            )
            DropdownMenuItem(
                text = {
                    Text("Cerrar sesión", color = MaterialTheme.colorScheme.error)
                },
                leadingIcon = {
                    Icon(
                        Icons.AutoMirrored.Outlined.Logout,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                },
                onClick = {
                    expanded = false
                    onLogout()
                },
            )
        }
    }
}
