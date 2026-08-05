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


// Punto de entrada del estimado: conecta el ViewModel con la pantalla,
// gestiona el aviso de cambios sin guardar y la vista previa del PDF.

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
        onReintentarSubida = viewModel::reintentarSubida,
        onCargarCambios = viewModel::cargarCambiosDelCompanero,
        onSelectClientClick = { showClientPicker = true },
        onSelectSitioClick = { showSitioPicker = true },
        onAddDamagePhoto = viewModel::addDamagePhoto,
        onAddRepairPhoto = viewModel::addRepairPhoto,
        getPendingDamageDescription = viewModel::getPendingDamageDescription,
        getPendingRepairAction = viewModel::getPendingRepairAction,
        getPendingCantidad = viewModel::getPendingCantidad,
        getPendingPrecioUnitario = viewModel::getPendingPrecioUnitario,
        getPendingManoDeObra = viewModel::getPendingManoDeObra,
        getPendingNombreItem = viewModel::getPendingNombreItem,
        getPendingContenedor = viewModel::getPendingContenedor,
    )
}
