package com.checkingcontainer.feature.units

import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.concurrent.Executor

@Composable
internal fun ScannerViewfinder(
    controller: LifecycleCameraController,
    analyzer: TextRecognitionAnalyzer,
    analysisExecutor: Executor,
    mode: ScannerMode,
    verticalMode: Boolean,
    trackedItems: List<DetectedCharacter>,
    onVerticalModeToggle: () -> Unit,
    onGalleryClick: () -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var isTorchOn by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth().height(480.dp)) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).also { previewView ->
                    controller.setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
                    controller.setImageAnalysisResolutionSelector(
                        ResolutionSelector.Builder()
                            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
                            .setResolutionStrategy(
                                ResolutionStrategy(
                                    android.util.Size(1280, 720),
                                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                                ),
                            )
                            .build(),
                    )
                    controller.setImageAnalysisAnalyzer(analysisExecutor, analyzer)
                    previewView.controller = controller
                    controller.bindToLifecycle(lifecycleOwner)
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            // PreviewView usa FILL_CENTER: el frame 9:16 se escala para llenar el ancho,
            // luego recorta arriba/abajo si la vista es más corta que 9:16.
            // Hay que aplicar este transform al dibujar el ROI y las cajas de tracking,
            // o los recuadros no coinciden con los caracteres visibles en la imagen.
            val frameAspect = 16f / 9f                               // frame portrait: alto/ancho
            val scaledFrameH = size.width * frameAspect              // alto del frame escalado al ancho de la vista
            val topCropPx = ((scaledFrameH - size.height) / 2f).coerceAtLeast(0f)

            // Convierte coordenada Y relativa al frame (0..1) → píxeles en el Canvas
            fun fy(rel: Float): Float = rel * scaledFrameH - topCropPx

            val stroke = 4.dp.toPx()
            val corner = 36.dp.toPx()
            val white = Color.White
            val accent = Color(0xFF00E676)

            if (verticalMode) {
                // ROI vertical: 15% ancho × 80% alto, centrado en el frame
                val roiW    = size.width * 0.15f
                val roiLeft = (size.width - roiW) / 2f
                val roiRight  = roiLeft + roiW
                val roiTop    = fy(0.10f)
                val roiBottom = fy(0.90f)
                val roiH      = roiBottom - roiTop

                drawRect(
                    color = Color.White.copy(alpha = 0.06f),
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
                drawLine(accent, Offset(roiLeft,  roiTop),    Offset(roiLeft + c,  roiTop),    s)
                drawLine(accent, Offset(roiLeft,  roiTop),    Offset(roiLeft,      roiTop + c), s)
                drawLine(accent, Offset(roiRight, roiTop),    Offset(roiRight - c, roiTop),    s)
                drawLine(accent, Offset(roiRight, roiTop),    Offset(roiRight,     roiTop + c), s)
                drawLine(accent, Offset(roiLeft,  roiBottom), Offset(roiLeft + c,  roiBottom), s)
                drawLine(accent, Offset(roiLeft,  roiBottom), Offset(roiLeft,      roiBottom - c), s)
                drawLine(accent, Offset(roiRight, roiBottom), Offset(roiRight - c, roiBottom), s)
                drawLine(accent, Offset(roiRight, roiBottom), Offset(roiRight,     roiBottom - c), s)
            } else {
                // ROI horizontal: marcas de esquina en los bordes de la vista
                val pad = 48.dp.toPx()
                drawLine(white, Offset(pad, pad), Offset(pad + corner, pad), stroke)
                drawLine(white, Offset(pad, pad), Offset(pad, pad + corner), stroke)
                drawLine(white, Offset(size.width - pad, pad), Offset(size.width - pad - corner, pad), stroke)
                drawLine(white, Offset(size.width - pad, pad), Offset(size.width - pad, pad + corner), stroke)
                drawLine(white, Offset(pad, size.height - pad), Offset(pad + corner, size.height - pad), stroke)
                drawLine(white, Offset(pad, size.height - pad), Offset(pad, size.height - pad - corner), stroke)
                drawLine(white, Offset(size.width - pad, size.height - pad), Offset(size.width - pad - corner, size.height - pad), stroke)
                drawLine(white, Offset(size.width - pad, size.height - pad), Offset(size.width - pad, size.height - pad - corner), stroke)
            }

            // Overlay de tracking: recuadro verde por cada carácter detectado por ML Kit.
            // Coordenadas corregidas con el transform FILL_CENTER.
            for (item in trackedItems) {
                val left   = item.boundingBox.left   * size.width
                val top    = fy(item.boundingBox.top)
                val right  = item.boundingBox.right  * size.width
                val bottom = fy(item.boundingBox.bottom)
                val bw = right - left
                val bh = bottom - top
                drawRect(
                    color = Color(0x3300E676),
                    topLeft = Offset(left, top),
                    size = Size(bw, bh),
                )
                drawRect(
                    color = Color(0xFF00E676),
                    topLeft = Offset(left, top),
                    size = Size(bw, bh),
                    style = Stroke(width = 2.dp.toPx()),
                )
            }
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
                .padding(top = 16.dp)
                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                .padding(horizontal = 12.dp, vertical = 4.dp),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(horizontal = 24.dp, vertical = 28.dp),
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
                        controller.cameraControl?.enableTorch(isTorchOn)
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
}