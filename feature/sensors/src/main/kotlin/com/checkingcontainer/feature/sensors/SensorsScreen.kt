package com.checkingcontainer.feature.sensors

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.BluetoothSearching
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SensorsRoute(
    onBack: () -> Unit,
    viewModel: SensorsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val permisos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        if (result.values.all { it }) viewModel.toggleEscaneo()
    }

    SensorsScreen(
        state = state,
        onBack = onBack,
        onToggleScan = { launcher.launch(permisos) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SensorsScreen(
    state: SensorsUiState,
    onBack: () -> Unit,
    onToggleScan: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mediciones") },
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
        bottomBar = {
            Button(
                onClick = onToggleScan,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            ) {
                Icon(
                    if (state.escaneando) Icons.Outlined.BluetoothSearching else Icons.Outlined.Bluetooth,
                    contentDescription = null,
                )
                Text(
                    if (state.escaneando) "  Buscando… (tocar para detener)" else "  Conectar sensores",
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "cabecera") { Cabecera(state.containerNo) }

            if (state.bluetoothApagado) {
                item(key = "bt-off") {
                    Text(
                        "Activa el Bluetooth para detectar los sensores.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            if (!state.hayDatos) {
                item(key = "vacio") {
                    Text(
                        if (state.escaneando) "Buscando sensores cercanos…"
                        else "Toca \"Conectar sensores\" y enciende los instrumentos.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            if (state.presiones.isNotEmpty()) {
                item(key = "h-presion") { SeccionTitulo("Presiones") }
                items(state.presiones, key = { "p-" + it.deviceName }) { TarjetaPresion(it) }
            }
            if (state.temperaturas.isNotEmpty()) {
                item(key = "h-temp") { SeccionTitulo("Temperaturas") }
                items(state.temperaturas, key = { "t-" + it.deviceName }) { TarjetaTemperatura(it) }
            }
            if (state.corrientes.isNotEmpty()) {
                item(key = "h-amp") { SeccionTitulo("Corriente") }
                items(state.corrientes, key = { "a-" + it.deviceName }) { TarjetaCorriente(it) }
            }
        }
    }
}

@Composable
private fun Cabecera(containerNo: String) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                containerNo.ifBlank { "Equipo" },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                FECHA.format(Date()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SeccionTitulo(t: String) {
    Text(
        t,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
    )
}

/** Tarjeta de una sola columna: número grande izq. + últimas 5 tomas der. */
@Composable
private fun TarjetaMedicion(
    titulo: String,
    deviceName: String,
    valor: String,
    unidad: String,
    historial: List<String>,
) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(titulo, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(valor, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                    Text(
                        " $unidad",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
                Text(
                    deviceName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "Últimas tomas",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                historial.takeLast(5).reversed().forEach {
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

private fun fmt(v: Double): String =
    if (v == SensorReading.SIN_DATO) "—" else String.format(Locale.US, "%.1f", v)

@Composable
private fun TarjetaPresion(t: TarjetaSensor) {
    // valor1 / valor2 = las dos presiones; el técnico decide alta/baja (pendiente UI selector)
    TarjetaMedicion(
        titulo = "Presión",
        deviceName = t.deviceName,
        valor = fmt(t.ultima.valor1),
        unidad = "PSI",
        historial = t.historial.map { fmt(it.valor1) },
    )
}

@Composable
private fun TarjetaTemperatura(t: TarjetaSensor) {
    TarjetaMedicion(
        titulo = "Temperatura",
        deviceName = t.deviceName,
        valor = fmt(t.ultima.valor1),
        unidad = "°C",
        historial = t.historial.map { fmt(it.valor1) },
    )
}

@Composable
private fun TarjetaCorriente(t: TarjetaSensor) {
    TarjetaMedicion(
        titulo = "Corriente",
        deviceName = t.deviceName,
        valor = fmt(t.ultima.valor1),
        unidad = "A",
        historial = t.historial.map { fmt(it.valor1) },
    )
}

private val FECHA = SimpleDateFormat("dd/MM/yyyy · HH:mm", Locale.getDefault())
