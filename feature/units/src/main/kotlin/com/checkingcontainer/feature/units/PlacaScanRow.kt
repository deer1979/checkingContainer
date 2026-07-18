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
import androidx.compose.material.icons.outlined.Close
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
import com.checkingcontainer.core.model.CampoFicha
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
    onResult: (Map<String, String>, List<CampoFicha>) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var procesando by remember { mutableStateOf(false) }
    var pendingCameraUri by rememberSaveable { mutableStateOf<String?>(null) }

    fun procesar(uri: Uri) {
        scope.launch {
            procesando = true
            runCatching { PlacaEquipoExtractor.desdeImagen(context, uri, tipo) }
                .onSuccess { r ->
                    // No pisar un código ya escrito a mano.
                    val filtrado = if (codigoActual.isNotBlank()) r.fields - "Container No." else r.fields
                    if (filtrado.isNotEmpty() || r.ficha.isNotEmpty()) onResult(filtrado, r.ficha)
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
                "Encuadra la placa completa y bien iluminada: se lee TODA la placa " +
                    "(ficha técnica) y se sugiere el código del equipo.",
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

/**
 * Ficha técnica del equipo: todos los datos leídos de la placa, editable
 * antes de guardar (la X quita un par mal leído o irrelevante).
 */
@Composable
internal fun FichaTecnicaCard(
    ficha: List<CampoFicha>,
    onRemove: (Int) -> Unit,
) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Ficha técnica (placa)", style = MaterialTheme.typography.titleSmall)
            Text(
                "Leída de la placa — quita con la X lo que no aplique.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ficha.forEachIndexed { index, campo ->
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    Text(
                        campo.etiqueta,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(0.42f),
                    )
                    Text(
                        campo.valor,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(0.58f),
                    )
                    androidx.compose.material3.IconButton(
                        onClick = { onRemove(index) },
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = "Quitar dato",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
