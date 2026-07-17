package com.checkingcontainer.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import com.checkingcontainer.core.model.InspStatus

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650A4)
val PurpleGrey40 = Color(0xFF625B71)
val Pink40 = Color(0xFF7D5260)

// ── Status semantic colors ──────────────────────────────────────────────────
// Fixed palette — NOT dynamic. Must contrast on both light and dark surfaces.

private val OpContainerLight = Color(0xFF2E7D32)
private val OpContainerDark = Color(0xFF388E3C)
private val OpOnContainer = Color.White

private val NestContainerLight = Color(0xFFC62828)
private val NestContainerDark = Color(0xFFD32F2F)
private val NestOnContainer = Color.White

private val EstContainerLight = Color(0xFFF57F17)
private val EstContainerDark = Color(0xFFF9A825)
private val EstOnContainer = Color(0xFF1A1100)

private val InspContainerLight = Color(0xFF546E7A)
private val InspContainerDark = Color(0xFF607D8B)
private val InspOnContainer = Color.White

data class StatusColors(val container: Color, val onContainer: Color)

fun InspStatus.chipColors(isDark: Boolean): StatusColors = when (this) {
    InspStatus.OP -> StatusColors(
        container = if (isDark) OpContainerDark else OpContainerLight,
        onContainer = OpOnContainer,
    )
    InspStatus.NEST -> StatusColors(
        container = if (isDark) NestContainerDark else NestContainerLight,
        onContainer = NestOnContainer,
    )
    InspStatus.EST -> StatusColors(
        container = if (isDark) EstContainerDark else EstContainerLight,
        onContainer = EstOnContainer,
    )
    // Estados de mantenimiento (equipos no-reefer): preventivo=verde (rutina),
    // correctivo=ámbar (hubo algo), reparación=rojo/est (genera estimado).
    InspStatus.MANT_PREVENTIVO -> StatusColors(
        container = if (isDark) OpContainerDark else OpContainerLight,
        onContainer = OpOnContainer,
    )
    InspStatus.MANT_CORRECTIVO -> StatusColors(
        container = if (isDark) NestContainerDark else NestContainerLight,
        onContainer = NestOnContainer,
    )
    InspStatus.REPARACION -> StatusColors(
        container = if (isDark) EstContainerDark else EstContainerLight,
        onContainer = EstOnContainer,
    )
    InspStatus.INSP -> StatusColors(
        container = if (isDark) InspContainerDark else InspContainerLight,
        onContainer = InspOnContainer,
    )
}
