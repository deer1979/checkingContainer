package com.checkingcontainer.feature.units

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.LaunchedEffect
import com.checkingcontainer.core.model.Client
import com.checkingcontainer.core.model.ClientIdType
import com.checkingcontainer.core.model.IdentificacionEc

// ── Lista de clientes (sección "Clientes" desde Ajustes) ─────────────────────────

@Composable
fun ClientesListRoute(
    onBack: () -> Unit,
    onClientClick: (Long) -> Unit,
    onNewClient: () -> Unit,
    viewModel: ClientesListViewModel = hiltViewModel(),
) {
    val clients by viewModel.clients.collectAsStateWithLifecycle()
    ClientesListScreen(
        clients = clients,
        onBack = onBack,
        onClientClick = onClientClick,
        onNewClient = onNewClient,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClientesListScreen(
    clients: List<Client>,
    onBack: () -> Unit,
    onClientClick: (Long) -> Unit,
    onNewClient: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Clientes") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNewClient) {
                Icon(Icons.Outlined.Add, contentDescription = "Nuevo cliente")
            }
        },
    ) { innerPadding ->
        if (clients.isEmpty()) {
            Box(
                Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Outlined.Business,
                        contentDescription = null,
                        modifier = Modifier.padding(bottom = 8.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Sin clientes registrados",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp),
            ) {
                items(clients, key = { it.id }) { client ->
                    ElevatedCard(
                        Modifier.fillMaxWidth().clickable { onClientClick(client.id) },
                    ) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(client.razonSocial, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            if (client.idNumber.isNotEmpty()) {
                                Text(
                                    "${client.idType.etiqueta()}: ${client.idNumber}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (client.telefono.isNotEmpty() || client.email.isNotEmpty()) {
                                Text(
                                    listOf(client.telefono, client.email).filter { it.isNotEmpty() }.joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Formulario de cliente ────────────────────────────────────────────────────────

@Composable
fun ClientFormRoute(
    onBack: () -> Unit,
    viewModel: ClientFormViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved) { if (state.saved) onBack() }
    ClientFormScreen(
        state = state,
        onClientChange = viewModel::onClientChange,
        onSave = viewModel::save,
        onDeactivate = viewModel::deactivate,
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClientFormScreen(
    state: ClientFormUiState,
    onClientChange: (Client) -> Unit,
    onSave: () -> Unit,
    onDeactivate: () -> Unit,
    onBack: () -> Unit,
) {
    val esEdicion = state.client.id > 0
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (esEdicion) "Editar cliente" else "Nuevo cliente") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ClientFormFields(
                client = state.client,
                onChange = onClientChange,
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            )

            state.errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }

            Button(
                onClick = onSave,
                enabled = !state.isSaving && state.client.esGuardable(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.isSaving) "Guardando…" else "Guardar cliente")
            }
            if (esEdicion) {
                OutlinedButton(
                    onClick = onDeactivate,
                    enabled = !state.isSaving,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Eliminar cliente", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

// ── Campos del formulario (compartidos con el selector del estimado) ────────────

internal fun ClientIdType.etiqueta(): String = when (this) {
    ClientIdType.RUC -> "RUC"
    ClientIdType.CEDULA -> "Cédula"
    ClientIdType.PASAPORTE -> "Pasaporte"
}

/** Guardable: razón social + identificación válida (dígito verificador SRI). */
internal fun Client.esGuardable(): Boolean =
    razonSocial.isNotBlank() && IdentificacionEc.valida(idType, idNumber)

@Composable
internal fun ClientFormFields(
    client: Client,
    onChange: (Client) -> Unit,
    modifier: Modifier = Modifier,
) {
    val idValida = IdentificacionEc.valida(client.idType, client.idNumber)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Asistente: pegar texto (WhatsApp) o foto de factura → pre-llenar.
        ClientAssistRow(client = client, onChange = onChange)
        OutlinedTextField(
            value = client.razonSocial,
            onValueChange = { onChange(client.copy(razonSocial = it)) },
            label = { Text("Razón social / Nombres") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ClientIdType.entries.forEach { tipo ->
                FilterChip(
                    selected = client.idType == tipo,
                    onClick = { onChange(client.copy(idType = tipo)) },
                    label = { Text(tipo.etiqueta()) },
                )
            }
        }
        OutlinedTextField(
            value = client.idNumber,
            onValueChange = { nuevo ->
                val filtrado = if (client.idType == ClientIdType.PASAPORTE) {
                    nuevo.uppercase().trim()
                } else {
                    nuevo.filter(Char::isDigit)
                }
                onChange(client.copy(idNumber = filtrado))
            },
            label = { Text("Número de ${client.idType.etiqueta()}") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = client.idNumber.isNotEmpty() && !idValida,
            supportingText = {
                if (client.idNumber.isNotEmpty() && !idValida) {
                    Text(
                        when (client.idType) {
                            ClientIdType.RUC -> "RUC inválido (13 dígitos, verificador SRI)"
                            ClientIdType.CEDULA -> "Cédula inválida (10 dígitos, verificador)"
                            ClientIdType.PASAPORTE -> "Requerido"
                        },
                    )
                }
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = if (client.idType == ClientIdType.PASAPORTE) KeyboardType.Text else KeyboardType.Number,
            ),
        )
        OutlinedTextField(
            value = client.email,
            onValueChange = { onChange(client.copy(email = it.trim())) },
            label = { Text("Email (para la factura electrónica)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        )
        OutlinedTextField(
            value = client.direccion,
            onValueChange = { onChange(client.copy(direccion = it)) },
            label = { Text("Dirección") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        OutlinedTextField(
            value = client.telefono,
            onValueChange = { onChange(client.copy(telefono = it)) },
            label = { Text("Teléfono") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        )
        OutlinedTextField(
            value = client.contacto,
            onValueChange = { onChange(client.copy(contacto = it)) },
            label = { Text("Persona de contacto") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
        )
        OutlinedTextField(
            value = client.notas,
            onValueChange = { onChange(client.copy(notas = it)) },
            label = { Text("Notas") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
        )
    }
}
