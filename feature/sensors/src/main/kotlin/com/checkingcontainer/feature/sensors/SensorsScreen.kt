package com.checkingcontainer.feature.sensors

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.outlined.BluetoothConnected
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
            // El escaneo del advertising debe seguir activo para recibir lecturas
            // nuevas (no hay "conexión" fija). El texto refleja si ya llegan datos.
            val (icono, texto) = when {
                !state.escaneando -> Icons.Outlined.Bluetooth to "Conectar sensores"
                state.hayDatos -> Icons.Outlined.BluetoothConnected to "Conectado · recibiendo (tocar para detener)"
                else -> Icons.Outlined.BluetoothSearching to "Buscando… (tocar para detener)"
            }
            Button(
                onClick = onToggleScan,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            ) {
                Icon(icono, contentDescription = null)
                Text(texto, modifier = Modifier.padding(start = 8.dp))
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
                    // Alta y Baja lado a lado en la misma fila.
                    item(key = "p-${t.deviceName}") {
                        FilaAltaBaja(
                            tarjeta = t,
                            tipo = SensorType.PRESION,
                            etiquetaAlta = "Alta",
                            etiquetaBaja = "Baja",
                            unidad = "PSIG",
                            convertir = YjackParser::aPsig,
                            rolDe = state::rolDe,
                            onToggleRol = onToggleRol,
                        )
                    }
                }
            }
            if (state.temperaturas.isNotEmpty()) {
                item(key = "h-temp") { SeccionTitulo("Temperaturas") }
                state.temperaturas.forEach { t ->
                    item(key = "t-${t.deviceName}") {
                        FilaAltaBaja(
                            tarjeta = t,
                            tipo = SensorType.TEMPERATURA,
                            etiquetaAlta = "Descarga",
                            etiquetaBaja = "Succión",
                            unidad = "°C",
                            convertir = YjackParser::aCelsius,
                            rolDe = state::rolDe,
                            onToggleRol = onToggleRol,
                        )
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
private const val SLOTS = 5

/** Fila con las dos lecturas (valor1/valor2) lado a lado, cada una marcable alta/baja. */
@Composable
private fun FilaAltaBaja(
    tarjeta: TarjetaSensor,
    tipo: SensorType,
    etiquetaAlta: String,
    etiquetaBaja: String,
    unidad: String,
    convertir: (Double) -> Double,
    rolDe: (String, SensorType, Int) -> RolMedicion,
    onToggleRol: (String, SensorType, Int) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        for (idx in 0..1) {
            val tieneValor = if (idx == 0) true else tarjeta.ultima.tieneValor2
            if (!tieneValor) {
                Spacer(Modifier.weight(1f))
                continue
            }
            val valorActual = if (idx == 0) tarjeta.ultima.valor1 else tarjeta.ultima.valor2
            TarjetaRol(
                rol = rolDe(tarjeta.deviceName, tipo, idx),
                etiquetaAlta = etiquetaAlta,
                etiquetaBaja = etiquetaBaja,
                valor = fmt(convertir(valorActual)),
                unidad = unidad,
                tomas = tarjeta.historial.map { fmt(convertir(if (idx == 0) it.valor1 else it.valor2)) },
                onToggle = { onToggleRol(tarjeta.deviceName, tipo, idx) },
            )
        }
    }
}

/**
 * Tarjeta compacta coloreada por rol (media columna). Chip para alternar ALTA/BAJA;
 * número grande en vivo; abajo 5 slots con las tomas cada 5 s (vacíos al inicio).
 */
@Composable
private fun RowScope.TarjetaRol(
    rol: RolMedicion,
    etiquetaAlta: String,
    etiquetaBaja: String,
    valor: String,
    unidad: String,
    tomas: List<String>,
    onToggle: () -> Unit,
) {
    val bg = if (rol == RolMedicion.ALTA) COLOR_ALTA else COLOR_BAJA
    val etiqueta = if (rol == RolMedicion.ALTA) etiquetaAlta else etiquetaBaja
    ElevatedCard(
        modifier = Modifier.weight(1f),
        colors = CardDefaults.elevatedCardColors(containerColor = bg),
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Surface(
                onClick = onToggle,
                color = Color.White.copy(alpha = 0.25f),
                shape = RoundedCornerShape(50),
            ) {
                Row(
                    Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.SwapHoriz, contentDescription = "Cambiar alta/baja", tint = Color.White, modifier = Modifier.size(16.dp))
                    Text("  $etiqueta", style = MaterialTheme.typography.labelMedium, color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(valor, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color.White)
                Text(" $unidad", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.9f), modifier = Modifier.padding(bottom = 3.dp))
            }
            // 5 tomas (cada 5 s). Slots no llenos en blanco ("·").
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (i in 0 until SLOTS) {
                    Text(
                        tomas.getOrNull(i) ?: "·",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.85f),
                    )
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
