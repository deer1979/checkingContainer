package com.checkingcontainer.feature.units

import androidx.compose.runtime.Immutable
import com.checkingcontainer.core.model.InspectionWithEquipment
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Immutable
data class UnitListUiState(
    val units: ImmutableList<InspectionWithEquipment> = persistentListOf(),
    val isLoading: Boolean = true,
)
