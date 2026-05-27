package com.checkingcontainer.feature.units

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
internal fun EquipmentDataCard(
    state: UnitEntryUiState,
    onEvent: (UnitEntryEvent) -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionTitle("Datos del Equipo")
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { onEvent(UnitEntryEvent.OpenScanner(ScannerMode.DATA_PLATE)) },
                    enabled = state.isContainerValid && !state.isLookingUpCatalog,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.isLookingUpCatalog) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Buscando en catálogo…")
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.CameraAlt,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Escanear Placa de Datos")
                    }
                }
            }
            if (state.unitModelNo.isNotBlank()) {
                ManufacturerBadge(state.brand)
            }
            OutlinedTextField(
                value = state.unitModelNo,
                onValueChange = { onEvent(UnitEntryEvent.UnitModelNoChange(it)) },
                label = { Text("Unit model No.") },
                singleLine = true,
                isError = state.showUnitModelNoError,
                supportingText = if (state.showUnitModelNoError) {
                    { Text("Campo obligatorio") }
                } else null,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    imeAction = ImeAction.Next,
                ),
            )
            if (state.unitModel.isNotBlank()) {
                OutlinedTextField(
                    value = state.unitModel,
                    onValueChange = {},
                    label = { Text("Unit Model") },
                    singleLine = true,
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (state.unitType.isNotBlank()) {
                OutlinedTextField(
                    value = state.unitType,
                    onValueChange = {},
                    label = { Text("Unit Type") },
                    singleLine = true,
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            OutlinedTextField(
                value = state.unitSerialNo,
                onValueChange = { onEvent(UnitEntryEvent.UnitSerialNoChange(it)) },
                label = { Text("Unit Serial No.") },
                singleLine = true,
                isError = state.showUnitSerialNoError,
                supportingText = if (state.showUnitSerialNoError) {
                    { Text("Campo obligatorio") }
                } else null,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    imeAction = ImeAction.Next,
                ),
            )
            OutlinedTextField(
                value = state.yearOfBuilt,
                onValueChange = { v ->
                    if (v.length <= 4) onEvent(UnitEntryEvent.YearOfBuiltChange(v.filter(Char::isDigit)))
                },
                label = { Text("Year of Built") },
                singleLine = true,
                isError = state.showYearOfBuiltError,
                supportingText = if (state.showYearOfBuiltError) {
                    { Text("Campo obligatorio") }
                } else null,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ),
            )
        }
    }
}
