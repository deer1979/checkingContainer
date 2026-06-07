package com.checkingcontainer.feature.units

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerOrientationPicker(
    onSelect: (isVertical: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outline
    val background = MaterialTheme.colorScheme.primaryContainer

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Text(
                text = "Seleccionar modo de escaneo",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            )
            HorizontalDivider()
            OrientationRow(
                label = "Contenedor Horizontal",
                vertical = false,
                primary = primary,
                outline = outline,
                background = background,
                onClick = { onSelect(false) },
            )
            HorizontalDivider()
            OrientationRow(
                label = "Contenedor Vertical",
                vertical = true,
                primary = primary,
                outline = outline,
                background = background,
                onClick = { onSelect(true) },
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun OrientationRow(
    label: String,
    vertical: Boolean,
    primary: Color,
    outline: Color,
    background: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ScanOrientationIcon(vertical = vertical, primary = primary, outline = outline, background = background)
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = primary,
        )
    }
}

@Composable
private fun ScanOrientationIcon(
    vertical: Boolean,
    primary: Color,
    outline: Color,
    background: Color,
) {
    Canvas(modifier = Modifier.size(48.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 2.dp.toPx()
        val corner = CornerRadius(4.dp.toPx())
        val pad = stroke + 3.dp.toPx()

        // Fondo
        drawRoundRect(color = background, cornerRadius = corner)
        // Contorno
        drawRoundRect(color = outline, cornerRadius = corner, style = Stroke(width = stroke))

        if (!vertical) {
            // Barra horizontal gruesa en la parte superior
            val barH = h * 0.28f
            drawRoundRect(
                color = primary,
                topLeft = Offset(pad, pad),
                size = Size(w - pad * 2, barH),
                cornerRadius = CornerRadius(2.dp.toPx()),
            )
            // Línea delgada debajo
            val lineY = pad + barH + 4.dp.toPx()
            drawLine(
                color = outline,
                start = Offset(pad, lineY),
                end = Offset(w - pad, lineY),
                strokeWidth = stroke,
            )
        } else {
            // Barra vertical gruesa en el lado derecho
            val barW = w * 0.28f
            drawRoundRect(
                color = primary,
                topLeft = Offset(w - barW - pad, pad),
                size = Size(barW, h - pad * 2),
                cornerRadius = CornerRadius(2.dp.toPx()),
            )
            // Línea delgada a la izquierda de la barra
            val lineX = w - barW - pad - 4.dp.toPx()
            drawLine(
                color = outline,
                start = Offset(lineX, pad),
                end = Offset(lineX, h - pad),
                strokeWidth = stroke,
            )
        }
    }
}
