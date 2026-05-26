package com.checkingcontainer.core.model

import kotlinx.collections.immutable.ImmutableList

data class Manufacturer(
    val id: Long = 0,
    val name: String,
    val modelPrefixes: ImmutableList<String>,
)
