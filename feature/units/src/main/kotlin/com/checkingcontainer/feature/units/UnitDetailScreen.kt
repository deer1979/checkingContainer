package com.checkingcontainer.feature.units

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun UnitDetailRoute(
    onBack: () -> Unit,
    viewModel: UnitDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    UnitDetailScreen(state = state, onBack = onBack, onLoadAll = viewModel::loadAll)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnitDetailScreen(
    state: UnitDetailUiState,
    onBack: () -> Unit,
    onLoadAll: () -> Unit,
) {
    val unit = state.latest

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(unit?.containerNo ?: "Detalle") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
    ) { innerPadding ->
        if (unit == null && !state.isLoading) {
            Text(
                text = "No se encontró información para este contenedor.",
                modifier = Modifier.padding(innerPadding).padding(16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (unit != null) {
                UnitDetailCard(unit = unit)
                UnitTimeline(
                    latest = unit,
                    history = state.history,
                    isLoadingAll = state.isLoadingAll,
                    hasLoadedAll = state.hasLoadedAll,
                    onLoadAll = onLoadAll,
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
