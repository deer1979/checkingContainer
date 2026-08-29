package com.checkingcontainer.feature.units

import com.checkingcontainer.core.model.Iso6346
import com.checkingcontainer.core.reporting.TipoDocumento
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
import com.checkingcontainer.core.model.ID_FOTOS_OBSERVACIONES
import com.checkingcontainer.core.model.MAX_FOTOS_POR_GRUPO
import com.checkingcontainer.core.model.MedicionSnapshot
import com.checkingcontainer.core.model.Severidad
import com.checkingcontainer.core.model.TipoExpansion
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


internal val USD = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-US")).apply {
    maximumFractionDigits = 2
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EstimadoScreen(
    state: EstimadoUiState,
    onBack: () -> Unit,
    onEvent: (EstimadoEvent) -> Unit,
    onSave: () -> Unit,
    onGeneratePdf: (TipoDocumento) -> Unit,
    onReintentarSubida: () -> Unit = {},
    onCargarCambios: () -> Unit = {},
    onSelectClientClick: () -> Unit = {},
    onSelectSitioClick: () -> Unit = {},
    onAddDamagePhoto: (String, Uri) -> Unit,
    onAddRepairPhoto: (String, Uri) -> Unit,
    onAddObservacionPhoto: (Uri) -> Unit = {},
    getPendingDamageDescription: () -> String,
    getPendingRepairAction: () -> String,
    getPendingCantidad: () -> String,
    getPendingPrecioUnitario: () -> String,
    getPendingManoDeObra: () -> String,
    getPendingNombreItem: () -> String,
    getPendingContenedor: () -> String = { "" },
    getPendingObservaciones: () -> String = { "" },
    onCerrar: () -> Unit = {},
    onReabrir: () -> Unit = {},
    onNuevoParaEquipo: () -> Unit = {},
    onNuevoParaEquipoConfirmado: () -> Unit = {},
    onDescartarConfirmacion: () -> Unit = {},
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    // Un solo diálogo antes de generar: qué documento se quiere y, si falta algo
    // del cliente, el aviso en el mismo sitio. Encadenar dos ventanas seguidas
    // para lo mismo es peor que juntarlas.
    var pidiendoDocumento by remember { mutableStateOf(false) }
    val faltantes = state.datosClienteFaltantes

    if (pidiendoDocumento) {
        AlertDialog(
            onDismissRequest = { pidiendoDocumento = false },
            title = { Text("¿Qué documento generar?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OpcionDocumento(
                        titulo = "Estimado",
                        detalle = "Con la tabla de valores, mano de obra e IVA. Para cotizar.",
                        onClick = {
                            pidiendoDocumento = false
                            onGeneratePdf(TipoDocumento.ESTIMADO)
                        },
                    )
                    OpcionDocumento(
                        titulo = "Informe de reparación",
                        detalle = "Sin valores. Constancia del trabajo hecho, con fotos y mediciones.",
                        onClick = {
                            pidiendoDocumento = false
                            onGeneratePdf(TipoDocumento.INFORME)
                        },
                    )
                    if (faltantes.isNotEmpty()) {
                        // Aviso, no bloqueo: a veces hace falta el PDF ya mismo.
                        Text(
                            "⚠  El encabezado saldrá incompleto. Falta: " +
                                faltantes.joinToString(", "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { pidiendoDocumento = false }) { Text("Cancelar") }
            },
        )
    }

    state.confirmarNuevoConAbierto?.let { aviso ->
        AlertDialog(
            onDismissRequest = onDescartarConfirmacion,
            title = { Text("Ya hay un estimado abierto") },
            text = { Text(aviso) },
            confirmButton = {
                TextButton(onClick = onNuevoParaEquipoConfirmado) { Text("Crear igual") }
            },
            dismissButton = {
                TextButton(onClick = onDescartarConfirmacion) { Text("Cancelar") }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (state.containerNo.isNotEmpty()) "Estimado — ${state.containerNo}"
                        else "Estimado",
                    )
                },
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
            if (!state.isLoading) {
                BottomAppBar(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .shadow(elevation = 8.dp, shape = RoundedCornerShape(28.dp))
                        .clip(RoundedCornerShape(28.dp)),
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    windowInsets = WindowInsets(0),
                ) {
                    BottomBarBtn(
                        icon = { Icon(Icons.AutoMirrored.Outlined.ArrowBack, null, Modifier.size(24.dp)) },
                        label = "Atrás",
                        onClick = onBack,
                    )
                    if (state.status != EstimadoStatus.CERRADO) {
                        BottomBarBtn(
                            icon = {
                                if (state.isSaving)
                                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                                else
                                    Icon(Icons.Outlined.Save, null, Modifier.size(24.dp))
                            },
                            label = if (state.isSaving) "Guardando…" else "Guardar",
                            onClick = onSave,
                            enabled = !state.isSaving,
                        )
                    }
                    if (state.estimadoId != 0L && state.damages.isNotEmpty()) {
                        BottomBarBtn(
                            icon = {
                                if (state.isGeneratingPdf)
                                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                                else
                                    Icon(Icons.Outlined.Share, null, Modifier.size(24.dp))
                            },
                            label = if (state.isGeneratingPdf) "Generando…" else "Ver PDF",
                            onClick = { pidiendoDocumento = true },
                            enabled = !state.isGeneratingPdf,
                        )
                    }
                }
            }
        },
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0),
    ) { innerPadding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        // Launchers compartidos para fotos: un solo par galería/cámara para toda
        // la pantalla (antes se creaban 4 por cada ítem de daño). El destino
        // pendiente sobrevive a process death (la cámara puede matar la app).
        val context = LocalContext.current
        var pendingPhotoItemId by rememberSaveable { mutableStateOf<String?>(null) }
        var pendingPhotoIsRepair by rememberSaveable { mutableStateOf(false) }
        var pendingCameraUri by rememberSaveable { mutableStateOf<String?>(null) }

        // Las fotos de observaciones viajan por los mismos lanzadores usando el
        // id reservado; así no hay que duplicar cámara y galería.
        val entregarFoto: (String, Uri) -> Unit = { itemId, uri ->
            when {
                itemId == ID_FOTOS_OBSERVACIONES -> onAddObservacionPhoto(uri)
                pendingPhotoIsRepair -> onAddRepairPhoto(itemId, uri)
                else -> onAddDamagePhoto(itemId, uri)
            }
        }
        val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            val itemId = pendingPhotoItemId
            if (uri != null && itemId != null) entregarFoto(itemId, uri)
            pendingPhotoItemId = null
        }
        val capturePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            val itemId = pendingPhotoItemId
            val uri = pendingCameraUri?.let(Uri::parse)
            if (success && itemId != null && uri != null) entregarFoto(itemId, uri)
            pendingPhotoItemId = null
            pendingCameraUri = null
        }
        val requestGalleryPhoto: (String, Boolean) -> Unit = { itemId, isRepair ->
            pendingPhotoItemId = itemId
            pendingPhotoIsRepair = isRepair
            pickPhoto.launch("image/*")
        }
        val requestCameraPhoto: (String, Boolean) -> Unit = { itemId, isRepair ->
            val uri = createCameraUri(context)
            pendingPhotoItemId = itemId
            pendingPhotoIsRepair = isRepair
            pendingCameraUri = uri.toString()
            capturePhoto.launch(uri)
        }

        // LazyColumn con ítems independientes: teclear en un campo solo recompone
        // su propio ítem, no la pantalla completa.
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // El compañero guardó mientras yo tenía esto abierto. No se recarga
            // solo: puedo estar escribiendo. Decido yo cuándo traerlo.
            if (state.hayCambiosDelCompanero) {
                item(key = "cambios-companero", contentType = "aviso") {
                    ElevatedCard(
                        Modifier.fillMaxWidth(),
                        colors = androidx.compose.material3.CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        ),
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "Tu compañero actualizó este estimado",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                )
                                Text(
                                    "Tus cambios sin guardar no se pierden al cargarlos.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                )
                            }
                            FilledTonalButton(onClick = onCargarCambios) { Text("Ver cambios") }
                        }
                    }
                }
            }

            // Aviso de que el trabajo está solo en el teléfono. Va arriba del
            // todo porque es lo primero que el técnico debe saber al abrirlo.
            if (state.pendienteDeSubir) {
                item(key = "pendiente", contentType = "aviso") {
                    ElevatedCard(
                        Modifier.fillMaxWidth(),
                        colors = androidx.compose.material3.CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                        ),
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "Sin subir a la nube",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                )
                                Text(
                                    "Este estimado está guardado en el teléfono. " +
                                        "Se subirá solo cuando haya señal.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                )
                            }
                            FilledTonalButton(onClick = onReintentarSubida, enabled = !state.isSaving) {
                                Text("Reintentar")
                            }
                        }
                    }
                }
            }

            // ── CLIENTE ──────────────────────────────────────────────────────────
            item(key = "cliente", contentType = "card") {
                EstimadoClienteCard(
                    state = state,
                    onEvent = onEvent,
                    onSelectClientClick = onSelectClientClick,
                    onSelectSitioClick = onSelectSitioClick,
                )
            }

            // ── EQUIPO ───────────────────────────────────────────────────────────
            item(key = "equipo", contentType = "card") {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        EstimadoSectionTitle("Datos del equipo")
                        InfoRow(if (Iso6346.isValid(state.containerNo)) "No. Contenedor" else "Código de equipo", state.containerNo)
                        if (state.unitSerialNo.isNotEmpty()) InfoRow("No. Serie", state.unitSerialNo)
                        if (state.manufacturer.isNotEmpty()) InfoRow("Fabricante", state.manufacturer)
                        if (state.unitModel.isNotEmpty()) InfoRow("Modelo", state.unitModel)
                        if (state.unitModelNo.isNotEmpty()) InfoRow("No. Modelo", state.unitModelNo)
                        if (state.yearOfBuilt.isNotEmpty()) InfoRow("Año", state.yearOfBuilt)
                        if (state.unitType.isNotEmpty()) InfoRow("Tipo", state.unitType)
                        if (state.status != EstimadoStatus.CERRADO) {
                            OutlinedButton(
                                onClick = { onEvent(EstimadoEvent.ShowSheet(EstimadoSheet.CorregirEquipo)) },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Corregir equipo") }
                        }
                    }
                }
            }

            // ── MEDICIONES BLE (capturadas desde la pantalla de sensores) ────────
            if (state.mediciones.isNotEmpty()) {
                item(key = "mediciones", contentType = "card") {
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            EstimadoSectionTitle("Mediciones")
                            state.mediciones.forEach { m ->
                                MedicionRow(
                                    medicion = m,
                                    canRemove = state.status != EstimadoStatus.CERRADO,
                                    onRemove = { onEvent(EstimadoEvent.RemoveMedicion(m.timestamp)) },
                                )
                            }
                        }
                    }
                }
            }

            // ── DAÑOS ────────────────────────────────────────────────────────────
            item(key = "danos-header", contentType = "header") {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        EstimadoSectionTitle("Daños encontrados")
                        if (state.damages.isEmpty()) {
                            Text(
                                "Sin ítems de daño aún.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            itemsIndexed(
                items = state.damages,
                key = { _, item -> item.id },
                contentType = { _, _ -> "damage" },
            ) { index, item ->
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        DamageItemCard(
                            item = item,
                            index = index + 1,
                            estimadoSaved = state.estimadoId != 0L,
                            isUploading = state.isUploadingPhoto,
                            isClosed = state.status == EstimadoStatus.CERRADO,
                            onEditDescriptionClick = { onEvent(EstimadoEvent.ShowSheet(EstimadoSheet.EditDamage(item.id))) },
                            onRepairClick = { onEvent(EstimadoEvent.ShowSheet(EstimadoSheet.RepairItem(item.id))) },
                            onRemoveClick = { onEvent(EstimadoEvent.RemoveDamageItem(item.id)) },
                            onRemoveDamagePhoto = { url -> onEvent(EstimadoEvent.RemoveDamagePhoto(item.id, url)) },
                            onRemoveRepairPhoto = { url -> onEvent(EstimadoEvent.RemoveRepairPhoto(item.id, url)) },
                            onRequestGalleryPhoto = { isRepair -> requestGalleryPhoto(item.id, isRepair) },
                            onRequestCameraPhoto = { isRepair -> requestCameraPhoto(item.id, isRepair) },
                        )
                    }
                }
            }

            if (state.status != EstimadoStatus.CERRADO) {
                item(key = "danos-add", contentType = "button") {
                    OutlinedButton(
                        onClick = { onEvent(EstimadoEvent.ShowSheet(EstimadoSheet.AddDamage)) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Agregar daño")
                    }
                }
            }

            // ── VALORES ──────────────────────────────────────────────────────────
            if (state.damages.isNotEmpty()) {
                item(key = "valores", contentType = "card") {
                    ValoresSummaryCard(
                        damages = state.damages,
                        hasIva = state.hasIva,
                        isClosed = state.status == EstimadoStatus.CERRADO,
                        manoDeObraTotal = state.manoDeObraTotal,
                        onIvaToggle = { onEvent(EstimadoEvent.IvaToggle(it)) },
                        onEditValor = { itemId -> onEvent(EstimadoEvent.ShowSheet(EstimadoSheet.EditValor(itemId))) },
                        onEditManoDeObra = { onEvent(EstimadoEvent.ShowSheet(EstimadoSheet.EditManoDeObra)) },
                    )
                }
            }

            item(key = "observaciones", contentType = "card") {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        EstimadoSectionTitle("Observaciones y recomendaciones")
                        Text(
                            state.observaciones.ifBlank {
                                "Sin observaciones. Aquí puedes avisarle al cliente de algo " +
                                    "que viste y no se cobra en este trabajo."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (state.observaciones.isBlank()) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                        if (!state.estaCerrado) {
                            OutlinedButton(
                                onClick = { onEvent(EstimadoEvent.ShowSheet(EstimadoSheet.EditarObservaciones)) },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(if (state.observaciones.isBlank()) "Agregar observaciones" else "Editar observaciones") }
                        }

                        // Evidencia de lo advertido: al cliente hay que mostrarle
                        // el condensador sucio, no solo contárselo. Necesita el
                        // estimado guardado, porque las fotos cuelgan de su carpeta.
                        if (state.estimadoId != 0L) {
                            PhotoGroup(
                                titulo = "Fotos de las observaciones",
                                fotos = state.observacionesFotos,
                                isUploading = state.isUploadingPhoto,
                                puedeAgregar = !state.estaCerrado &&
                                    state.observacionesFotos.size < MAX_FOTOS_POR_GRUPO,
                                puedeEliminar = !state.estaCerrado,
                                onRemove = { url -> onEvent(EstimadoEvent.RemoveObservacionPhoto(url)) },
                                onGallery = { requestGalleryPhoto(ID_FOTOS_OBSERVACIONES, false) },
                                onCamera = { requestCameraPhoto(ID_FOTOS_OBSERVACIONES, false) },
                            )
                        }
                    }
                }
            }

            // Cerrar / reabrir y el trabajo siguiente del mismo equipo. La
            // tarjeta solo aparece cuando tiene algo que ofrecer.
            val puedeClonar = state.estimadoId != 0L && state.containerNo.isNotBlank()
            if (state.estaCerrado || state.todoReparado || puedeClonar) {
                item(key = "acciones-estado", contentType = "card") {
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (state.estaCerrado) {
                                Text(
                                    "Estimado cerrado. Reábrelo si necesitas agregar fotos o corregir algo.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Button(onClick = onReabrir, modifier = Modifier.fillMaxWidth()) {
                                    Text("Reabrir estimado")
                                }
                            } else if (state.todoReparado) {
                                Text(
                                    "Todos los ítems están reparados.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Button(onClick = onCerrar, modifier = Modifier.fillMaxWidth()) {
                                    Text("Cerrar estimado")
                                }
                            }
                            if (puedeClonar) {
                                OutlinedButton(
                                    onClick = onNuevoParaEquipo,
                                    enabled = !state.isSaving,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text("Nuevo estimado para este equipo")
                                }
                            }
                        }
                    }
                }
            }

            item(key = "mensajes", contentType = "messages") {
                Column {
                    state.savedMessage?.let { msg ->
                        Text("✓ $msg", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                    state.errorMessage?.let { msg ->
                        Text(msg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }

    // ── BOTTOM SHEETS ──────────────────────────────────────────────────────────
    when (val sheet = state.activeSheet) {
        is EstimadoSheet.AddDamage -> {
            ModalBottomSheet(
                onDismissRequest = { onEvent(EstimadoEvent.DismissSheet) },
                sheetState = sheetState,
            ) {
                AddDamageSheet(
                    title = "Agregar daño",
                    initialNombre = getPendingNombreItem(),
                    initialDescription = getPendingDamageDescription(),
                    onNombreChange = { onEvent(EstimadoEvent.NombreItemChange(it)) },
                    onDescriptionChange = { onEvent(EstimadoEvent.DamageDescriptionChange(it)) },
                    onCancel = { scope.launch { sheetState.hide() }.invokeOnCompletion { onEvent(EstimadoEvent.DismissSheet) } },
                    onConfirm = { onEvent(EstimadoEvent.ConfirmAddDamage) },
                )
            }
        }
        is EstimadoSheet.EditDamage -> {
            ModalBottomSheet(
                onDismissRequest = { onEvent(EstimadoEvent.DismissSheet) },
                sheetState = sheetState,
            ) {
                AddDamageSheet(
                    title = "Editar daño",
                    initialNombre = getPendingNombreItem(),
                    initialDescription = getPendingDamageDescription(),
                    onNombreChange = { onEvent(EstimadoEvent.NombreItemChange(it)) },
                    onDescriptionChange = { onEvent(EstimadoEvent.DamageDescriptionChange(it)) },
                    onCancel = { scope.launch { sheetState.hide() }.invokeOnCompletion { onEvent(EstimadoEvent.DismissSheet) } },
                    onConfirm = { onEvent(EstimadoEvent.ConfirmEditDamage(sheet.itemId)) },
                )
            }
        }
        is EstimadoSheet.RepairItem -> {
            ModalBottomSheet(
                onDismissRequest = { onEvent(EstimadoEvent.DismissSheet) },
                sheetState = sheetState,
            ) {
                val damageName = state.damages.find { it.id == sheet.itemId }?.damageDescription ?: ""
                RepairItemSheet(
                    damageReference = damageName,
                    initialAction = getPendingRepairAction(),
                    onActionChange = { onEvent(EstimadoEvent.RepairActionChange(sheet.itemId, it)) },
                    onCancel = { scope.launch { sheetState.hide() }.invokeOnCompletion { onEvent(EstimadoEvent.DismissSheet) } },
                    onConfirm = { onEvent(EstimadoEvent.ConfirmRepair(sheet.itemId)) },
                )
            }
        }
        is EstimadoSheet.EditValor -> {
            ModalBottomSheet(
                onDismissRequest = { onEvent(EstimadoEvent.DismissSheet) },
                sheetState = sheetState,
            ) {
                val indice = state.damages.indexOfFirst { it.id == sheet.itemId }
                val referencia = state.damages.getOrNull(indice)?.nombreParaMostrar(indice).orEmpty()
                EditValorSheet(
                    damageReference = referencia,
                    initialCantidad = getPendingCantidad(),
                    initialPrecio = getPendingPrecioUnitario(),
                    onCantidadChange = { onEvent(EstimadoEvent.CantidadChange(sheet.itemId, it)) },
                    onPrecioChange = { onEvent(EstimadoEvent.PrecioUnitarioChange(sheet.itemId, it)) },
                    onCancel = { scope.launch { sheetState.hide() }.invokeOnCompletion { onEvent(EstimadoEvent.DismissSheet) } },
                    onConfirm = { onEvent(EstimadoEvent.ConfirmValor(sheet.itemId)) },
                )
            }
        }
        EstimadoSheet.CorregirEquipo -> {
            ModalBottomSheet(
                onDismissRequest = { onEvent(EstimadoEvent.DismissSheet) },
                sheetState = sheetState,
            ) {
                CorregirEquipoSheet(
                    contenedorActual = state.containerNo,
                    initialValor = getPendingContenedor(),
                    onValorChange = { onEvent(EstimadoEvent.ContenedorCorregidoChange(it)) },
                    onCancel = { scope.launch { sheetState.hide() }.invokeOnCompletion { onEvent(EstimadoEvent.DismissSheet) } },
                    onConfirm = { onEvent(EstimadoEvent.ConfirmCorregirEquipo) },
                )
            }
        }
        EstimadoSheet.EditarObservaciones -> {
            ModalBottomSheet(
                onDismissRequest = { onEvent(EstimadoEvent.DismissSheet) },
                sheetState = sheetState,
            ) {
                ObservacionesSheet(
                    initialValor = getPendingObservaciones(),
                    onValorChange = { onEvent(EstimadoEvent.ObservacionesChange(it)) },
                    onCancel = { scope.launch { sheetState.hide() }.invokeOnCompletion { onEvent(EstimadoEvent.DismissSheet) } },
                    onConfirm = { onEvent(EstimadoEvent.ConfirmObservaciones) },
                )
            }
        }
        EstimadoSheet.EditManoDeObra -> {
            ModalBottomSheet(
                onDismissRequest = { onEvent(EstimadoEvent.DismissSheet) },
                sheetState = sheetState,
            ) {
                EditManoDeObraSheet(
                    initialValor = getPendingManoDeObra(),
                    onValorChange = { onEvent(EstimadoEvent.ManoDeObraChange(it)) },
                    onCancel = { scope.launch { sheetState.hide() }.invokeOnCompletion { onEvent(EstimadoEvent.DismissSheet) } },
                    onConfirm = { onEvent(EstimadoEvent.ConfirmManoDeObra) },
                )
            }
        }
        null -> Unit
    }
}

/** Una opción del diálogo de documento: título, para qué sirve, y toca para elegir. */
@Composable
private fun OpcionDocumento(titulo: String, detalle: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(titulo, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                detalle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
