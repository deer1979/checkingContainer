package com.checkingcontainer.core.model

/** Tipo de identificación tributaria del cliente (Ecuador / SRI). */
enum class ClientIdType { RUC, CEDULA, PASAPORTE }

/**
 * Cliente del catálogo: los datos que exige el SRI para facturarle
 * (razón social + identificación) más los operativos (email para la factura
 * electrónica, dirección, teléfono, contacto). Se crea una vez y se
 * reutiliza en los estimados.
 */
data class Client(
    val id: Long = 0,
    val razonSocial: String,
    val idType: ClientIdType = ClientIdType.RUC,
    val idNumber: String = "",
    val email: String = "",
    val direccion: String = "",
    val telefono: String = "",
    val contacto: String = "",
    val notas: String = "",
    val isActive: Boolean = true,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)
