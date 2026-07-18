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
import com.checkingcontainer.core.model.EstimadoStatus
import com.checkingcontainer.core.model.EstimadoTotals
import com.checkingcontainer.core.model.MAX_FOTOS_POR_GRUPO
import com.checkingcontainer.core.model.MedicionSnapshot
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val USD = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-US")).apply {
    maximumFractionDigits = 2
}

@Composable
fun EstimadoRoute(
    onBack: () -> Unit,
    viewModel: EstimadoViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showDiscardDialog by remember { mutableStateOf(false) }

    // Aplica tanto a estimados nuevos como a EDICIONES de uno existente:
    // cualquier cambio sin guardar dispara el aviso al salir.
    val hasUnsavedData = !state.isLoading && state.isDirty

    val onBackSafe: () -> Unit = {
        if (hasUnsavedData) showDiscardDialog = true else onBack()
    }

    BackHandler(enabled = hasUnsavedData) { showDiscardDialog = true }

    // "Guardar y salir": espera a que el guardado termine bien antes de salir
    // (salir de inmediato cancelaría la corrutina del ViewModel a mitad).
    var exitAfterSave by remember { mutableStateOf(false) }
    LaunchedEffect(state.isSaving, state.savedMessage, state.errorMessage) {
        if (exitAfterSave && !state.isSaving) {
            if (state.savedMessage != null) onBack()
            else if (state.errorMessage != null) exitAfterSave = false
        }
    }

    // Mostrar preview del PDF cuando esté listo
    val context = LocalContext.current
    var showPdfPreview by remember { mutableStateOf(false) }
    val pdfPreviewPath = state.pdfPreviewPath
    LaunchedEffect(pdfPreviewPath) {
        if (pdfPreviewPath != null) showPdfPreview = true
    }

    if (showPdfPreview && pdfPreviewPath != null) {
        PdfPreviewSheet(
            filePath = pdfPreviewPath,
            onShare = {
                val file = File(pdfPreviewPath)
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "Compartir estimado"))
            },
            onDismiss = {
                showPdfPreview = false
                viewModel.clearPdfPath()
            },
        )
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("¿Guardar cambios?") },
            text = { Text("Tienes cambios sin guardar en este estimado. Si sales sin guardar, se perderán.") },
            confirmButton = {
                Button(onClick = {
                    showDiscardDialog = false
                    exitAfterSave = true
                    viewModel.save()
                }) {
                    Text("Guardar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false; onBack() }) {
                    Text("Salir sin guardar")
                }
            },
        )
    }

    var showClientPicker by remember { mutableStateOf(false) }
    var showSitioPicker by remember { mutableStateOf(false) }
    if (showSitioPicker) {
        val clients by viewModel.activeClients.collectAsStateWithLifecycle()
        ClientPickerSheet(
            clients = clients,
            isSaving = state.isSavingClient,
            onSelect = { client ->
                viewModel.selectSitio(client)
                showSitioPicker = false
            },
            onCreate = { client ->
                viewModel.createClientAndSelectSitio(client) { showSitioPicker = false }
            },
            onDismiss = { showSitioPicker = false },
        )
    }
    if (showClientPicker) {
        val clients by viewModel.activeClients.collectAsStateWithLifecycle()
        ClientPickerSheet(
            clients = clients,
            isSaving = state.isSavingClient,
            onSelect = { client ->
                viewModel.selectClient(client)
                showClientPicker = false
            },
            onCreate = { client ->
                viewModel.createClientAndSelect(client) { showClientPicker = false }
            },
            onDismiss = { showClientPicker = false },
        )
    }

    EstimadoScreen(
        state = state,
        onBack = onBackSafe,
        onEvent = viewModel::onEvent,
        onSave = viewModel::save,
        onGeneratePdf = viewModel::generateAndSharePdf,
        onSelectClientClick = { showClientPicker = true },
        onSelectSitioClick = { showSitioPicker = true },
        onAddDamagePhoto = viewModel::addDamagePhoto,
        onAddRepairPhoto = viewModel::addRepairPhoto,
        getPendingDamageDescription = viewModel::getPendingDamageDescription,
        getPendingRepairAction = viewModel::getPendingRepairAction,
        getPendingLaborCost = viewModel::getPendingLaborCost,
        getPendingMaterialCost = viewModel::getPendingMaterialCost,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EstimadoScreen(
    state: EstimadoUiState,
    onBack: () -> Unit,
    onEvent: (EstimadoEvent) -> Unit,
    onSave: () -> Unit,
    onGeneratePdf: () -> Unit,
    onSelectClientClick: () -> Unit = {},
    onSelectSitioClick: () -> Unit = {},
    onAddDamagePhoto: (String, Uri) -> Unit,
    onAddRepairPhoto: (String, Uri) -> Unit,
    getPendingDamageDescription: () -> String,
    getPendingRepairAction: () -> String,
    getPendingLaborCost: () -> String,
    getPendingMaterialCost: () -> String,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

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
                            onClick = onGeneratePdf,
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

        val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            val itemId = pendingPhotoItemId
            if (uri != null && itemId != null) {
                if (pendingPhotoIsRepair) onAddRepairPhoto(itemId, uri) else onAddDamagePhoto(itemId, uri)
            }
            pendingPhotoItemId = null
        }
        val capturePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            val itemId = pendingPhotoItemId
            val uri = pendingCameraUri?.let(Uri::parse)
            if (success && itemId != null && uri != null) {
                if (pendingPhotoIsRepair) onAddRepairPhoto(itemId, uri) else onAddDamagePhoto(itemId, uri)
            }
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
            // ── CLIENTE ──────────────────────────────────────────────────────────
            item(key = "cliente", contentType = "card") {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SectionTitle("Cliente")
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
                            InfoRow("Fecha", formatDate(state.createdAt))
                        }
                        if (state.approvedAt != null) {
                            InfoRow("Aprobado", formatDate(state.approvedAt))
                        }
                    }
                }
            }

            // ── EQUIPO ───────────────────────────────────────────────────────────
            item(key = "equipo", contentType = "card") {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionTitle("Datos del equipo")
                        InfoRow(if (Iso6346.isValid(state.containerNo)) "No. Contenedor" else "Código de equipo", state.containerNo)
                        if (state.unitSerialNo.isNotEmpty()) InfoRow("No. Serie", state.unitSerialNo)
                        if (state.manufacturer.isNotEmpty()) InfoRow("Fabricante", state.manufacturer)
                        if (state.unitModel.isNotEmpty()) InfoRow("Modelo", state.unitModel)
                        if (state.unitModelNo.isNotEmpty()) InfoRow("No. Modelo", state.unitModelNo)
                        if (state.yearOfBuilt.isNotEmpty()) InfoRow("Año", state.yearOfBuilt)
                        if (state.unitType.isNotEmpty()) InfoRow("Tipo", state.unitType)
                    }
                }
            }

            // ── MEDICIONES BLE (capturadas desde la pantalla de sensores) ────────
            if (state.mediciones.isNotEmpty()) {
                item(key = "mediciones", contentType = "card") {
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            SectionTitle("Mediciones")
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
                        SectionTitle("Daños encontrados")
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
                        onIvaToggle = { onEvent(EstimadoEvent.IvaToggle(it)) },
                        onEditValor = { itemId -> onEvent(EstimadoEvent.ShowSheet(EstimadoSheet.EditValor(itemId))) },
                    )
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
                    initialDescription = getPendingDamageDescription(),
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
                    initialDescription = getPendingDamageDescription(),
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
                val damageName = state.damages.find { it.id == sheet.itemId }?.damageDescription ?: ""
                EditValorSheet(
                    damageReference = damageName,
                    initialLabor = getPendingLaborCost(),
                    initialMaterial = getPendingMaterialCost(),
                    onLaborChange = { onEvent(EstimadoEvent.LaborCostChange(sheet.itemId, it)) },
                    onMaterialChange = { onEvent(EstimadoEvent.MaterialCostChange(sheet.itemId, it)) },
                    onCancel = { scope.launch { sheetState.hide() }.invokeOnCompletion { onEvent(EstimadoEvent.DismissSheet) } },
                    onConfirm = { onEvent(EstimadoEvent.ConfirmValor(sheet.itemId)) },
                )
            }
        }
        null -> Unit
    }
}

