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


// Fotos del estimado: grupo, miniatura y botón de captura.

// ── Componentes auxiliares ────────────────────────────────────────────────────

/**
 * Grupo de fotos (daño o reparación) de un ítem: galería horizontal con las
 * miniaturas existentes y, al final, el botón para agregar otra (hasta el máximo).
 */
@Composable
internal fun PhotoGroup(
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
internal fun PhotoThumbnail(url: String, canRemove: Boolean, onRemove: () -> Unit = {}, modifier: Modifier = Modifier) {
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
internal fun PhotoPickerButton(isUploading: Boolean, onGallery: () -> Unit, onCamera: () -> Unit, modifier: Modifier = Modifier) {
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
