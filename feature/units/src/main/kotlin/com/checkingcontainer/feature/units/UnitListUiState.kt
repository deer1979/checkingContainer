package com.checkingcontainer.feature.units

import androidx.compose.runtime.Immutable
import com.checkingcontainer.core.model.ReeferUnit
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Immutable
data class UnitListUiState(
    val units: ImmutableList<ReeferUnit> = persistentListOf(),
    val isLoading: Boolean = true,
)
