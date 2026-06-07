package com.checkingcontainer.feature.units

import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FlashOff
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.RotateRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
internal fun ScannerViewfinder(
    imageCapture: ImageCapture,
    mode: ScannerMode,
    verticalMode: Boolean,
    processing: Boolean,
    statusMessage: String?,
    onShutter: () -> Unit,
    onVerticalModeToggle: () -> Unit,
    onGalleryClick: () -> Unit,
    onClose: () -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var isTorchOn by remember { mutableStateOf(false) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
                val providerFuture = ProcessCameraProvider.getInstance(ctx)
                providerFuture.addListener({
                    val provider = providerFuture.get()
                    cameraProvider = provider
                    previewView.post {
                        val viewPort = previewView.viewPort ?: return@post
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }
                        // ViewPort: la foto comparte el FOV del preview (cropRect).
                        val group = UseCaseGroup.Builder()
                            .addUseCase(preview)
                            .addUseCase(imageCapture)
                            .setViewPort(viewPort)
                            .build()
                        provider.unbindAll()
                        camera = provider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            group,
                        )
                        camera?.cameraControl?.enableTorch(isTorchOn)
                    }
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            },
            modifier = Modifier.fillMaxSize(),
        )

        // Guía de encuadre estática: scrim oscurece todo menos el ROI.
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 4.dp.toPx()
            val white = Color.White
            val accent = Color(0xFF00E676)
            val roiW = size.width * if (verticalMode) StillCodeRecognizer.ROI_VERTICAL_WIDTH else StillCodeRecognizer.ROI_HORIZ_WIDTH
            val roiH = size.height * if (verticalMode) StillCodeRecognizer.ROI_VERTICAL_HEIGHT else StillCodeRecognizer.ROI_HORIZ_HEIGHT
            val roiLeft = (size.width - roiW) / 2f
            val roiTop = (size.height - roiH) / 2f
            val roiRight = roiLeft + roiW
            val roiBottom = roiTop + roiH

            val scrim = Color.Black.copy(alpha = 0.5f)
            drawRect(scrim, Offset(0f, 0f), Size(size.width, roiTop))
            drawRect(scrim, Offset(0f, roiBottom), Size(size.width, size.height - roiBottom))
            drawRect(scrim, Offset(0f, roiTop), Size(roiLeft, roiH))
            drawRect(scrim, Offset(roiRight, roiTop), Size(size.width - roiRight, roiH))

            drawRect(
                color = white.copy(alpha = 0.85f),
                topLeft = Offset(roiLeft, roiTop),
                size = Size(roiW, roiH),
                style = Stroke(width = stroke),
            )
            val c = 28.dp.toPx()
            val cs = stroke * 1.4f
            drawLine(accent, Offset(roiLeft, roiTop), Offset(roiLeft + c, roiTop), cs)
            drawLine(accent, Offset(roiLeft, roiTop), Offset(roiLeft, roiTop + c), cs)
            drawLine(accent, Offset(roiRight, roiTop), Offset(roiRight - c, roiTop), cs)
            drawLine(accent, Offset(roiRight, roiTop), Offset(roiRight, roiTop + c), cs)
            drawLine(accent, Offset(roiLeft, roiBottom), Offset(roiLeft + c, roiBottom), cs)
            drawLine(accent, Offset(roiLeft, roiBottom), Offset(roiLeft, roiBottom - c), cs)
            drawLine(accent, Offset(roiRight, roiBottom), Offset(roiRight - c, roiBottom), cs)
            drawLine(accent, Offset(roiRight, roiBottom), Offset(roiRight, roiBottom - c), cs)
        }

        // Cerrar
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
                .size(44.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Close, contentDescription = "Cerrar", tint = Color.White, modifier = Modifier.size(24.dp))
        }

        Text(
            text = statusMessage ?: when {
                mode == ScannerMode.DATA_PLATE -> "Encuadre la placa y toque para capturar"
                verticalMode -> "Acerque, encuadre la columna y toque"
                else -> "Encuadre el número y toque para capturar"
            },
            color = Color.White,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 20.dp)
                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                .padding(horizontal = 12.dp, vertical = 4.dp),
        )

        // Procesando…
        if (processing) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color.White)
                    Text(
                        text = "Procesando…",
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(horizontal = 24.dp, vertical = 36.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Galería
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clickable(enabled = !processing, onClick = onGalleryClick),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.PhotoLibrary, contentDescription = "Galería", tint = Color.White, modifier = Modifier.size(24.dp))
            }

            // Disparador → captura foto
            Box(
                modifier = Modifier.size(76.dp).clickable(enabled = !processing, onClick = onShutter),
                contentAlignment = Alignment.Center,
            ) {
                Box(modifier = Modifier.fillMaxSize().border(3.dp, Color.White, CircleShape))
                Box(modifier = Modifier.size(60.dp).background(Color.White, CircleShape))
            }

            // Toggle vertical/horizontal
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (verticalMode) Color(0xFF00E676).copy(alpha = 0.85f)
                        else Color.Black.copy(alpha = 0.55f),
                    )
                    .clickable(enabled = !processing, onClick = onVerticalModeToggle),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.RotateRight,
                    contentDescription = "Lectura vertical",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp),
                )
            }

            // Flash
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (isTorchOn) Color(0xFFFFD600).copy(alpha = 0.85f)
                        else Color.Black.copy(alpha = 0.55f),
                    )
                    .clickable {
                        isTorchOn = !isTorchOn
                        camera?.cameraControl?.enableTorch(isTorchOn)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isTorchOn) Icons.Outlined.FlashOn else Icons.Outlined.FlashOff,
                    contentDescription = "Flash",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { cameraProvider?.unbindAll() }
    }
}
