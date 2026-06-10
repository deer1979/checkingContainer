package com.checkingcontainer.feature.units

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.AttachMoney
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
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
    onNavigateToList: () -> Unit,
    viewModel: EstimadoViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    EstimadoScreen(
        state = state,
        onBack = onNavigateToList,
        onEvent = viewModel::onEvent,
        onSave = viewModel::save,
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
        floatingActionButton = {
            if (!state.isLoading && state.status != EstimadoStatus.CERRADO) {
                ExtendedFloatingActionButton(
                    onClick = onSave,
                    icon = {
                        if (state.isSaving) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Outlined.Save, contentDescription = null)
                    },
                    text = { Text(if (state.isSaving) "Guardando…" else "Guardar Estimado") },
                )
            }
        },
        modifier = Modifier.fillMaxSize(),
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
                            onRepairClick = { onEvent(EstimadoEvent.ShowSheet(EstimadoSheet.RepairItem(item.id))) },
                            onRemoveClick = { onEvent(EstimadoEvent.RemoveDamageItem(item.id)) },
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

            Spacer(Modifier.height(88.dp))
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
                    initialDescription = getPendingDamageDescription(),
                    onDescriptionChange = { onEvent(EstimadoEvent.DamageDescriptionChange(it)) },
                    onCancel = { scope.launch { sheetState.hide() }.invokeOnCompletion { onEvent(EstimadoEvent.DismissSheet) } },
                    onConfirm = { onEvent(EstimadoEvent.ConfirmAddDamage) },
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
    onRepairClick: () -> Unit,
    onRemoveClick: () -> Unit,
    onAddDamagePhoto: (Uri) -> Unit,
    onAddRepairPhoto: (Uri) -> Unit,
) {
    val pickDamage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { it?.let(onAddDamagePhoto) }
    val captureDamage = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { }
    val pickRepair = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { it?.let(onAddRepairPhoto) }

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
                if (!isClosed && item.status == DamageItemStatus.PENDIENTE) {
                    IconButton(onClick = onRemoveClick, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Outlined.Close, contentDescription = "Eliminar ítem", modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        Text(
            item.damageDescription,
            style = MaterialTheme.typography.bodyMedium,
        )

        // Fotos lado a lado: daño (izq) / reparación (der)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Foto daño — izquierda
            Box(Modifier.weight(1f)) {
                if (item.damagePhoto != null) {
                    PhotoThumbnail(
                        url = item.damagePhoto,
                        label = "ANTES",
                        canRemove = false,
                    )
                } else if (!isClosed && item.status == DamageItemStatus.PENDIENTE) {
                    PhotoPickerButton(
                        label = "Foto del daño",
                        isUploading = isUploading,
                        onGallery = { pickDamage.launch("image/*") },
                        onCamera = { /* camera intent */ },
                    )
                }
            }
            // Foto reparación — derecha
            Box(Modifier.weight(1f)) {
                if (item.repairPhoto != null) {
                    PhotoThumbnail(
                        url = item.repairPhoto,
                        label = "DESPUÉS",
                        canRemove = false,
                    )
                } else if (item.status == DamageItemStatus.REPARADO && !isClosed) {
                    PhotoPickerButton(
                        label = "Foto reparación",
                        isUploading = isUploading,
                        onGallery = { pickRepair.launch("image/*") },
                        onCamera = { },
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
                Icon(Icons.Outlined.Build, contentDescription = null, modifier = Modifier.size(16.dp))
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
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Ítem ${index + 1}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "M. obra: ${USD.format(item.laborCost ?: 0.0)}  Mat: ${USD.format(item.materialCost ?: 0.0)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (!isClosed) {
                        IconButton(onClick = { onEditValor(item.id) }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Outlined.AttachMoney, contentDescription = "Editar valor", modifier = Modifier.size(18.dp))
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
        Text("Agregar daño", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
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
        AsyncImage(
            model = url,
            contentDescription = label,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
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
                    .size(24.dp)
                    .background(Color.Black.copy(alpha = 0.55f), CircleShape),
            ) {
                Icon(Icons.Outlined.Close, contentDescription = "Eliminar foto", tint = Color.White, modifier = Modifier.size(12.dp))
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = onCamera, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Outlined.AddAPhoto, contentDescription = "Cámara", modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onGallery, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Outlined.Image, contentDescription = "Galería", modifier = Modifier.size(20.dp))
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

private fun formatDate(millis: Long): String {
    val sdf = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(millis))
}
