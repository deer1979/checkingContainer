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
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
    onEdit: (Long) -> Unit = {},
    viewModel: UnitDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    UnitDetailScreen(
        state = state,
        onBack = onBack,
        onEdit = onEdit,
        onRequestLoadAll = viewModel::requestLoadAll,
        onDismissLoadAll = viewModel::dismissLoadAll,
        onConfirmLoadAll = viewModel::confirmLoadAll,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnitDetailScreen(
    state: UnitDetailUiState,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onRequestLoadAll: () -> Unit,
    onDismissLoadAll: () -> Unit,
    onConfirmLoadAll: () -> Unit,
) {
    val equipment = state.equipment
    val latestInspection = state.recentInspections.firstOrNull()
    val timelineInspections = if (state.hasLoadedAll) state.allInspections else state.recentInspections

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(equipment?.containerNo ?: "Detalle") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    if (latestInspection != null) {
                        IconButton(onClick = { onEdit(latestInspection.id) }) {
                            Icon(Icons.Outlined.Edit, contentDescription = "Editar inspección")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
    ) { innerPadding ->
        if (equipment == null && !state.isLoading) {
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
            if (equipment != null) {
                UnitDetailCard(
                    equipment = equipment,
                    deployedAs = latestInspection?.deployedAs,
                )
                if (timelineInspections.isNotEmpty()) {
                    UnitTimeline(
                        inspections = timelineInspections,
                        remaining = state.remaining,
                        isLoadingAll = state.isLoadingAll,
                        hasLoadedAll = state.hasLoadedAll,
                        onRequestLoadAll = onRequestLoadAll,
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (state.showLoadAllConfirm) {
        AlertDialog(
            onDismissRequest = onDismissLoadAll,
            title = { Text("Cargar historial completo") },
            text = { Text("Se cargarán ${state.remaining} inspecciones adicionales. Esto consume lecturas de base de datos.") },
            confirmButton = {
                TextButton(onClick = onConfirmLoadAll) {
                    Text("Cargar")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissLoadAll) {
                    Text("Cancelar")
                }
            },
        )
    }
}
