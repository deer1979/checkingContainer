package com.checkingcontainer.feature.units

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.Executors

/**
 * Escáner OCR a pantalla completa (Dialog). Antes era un ModalBottomSheet de altura
 * fija; ahora ocupa toda la pantalla, como las apps grandes de escaneo.
 */
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

    // Executor de fondo: el análisis OCR (toBitmap/rotate/crop/projection/ML Kit) NO
    // debe correr en el hilo principal o produce jank. Los callbacks de ML Kit siguen
    // disparándose en el hilo principal, así que actualizar estado Compose es seguro.
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

    val analyzer = remember(mode) {
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
        }
    }
}
