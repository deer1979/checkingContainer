package com.checkingcontainer.feature.units

import android.content.Intent
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.activity.compose.BackHandler
import androidx.core.content.FileProvider
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.AttachMoney
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import java.io.File
import com.checkingcontainer.core.model.DamageItem
import com.checkingcontainer.core.model.DamageItemStatus
import com.checkingcontainer.core.model.DiagnosticoRefrigeracion
import com.checkingcontainer.core.model.EstimadoStatus
import com.checkingcontainer.core.model.EstimadoTotals
import com.checkingcontainer.core.model.MAX_FOTOS_POR_GRUPO
import com.checkingcontainer.core.model.MedicionSnapshot
import com.checkingcontainer.core.model.Severidad
import com.checkingcontainer.core.model.TipoExpansion
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


// Tarjeta de datos del cliente dentro del estimado: selección del catálogo,
// identificación, contacto, sitio del trabajo y fechas.

@Composable
internal fun EstimadoClienteCard(
    state: EstimadoUiState,
    onEvent: (EstimadoEvent) -> Unit,
    onSelectClientClick: () -> Unit,
    onSelectSitioClick: () -> Unit,
) {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    EstimadoSectionTitle("Cliente")
                    if (state.clientName.isNotEmpty()) {
                        InfoRow("Nombre", state.clientName)
                        if (state.clientIdNumber.isNotEmpty()) InfoRow("RUC/CI", state.clientIdNumber)
                        if (state.clientTelefono.isNotEmpty()) InfoRow("Teléfono", state.clientTelefono)
                    } else {
                        Text(
                            "Sin cliente asignado",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (state.status != EstimadoStatus.CERRADO) {
                        OutlinedButton(
                            onClick = onSelectClientClick,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(if (state.clientName.isEmpty()) "Seleccionar cliente" else "Cambiar cliente")
                        }
                    }

                    // Sitio del trabajo (cliente final) — opcional, para trabajos
                    // vía contratante. Solo nombre; el PDF lo imprime aparte.
                    if (state.sitioNombre.isNotEmpty()) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                InfoRow("Trabajo en", state.sitioNombre)
                            }
                            if (state.status != EstimadoStatus.CERRADO) {
                                IconButton(
                                    onClick = { onEvent(EstimadoEvent.ClearSitio) },
                                    modifier = Modifier.size(28.dp),
                                ) {
                                    Icon(
                                        Icons.Outlined.Close,
                                        contentDescription = "Quitar sitio",
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    } else if (state.status != EstimadoStatus.CERRADO) {
                        OutlinedButton(
                            onClick = onSelectSitioClick,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Sitio del trabajo (opcional)")
                        }
                    }
                    OutlinedTextField(
                        value = state.ordenTrabajo,
                        onValueChange = { onEvent(EstimadoEvent.OrdenTrabajoChange(it)) },
                        label = { Text("Orden de trabajo / Referencia Nº") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = state.status != EstimadoStatus.CERRADO,
                    )
                    OutlinedTextField(
                        value = state.location,
                        onValueChange = { onEvent(EstimadoEvent.LocationChange(it)) },
                        label = { Text("Localidad") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                        enabled = state.status != EstimadoStatus.CERRADO,
                    )
                    if (state.technicianName.isNotEmpty()) {
                        InfoRow("Elaborado por", state.technicianName)
                    }
                    if (state.createdAt > 0) {
                        InfoRow("Fecha", formatFechaEstimado(state.createdAt))
                    }
                    if (state.approvedAt != null) {
                        InfoRow("Aprobado", formatFechaEstimado(state.approvedAt))
                    }
                }
            }
}
