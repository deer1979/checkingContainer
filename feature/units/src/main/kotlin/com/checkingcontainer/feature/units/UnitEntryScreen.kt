package com.checkingcontainer.feature.units

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.checkingcontainer.core.designsystem.R
import com.checkingcontainer.core.designsystem.theme.AppTheme
import com.checkingcontainer.core.designsystem.theme.chipColors
import com.checkingcontainer.core.model.InspStatus
import com.checkingcontainer.core.model.PtiInstruction
import com.checkingcontainer.core.model.UnitType

@Composable
fun UnitEntryRoute(
    onBack: () -> Unit,
    viewModel: UnitEntryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.savedSuccessfully) {
        if (state.savedSuccessfully) onBack()
    }

    UnitEntryScreen(
        state = state,
        onBack = onBack,
        onEvent = viewModel::onEvent,
        onSave = viewModel::saveUnit,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnitEntryScreen(
    state: UnitEntryUiState,
    onBack: () -> Unit,
    onEvent: (UnitEntryEvent) -> Unit,
    onSave: () -> Unit,
) {
    if (state.showScanner) {
        OcrScannerBottomSheet(
            mode = state.scannerMode,
            onSuccess = { fields -> onEvent(UnitEntryEvent.OcrResult(fields)) },
            onDismiss = { onEvent(UnitEntryEvent.CloseScanner) },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ingreso de Unidad") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Atrás")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onSave,
                icon = { Icon(Icons.Outlined.Save, contentDescription = null) },
                text = { Text(if (state.isSaving) "Guardando…" else "Guardar Unidad") },
                containerColor = if (state.canSave)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.surfaceVariant,
            )
        },
        modifier = Modifier.fillMaxSize(),
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            IdentificationCard(state = state, onEvent = onEvent)
            EquipmentDataCard(state = state, onEvent = onEvent)
            InspectionCard(state = state, onEvent = onEvent)
            if (state.errorMessage != null) {
                Text(
                    text = state.errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
private fun IdentificationCard(
    state: UnitEntryUiState,
    onEvent: (UnitEntryEvent) -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    val validColor = InspStatus.OP.chipColors(isDark).container

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
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
                    imeAction = ImeAction.Done,
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

@Composable
private fun EquipmentDataCard(
    state: UnitEntryUiState,
    onEvent: (UnitEntryEvent) -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionTitle("Datos del Equipo")
            OutlinedButton(
                onClick = { onEvent(UnitEntryEvent.OpenScanner(ScannerMode.DATA_PLATE)) },
                enabled = state.isContainerValid,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Outlined.CameraAlt,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Escanear Placa de Datos")
            }

            // Manufacturer selector
            FieldLabel("Fabricante")
            ManufacturerSelector(
                selected = state.unitType,
                onSelect = { onEvent(UnitEntryEvent.UnitTypeChange(it)) },
            )

            OutlinedTextField(
                value = state.unitModel,
                onValueChange = { onEvent(UnitEntryEvent.UnitModelChange(it)) },
                label = { Text("Unit Model") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )
            OutlinedTextField(
                value = state.unitSerialNo,
                onValueChange = { onEvent(UnitEntryEvent.UnitSerialNoChange(it)) },
                label = { Text("Unit Serial No.") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    imeAction = ImeAction.Next,
                ),
            )
            OutlinedTextField(
                value = state.yearOfBuilt,
                onValueChange = { onEvent(UnitEntryEvent.YearOfBuiltChange(it)) },
                label = { Text("Year of Built") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ),
            )
        }
    }
}

@Composable
private fun ManufacturerSelector(
    selected: UnitType,
    onSelect: (UnitType) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UnitType.entries.forEach { type ->
            val isSelected = type == selected
            val logoRes = when (type) {
                UnitType.CARRIER -> R.drawable.logo_carrier
                UnitType.STAR_COOL -> R.drawable.logo_starcool
                UnitType.THERMO_KING -> R.drawable.logo_thermoking
                UnitType.DAIKIN -> R.drawable.logo_daikin
            }
            Surface(
                color = Color.White,
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier
                    .height(36.dp)
                    .weight(1f)
                    .border(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.outlineVariant,
                        shape = RoundedCornerShape(6.dp),
                    )
                    .clickable { onSelect(type) },
            ) {
                Image(
                    painter = painterResource(id = logoRes),
                    contentDescription = type.label,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.padding(4.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InspectionCard(
    state: UnitEntryUiState,
    onEvent: (UnitEntryEvent) -> Unit,
) {
    val isDark = isSystemInDarkTheme()

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionTitle("Inspección")

            FieldLabel("Estado")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InspStatus.entries.forEach { status ->
                    val colors = status.chipColors(isDark)
                    FilterChip(
                        selected = state.status == status,
                        onClick = { onEvent(UnitEntryEvent.StatusChange(status)) },
                        label = { Text(status.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = colors.container,
                            selectedLabelColor = colors.onContainer,
                        ),
                    )
                }
            }

            FieldLabel("Instrucción PTI")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PtiInstruction.entries.forEach { pti ->
                    FilterChip(
                        selected = state.ptiInstruction == pti,
                        onClick = { onEvent(UnitEntryEvent.PtiInstructionChange(pti)) },
                        label = { Text(pti.label) },
                    )
                }
            }

            if (state.unitType == UnitType.STAR_COOL) {
                FieldLabel("Tipo de despliegue")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Estándar", "Atmósfera Controlada").forEach { option ->
                        FilterChip(
                            selected = state.deployedAs == option,
                            onClick = { onEvent(UnitEntryEvent.DeployedAsChange(option)) },
                            label = { Text(option) },
                        )
                    }
                }
            }

            OutlinedTextField(
                value = state.observations,
                onValueChange = { onEvent(UnitEntryEvent.ObservationsChange(it)) },
                label = { Text("Observaciones") },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun UnitEntryScreenPreview() {
    AppTheme {
        UnitEntryScreen(
            state = UnitEntryUiState(
                containerNo = "BMOU9012909",
                unitModel = "69NT40-531-J04",
                unitSerialNo = "KSB60036558",
                yearOfBuilt = "2008",
            ),
            onBack = {},
            onEvent = {},
            onSave = {},
        )
    }
}
