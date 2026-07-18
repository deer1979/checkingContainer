package com.checkingcontainer.feature.units

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
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
    fotoUrl: String?,
    analizando: Boolean,
    metodo: String?,
    onFoto: (Uri) -> Unit,
    onReanalizar: () -> Unit,
) {
    val context = LocalContext.current
    var pendingCameraUri by rememberSaveable { mutableStateOf<String?>(null) }

    val galeria = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let(onFoto)
    }
    val camara = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = pendingCameraUri?.let(Uri::parse)
        pendingCameraUri = null
        if (ok && uri != null) onFoto(uri)
    }

    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Escanear placa de datos", style = MaterialTheme.typography.titleSmall)
            Text(
                "La foto queda guardada con el equipo y el análisis corre en " +
                    "segundo plano: puedes seguir llenando mientras.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (fotoUrl != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    coil3.compose.AsyncImage(
                        model = fotoUrl,
                        contentDescription = "Foto de la placa",
                        modifier = Modifier
                            .size(72.dp)
                            .clip(
                                androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                            ),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    )
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (analizando) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Text("Analizando placa…", style = MaterialTheme.typography.bodySmall)
                            }
                        } else {
                            metodo?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (it.startsWith("No se pudo")) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.primary
                                    },
                                )
                            }
                            OutlinedButton(onClick = onReanalizar) { Text("Volver a leer placa") }
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        val file = File(context.cacheDir, "placa_${System.currentTimeMillis()}.jpg")
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                        pendingCameraUri = uri.toString()
                        camara.launch(uri)
                    },
                    enabled = !analizando,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Outlined.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(" Cámara")
                }
                OutlinedButton(
                    onClick = { galeria.launch("image/*") },
                    enabled = !analizando,
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
    onUpdate: (Int, CampoFicha) -> Unit = { _, _ -> },
    onAdd: (CampoFicha) -> Unit = {},
) {
    // index en edición: -1 = agregar nuevo, null = cerrado
    var editando by remember { mutableStateOf<Int?>(null) }

    editando?.let { idx ->
        val original = if (idx >= 0) ficha.getOrNull(idx) else null
        var etiqueta by remember(idx) { mutableStateOf(original?.etiqueta ?: "") }
        var valor by remember(idx) { mutableStateOf(original?.valor ?: "") }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { editando = null },
            title = { Text(if (idx >= 0) "Editar dato" else "Agregar dato") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.OutlinedTextField(
                        value = etiqueta,
                        onValueChange = { etiqueta = it },
                        label = { Text("Etiqueta") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    androidx.compose.material3.OutlinedTextField(
                        value = valor,
                        onValueChange = { valor = it },
                        label = { Text("Valor") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    enabled = etiqueta.isNotBlank() && valor.isNotBlank(),
                    onClick = {
                        val campo = CampoFicha(etiqueta.trim(), valor.trim())
                        if (idx >= 0) onUpdate(idx, campo) else onAdd(campo)
                        editando = null
                    },
                ) { Text("Aceptar") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { editando = null }) { Text("Cancelar") }
            },
        )
    }

    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Ficha técnica (placa)", style = MaterialTheme.typography.titleSmall)
            Text(
                "Toca un dato para corregirlo; la X lo quita.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ficha.forEachIndexed { index, campo ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { editando = index },
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
            OutlinedButton(
                onClick = { editando = -1 },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Agregar dato") }
        }
    }
}
