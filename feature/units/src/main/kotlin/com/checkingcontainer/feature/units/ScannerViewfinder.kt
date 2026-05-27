package com.checkingcontainer.feature.units

import android.util.Size
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
internal fun ScannerViewfinder(
    controller: LifecycleCameraController,
    analyzer: TextRecognitionAnalyzer,
    mode: ScannerMode,
    onGalleryClick: () -> Unit,
) {
    val context = LocalContext.current
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
                                    Size(1280, 720),
                                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                                ),
                            )
                            .build(),
                    )
                    controller.setImageAnalysisAnalyzer(ContextCompat.getMainExecutor(ctx), analyzer)
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
            Box(
                modifier = Modifier.size(76.dp).clickable { analyzer.triggerCapture() },
                contentAlignment = Alignment.Center,
            ) {
                Box(modifier = Modifier.fillMaxSize().border(3.dp, Color.White, CircleShape))
                Box(modifier = Modifier.size(60.dp).background(Color.White, CircleShape))
            }
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(if (isTorchOn) Color(0xFFFFD600).copy(alpha = 0.85f) else Color.Black.copy(alpha = 0.55f))
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
