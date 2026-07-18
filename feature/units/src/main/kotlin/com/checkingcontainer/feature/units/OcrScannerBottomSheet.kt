package com.checkingcontainer.feature.units

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

@Composable
fun OcrScannerBottomSheet(
    mode: ScannerMode,
    initialVertical: Boolean = false,
    onSuccess: (Map<String, String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
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

    var verticalMode by remember { mutableStateOf(initialVertical) }
    var trackedItems by remember { mutableStateOf(emptyList<DetectedCharacter>()) }
    var nanoBusy by remember { mutableStateOf(false) }

    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val scope = rememberCoroutineScope()

    lateinit var analyzer: TextRecognitionAnalyzer
    analyzer = remember(mode) {
        TextRecognitionAnalyzer(
            mode = mode,
            isVerticalMode = { verticalMode },
            onTrackingUpdated = { trackedItems = it },
            onValidContainerIdFound = { containerId ->
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onSuccess(mapOf("Container No." to containerId))
            },
            onSuccess = { fields ->
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onSuccess(fields)
            },
            // Respaldo de IA local (Gemini Nano): solo corre si el OCR normal no
            // pudo leer tras el disparo manual. En equipos sin IA devuelve null al
            // instante y el flujo sigue igual que siempre.
            onNanoFallback = { frame, rawFallback ->
                scope.launch {
                    nanoBusy = true
                    val fields = when (mode) {
                        ScannerMode.CONTAINER ->
                            GeminiNanoOcr.readContainerNumber(frame)
                                ?.let { mapOf("Container No." to it) }
                        ScannerMode.DATA_PLATE -> GeminiNanoOcr.readDataPlate(frame)
                    }
                    frame.recycle()
                    nanoBusy = false
                    when {
                        fields != null -> {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onSuccess(fields)
                        }
                        // Sin resultado de IA pero con lectura cruda (modo prueba
                        // vertical previo): se entrega tal cual, como antes.
                        rawFallback != null -> {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onSuccess(mapOf("Container No." to rawFallback))
                        }
                        else -> analyzer.nanoFinished() // reanuda el escaneo en vivo
                    }
                }
            },
        )
    }

    DisposableEffect(Unit) {
        onDispose { analysisExecutor.shutdown() }
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
                    } else {
                        // El OCR normal no leyó nada válido en la foto: respaldo de
                        // IA local (en equipos sin Gemini Nano devuelve null y ya).
                        scope.launch {
                            nanoBusy = true
                            val fields = runCatching {
                                val bmp = decodeBitmapForIa(context, uri)
                                    ?: return@runCatching null
                                when (mode) {
                                    ScannerMode.CONTAINER ->
                                        GeminiNanoOcr.readContainerNumber(bmp)
                                            ?.let { mapOf("Container No." to it) }
                                    ScannerMode.DATA_PLATE -> GeminiNanoOcr.readDataPlate(bmp)
                                }
                            }.getOrNull()
                            nanoBusy = false
                            if (fields != null) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onSuccess(fields)
                            }
                        }
                    }
                }
        } catch (_: Exception) { }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false,
        ),
    ) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            if (hasCameraPermission) {
                ScannerViewfinder(
                    analyzer = analyzer,
                    analysisExecutor = analysisExecutor,
                    mode = mode,
                    verticalMode = verticalMode,
                    trackedItems = trackedItems,
                    onVerticalModeToggle = { verticalMode = !verticalMode },
                    onGalleryClick = { galleryLauncher.launch("image/*") },
                    onClose = onDismiss,
                )
            } else {
                CameraPermissionRequest(
                    onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                )
            }

            if (nanoBusy) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                    shape = MaterialTheme.shapes.large,
                    color = Color.Black.copy(alpha = 0.75f),
                ) {
                    Text(
                        "Leyendo con IA local…",
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
}
