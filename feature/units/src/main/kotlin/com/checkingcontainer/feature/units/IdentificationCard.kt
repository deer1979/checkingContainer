package com.checkingcontainer.feature.units

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.checkingcontainer.core.designsystem.theme.chipColors
import com.checkingcontainer.core.model.InspStatus

@Composable
internal fun IdentificationCard(
    state: UnitEntryUiState,
    onEvent: (UnitEntryEvent) -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    val validColor = InspStatus.OP.chipColors(isDark).container
    val focusManager = LocalFocusManager.current

    var containerFocused by remember { mutableStateOf(false) }
    val containerFontSize by animateFloatAsState(
        targetValue = if (containerFocused) 20f else 16f,
        label = "containerFontSize",
    )

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionTitle(
                text = "Identificación",
                isComplete = if (state.containerNo.isNotEmpty()) state.isContainerValid else null,
            )
            OutlinedTextField(
                value = state.containerNo,
                onValueChange = { v ->
                    // Recortar a 11 en vez de descartar toda la entrada: si el campo ya
                    // tiene 11 caracteres (p. ej. tras escanear o al editar una unidad),
                    // rechazar la tecla congelaba la edición. Con take(11) se puede
                    // corregir borrando, seleccionando-y-reemplazando o insertando.
                    val upper = v.uppercase().take(11)
                    if (upper != state.containerNo) {
                        onEvent(UnitEntryEvent.ContainerNoChange(upper))
                        if (upper.length == 11 && Iso6346.isValid(upper)) {
                            focusManager.moveFocus(FocusDirection.Down)
                        }
                    }
                },
                label = { Text("Container No.") },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = containerFontSize.sp),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { containerFocused = it.isFocused },
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
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) },
                ),
                trailingIcon = {
                    if (state.containerNo.isNotEmpty()) {
                        IconButton(onClick = { onEvent(UnitEntryEvent.ContainerNoChange("")) }) {
                            Icon(Icons.Outlined.Clear, contentDescription = "Limpiar")
                        }
                    } else {
                        IconButton(
                            onClick = { onEvent(UnitEntryEvent.OpenOrientationPicker) },
                        ) {
                            Icon(Icons.Outlined.CameraAlt, contentDescription = "Escanear")
                        }
                    }
                },
            )
        }
    }
}
