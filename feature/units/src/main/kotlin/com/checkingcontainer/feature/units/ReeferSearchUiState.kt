package com.checkingcontainer.feature.units

import com.checkingcontainer.core.model.ReeferEquipment

data class ReeferSearchUiState(
    val query: String = "",
    val isSearching: Boolean = false,
    val result: SearchResult = SearchResult.Idle,
)

sealed interface SearchResult {
    data object Idle : SearchResult
    data object NotFound : SearchResult
    data class Found(val equipment: ReeferEquipment, val inspectionCount: Int) : SearchResult
}
