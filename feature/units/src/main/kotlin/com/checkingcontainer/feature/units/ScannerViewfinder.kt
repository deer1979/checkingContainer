package com.checkingcontainer.feature.units

import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import java.util.concurrent.Executor

@Composable
internal fun ScannerViewfinder(
    analyzer: TextRecognitionAnalyzer,
    analysisExecutor: Executor,
    mode: ScannerMode,
    verticalMode: Boolean,
    trackedItems: List<DetectedCharacter>,
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
                    // viewPort necesita la vista ya medida → post.
                    previewView.post {
                        val viewPort = previewView.viewPort ?: return@post
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }
                        val resolutionSelector = ResolutionSelector.Builder()
                            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
                            .setResolutionStrategy(
                                ResolutionStrategy(
                                    android.util.Size(1280, 720),
                                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                                ),
                            )
                            .build()
                        val analysis = ImageAnalysis.Builder()
                            .setResolutionSelector(resolutionSelector)
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                            .also { it.setAnalyzer(analysisExecutor, analyzer) }
                        // UseCaseGroup + ViewPort: preview y análisis comparten el mismo
                        // recorte (cropRect), así las coordenadas mapean 1:1 a la vista.
                        val group = UseCaseGroup.Builder()
                            .addUseCase(preview)
                            .addUseCase(analysis)
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

        // Overlay del ROI + tracking. Como el ViewPort recorta el análisis al mismo
        // FOV que el preview, las coordenadas relativas (0..1) mapean directo a la
        // vista: basta multiplicar por el tamaño. Sin matemática FILL_CENTER manual.
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 4.dp.toPx()
            val corner = 36.dp.toPx()
            val white = Color.White
            val accent = Color(0xFF00E676)

            if (verticalMode) {
                val roiW = size.width * 0.15f
                val roiLeft = (size.width - roiW) / 2f
                val roiRight = roiLeft + roiW
                val roiTop = size.height * 0.10f
                val roiBottom = size.height * 0.90f
                val roiH = roiBottom - roiTop

                drawRect(
                    color = white.copy(alpha = 0.06f),
                    topLeft = Offset(roiLeft, roiTop),
                    size = Size(roiW, roiH),
                )
                drawRect(
                    color = white.copy(alpha = 0.7f),
                    topLeft = Offset(roiLeft, roiTop),
                    size = Size(roiW, roiH),
                    style = Stroke(width = stroke),
                )
                val c = corner * 0.7f
                val s = stroke * 1.5f
                drawLine(accent, Offset(roiLeft, roiTop), Offset(roiLeft + c, roiTop), s)
                drawLine(accent, Offset(roiLeft, roiTop), Offset(roiLeft, roiTop + c), s)
                drawLine(accent, Offset(roiRight, roiTop), Offset(roiRight - c, roiTop), s)
                drawLine(accent, Offset(roiRight, roiTop), Offset(roiRight, roiTop + c), s)
                drawLine(accent, Offset(roiLeft, roiBottom), Offset(roiLeft + c, roiBottom), s)
                drawLine(accent, Offset(roiLeft, roiBottom), Offset(roiLeft, roiBottom - c), s)
                drawLine(accent, Offset(roiRight, roiBottom), Offset(roiRight - c, roiBottom), s)
                drawLine(accent, Offset(roiRight, roiBottom), Offset(roiRight, roiBottom - c), s)
            } else {
                // ROI horizontal: 80% ancho × 35% alto centrado, con marcas de esquina.
                val roiW = size.width * 0.80f
                val roiH = size.height * 0.35f
                val roiLeft = (size.width - roiW) / 2f
                val roiTop = (size.height - roiH) / 2f
                val roiRight = roiLeft + roiW
                val roiBottom = roiTop + roiH
                drawLine(white, Offset(roiLeft, roiTop), Offset(roiLeft + corner, roiTop), stroke)
                drawLine(white, Offset(roiLeft, roiTop), Offset(roiLeft, roiTop + corner), stroke)
                drawLine(white, Offset(roiRight, roiTop), Offset(roiRight - corner, roiTop), stroke)
                drawLine(white, Offset(roiRight, roiTop), Offset(roiRight, roiTop + corner), stroke)
                drawLine(white, Offset(roiLeft, roiBottom), Offset(roiLeft + corner, roiBottom), stroke)
                drawLine(white, Offset(roiLeft, roiBottom), Offset(roiLeft, roiBottom - corner), stroke)
                drawLine(white, Offset(roiRight, roiBottom), Offset(roiRight - corner, roiBottom), stroke)
                drawLine(white, Offset(roiRight, roiBottom), Offset(roiRight, roiBottom - corner), stroke)
            }

            // Tracking: un recuadro verde por carácter detectado por ML Kit.
            for (item in trackedItems) {
                val left = item.boundingBox.left * size.width
                val top = item.boundingBox.top * size.height
                val right = item.boundingBox.right * size.width
                val bottom = item.boundingBox.bottom * size.height
                drawRect(
                    color = Color(0x3300E676),
                    topLeft = Offset(left, top),
                    size = Size(right - left, bottom - top),
                )
                drawRect(
                    color = Color(0xFF00E676),
                    topLeft = Offset(left, top),
                    size = Size(right - left, bottom - top),
                    style = Stroke(width = 2.dp.toPx()),
                )
            }
        }

        // Cerrar (pantalla completa: no hay swipe-down como en el bottom sheet)
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
            text = when (mode) {
                ScannerMode.CONTAINER -> if (verticalMode) "Modo vertical — apunte a la columna" else "Encuadre el número de contenedor"
                ScannerMode.DATA_PLATE -> "Encuadre la placa de datos"
            },
            color = Color.White,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 20.dp)
                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                .padding(horizontal = 12.dp, vertical = 4.dp),
        )

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
                    .clickable(onClick = onGalleryClick),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.PhotoLibrary, contentDescription = "Galería", tint = Color.White, modifier = Modifier.size(24.dp))
            }

            // Disparador
            Box(
                modifier = Modifier.size(76.dp).clickable { analyzer.triggerCapture() },
                contentAlignment = Alignment.Center,
            ) {
                Box(modifier = Modifier.fillMaxSize().border(3.dp, Color.White, CircleShape))
                Box(modifier = Modifier.size(60.dp).background(Color.White, CircleShape))
            }

            // Toggle lectura vertical
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (verticalMode) Color(0xFF00E676).copy(alpha = 0.85f)
                        else Color.Black.copy(alpha = 0.55f),
                    )
                    .clickable(onClick = onVerticalModeToggle),
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

    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { cameraProvider?.unbindAll() }
    }
}
