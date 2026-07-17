package com.checkingcontainer.feature.units

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.checkingcontainer.core.model.TipoEquipo
import kotlinx.coroutines.launch
import java.io.File

/**
 * Escaneo de placa para equipos NO-reefer: foto con la cámara o desde galería
 * → Gemini Nano (salida estructurada) o OCR extraen marca/modelo/serie/año y
 * sugieren el código de equipo desde el serial. Pre-llena; el usuario revisa.
 */
@Composable
internal fun PlacaScanRow(
    tipo: TipoEquipo,
    codigoActual: String,
    onResult: (Map<String, String>) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var procesando by remember { mutableStateOf(false) }
    var pendingCameraUri by rememberSaveable { mutableStateOf<String?>(null) }

    fun procesar(uri: Uri) {
        scope.launch {
            procesando = true
            runCatching { PlacaEquipoExtractor.desdeImagen(context, uri, tipo) }
                .onSuccess { fields ->
                    // No pisar un código ya escrito a mano.
                    val filtrado = if (codigoActual.isNotBlank()) fields - "Container No." else fields
                    if (filtrado.isNotEmpty()) onResult(filtrado)
                }
            procesando = false
        }
    }

    val galeria = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let(::procesar)
    }
    val camara = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = pendingCameraUri?.let(Uri::parse)
        pendingCameraUri = null
        if (ok && uri != null) procesar(uri)
    }

    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Escanear placa de datos",
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                "Encuadra la placa completa y bien iluminada: se extraen marca, " +
                    "modelo, serie y año, y se sugiere el código del equipo.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        val file = File(context.cacheDir, "placa_${System.currentTimeMillis()}.jpg")
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                        pendingCameraUri = uri.toString()
                        camara.launch(uri)
                    },
                    enabled = !procesando,
                    modifier = Modifier.weight(1f),
                ) {
                    if (procesando) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text(" Analizando…")
                    } else {
                        Icon(Icons.Outlined.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(" Cámara")
                    }
                }
                OutlinedButton(
                    onClick = { galeria.launch("image/*") },
                    enabled = !procesando,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Outlined.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(" Galería")
                }
            }
        }
    }
}
