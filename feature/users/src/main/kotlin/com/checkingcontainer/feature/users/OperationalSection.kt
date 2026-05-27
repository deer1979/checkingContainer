package com.checkingcontainer.feature.users

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
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
                isError = state.showCompanyError,
                supportingText = if (state.showCompanyError) {
                    { Text("Campo obligatorio") }
                } else null,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Next,
                ),
            )
            OutlinedTextField(
                value = state.location,
                onValueChange = onLocationChange,
                label = { Text("Localidad / Depósito") },
                singleLine = true,
                isError = state.showLocationError,
                supportingText = if (state.showLocationError) {
                    { Text("Campo obligatorio") }
                } else null,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Done,
                ),
            )
        }
    }
}