// ── Tarjeta de ítem de daño ────────────────────────────────────────────────────

@Composable
private fun DamageItemCard(
    item: DamageItem,
    index: Int,
    estimadoSaved: Boolean,
    isUploading: Boolean,
    isClosed: Boolean,
    onEditDescriptionClick: () -> Unit,
    onRepairClick: () -> Unit,
    onRemoveClick: () -> Unit,
    onRemoveDamagePhoto: (String) -> Unit,
    onRemoveRepairPhoto: (String) -> Unit,
    // isRepair: false = foto del daño (antes), true = foto de la reparación (después)
    onRequestGalleryPhoto: (Boolean) -> Unit,
    onRequestCameraPhoto: (Boolean) -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Ítem $index",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (item.status == DamageItemStatus.REPARADO) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(50),
                    ) {
                        Text(
                            "✓ Reparado",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
                if (!isClosed) {
                    IconButton(onClick = onEditDescriptionClick, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Outlined.Edit, contentDescription = "Editar descripción", modifier = Modifier.size(20.dp))
                    }
                    if (item.status == DamageItemStatus.PENDIENTE) {
                        IconButton(onClick = onRemoveClick, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Outlined.Close, contentDescription = "Eliminar ítem", modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }

        Text(item.damageDescription, style = MaterialTheme.typography.bodyMedium)

        // Fotos del daño (antes): varias por ítem, en galería horizontal.
        PhotoGroup(
            titulo = "Daño (antes)",
            fotos = item.damagePhotos,
            isUploading = isUploading,
            puedeAgregar = !isClosed && item.damagePhotos.size < MAX_FOTOS_POR_GRUPO,
            puedeEliminar = !isClosed,
            onRemove = onRemoveDamagePhoto,
            onGallery = { onRequestGalleryPhoto(false) },
            onCamera = { onRequestCameraPhoto(false) },
        )

        // Fotos de la reparación (después): solo una vez reparado el ítem.
        if (item.status == DamageItemStatus.REPARADO) {
            PhotoGroup(
                titulo = "Reparación (después)",
                fotos = item.repairPhotos,
                isUploading = isUploading,
                puedeAgregar = !isClosed && item.repairPhotos.size < MAX_FOTOS_POR_GRUPO,
                puedeEliminar = !isClosed,
                onRemove = onRemoveRepairPhoto,
                onGallery = { onRequestGalleryPhoto(true) },
                onCamera = { onRequestCameraPhoto(true) },
            )
        }

        if (item.status == DamageItemStatus.REPARADO && item.repairAction.isNotEmpty()) {
            Text(
                "Acción: ${item.repairAction}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (estimadoSaved && item.status == DamageItemStatus.PENDIENTE && !isClosed) {
            FilledTonalButton(
                onClick = onRepairClick,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.Build, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Reparar ítem")
            }
        }
    }
}

// ── Resumen de valores ──────────────────────────────────────────────────────────

@Composable
private fun ValoresSummaryCard(
    damages: List<DamageItem>,
    hasIva: Boolean,
    isClosed: Boolean,
    onIvaToggle: (Boolean) -> Unit,
    onEditValor: (String) -> Unit,
) {
    val totals = EstimadoTotals.calcular(damages, hasIva)
    val totalLabor = totals.laborTotal
    val totalMaterial = totals.materialTotal
    val ivaAmount = totals.ivaAmount
    val total = totals.total

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle("Valores")

            damages.forEachIndexed { index, item ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Ítem ${index + 1}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "M. obra: ${USD.format(item.laborCost ?: 0.0)}  |  Mat: ${USD.format(item.materialCost ?: 0.0)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (!isClosed) {
                        FilledTonalButton(
                            onClick = { onEditValor(item.id) },
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        ) {
                            Icon(Icons.Outlined.AttachMoney, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Editar", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            HorizontalDivider()

            ValueRow("Mano de obra total", USD.format(totalLabor))
            ValueRow("Materiales total", USD.format(totalMaterial))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("IVA 12%", style = MaterialTheme.typography.bodyMedium)
                if (!isClosed) {
                    Switch(checked = hasIva, onCheckedChange = onIvaToggle)
                } else {
                    Text(USD.format(ivaAmount), style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (hasIva) ValueRow("IVA", USD.format(ivaAmount))

            HorizontalDivider()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("TOTAL", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(USD.format(total), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ── Bottom sheet: Agregar daño ─────────────────────────────────────────────────

@Composable
private fun AddDamageSheet(
    title: String,
    initialDescription: String,
    onDescriptionChange: (String) -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    var text by remember { mutableStateOf(initialDescription) }

    Column(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = text,
            onValueChange = { text = it; onDescriptionChange(it) },
            label = { Text("¿Qué se encontró?") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            maxLines = 6,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancelar") }
            Button(
                onClick = onConfirm,
                modifier = Modifier.weight(1f),
                enabled = text.isNotBlank(),
            ) { Text("Guardar daño") }
        }
    }
}

// ── Bottom sheet: Registrar reparación ────────────────────────────────────────

@Composable
private fun RepairItemSheet(
    damageReference: String,
    initialAction: String,
    onActionChange: (String) -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    var text by remember { mutableStateOf(initialAction) }

    Column(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Registrar reparación", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        if (damageReference.isNotEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    "Daño: $damageReference",
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        OutlinedTextField(
            value = text,
            onValueChange = { text = it; onActionChange(it) },
            label = { Text("¿Qué se hizo?") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            maxLines = 6,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancelar") }
            Button(
                onClick = onConfirm,
                modifier = Modifier.weight(1f),
                enabled = text.isNotBlank(),
            ) { Text("Guardar reparación") }
        }
    }
}

// ── Bottom sheet: Editar valores ──────────────────────────────────────────────

@Composable
private fun EditValorSheet(
    damageReference: String,
    initialLabor: String,
    initialMaterial: String,
    onLaborChange: (String) -> Unit,
    onMaterialChange: (String) -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    var labor by remember { mutableStateOf(initialLabor) }
    var material by remember { mutableStateOf(initialMaterial) }

    Column(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Valores del ítem", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        if (damageReference.isNotEmpty()) {
            Text(
                damageReference,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedTextField(
            value = labor,
            onValueChange = { labor = it; onLaborChange(it) },
            label = { Text("Mano de obra ($)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            leadingIcon = { Icon(Icons.Outlined.AttachMoney, contentDescription = null) },
        )
        OutlinedTextField(
            value = material,
            onValueChange = { material = it; onMaterialChange(it) },
            label = { Text("Costo de material ($)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            leadingIcon = { Icon(Icons.Outlined.AttachMoney, contentDescription = null) },
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancelar") }
            Button(onClick = onConfirm, modifier = Modifier.weight(1f)) { Text("Guardar") }
        }
    }
}

// ── Componentes auxiliares ────────────────────────────────────────────────────

/**
 * Grupo de fotos (daño o reparación) de un ítem: galería horizontal con las
 * miniaturas existentes y, al final, el botón para agregar otra (hasta el máximo).
 */
@Composable
private fun PhotoGroup(
    titulo: String,
    fotos: List<String>,
    isUploading: Boolean,
    puedeAgregar: Boolean,
    puedeEliminar: Boolean,
    onRemove: (String) -> Unit,
    onGallery: () -> Unit,
    onCamera: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "$titulo · ${fotos.size}/$MAX_FOTOS_POR_GRUPO",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (fotos.isEmpty() && !puedeAgregar) {
            Text(
                "Sin fotos",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(fotos, key = { it }) { url ->
                    PhotoThumbnail(
                        url = url,
                        canRemove = puedeEliminar,
                        onRemove = { onRemove(url) },
                        modifier = Modifier.size(110.dp),
                    )
                }
                if (puedeAgregar) {
                    item(key = "add-$titulo") {
                        PhotoPickerButton(
                            isUploading = isUploading,
                            onGallery = onGallery,
                            onCamera = onCamera,
                            modifier = Modifier.size(110.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoThumbnail(url: String, canRemove: Boolean, onRemove: () -> Unit = {}, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(8.dp))) {
        // Si la descarga falla (datos móviles flojos, foto pesada), antes quedaba
        // un cuadro gris mudo: ahora se avisa y un toque reintenta la carga.
        var reintento by remember(url) { mutableStateOf(0) }
        var fallo by remember(url) { mutableStateOf(false) }
        // Decodificar a 600px en vez de la resolución completa de la cámara ahorra
        // memoria y carga más rápido.
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(url)
                .size(600)
                .memoryCacheKey("$url#r$reintento")
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            onState = { state -> fallo = state is coil3.compose.AsyncImagePainter.State.Error },
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        if (fallo) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { reintento++ },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    Icons.Outlined.Refresh,
                    contentDescription = "Reintentar carga de foto",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Reintentar",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (canRemove) {
            IconButton(
                onClick = onRemove,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(32.dp)
                    .background(Color.Black.copy(alpha = 0.6f), CircleShape),
            ) {
                Icon(Icons.Outlined.Close, contentDescription = "Eliminar foto", tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun PhotoPickerButton(isUploading: Boolean, onGallery: () -> Unit, onCamera: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (isUploading) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = onCamera, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Outlined.AddAPhoto, contentDescription = "Cámara", modifier = Modifier.size(24.dp))
                }
                IconButton(onClick = onGallery, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Outlined.Image, contentDescription = "Galería", modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ValueRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
}

// Instancia única: crearla en cada recomposición es costoso. Solo se usa desde composición (un hilo).
private val DATE_FORMAT = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault())

private fun formatDate(millis: Long): String = DATE_FORMAT.format(java.util.Date(millis))

private fun createCameraUri(context: android.content.Context): Uri {
    val file = java.io.File(context.cacheDir, "photo_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

@Composable
private fun RowScope.BottomBarBtn(
    icon: @Composable () -> Unit,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        icon()
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (enabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            maxLines = 1,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PdfPreviewSheet(
    filePath: String,
    onShare: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var pages by remember { mutableStateOf<List<android.graphics.Bitmap>>(emptyList()) }
    var isRendering by remember { mutableStateOf(true) }

    LaunchedEffect(filePath) {
        withContext(Dispatchers.IO) {
            runCatching {
                val file = java.io.File(filePath)
                val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(pfd)
                val dm = context.resources.displayMetrics
                val targetW = dm.widthPixels - (32 * dm.density).toInt()
                val list = mutableListOf<android.graphics.Bitmap>()
                for (i in 0 until renderer.pageCount) {
                    val page = renderer.openPage(i)
                    val scale = targetW.toFloat() / page.width
                    val bmp = android.graphics.Bitmap.createBitmap(
                        (page.width * scale).toInt(),
                        (page.height * scale).toInt(),
                        android.graphics.Bitmap.Config.ARGB_8888,
                    )
                    page.render(bmp, null, Matrix().apply { setScale(scale, scale) }, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()
                    list.add(bmp)
                }
                renderer.close()
                pfd.close()
                list
            }.onSuccess { list -> pages = list }
            isRendering = false
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        // Sin gestos de arrastre en la hoja: el deslizar queda solo para el scroll
        // de las páginas (antes el gesto arrastraba la hoja y rebotaba arriba).
        // Se cierra con el botón "Cerrar" o tocando fuera.
        sheetGesturesEnabled = false,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Vista previa", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onDismiss) { Text("Cerrar") }
                    Button(onClick = onShare) {
                        Icon(Icons.Outlined.Share, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Compartir")
                    }
                }
            }
            HorizontalDivider()

            if (isRendering) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircularProgressIndicator()
                        Text("Preparando vista previa…", style = MaterialTheme.typography.bodySmall)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    itemsIndexed(
                        items = pages,
                        key = { i, _ -> i },
                        contentType = { _, _ -> "page" },
                    ) { _, bmp ->
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp)),
                            contentScale = ContentScale.FillWidth,
                        )
                    }
                }
            }
        }
    }
}

// ── Mediciones BLE ──────────────────────────────────────────────────────────────

private val MEDICION_FECHA_FMT = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
private fun Double?.fmtMedicion(decimales: Int = 1): String =
    this?.let { String.format(Locale.US, "%.${decimales}f", it) } ?: "—"

/** Tarjeta compacta de una captura de sensores: fecha, gas, ALTA/BAJA, SH/SC, corriente. */
@Composable
private fun MedicionRow(
    medicion: MedicionSnapshot,
    canRemove: Boolean,
    onRemove: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceContainerHigh,
                RoundedCornerShape(8.dp),
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                MEDICION_FECHA_FMT.format(Date(medicion.timestamp)) +
                    if (medicion.refrigerante.isNotEmpty()) "  ·  ${medicion.refrigerante}" else "",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (canRemove) {
                IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = "Eliminar medición",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Text(
            "ALTA ${medicion.presionAltaPsig.fmtMedicion(0)} psig · sat ${medicion.satLiquidoC.fmtMedicion()} °C" +
                " · SC ${medicion.subcoolingC.fmtMedicion()} K",
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            "BAJA ${medicion.presionBajaPsig.fmtMedicion(0)} psig · sat ${medicion.satVaporC.fmtMedicion()} °C" +
                " · SH ${medicion.superheatC.fmtMedicion()} K",
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            "Corriente ${medicion.corrienteA.fmtMedicion()} A" +
                if (medicion.dispositivos.isNotEmpty()) " · ${medicion.dispositivos.joinToString(", ")}" else "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
