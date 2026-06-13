package com.checkingcontainer.feature.sensors

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
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
    Scaffold { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Barra superior mínima: volver + identidad/estado + conexión. Sin
            // encabezado grande ni app bar, para dejar la info "pulida".
            item(key = "barra") {
                BarraSuperior(
                    containerNo = state.containerNo,
                    escaneando = state.escaneando,
                    hayDatos = state.hayDatos,
                    onBack = onBack,
                    onToggleScan = onToggleScan,
                )
            }

            if (state.bluetoothApagado) {
                item(key = "bt-off") {
                    Text(
                        "Activa el Bluetooth para detectar los sensores.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            // En refrigeración el consumo de corriente es clave → va ARRIBA.
            item(key = "h-amp") { SeccionTitulo("Corriente") }
            item(key = "corriente") { TarjetaCorriente(state.corriente) }

            // Tarjetas FIJAS: siempre presentes (grises/vacías sin datos, con color
            // al conectar). No se reconstruyen al reiniciar el escaneo.
            item(key = "h-presion") { SeccionTitulo("Presiones") }
            item(key = "presion") {
                FilaAltaBaja(
                    tarjeta = state.presion,
                    tipo = SensorType.PRESION,
                    etiquetaAlta = "Alta",
                    etiquetaBaja = "Baja",
                    unidad = "PSIG",
                    convertir = YjackParser::aPsig,
                    rolDe = state::rolDe,
                    onToggleRol = onToggleRol,
                )
            }
            item(key = "h-temp") { SeccionTitulo("Temperaturas") }
            item(key = "temp") {
                FilaAltaBaja(
                    tarjeta = state.temperatura,
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
}

/** Barra superior compacta: volver · identidad + estado · conectar/detener. */
@Composable
private fun BarraSuperior(
    containerNo: String,
    escaneando: Boolean,
    hayDatos: Boolean,
    onBack: () -> Unit,
    onToggleScan: () -> Unit,
) {
    val (icono, tint) = when {
        !escaneando -> Icons.Outlined.Bluetooth to MaterialTheme.colorScheme.onSurfaceVariant
        hayDatos -> Icons.Outlined.BluetoothConnected to MaterialTheme.colorScheme.primary
        else -> Icons.Outlined.BluetoothSearching to MaterialTheme.colorScheme.primary
    }
    val (punto, estado) = when {
        !escaneando -> MaterialTheme.colorScheme.onSurfaceVariant to "Conecte su dispositivo →"
        hayDatos -> Color(0xFF2E7D32) to "Recibiendo datos"
        else -> MaterialTheme.colorScheme.primary to "Buscando…"
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Atrás")
        }
        Column(Modifier.weight(1f)) {
            Text(
                containerNo.ifBlank { "Equipo" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(punto, androidx.compose.foundation.shape.CircleShape))
                Text(
                    "  $estado",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = onToggleScan) {
            Icon(icono, contentDescription = "Conectar o detener sensores", tint = tint)
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

// Colores de rol: ALTA (descarga/líquido) rojo · BAJA (succión/vapor) celeste ·
// corriente verde. Sin datos = gris (surfaceVariant).
private val COLOR_ALTA = Color(0xFFD32F2F)
private val COLOR_BAJA = Color(0xFF03A9F4)
private val COLOR_CORRIENTE = Color(0xFF2E7D32)
private const val SLOTS = 5

/** Lista de tomas para mostrar: más reciente primero, rellena a 5 con "·". */
private fun tomasParaMostrar(
    historial: List<SensorReading>,
    selector: (SensorReading) -> Double,
    convertir: (Double) -> Double,
): List<String> {
    val recientesPrimero = historial.asReversed().map { fmt(convertir(selector(it))) }
    return (recientesPrimero + List(SLOTS) { "·" }).take(SLOTS)
}

/**
 * Fila con las dos lecturas (valor1/valor2) lado a lado. Siempre renderiza las dos
 * tarjetas; si no hay datos las muestra grises/vacías (no se destruyen).
 */
@Composable
private fun FilaAltaBaja(
    tarjeta: TarjetaSensor?,
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
            val activo = tarjeta != null && (idx == 0 || tarjeta.ultima.tieneValor2)
            val rol = if (tarjeta != null) rolDe(tarjeta.deviceName, tipo, idx)
                      else if (idx == 0) RolMedicion.ALTA else RolMedicion.BAJA
            val valorActual = if (activo) {
                if (idx == 0) tarjeta!!.ultima.valor1 else tarjeta!!.ultima.valor2
            } else SensorReading.SIN_DATO
            val tomas = if (activo) {
                tomasParaMostrar(tarjeta!!.historial, { if (idx == 0) it.valor1 else it.valor2 }, convertir)
            } else List(SLOTS) { "·" }
            TarjetaRol(
                activo = activo,
                rol = rol,
                etiquetaAlta = etiquetaAlta,
                etiquetaBaja = etiquetaBaja,
                valor = if (activo) fmt(convertir(valorActual)) else "—",
                unidad = unidad,
                deviceName = if (activo) tarjeta!!.deviceName else "",
                tomas = tomas,
                onToggle = { if (tarjeta != null) onToggleRol(tarjeta.deviceName, tipo, idx) },
            )
        }
    }
}

/**
 * Tarjeta compacta (media columna). Color por rol (rojo/celeste) cuando hay datos,
 * gris cuando no. Chip para alternar ALTA/BAJA; número en vivo; tomas en columna a
 * la derecha (la más reciente arriba).
 */
@Composable
private fun RowScope.TarjetaRol(
    activo: Boolean,
    rol: RolMedicion,
    etiquetaAlta: String,
    etiquetaBaja: String,
    valor: String,
    unidad: String,
    deviceName: String,
    tomas: List<String>,
    onToggle: () -> Unit,
) {
    val bg = when {
        !activo -> MaterialTheme.colorScheme.surfaceVariant
        rol == RolMedicion.ALTA -> COLOR_ALTA
        else -> COLOR_BAJA
    }
    val fg = if (activo) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
    val etiqueta = if (rol == RolMedicion.ALTA) etiquetaAlta else etiquetaBaja
    ElevatedCard(
        modifier = Modifier.weight(1f),
        colors = CardDefaults.elevatedCardColors(containerColor = bg),
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Surface(
                    onClick = onToggle,
                    color = fg.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(50),
                ) {
                    Row(
                        Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.SwapHoriz, contentDescription = "Cambiar alta/baja", tint = fg, modifier = Modifier.size(16.dp))
                        Text("  $etiqueta", style = MaterialTheme.typography.labelMedium, color = fg, fontWeight = FontWeight.Bold)
                    }
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(valor, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = fg)
                    Text(" $unidad", style = MaterialTheme.typography.bodySmall, color = fg.copy(alpha = 0.9f), modifier = Modifier.padding(bottom = 3.dp))
                }
                if (deviceName.isNotEmpty()) {
                    Text(deviceName, style = MaterialTheme.typography.labelSmall, color = fg.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            // Tomas cada 5 s, columna vertical, la más reciente arriba.
            Column(horizontalAlignment = Alignment.End) {
                tomas.forEach {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = fg.copy(alpha = 0.85f))
                }
            }
        }
    }
}

private fun fmt(v: Double): String =
    if (v == SensorReading.SIN_DATO) "—" else String.format(Locale.US, "%.1f", v)

/** Corriente: tarjeta de ancho completo, verde con datos, gris sin datos. */
@Composable
private fun TarjetaCorriente(tarjeta: TarjetaSensor?) {
    val activo = tarjeta != null
    val bg = if (activo) COLOR_CORRIENTE else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (activo) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
    val tomas = if (activo) tomasParaMostrar(tarjeta!!.historial, { it.valor1 }, { it }) else List(SLOTS) { "·" }
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = bg),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Corriente", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = fg)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(if (activo) fmt(tarjeta!!.ultima.valor1) else "—", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = fg)
                    Text(" A", style = MaterialTheme.typography.bodyMedium, color = fg.copy(alpha = 0.9f), modifier = Modifier.padding(bottom = 3.dp))
                }
                if (activo) {
                    Text(tarjeta!!.deviceName, style = MaterialTheme.typography.labelSmall, color = fg.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                tomas.forEach {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = fg.copy(alpha = 0.85f))
                }
            }
        }
    }
}

private val FECHA = SimpleDateFormat("dd/MM/yyyy · HH:mm", Locale.getDefault())
