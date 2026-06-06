package com.checkingcontainer.feature.units

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun EquipmentDataCard(
    state: UnitEntryUiState,
    onEvent: (UnitEntryEvent) -> Unit,
) {
    val focusManager = LocalFocusManager.current

    var modelFocused by remember { mutableStateOf(false) }
    var serialFocused by remember { mutableStateOf(false) }
    var yearFocused by remember { mutableStateOf(false) }

    val modelFontSize by animateFloatAsState(if (modelFocused) 20f else 16f, label = "model")
    val serialFontSize by animateFloatAsState(if (serialFocused) 20f else 16f, label = "serial")
    val yearFontSize by animateFloatAsState(if (yearFocused) 20f else 16f, label = "year")

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val equipmentComplete = state.unitModelNo.isNotBlank() &&
                state.unitSerialNo.isNotBlank() &&
                state.yearOfBuilt.isNotBlank()
            SectionTitle(
                text = "Datos del Equipo",
                isComplete = if (state.isContainerValid) equipmentComplete else null,
            )
            ScanDataPlateButton(
                isContainerValid = state.isContainerValid,
                isLookingUp = state.isLookingUpCatalog,
                onScan = { onEvent(UnitEntryEvent.OpenScanner(ScannerMode.DATA_PLATE)) },
            )
            if (state.unitModel.isNotBlank()) {
                ManufacturerBadge(state.brand)
            }
            OutlinedTextField(
                value = state.unitModelNo,
                onValueChange = { onEvent(UnitEntryEvent.UnitModelNoChange(it)) },
                label = { Text("Unit model No.") },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = modelFontSize.sp),
                isError = state.showUnitModelNoError || state.catalogError != null,
                supportingText = when {
                    state.showUnitModelNoError -> { { Text("Campo obligatorio") } }
                    state.catalogError != null -> { { Text(state.catalogError.orEmpty()) } }
                    else -> null
                },
                trailingIcon = {
                    if (!state.isLookingUpCatalog && state.unitModelNo.isNotBlank()) {
                        IconButton(onClick = {
                            focusManager.clearFocus()
                            onEvent(UnitEntryEvent.TriggerManualLookup)
                        }) {
                            Icon(
                                imageVector = Icons.Outlined.Search,
                                contentDescription = "Buscar en catálogo",
                                tint = if (state.catalogError != null)
                                    MaterialTheme.colorScheme.error
                                else
                                    MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { modelFocused = it.isFocused },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    imeAction = ImeAction.Search,
                ),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        focusManager.clearFocus()
                        onEvent(UnitEntryEvent.TriggerManualLookup)
                    },
                ),
            )
            if (state.unitModel.isNotBlank()) {
                OutlinedTextField(
                    value = state.unitModel,
                    onValueChange = {},
                    label = { Text("Unit Model") },
                    singleLine = true,
                    readOnly = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusProperties { canFocus = false },
                )
            }
            if (state.unitType.isNotBlank()) {
                OutlinedTextField(
                    value = state.unitType,
                    onValueChange = {},
                    label = { Text("Unit Type") },
                    singleLine = true,
                    readOnly = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusProperties { canFocus = false },
                )
            }
            OutlinedTextField(
                value = state.unitSerialNo,
                onValueChange = { onEvent(UnitEntryEvent.UnitSerialNoChange(it)) },
                label = { Text("Unit Serial No.") },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = serialFontSize.sp),
                isError = state.showUnitSerialNoError,
                supportingText = if (state.showUnitSerialNoError) {
                    { Text("Campo obligatorio") }
                } else null,
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { serialFocused = it.isFocused },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    imeAction = ImeAction.Next,
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) },
                ),
            )
            OutlinedTextField(
                value = state.yearOfBuilt,
                onValueChange = { v ->
                    val digits = v.filter(Char::isDigit)
                    if (digits.length <= 4) {
                        onEvent(UnitEntryEvent.YearOfBuiltChange(digits))
                        if (digits.length == 4) focusManager.clearFocus()
                    }
                },
                label = { Text("Year of Built") },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = yearFontSize.sp),
                isError = state.showYearOfBuiltError,
                supportingText = if (state.showYearOfBuiltError) {
                    { Text("Campo obligatorio") }
                } else null,
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { yearFocused = it.isFocused },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = { focusManager.clearFocus() },
                ),
            )
        }
    }
}
