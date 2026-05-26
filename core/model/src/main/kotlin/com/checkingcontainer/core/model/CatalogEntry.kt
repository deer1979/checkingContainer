package com.checkingcontainer.core.model

data class CatalogEntry(
    val id: Long = 0,
    val manufacturerId: Long,
    val modelFamily: String,
    val description: String,
    val serialRangeStart: Long?,
    val serialRangeEnd: Long?,
)
