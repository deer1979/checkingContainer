package com.checkingcontainer.feature.units

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.checkingcontainer.core.domain.EstimadosRepository
import com.checkingcontainer.core.domain.InspectionRepository
import com.checkingcontainer.core.domain.ReeferEquipmentRepository
import com.checkingcontainer.core.model.DamageItem
import com.checkingcontainer.core.model.DamageItemStatus
import com.checkingcontainer.core.model.Estimado
import com.checkingcontainer.core.model.EstimadoStatus
import com.checkingcontainer.feature.units.navigation.ESTIMADO_INSPECTION_ID_ARG
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class EstimadoViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val estimadosRepo: EstimadosRepository,
    private val inspectionRepo: InspectionRepository,
    private val reeferUnitRepo: ReeferEquipmentRepository,
    private val pdfGenerator: EstimadoPdfGenerator,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val inspectionId: Long = checkNotNull(savedStateHandle[ESTIMADO_INSPECTION_ID_ARG])

    private val _state = MutableStateFlow(EstimadoUiState(inspectionId = inspectionId))
    val state: StateFlow<EstimadoUiState> = _state.asStateFlow()

    // Temporary fields for bottom-sheet forms
    private var pendingDamageDescription = ""
    private var pendingRepairAction = ""
    private var pendingLaborCost = ""
    private var pendingMaterialCost = ""

    init {
        viewModelScope.launch {
            // findById y findByInspectionId son independientes: en paralelo.
            val inspectionDeferred = async { inspectionRepo.findById(inspectionId) }
            val existingDeferred = async { estimadosRepo.findByInspectionId(inspectionId) }
            val inspection = inspectionDeferred.await()
            val unit = inspection?.containerNo?.let { reeferUnitRepo.findByContainerNo(it) }
            val existing = existingDeferred.await()
            _state.update {
                it.copy(
                    isLoading = false,
                    containerNo = inspection?.containerNo ?: "",
                    technicianName = inspection?.technicianName ?: "",
                    manufacturer = unit?.manufacturer ?: "",
                    unitModel = unit?.unitModel ?: "",
                    unitModelNo = unit?.unitModelNo ?: "",
                    unitSerialNo = unit?.unitSerialNo ?: "",
                    yearOfBuilt = unit?.yearOfBuilt ?: "",
                    unitType = unit?.unitType ?: "",
                    estimadoId = existing?.id ?: 0L,
                    clientName = existing?.clientName ?: "",
                    location = existing?.location ?: inspection?.location ?: "",
                    createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                    approvedAt = existing?.approvedAt,
                    status = existing?.status ?: EstimadoStatus.ABIERTO,
                    damages = existing?.damages ?: emptyList(),
                    hasIva = existing?.hasIva ?: false,
                )
            }
        }
    }

    fun onEvent(event: EstimadoEvent) {
        when (event) {
            is EstimadoEvent.ClientNameChange ->
                _state.update { it.copy(clientName = event.value, savedMessage = null) }
            is EstimadoEvent.LocationChange ->
                _state.update { it.copy(location = event.value, savedMessage = null) }
            is EstimadoEvent.ShowSheet -> {
                when (val sheet = event.sheet) {
                    is EstimadoSheet.AddDamage -> {
                        pendingDamageDescription = ""
                        _state.update { it.copy(activeSheet = sheet) }
                    }
                    is EstimadoSheet.EditDamage -> {
                        pendingDamageDescription = _state.value.damages
                            .find { it.id == sheet.itemId }?.damageDescription ?: ""
                        _state.update { it.copy(activeSheet = sheet) }
                    }
                    is EstimadoSheet.RepairItem -> {
                        pendingRepairAction = _state.value.damages
                            .find { it.id == sheet.itemId }?.repairAction ?: ""
                        _state.update { it.copy(activeSheet = sheet) }
                    }
                    is EstimadoSheet.EditValor -> {
                        val item = _state.value.damages.find { it.id == sheet.itemId }
                        pendingLaborCost = item?.laborCost?.toString() ?: ""
                        pendingMaterialCost = item?.materialCost?.toString() ?: ""
                        _state.update { it.copy(activeSheet = sheet) }
                    }
                }
            }
            EstimadoEvent.DismissSheet ->
                _state.update { it.copy(activeSheet = null) }
            is EstimadoEvent.IvaToggle ->
                _state.update { it.copy(hasIva = event.enabled) }
            is EstimadoEvent.DamageDescriptionChange ->
                pendingDamageDescription = event.value
            is EstimadoEvent.ConfirmAddDamage -> {
                if (pendingDamageDescription.isBlank()) return
                val newItem = DamageItem(
                    id = UUID.randomUUID().toString(),
                    damageDescription = pendingDamageDescription.trim(),
                )
                _state.update {
                    it.copy(
                        damages = it.damages + newItem,
                        activeSheet = null,
                        savedMessage = null,
                    )
                }
            }
            is EstimadoEvent.ConfirmEditDamage -> {
                if (pendingDamageDescription.isBlank()) return
                _state.update { s ->
                    s.copy(
                        damages = s.damages.map { item ->
                            if (item.id == event.itemId)
                                item.copy(damageDescription = pendingDamageDescription.trim())
                            else item
                        },
                        activeSheet = null,
                        savedMessage = null,
                    )
                }
            }
            is EstimadoEvent.RemoveDamageItem -> {
                val item = _state.value.damages.find { it.id == event.itemId } ?: return
                _state.update { it.copy(damages = it.damages - item) }
                item.damagePhoto?.let { url -> deletePhotoAsync(url) }
                item.repairPhoto?.let { url -> deletePhotoAsync(url) }
            }
            is EstimadoEvent.RemoveDamagePhoto -> {
                val item = _state.value.damages.find { it.id == event.itemId } ?: return
                item.damagePhoto?.let { deletePhotoAsync(it) }
                _state.update { s ->
                    s.copy(damages = s.damages.map {
                        if (it.id == event.itemId) it.copy(damagePhoto = null) else it
                    })
                }
            }
            is EstimadoEvent.RemoveRepairPhoto -> {
                val item = _state.value.damages.find { it.id == event.itemId } ?: return
                item.repairPhoto?.let { deletePhotoAsync(it) }
                _state.update { s ->
                    s.copy(damages = s.damages.map {
                        if (it.id == event.itemId) it.copy(repairPhoto = null) else it
                    })
                }
            }
            is EstimadoEvent.RepairActionChange ->
                pendingRepairAction = event.value
            is EstimadoEvent.ConfirmRepair -> {
                val now = System.currentTimeMillis()
                val isFirstRepair = _state.value.damages.none { it.status == DamageItemStatus.REPARADO }
                _state.update { s ->
                    s.copy(
                        damages = s.damages.map { item ->
                            if (item.id == event.itemId)
                                item.copy(
                                    repairAction = pendingRepairAction.trim(),
                                    status = DamageItemStatus.REPARADO,
                                )
                            else item
                        },
                        approvedAt = if (isFirstRepair && s.approvedAt == null) now else s.approvedAt,
                        activeSheet = null,
                        savedMessage = null,
                    )
                }
            }
            is EstimadoEvent.LaborCostChange -> pendingLaborCost = event.value
            is EstimadoEvent.MaterialCostChange -> pendingMaterialCost = event.value
            is EstimadoEvent.ConfirmValor -> {
                _state.update { s ->
                    s.copy(
                        damages = s.damages.map { item ->
                            if (item.id == event.itemId)
                                item.copy(
                                    laborCost = pendingLaborCost.toDoubleOrNull(),
                                    materialCost = pendingMaterialCost.toDoubleOrNull(),
                                )
                            else item
                        },
                        activeSheet = null,
                    )
                }
            }
        }
    }

    fun addDamagePhoto(itemId: String, uri: Uri) = uploadPhoto(itemId, isDano = true, uri = uri)
    fun addRepairPhoto(itemId: String, uri: Uri) = uploadPhoto(itemId, isDano = false, uri = uri)

    private fun uploadPhoto(itemId: String, isDano: Boolean, uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(isUploadingPhoto = true, errorMessage = null) }
            val bytes = withContext(Dispatchers.IO) { compressForUpload(uri) }
            if (bytes == null) {
                _state.update { it.copy(isUploadingPhoto = false, errorMessage = "No se pudo leer la foto") }
                return@launch
            }
            runCatching {
                estimadosRepo.uploadItemPhoto(inspectionId, itemId, isDano, bytes)
            }.onSuccess { url ->
                _state.update { s ->
                    s.copy(
                        isUploadingPhoto = false,
                        damages = s.damages.map { item ->
                            if (item.id != itemId) item
                            else if (isDano) item.copy(damagePhoto = url)
                            else item.copy(repairPhoto = url)
                        },
                    )
                }
            }.onFailure { error ->
                _state.update { it.copy(isUploadingPhoto = false, errorMessage = error.message ?: "Error al subir foto") }
            }
        }
    }

    private fun deletePhotoAsync(url: String) {
        viewModelScope.launch(Dispatchers.IO) { estimadosRepo.deletePhoto(url) }
    }

    /**
     * Reescala (máx 1600px) y comprime a JPEG 80 antes de subir: la cámara entrega
     * 3-6 MB por foto y sin esto se subían los bytes crudos a Storage. Aplica la
     * rotación EXIF porque al re-codificar se pierde ese metadato.
     */
    private fun compressForUpload(uri: Uri): ByteArray? = runCatching {
        val resolver = context.contentResolver
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use {
            android.graphics.BitmapFactory.decodeStream(it, null, bounds)
        } ?: return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_PHOTO_DIM) sample *= 2

        val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
        var bmp = resolver.openInputStream(uri)?.use {
            android.graphics.BitmapFactory.decodeStream(it, null, opts)
        } ?: return null

        val orientation = resolver.openInputStream(uri)?.use {
            android.media.ExifInterface(it).getAttributeInt(
                android.media.ExifInterface.TAG_ORIENTATION,
                android.media.ExifInterface.ORIENTATION_NORMAL,
            )
        } ?: android.media.ExifInterface.ORIENTATION_NORMAL
        val degrees = when (orientation) {
            android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (degrees != 0f) {
            val matrix = android.graphics.Matrix().apply { postRotate(degrees) }
            val rotated = android.graphics.Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
            if (rotated !== bmp) bmp.recycle()
            bmp = rotated
        }

        java.io.ByteArrayOutputStream().use { out ->
            bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            bmp.recycle()
            out.toByteArray()
        }
    }.getOrNull()

    fun save() {
        viewModelScope.launch {
            val current = _state.value
            _state.update { it.copy(isSaving = true, errorMessage = null, savedMessage = null) }
            val now = System.currentTimeMillis()
            val allReparado = current.damages.isNotEmpty() &&
                current.damages.all { it.status == DamageItemStatus.REPARADO }
            val estimado = Estimado(
                id = current.estimadoId,
                inspectionId = inspectionId,
                containerNo = current.containerNo,
                manufacturer = current.manufacturer,
                unitModel = current.unitModel,
                unitModelNo = current.unitModelNo,
                unitSerialNo = current.unitSerialNo,
                yearOfBuilt = current.yearOfBuilt,
                unitType = current.unitType,
                clientName = current.clientName.trim(),
                location = current.location.trim(),
                technicianId = 0,
                technicianName = current.technicianName,
                createdAt = if (current.estimadoId == 0L) now else current.createdAt,
                approvedAt = current.approvedAt,
                closedAt = if (allReparado) now else null,
                status = if (allReparado) EstimadoStatus.CERRADO else EstimadoStatus.ABIERTO,
                damages = current.damages,
                hasIva = current.hasIva,
            )
            runCatching { estimadosRepo.save(estimado) }
                .onSuccess { savedId ->
                    _state.update {
                        it.copy(
                            isSaving = false,
                            estimadoId = if (current.estimadoId == 0L) savedId else current.estimadoId,
                            createdAt = if (current.estimadoId == 0L) now else current.createdAt,
                            status = estimado.status,
                            savedMessage = if (allReparado) "Estimado cerrado" else "Guardado",
                        )
                    }
                }
                .onFailure { error ->
                    _state.update { it.copy(isSaving = false, errorMessage = error.message ?: "Error al guardar") }
                }
        }
    }

    fun getPendingDamageDescription() = pendingDamageDescription
    fun getPendingRepairAction() = pendingRepairAction
    fun getPendingLaborCost() = pendingLaborCost
    fun getPendingMaterialCost() = pendingMaterialCost

    fun generateAndSharePdf() {
        val current = _state.value
        if (current.estimadoId == 0L || current.damages.isEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(isGeneratingPdf = true, errorMessage = null) }
            val estimado = Estimado(
                id = current.estimadoId,
                inspectionId = inspectionId,
                containerNo = current.containerNo,
                manufacturer = current.manufacturer,
                unitModel = current.unitModel,
                unitModelNo = current.unitModelNo,
                unitSerialNo = current.unitSerialNo,
                yearOfBuilt = current.yearOfBuilt,
                unitType = current.unitType,
                clientName = current.clientName,
                location = current.location,
                technicianName = current.technicianName,
                createdAt = current.createdAt,
                approvedAt = current.approvedAt,
                status = current.status,
                damages = current.damages,
                hasIva = current.hasIva,
            )
            runCatching { pdfGenerator.generate(estimado) }
                .onSuccess { bytes ->
                    val file = File(context.cacheDir, "estimado_${inspectionId}.pdf")
                    withContext(Dispatchers.IO) { file.writeBytes(bytes) }
                    // Subir a Firebase Storage en segundo plano
                    launch(Dispatchers.IO) {
                        runCatching { estimadosRepo.uploadPdf(inspectionId, bytes) }
                    }
                    _state.update { it.copy(isGeneratingPdf = false, pdfPreviewPath = file.absolutePath) }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(isGeneratingPdf = false, errorMessage = "Error al generar PDF: ${error.message}")
                    }
                }
        }
    }

    fun clearPdfPath() = _state.update { it.copy(pdfPreviewPath = null) }
}

private const val MAX_PHOTO_DIM = 1600
private const val JPEG_QUALITY = 80
