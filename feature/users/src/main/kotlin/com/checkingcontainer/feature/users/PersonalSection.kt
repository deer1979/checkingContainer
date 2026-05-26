package com.checkingcontainer.feature.users

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

@Composable
internal fun PersonalSection(
    state: UserFormUiState,
    onFirstNameChange: (String) -> Unit,
    onLastNameChange: (String) -> Unit,
    onPinChange: (String) -> Unit,
    onTogglePin: () -> Unit,
    onConfirmPinChange: (String) -> Unit,
    onToggleConfirmPin: () -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionTitle("Datos personales y acceso")
            OutlinedTextField(
                value = state.firstName,
                onValueChange = onFirstNameChange,
                label = { Text("Nombre") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )
            OutlinedTextField(
                value = state.lastName,
                onValueChange = onLastNameChange,
                label = { Text("Apellido") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )
            NickReadonlyField(state.previewNick)
            PinFormField(
                label = "PIN de 6 dígitos",
                pin = state.pin,
                visible = state.pinVisible,
                onChange = onPinChange,
                onToggle = onTogglePin,
                imeAction = ImeAction.Next,
            )
            PinFormField(
                label = "Confirmar PIN",
                pin = state.confirmPin,
                visible = state.confirmPinVisible,
                onChange = onConfirmPinChange,
                onToggle = onToggleConfirmPin,
                isError = state.confirmPin.isNotEmpty() && !state.pinsMatch,
                supportingText = if (state.confirmPin.isNotEmpty() && !state.pinsMatch) {
                    { Text("Los PINs no coinciden") }
                } else null,
                imeAction = ImeAction.Next,
            )
        }
    }
}

@Composable
private fun NickReadonlyField(nick: String) {
    Column {
        Text(
            text = "Nick de acceso",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = nick.ifBlank { "—" },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun PinFormField(
    label: String,
    pin: String,
    visible: Boolean,
    onChange: (String) -> Unit,
    onToggle: () -> Unit,
    imeAction: ImeAction = ImeAction.Next,
    isError: Boolean = false,
    supportingText: (@Composable () -> Unit)? = null,
) {
    OutlinedTextField(
        value = pin,
        onValueChange = onChange,
        label = { Text(label) },
        isError = isError,
        supportingText = supportingText,
        trailingIcon = {
            IconButton(onClick = onToggle) {
                Icon(
                    imageVector = if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    contentDescription = if (visible) "Ocultar PIN" else "Mostrar PIN",
                )
            }
        },
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.NumberPassword,
            imeAction = imeAction,
        ),
    )
}
