package com.checkingcontainer.feature.units

import com.google.mlkit.genai.schema.annotations.Generable
import com.google.mlkit.genai.schema.annotations.Guide

/**
 * Esquema de salida ESTRUCTURADA para Gemini Nano (ML Kit R41 Structured
 * Output): el modelo está obligado a responder exactamente con esta forma —
 * se acabó el parseo de texto libre. La estructura garantiza formato, no
 * verdad: RUC/cédula se revalidan después con IdentificacionEc (SRI).
 */
// Pública (no internal): el provider que genera el compilador de esquemas es público.
@Generable(description = "Datos de contacto y facturación de un cliente ecuatoriano, extraídos de una factura o de texto")
data class DatosClienteExtraidos(
    @Guide(description = "Nombre completo o razón social del negocio o persona; cadena vacía si no aparece")
    val razonSocial: String,
    @Guide(description = "RUC ecuatoriano de exactamente 13 dígitos tal como aparece impreso; cadena vacía si no aparece")
    val ruc: String,
    @Guide(description = "Cédula ecuatoriana de exactamente 10 dígitos; cadena vacía si no aparece")
    val cedula: String,
    @Guide(description = "Correo electrónico; cadena vacía si no aparece")
    val email: String,
    @Guide(description = "Dirección física; cadena vacía si no aparece")
    val direccion: String,
    @Guide(description = "Número de teléfono; cadena vacía si no aparece")
    val telefono: String,
)
