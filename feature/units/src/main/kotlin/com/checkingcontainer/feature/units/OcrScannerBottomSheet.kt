package com.checkingcontainer.feature.units

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
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
 * Escáner OCR de **foto estática** a pantalla completa. Apuntas, tocas el disparador,
 * se captura una foto full-res y [StillCodeRecognizer] la procesa (deskew + binarizado
 * + varias tuberías) devolviendo el mejor esfuerzo, que rellena el campo (editable).
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
    var processing by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .build()
    }
    // Hilo de fondo para la captura + el reconocimiento pesado (Tasks.await bloquea).
    val captureExecutor = remember { Executors.newSingleThreadExecutor() }
    val mainExecutor = remember { ContextCompat.getMainExecutor(context) }

    DisposableEffect(Unit) {
        onDispose { captureExecutor.shutdown() }
    }

    val onShutter: () -> Unit = {
        if (!processing) {
            processing = true
            statusMessage = null
            imageCapture.takePicture(
                captureExecutor,
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        val result = try {
                            StillCodeRecognizer.recognize(image, mode, verticalMode)
                        } finally {
                            image.close()
                        }
                        mainExecutor.execute {
                            processing = false
                            if (result != null) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onSuccess(result)
                            } else {
                                statusMessage = "No se detectó. Acérquese al número y reintente."
                            }
                        }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        mainExecutor.execute {
                            processing = false
                            statusMessage = "Error al capturar. Reintente."
                        }
                    }
                },
            )
        }
    }

    // Galería: ML Kit directo sobre la imagen (sin ROI). Útil para fotos horizontales.
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
                        ScannerMode.CONTAINER -> StillCodeRecognizer.parseContainer(visionText.text)
                        ScannerMode.DATA_PLATE -> StillCodeRecognizer.parseDataPlate(visionText.text)
                    }
                    if (result != null) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSuccess(result)
                    } else {
                        statusMessage = "No se detectó en la imagen."
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
                    imageCapture = imageCapture,
                    mode = mode,
                    verticalMode = verticalMode,
                    processing = processing,
                    statusMessage = statusMessage,
                    onShutter = onShutter,
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
