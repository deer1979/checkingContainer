package com.testo3.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Typography built around variable-font axes. We render with [FontFamily.Default]
 * today, but every style declares its weight via [FontVariation.Settings] so
 * dropping a real variable font (e.g. Roboto Flex) into res/font/ and swapping
 * the family below activates true single-file variable rendering — no other
 * change required, the M3 Typography stays identical.
 */
private val AppFontFamily: FontFamily = FontFamily.Default

private fun variableStyle(
    weight: Int,
    size: Int,
    lineHeight: Int = (size * 1.4f).toInt(),
    letterSpacing: Float = 0f,
    width: Float = 100f,
    grade: Int = 0,
): TextStyle = TextStyle(
    fontFamily = AppFontFamily,
    fontWeight = FontWeight(weight),
    fontVariationSettings = FontVariation.Settings(
        FontVariation.weight(weight),
        FontVariation.width(width),
        FontVariation.grade(grade),
    ),
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = letterSpacing.sp,
)

val AppTypography: Typography = Typography(
    displayLarge = variableStyle(weight = 400, size = 57, lineHeight = 64, letterSpacing = -0.25f),
    displayMedium = variableStyle(weight = 400, size = 45, lineHeight = 52),
    displaySmall = variableStyle(weight = 400, size = 36, lineHeight = 44),

    headlineLarge = variableStyle(weight = 400, size = 32, lineHeight = 40),
    headlineMedium = variableStyle(weight = 500, size = 28, lineHeight = 36),
    headlineSmall = variableStyle(weight = 500, size = 24, lineHeight = 32),

    titleLarge = variableStyle(weight = 500, size = 22, lineHeight = 28),
    titleMedium = variableStyle(weight = 500, size = 16, lineHeight = 24, letterSpacing = 0.15f),
    titleSmall = variableStyle(weight = 500, size = 14, lineHeight = 20, letterSpacing = 0.1f),

    bodyLarge = variableStyle(weight = 400, size = 16, lineHeight = 24, letterSpacing = 0.5f),
    bodyMedium = variableStyle(weight = 400, size = 14, lineHeight = 20, letterSpacing = 0.25f),
    bodySmall = variableStyle(weight = 400, size = 12, lineHeight = 16, letterSpacing = 0.4f),

    labelLarge = variableStyle(weight = 500, size = 14, lineHeight = 20, letterSpacing = 0.1f),
    labelMedium = variableStyle(weight = 500, size = 12, lineHeight = 16, letterSpacing = 0.5f),
    labelSmall = variableStyle(weight = 500, size = 11, lineHeight = 16, letterSpacing = 0.5f),
)
