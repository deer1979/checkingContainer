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


// Tarjeta de un ítem de daño dentro del estimado.

// ── Tarjeta de ítem de daño ────────────────────────────────────────────────────

@Composable
internal fun DamageItemCard(
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

        // También cuando ya está reparado: siempre falta completar un comentario
        // o corregir lo que se escribió con prisa en el patio.
        if (estimadoSaved && !isClosed) {
            FilledTonalButton(
                onClick = onRepairClick,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.Build, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (item.status == DamageItemStatus.REPARADO) "Editar reparación"
                    else "Reparar ítem",
                )
            }
        }
    }
}
