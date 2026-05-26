package com.checkingcontainer.feature.units

import com.checkingcontainer.core.model.ReeferUnit

data class UnitListUiState(
    val units: List<ReeferUnit> = emptyList(),
    val isLoading: Boolean = true,
)
