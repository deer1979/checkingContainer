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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.BluetoothSearching
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
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
        onToggleRol = viewModel::toggleRol,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SensorsScreen(
    state: SensorsUiState,
    onBack: () -> Unit,
    onToggleScan: () -> Unit,
    onToggleRol: (String, SensorType, Int) -> Unit,
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
                state.presiones.forEach { t ->
                    // Cada presión (puerto) se renderiza por separado y se marca alta/baja.
                    for (idx in 0..1) {
                        val valor = if (idx == 0) t.ultima.valor1 else t.ultima.valor2
                        if (idx == 1 && !t.ultima.tieneValor2) continue
                        item(key = "p-${t.deviceName}-$idx") {
                            TarjetaRol(
                                rol = state.rolDe(t.deviceName, SensorType.PRESION, idx),
                                etiquetaAlta = "Alta",
                                etiquetaBaja = "Baja",
                                valor = fmt(YjackParser.aPsig(valor)),
                                unidad = "PSIG",
                                deviceName = t.deviceName,
                                historial = t.historial.map {
                                    fmt(YjackParser.aPsig(if (idx == 0) it.valor1 else it.valor2))
                                },
                                onToggle = { onToggleRol(t.deviceName, SensorType.PRESION, idx) },
                            )
                        }
                    }
                }
            }
            if (state.temperaturas.isNotEmpty()) {
                item(key = "h-temp") { SeccionTitulo("Temperaturas") }
                state.temperaturas.forEach { t ->
                    for (idx in 0..1) {
                        val valor = if (idx == 0) t.ultima.valor1 else t.ultima.valor2
                        if (idx == 1 && !t.ultima.tieneValor2) continue
                        item(key = "t-${t.deviceName}-$idx") {
                            TarjetaRol(
                                rol = state.rolDe(t.deviceName, SensorType.TEMPERATURA, idx),
                                etiquetaAlta = "Descarga",
                                etiquetaBaja = "Succión",
                                valor = fmt(YjackParser.aCelsius(valor)),
                                unidad = "°C",
                                deviceName = t.deviceName,
                                historial = t.historial.map {
                                    fmt(YjackParser.aCelsius(if (idx == 0) it.valor1 else it.valor2))
                                },
                                onToggle = { onToggleRol(t.deviceName, SensorType.TEMPERATURA, idx) },
                            )
                        }
                    }
                }
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

// Colores de rol: ALTA (descarga/líquido) rojo · BAJA (succión/vapor) celeste.
private val COLOR_ALTA = Color(0xFFD32F2F)
private val COLOR_BAJA = Color(0xFF03A9F4)

/**
 * Tarjeta compacta coloreada por rol. Chip arriba para alternar ALTA/BAJA;
 * toda la tarjeta toma el color del rol con texto blanco en negrita.
 */
@Composable
private fun TarjetaRol(
    rol: RolMedicion,
    etiquetaAlta: String,
    etiquetaBaja: String,
    valor: String,
    unidad: String,
    deviceName: String,
    historial: List<String>,
    onToggle: () -> Unit,
) {
    val bg = if (rol == RolMedicion.ALTA) COLOR_ALTA else COLOR_BAJA
    val etiqueta = if (rol == RolMedicion.ALTA) etiquetaAlta else etiquetaBaja
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = bg),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                // Chip selector: tocar para cambiar alta/baja.
                Surface(
                    onClick = onToggle,
                    color = Color.White.copy(alpha = 0.25f),
                    shape = RoundedCornerShape(50),
                ) {
                    Row(
                        Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Outlined.SwapHoriz,
                            contentDescription = "Cambiar alta/baja",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            "  $etiqueta",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(valor, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(
                        " $unidad",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.padding(bottom = 3.dp),
                    )
                }
                Text(
                    deviceName,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                historial.takeLast(5).reversed().forEach {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.9f))
                }
            }
        }
    }
}

private fun fmt(v: Double): String =
    if (v == SensorReading.SIN_DATO) "—" else String.format(Locale.US, "%.1f", v)

/** Corriente: tarjeta compacta neutra (sin rol alta/baja). */
@Composable
private fun TarjetaCorriente(t: TarjetaSensor) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Corriente", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(fmt(t.ultima.valor1), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(" A", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 3.dp))
                }
                Text(t.deviceName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment = Alignment.End) {
                t.historial.map { fmt(it.valor1) }.takeLast(5).reversed().forEach {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private val FECHA = SimpleDateFormat("dd/MM/yyyy · HH:mm", Locale.getDefault())
