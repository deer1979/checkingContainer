package com.checkingcontainer.feature.units

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.checkingcontainer.core.model.Client

/**
 * Selector de cliente para el estimado: búsqueda sobre el catálogo (por nombre
 * o identificación) + creación rápida sin salir del estimado (mismo formulario
 * validado de la sección Clientes).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ClientPickerSheet(
    clients: List<Client>,
    isSaving: Boolean,
    onSelect: (Client) -> Unit,
    onCreate: (Client) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var creando by remember { mutableStateOf(false) }
    var nuevo by remember { mutableStateOf(Client(razonSocial = "")) }

    val filtrados = remember(clients, query) {
        if (query.isBlank()) clients
        else clients.filter {
            it.razonSocial.contains(query, ignoreCase = true) || it.idNumber.contains(query)
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!creando) {
                Text("Seleccionar cliente", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Buscar por nombre o RUC") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    singleLine = true,
                )
                if (filtrados.isEmpty()) {
                    Text(
                        if (clients.isEmpty()) "Aún no hay clientes en el catálogo." else "Sin coincidencias.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(Modifier.heightIn(max = 320.dp)) {
                        items(filtrados, key = { it.id }) { client ->
                            ListItem(
                                headlineContent = { Text(client.razonSocial) },
                                supportingContent = {
                                    if (client.idNumber.isNotEmpty()) {
                                        Text("${client.idType.etiqueta()}: ${client.idNumber}")
                                    }
                                },
                                modifier = Modifier.clickable { onSelect(client) },
                            )
                            HorizontalDivider()
                        }
                    }
                }
                OutlinedButton(
                    onClick = { creando = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null)
                    Text("Nuevo cliente")
                }
            } else {
                Text("Nuevo cliente", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                ClientFormFields(
                    client = nuevo,
                    onChange = { nuevo = it },
                    modifier = Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                )
                Button(
                    onClick = { onCreate(nuevo) },
                    enabled = !isSaving && nuevo.esGuardable(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (isSaving) "Guardando…" else "Guardar y asignar al estimado")
                }
                OutlinedButton(
                    onClick = { creando = false },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Volver a la búsqueda")
                }
            }
        }
    }
}
