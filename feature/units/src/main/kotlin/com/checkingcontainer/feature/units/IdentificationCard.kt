package com.checkingcontainer.feature.units

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.checkingcontainer.core.designsystem.theme.chipColors
import com.checkingcontainer.core.model.InspStatus

@Composable
internal fun IdentificationCard(
    state: UnitEntryUiState,
    onEvent: (UnitEntryEvent) -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    val validColor = InspStatus.OP.chipColors(isDark).container

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionTitle("Identificación")
            OutlinedTextField(
                value = state.containerNo,
                onValueChange = { v ->
                    if (v.length <= 11) onEvent(UnitEntryEvent.ContainerNoChange(v))
                },
                label = { Text("Container No.") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                isError = state.showContainerError,
                supportingText = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = when {
                                state.showContainerError -> "Formato ISO 6346 inválido"
                                state.isContainerValid -> "Número válido"
                                else -> ""
                            },
                            color = when {
                                state.showContainerError -> MaterialTheme.colorScheme.error
                                state.isContainerValid -> validColor
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                        Text(
                            text = "${state.containerNo.length}/11",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                colors = if (state.isContainerValid) {
                    OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = validColor,
                        unfocusedBorderColor = validColor,
                        focusedLabelColor = validColor,
                    )
                } else {
                    OutlinedTextFieldDefaults.colors()
                },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    imeAction = ImeAction.Next,
                ),
                trailingIcon = {
                    if (state.containerNo.isNotEmpty()) {
                        IconButton(onClick = { onEvent(UnitEntryEvent.ContainerNoChange("")) }) {
                            Icon(Icons.Outlined.Clear, contentDescription = "Limpiar")
                        }
                    } else {
                        IconButton(
                            onClick = { onEvent(UnitEntryEvent.OpenScanner(ScannerMode.CONTAINER)) },
                        ) {
                            Icon(Icons.Outlined.CameraAlt, contentDescription = "Escanear")
                        }
                    }
                },
            )
        }
    }
}
