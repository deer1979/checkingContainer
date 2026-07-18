package com.checkingcontainer.core.model

data class ReeferEquipment(
    val containerNo: String,
    val manufacturer: String = "",
    val unitModel: String = "",
    val unitModelNo: String = "",
    val unitSerialNo: String = "",
    val yearOfBuilt: String = "",
    val brand: Brand = Brand.CARRIER,
    val unitType: String = "",
    val tipoEquipo: TipoEquipo = TipoEquipo.REEFER,
    // Ficha técnica: TODOS los datos legibles de la placa (lista abierta).
    val fichaTecnica: List<CampoFicha> = emptyList(),
    // Foto de la placa de datos (URL remota o ruta local file://)
    val fotoPlacaUrl: String? = null,
)
