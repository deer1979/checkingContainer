package com.checkingcontainer.feature.admin

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun AdminRoute(
    onBack: () -> Unit,
    onPublished: () -> Unit,
    viewModel: AdminViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.published) {
        if (state.published) onPublished()
    }
    AdminScreen(
        state = state,
        onBack = onBack,
        onTitleChange = viewModel::onTitleChange,
        onSummaryChange = viewModel::onSummaryChange,
        onBodyChange = viewModel::onBodyChange,
        onAttachmentPicked = viewModel::onAttachmentPicked,
        onRemoveAttachment = viewModel::onRemoveAttachment,
        onPublish = viewModel::onPublish,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdminScreen(
    state: AdminUiState,
    onBack: () -> Unit,
    onTitleChange: (String) -> Unit,
    onSummaryChange: (String) -> Unit,
    onBodyChange: (String) -> Unit,
    onAttachmentPicked: (Uri) -> Unit,
    onRemoveAttachment: (Uri) -> Unit,
    onPublish: () -> Unit,
) {
    val pickLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri -> uri?.let(onAttachmentPicked) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Crear anuncio") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Atrás",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Publica un nuevo anuncio para los usuarios.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        value = state.draftTitle,
                        onValueChange = onTitleChange,
                        label = { Text("Título") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = state.draftSummary,
                        onValueChange = onSummaryChange,
                        label = { Text("Resumen (opcional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = state.draftBody,
                        onValueChange = onBodyChange,
                        label = { Text("Contenido") },
                        minLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Text(
                        text = "Adjuntos",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = { pickLauncher.launch("image/*") },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Outlined.Image, contentDescription = null)
                            Text("  Imagen")
                        }
                        OutlinedButton(
                            onClick = { pickLauncher.launch("*/*") },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Outlined.AttachFile, contentDescription = null)
                            Text("  Archivo")
                        }
                    }
                    state.pendingAttachments.forEach { att ->
                        AttachmentRow(
                            name = att.name,
                            sizeBytes = att.sizeBytes,
                            isImage = att.isImage,
                            onRemove = { onRemoveAttachment(att.uri) },
                        )
                    }

                    state.errorMessage?.let { msg ->
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Button(
                        onClick = onPublish,
                        enabled = state.canPublish,
                        shape = CircleShape,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (state.isPublishing) "Publicando…" else "Publicar anuncio")
                    }
                }
            }
        }
    }
}

@Composable
private fun AttachmentRow(
    name: String,
    sizeBytes: Long,
    isImage: Boolean,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (isImage) Icons.Outlined.Image else Icons.Outlined.InsertDriveFile,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (sizeBytes > 0) {
                Text(
                    text = formatSize(sizeBytes),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Outlined.Close, contentDescription = "Quitar")
        }
    }
}

internal fun formatSize(bytes: Long): String = when {
    bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000.0)
    bytes >= 1_000 -> "%.0f KB".format(bytes / 1_000.0)
    else -> "$bytes B"
}
