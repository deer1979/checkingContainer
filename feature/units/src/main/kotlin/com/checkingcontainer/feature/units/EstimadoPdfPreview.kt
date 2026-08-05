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


// Vista previa del PDF antes de compartirlo.

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PdfPreviewSheet(
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
