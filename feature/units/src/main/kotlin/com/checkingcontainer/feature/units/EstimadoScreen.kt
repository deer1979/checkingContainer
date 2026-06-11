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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

private val USD = NumberFormat.getCurrencyInstance(Locale("es", "US")).apply {
    maximumFractionDigits = 2
}

@Composable
fun EstimadoRoute(
    onBack: () -> Unit,
    viewModel: EstimadoViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showDiscardDialog by remember { mutableStateOf(false) }

    val hasUnsavedData = !state.isLoading &&
        state.estimadoId == 0L &&
        (state.clientName.isNotBlank() || state.damages.isNotEmpty())

    val onBackSafe: () -> Unit = {
        if (hasUnsavedData) showDiscardDialog = true else onBack()
    }

    BackHandler(enabled = hasUnsavedData) { showDiscardDialog = true }

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
            title = { Text("¿Salir sin guardar?") },
            text = { Text("Los datos ingresados se perderán si sales sin guardar el estimado.") },
            confirmButton = {
                Button(onClick = { showDiscardDialog = false; onBack() }) {
                    Text("Salir")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text("Cancelar")
                }
            },
        )
    }

    EstimadoScreen(
        state = state,
        onBack = onBackSafe,
        onEvent = viewModel::onEvent,
        onSave = viewModel::save,
        onGeneratePdf = viewModel::generateAndSharePdf,
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

        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ── CLIENTE ──────────────────────────────────────────────────────────
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionTitle("Cliente")
                    OutlinedTextField(
                        value = state.clientName,
                        onValueChange = { onEvent(EstimadoEvent.ClientNameChange(it)) },
                        label = { Text("Nombre del cliente") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
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

            // ── EQUIPO ───────────────────────────────────────────────────────────
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionTitle("Datos del equipo")
                    InfoRow("No. Contenedor", state.containerNo)
                    if (state.unitSerialNo.isNotEmpty()) InfoRow("No. Serie", state.unitSerialNo)
                    if (state.manufacturer.isNotEmpty()) InfoRow("Fabricante", state.manufacturer)
                    if (state.unitModel.isNotEmpty()) InfoRow("Modelo", state.unitModel)
                    if (state.unitModelNo.isNotEmpty()) InfoRow("No. Modelo", state.unitModelNo)
                    if (state.yearOfBuilt.isNotEmpty()) InfoRow("Año", state.yearOfBuilt)
                    if (state.unitType.isNotEmpty()) InfoRow("Tipo", state.unitType)
                }
            }

            // ── DAÑOS ────────────────────────────────────────────────────────────
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

                    state.damages.forEachIndexed { index, item ->
                        if (index > 0) HorizontalDivider()
                        DamageItemCard(
                            item = item,
                            index = index + 1,
                            estimadoSaved = state.estimadoId != 0L,
                            isUploading = state.isUploadingPhoto,
                            isClosed = state.status == EstimadoStatus.CERRADO,
                            onEditDescriptionClick = { onEvent(EstimadoEvent.ShowSheet(EstimadoSheet.EditDamage(item.id))) },
                            onRepairClick = { onEvent(EstimadoEvent.ShowSheet(EstimadoSheet.RepairItem(item.id))) },
                            onRemoveClick = { onEvent(EstimadoEvent.RemoveDamageItem(item.id)) },
                            onRemoveDamagePhoto = { onEvent(EstimadoEvent.RemoveDamagePhoto(item.id)) },
                            onRemoveRepairPhoto = { onEvent(EstimadoEvent.RemoveRepairPhoto(item.id)) },
                            onAddDamagePhoto = { uri -> onAddDamagePhoto(item.id, uri) },
                            onAddRepairPhoto = { uri -> onAddRepairPhoto(item.id, uri) },
                        )
                    }

                    if (state.status != EstimadoStatus.CERRADO) {
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
            }

            // ── VALORES ──────────────────────────────────────────────────────────
            if (state.damages.isNotEmpty()) {
                ValoresSummaryCard(
                    damages = state.damages,
                    hasIva = state.hasIva,
                    isClosed = state.status == EstimadoStatus.CERRADO,
                    onIvaToggle = { onEvent(EstimadoEvent.IvaToggle(it)) },
                    onEditValor = { itemId -> onEvent(EstimadoEvent.ShowSheet(EstimadoSheet.EditValor(itemId))) },
                )
            }

            state.savedMessage?.let { msg ->
                Text("✓ $msg", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
            state.errorMessage?.let { msg ->
                Text(msg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
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
    onRemoveDamagePhoto: () -> Unit,
    onRemoveRepairPhoto: () -> Unit,
    onAddDamagePhoto: (Uri) -> Unit,
    onAddRepairPhoto: (Uri) -> Unit,
) {
    val context = LocalContext.current
    var cameraDamageUri by remember { mutableStateOf<Uri?>(null) }
    var cameraRepairUri by remember { mutableStateOf<Uri?>(null) }

    val pickDamage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { it?.let(onAddDamagePhoto) }
    val captureDamage = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) cameraDamageUri?.let { onAddDamagePhoto(it) }
    }
    val pickRepair = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { it?.let(onAddRepairPhoto) }
    val captureRepair = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) cameraRepairUri?.let { onAddRepairPhoto(it) }
    }

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

        // Fotos lado a lado: daño (izq) / reparación (der)
        val damagePhoto = item.damagePhoto
        val repairPhoto = item.repairPhoto
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Foto daño — izquierda
            Box(Modifier.weight(1f)) {
                if (damagePhoto != null) {
                    PhotoThumbnail(
                        url = damagePhoto,
                        label = "ANTES",
                        canRemove = !isClosed,
                        onRemove = onRemoveDamagePhoto,
                    )
                } else if (!isClosed) {
                    PhotoPickerButton(
                        label = "Foto del daño",
                        isUploading = isUploading,
                        onGallery = { pickDamage.launch("image/*") },
                        onCamera = {
                            val uri = createCameraUri(context)
                            cameraDamageUri = uri
                            captureDamage.launch(uri)
                        },
                    )
                }
            }
            // Foto reparación — derecha
            Box(Modifier.weight(1f)) {
                if (repairPhoto != null) {
                    PhotoThumbnail(
                        url = repairPhoto,
                        label = "DESPUÉS",
                        canRemove = !isClosed,
                        onRemove = onRemoveRepairPhoto,
                    )
                } else if (item.status == DamageItemStatus.REPARADO && !isClosed) {
                    PhotoPickerButton(
                        label = "Foto reparación",
                        isUploading = isUploading,
                        onGallery = { pickRepair.launch("image/*") },
                        onCamera = {
                            val uri = createCameraUri(context)
                            cameraRepairUri = uri
                            captureRepair.launch(uri)
                        },
                    )
                } else if (item.status == DamageItemStatus.PENDIENTE) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "DESPUÉS",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        )
                    }
                }
            }
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
    val totalLabor = damages.sumOf { it.laborCost ?: 0.0 }
    val totalMaterial = damages.sumOf { it.materialCost ?: 0.0 }
    val subtotal = totalLabor + totalMaterial
    val ivaAmount = if (hasIva) subtotal * 0.12 else 0.0
    val total = subtotal + ivaAmount

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

@Composable
private fun PhotoThumbnail(url: String, label: String, canRemove: Boolean, onRemove: () -> Unit = {}) {
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp)),
    ) {
        // Thumbnail cuadrado de media pantalla: decodificar a 600px en vez de
        // la resolución completa de la cámara ahorra memoria y carga más rápido.
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(url)
                .size(600)
                .build(),
            contentDescription = label,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Surface(
            modifier = Modifier.align(Alignment.BottomStart).padding(4.dp),
            color = Color.Black.copy(alpha = 0.55f),
            shape = RoundedCornerShape(4.dp),
        ) {
            Text(
                label,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
            )
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
private fun PhotoPickerButton(label: String, isUploading: Boolean, onGallery: () -> Unit, onCamera: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (isUploading) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
        } else {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconButton(onClick = onCamera, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Outlined.AddAPhoto, contentDescription = "Cámara", modifier = Modifier.size(26.dp))
                }
                IconButton(onClick = onGallery, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Outlined.Image, contentDescription = "Galería", modifier = Modifier.size(26.dp))
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
                    items(pages) { bmp ->
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
