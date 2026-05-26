package com.checkingcontainer.feature.users

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun OperationalSection(
    state: UserFormUiState,
    onCompanyChange: (String) -> Unit,
    onLocationChange: (String) -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionTitle("Datos operativos")
            OutlinedTextField(
                value = state.company,
                onValueChange = onCompanyChange,
                label = { Text("Empresa o contratista") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.location,
                onValueChange = onLocationChange,
                label = { Text("Localidad / Depósito") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
