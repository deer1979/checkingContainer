package com.checkingcontainer.core.model

data class CatalogEntry(
    val id: Long = 0,
    val manufacturerId: Long,
    val serie: String,
    val rangeStart: Int,
    val rangeEnd: Int,
    val unitModel: String,
    val unitType: String,
)
