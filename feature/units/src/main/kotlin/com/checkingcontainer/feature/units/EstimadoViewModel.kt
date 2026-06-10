package com.checkingcontainer.feature.units

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.checkingcontainer.core.domain.EstimadosRepository
import com.checkingcontainer.core.domain.InspectionRepository
import com.checkingcontainer.core.model.Estimado
import com.checkingcontainer.core.model.EstimadoFase
import com.checkingcontainer.core.model.EstimadoStatus
import com.checkingcontainer.feature.units.navigation.ESTIMADO_INSPECTION_ID_ARG
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class EstimadoViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val estimadosRepo: EstimadosRepository,
    private val inspectionRepo: InspectionRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val inspectionId: Long = checkNotNull(savedStateHandle[ESTIMADO_INSPECTION_ID_ARG])

    private val _state = MutableStateFlow(EstimadoUiState(inspectionId = inspectionId))
    val state: StateFlow<EstimadoUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val inspection = inspectionRepo.findById(inspectionId)
            val existing = estimadosRepo.findByInspectionId(inspectionId)
            _state.update {
                it.copy(
                    isLoading = false,
                    containerNo = inspection?.containerNo ?: "",
                    technicianId = inspection?.technicianId ?: 0L,
                    technicianName = inspection?.technicianName ?: "",
                    location = inspection?.location ?: "",
                    estimadoId = existing?.id ?: 0L,
                    clientName = existing?.clientName ?: "",
                    damageDescription = existing?.damageDescription ?: "",
                    damagePhotos = existing?.damagePhotos ?: emptyList(),
                    repairDescription = existing?.repairDescription ?: "",
                    repairPhotos = existing?.repairPhotos ?: emptyList(),
                    status = existing?.status ?: EstimadoStatus.ABIERTO,
                    createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                    showRepairSection = existing?.status == EstimadoStatus.REPARADO,
                )
            }
        }
    }

    fun onEvent(event: EstimadoEvent) {
        when (event) {
            is EstimadoEvent.ClientNameChange ->
                _state.update { it.copy(clientName = event.value, savedMessage = null) }
            is EstimadoEvent.DamageDescriptionChange ->
                _state.update { it.copy(damageDescription = event.value, savedMessage = null) }
            is EstimadoEvent.RepairDescriptionChange ->
                _state.update { it.copy(repairDescription = event.value, savedMessage = null) }
            EstimadoEvent.ShowRepairSection ->
                _state.update { it.copy(showRepairSection = true) }
            EstimadoEvent.MarkAsReparado -> {
                _state.update { it.copy(status = EstimadoStatus.REPARADO) }
                save()
            }
            is EstimadoEvent.RemoveDamagePhoto -> removePhoto(event.url, EstimadoFase.DANO)
            is EstimadoEvent.RemoveRepairPhoto -> removePhoto(event.url, EstimadoFase.REPARACION)
        }
    }

    fun addDamagePhoto(uri: Uri) = uploadPhoto(uri, EstimadoFase.DANO)
    fun addRepairPhoto(uri: Uri) = uploadPhoto(uri, EstimadoFase.REPARACION)

    private fun uploadPhoto(uri: Uri, fase: EstimadoFase) {
        viewModelScope.launch {
            _state.update { it.copy(isUploadingPhoto = true, errorMessage = null) }
            val bytes = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }
            if (bytes == null) {
                _state.update { it.copy(isUploadingPhoto = false, errorMessage = "No se pudo leer la foto") }
                return@launch
            }
            runCatching {
                estimadosRepo.uploadPhoto(inspectionId, fase, bytes)
            }.onSuccess { url ->
                _state.update { s ->
                    if (fase == EstimadoFase.DANO)
                        s.copy(isUploadingPhoto = false, damagePhotos = s.damagePhotos + url, savedMessage = null)
                    else
                        s.copy(isUploadingPhoto = false, repairPhotos = s.repairPhotos + url, savedMessage = null)
                }
            }.onFailure { error ->
                _state.update {
                    it.copy(isUploadingPhoto = false, errorMessage = error.message ?: "Error al subir foto")
                }
            }
        }
    }

    private fun removePhoto(url: String, fase: EstimadoFase) {
        _state.update { s ->
            if (fase == EstimadoFase.DANO)
                s.copy(damagePhotos = s.damagePhotos - url, savedMessage = null)
            else
                s.copy(repairPhotos = s.repairPhotos - url, savedMessage = null)
        }
        viewModelScope.launch(Dispatchers.IO) {
            estimadosRepo.deletePhoto(url)
        }
    }

    fun save() {
        viewModelScope.launch {
            val current = _state.value
            _state.update { it.copy(isSaving = true, errorMessage = null, savedMessage = null) }
            val now = System.currentTimeMillis()
            val estimado = Estimado(
                id = current.estimadoId,
                inspectionId = inspectionId,
                containerNo = current.containerNo,
                clientName = current.clientName.trim(),
                technicianId = current.technicianId,
                technicianName = current.technicianName,
                location = current.location,
                createdAt = if (current.estimadoId == 0L) now else current.createdAt,
                status = current.status,
                damageDescription = current.damageDescription.trim(),
                damagePhotos = current.damagePhotos,
                repairDescription = current.repairDescription.trim(),
                repairPhotos = current.repairPhotos,
            )
            runCatching { estimadosRepo.save(estimado) }
                .onSuccess { savedId ->
                    _state.update {
                        it.copy(
                            isSaving = false,
                            estimadoId = if (current.estimadoId == 0L) savedId else current.estimadoId,
                            createdAt = if (current.estimadoId == 0L) now else current.createdAt,
                            savedMessage = "Guardado",
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(isSaving = false, errorMessage = error.message ?: "Error al guardar")
                    }
                }
        }
    }
}
