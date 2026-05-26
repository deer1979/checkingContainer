package com.checkingcontainer.core.model

data class Manufacturer(
    val id: Long = 0,
    val name: String,
    val modelPrefixes: List<String>,
)
