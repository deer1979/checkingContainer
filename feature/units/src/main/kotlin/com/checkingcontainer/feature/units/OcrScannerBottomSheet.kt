package com.checkingcontainer.feature.units

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrScannerBottomSheet(
    mode: ScannerMode,
    onSuccess: (Map<String, String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptic = LocalHapticFeedback.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    val controller = remember { LifecycleCameraController(context) }
    val analyzer = remember(mode) {
        TextRecognitionAnalyzer(mode = mode) { fields ->
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onSuccess(fields)
        }
    }

    DisposableEffect(Unit) {
        onDispose { controller.unbind() }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        try {
            val image = InputImage.fromFilePath(context, uri)
            TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                .process(image)
                .addOnSuccessListener { visionText ->
                    val result = when (mode) {
                        ScannerMode.CONTAINER -> TextRecognitionAnalyzer.parseContainer(visionText.text)
                        ScannerMode.DATA_PLATE -> TextRecognitionAnalyzer.parseDataPlate(visionText.text)
                    }
                    if (result != null) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSuccess(result)
                    }
                }
        } catch (_: Exception) { }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        if (hasCameraPermission) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(480.dp),
            ) {
                AndroidView(
                    factory = { ctx ->
                        PreviewView(ctx).also { previewView ->
                            controller.setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
                            controller.setImageAnalysisAnalyzer(
                                ContextCompat.getMainExecutor(ctx),
                                analyzer,
                            )
                            previewView.controller = controller
                            controller.bindToLifecycle(lifecycleOwner)
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val stroke = 4.dp.toPx()
                    val corner = 36.dp.toPx()
                    val pad = 48.dp.toPx()
                    val white = Color.White
                    drawLine(white, Offset(pad, pad), Offset(pad + corner, pad), stroke)
                    drawLine(white, Offset(pad, pad), Offset(pad, pad + corner), stroke)
                    drawLine(white, Offset(size.width - pad, pad), Offset(size.width - pad - corner, pad), stroke)
                    drawLine(white, Offset(size.width - pad, pad), Offset(size.width - pad, pad + corner), stroke)
                    drawLine(white, Offset(pad, size.height - pad), Offset(pad + corner, size.height - pad), stroke)
                    drawLine(white, Offset(pad, size.height - pad), Offset(pad, size.height - pad - corner), stroke)
                    drawLine(white, Offset(size.width - pad, size.height - pad), Offset(size.width - pad - corner, size.height - pad), stroke)
                    drawLine(white, Offset(size.width - pad, size.height - pad), Offset(size.width - pad, size.height - pad - corner), stroke)
                }

                Text(
                    text = when (mode) {
                        ScannerMode.CONTAINER -> "Encuadre el número de contenedor"
                        ScannerMode.DATA_PLATE -> "Encuadre la placa de datos"
                    },
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp)
                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 36.dp, vertical = 28.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Gallery picker (for testing)
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.55f))
                            .clickable { galleryLauncher.launch("image/*") },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.PhotoLibrary,
                            contentDescription = "Seleccionar de galería",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp),
                        )
                    }

                    // Shutter button
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clickable { analyzer.triggerCapture() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .border(3.dp, Color.White, CircleShape),
                        )
                        Box(
                            modifier = Modifier
                                .size(60.dp)
                                .background(Color.White, CircleShape),
                        )
                    }

                    // Balance spacer
                    Spacer(modifier = Modifier.size(48.dp))
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.CameraAlt,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "Se necesita permiso de cámara para escanear",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                    Text("Conceder permiso")
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
