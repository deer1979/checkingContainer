package com.testo3.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Variable-font-ready Typography. Every style declares its weight as an
 * explicit [FontWeight] integer; when a real variable font is wired in
 * (see [AppFontFamily] below) the renderer interpolates the matching
 * weight axis from a single .ttf instead of pulling separate static files.
 *
 * To activate true variable-font rendering:
 *   1. Drop e.g. RobotoFlex[wght,wdth,GRAD].ttf into core/designsystem/src/main/res/font/
 *   2. Replace [AppFontFamily] below with:
 *        val AppFontFamily = FontFamily(
 *            Font(
 *                resId = R.font.roboto_flex,
 *                variationSettings = FontVariation.Settings(
 *                    FontVariation.weight(400),
 *                    FontVariation.width(100f),
 *                    FontVariation.grade(0),
 *                ),
 *            ),
 *        )
 *   No change needed in this file or any consumer of MaterialTheme.typography.
 */
private val AppFontFamily: FontFamily = FontFamily.Default

private fun textStyle(
    weight: Int,
    size: Int,
    lineHeight: Int = (size * 1.4f).toInt(),
    letterSpacing: Float = 0f,
): TextStyle = TextStyle(
    fontFamily = AppFontFamily,
    fontWeight = FontWeight(weight),
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = letterSpacing.sp,
)

val AppTypography: Typography = Typography(
    displayLarge = textStyle(weight = 400, size = 57, lineHeight = 64, letterSpacing = -0.25f),
    displayMedium = textStyle(weight = 400, size = 45, lineHeight = 52),
    displaySmall = textStyle(weight = 400, size = 36, lineHeight = 44),

    headlineLarge = textStyle(weight = 400, size = 32, lineHeight = 40),
    headlineMedium = textStyle(weight = 500, size = 28, lineHeight = 36),
    headlineSmall = textStyle(weight = 500, size = 24, lineHeight = 32),

    titleLarge = textStyle(weight = 500, size = 22, lineHeight = 28),
    titleMedium = textStyle(weight = 500, size = 16, lineHeight = 24, letterSpacing = 0.15f),
    titleSmall = textStyle(weight = 500, size = 14, lineHeight = 20, letterSpacing = 0.1f),

    bodyLarge = textStyle(weight = 400, size = 16, lineHeight = 24, letterSpacing = 0.5f),
    bodyMedium = textStyle(weight = 400, size = 14, lineHeight = 20, letterSpacing = 0.25f),
    bodySmall = textStyle(weight = 400, size = 12, lineHeight = 16, letterSpacing = 0.4f),

    labelLarge = textStyle(weight = 500, size = 14, lineHeight = 20, letterSpacing = 0.1f),
    labelMedium = textStyle(weight = 500, size = 12, lineHeight = 16, letterSpacing = 0.5f),
    labelSmall = textStyle(weight = 500, size = 11, lineHeight = 16, letterSpacing = 0.5f),
)
