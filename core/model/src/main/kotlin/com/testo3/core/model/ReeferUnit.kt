package com.testo3.core.model

data class ReeferUnit(
    val id: Long = 0,
    val containerNo: String,
    val manufacturer: String = "",
    val unitModel: String,
    val unitModelNo: String = "",
    val unitSerialNo: String,
    val yearOfBuilt: String,
    val createdAt: Long = System.currentTimeMillis(),
)