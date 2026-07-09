package com.checkingcontainer.feature.units

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.checkingcontainer.core.model.Client
import kotlinx.coroutines.launch

/**
 * Asistente de llenado del formulario de cliente: pega el texto que te mandó
 * el cliente (WhatsApp) o elige la foto de su factura, y los campos se
 * pre-llenan (IA local si hay; si no, OCR + patrones). El RUC/cédula extraído
 * ya viene validado por dígito verificador; el resto lo revisas tú.
 */
@Composable
internal fun ClientAssistRow(
    client: Client,
    onChange: (Client) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var procesando by remember { mutableStateOf(false) }
    var mostrarPegar by remember { mutableStateOf(false) }
    var textoPegado by remember { mutableStateOf("") }

    // Pre-llenar: lo extraído solo pisa campos con contenido nuevo; lo que ya
    // escribiste a mano y la extracción no trae, se conserva.
    fun aplicar(extraido: Client) {
        onChange(
            client.copy(
                razonSocial = extraido.razonSocial.ifEmpty { client.razonSocial },
                idType = if (extraido.idNumber.isNotEmpty()) extraido.idType else client.idType,
                idNumber = extraido.idNumber.ifEmpty { client.idNumber },
                email = extraido.email.ifEmpty { client.email },
                direccion = extraido.direccion.ifEmpty { client.direccion },
                telefono = extraido.telefono.ifEmpty { client.telefono },
            ),
        )
    }

    val galeria = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            procesando = true
            runCatching { ClientDataExtractor.desdeImagen(context, uri) }
                .onSuccess { aplicar(it) }
            procesando = false
        }
    }

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(
            onClick = { mostrarPegar = true },
            enabled = !procesando,
            modifier = Modifier.weight(1f),
        ) {
            Icon(Icons.Outlined.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(" Pegar datos")
        }
        OutlinedButton(
            onClick = { galeria.launch("image/*") },
            enabled = !procesando,
            modifier = Modifier.weight(1f),
        ) {
            if (procesando) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Outlined.Image, contentDescription = null, modifier = Modifier.size(18.dp))
            }
            Text(if (procesando) " Analizando…" else " Desde foto")
        }
    }

    if (mostrarPegar) {
        AlertDialog(
            onDismissRequest = { mostrarPegar = false },
            title = { Text("Pegar datos del cliente") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Pega el texto tal como te lo mandaron (WhatsApp, correo…). " +
                            "Se acomodará en los campos y tú revisas antes de guardar.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(
                        value = textoPegado,
                        onValueChange = { textoPegado = it },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 4,
                        maxLines = 8,
                        placeholder = { Text("Ej.: Naviera XYZ S.A.\nRUC 1790004562001\n099…") },
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = textoPegado.isNotBlank() && !procesando,
                    onClick = {
                        mostrarPegar = false
                        scope.launch {
                            procesando = true
                            runCatching { ClientDataExtractor.desdeTexto(textoPegado) }
                                .onSuccess { aplicar(it) }
                            procesando = false
                            textoPegado = ""
                        }
                    },
                ) { Text("Procesar") }
            },
            dismissButton = {
                TextButton(onClick = { mostrarPegar = false }) { Text("Cancelar") }
            },
        )
    }
}
