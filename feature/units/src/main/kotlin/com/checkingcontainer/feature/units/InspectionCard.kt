package com.checkingcontainer.feature.units

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.checkingcontainer.core.designsystem.theme.chipColors
import com.checkingcontainer.core.model.InspStatus
import com.checkingcontainer.core.model.PtiInstruction
import com.checkingcontainer.core.model.UnitType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun InspectionCard(
    state: UnitEntryUiState,
    onEvent: (UnitEntryEvent) -> Unit,
) {
    val isDark = isSystemInDarkTheme()

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
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
                if (state.deployedAs == null) {
                    Text(
                        text = "Selecciona el tipo de despliegue para poder guardar",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
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
