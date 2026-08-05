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


// Hojas inferiores: agregar daño y registrar reparación.

// ── Bottom sheet: Agregar daño ─────────────────────────────────────────────────

@Composable
internal fun AddDamageSheet(
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
internal fun RepairItemSheet(
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
