package com.checkingcontainer.feature.units

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.checkingcontainer.core.domain.ClientsRepository
import com.checkingcontainer.core.domain.EstimadosRepository
import com.checkingcontainer.core.domain.InspectionRepository
import com.checkingcontainer.core.domain.ReeferEquipmentRepository
import com.checkingcontainer.core.model.Client
import com.checkingcontainer.core.model.DamageItem
import com.checkingcontainer.core.model.DamageItemStatus
import com.checkingcontainer.core.model.Estimado
import com.checkingcontainer.core.model.EstimadoStatus
import com.checkingcontainer.core.model.MAX_FOTOS_POR_GRUPO
import com.checkingcontainer.feature.units.navigation.ESTIMADO_INSPECTION_ID_ARG
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
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
    private val clientsRepo: ClientsRepository,
    private val inspectionRepo: InspectionRepository,
    private val reeferUnitRepo: ReeferEquipmentRepository,
    private val pdfGenerator: EstimadoPdfGenerator,
    @param:ApplicationContext private val context: Context,
) : ViewModel() {

    private val inspectionId: Long = checkNotNull(savedStateHandle[ESTIMADO_INSPECTION_ID_ARG])

    private val _state = MutableStateFlow(EstimadoUiState(inspectionId = inspectionId))
    val state: StateFlow<EstimadoUiState> = _state.asStateFlow()

    /** Catálogo de clientes activos, para el selector. */
    val activeClients: StateFlow<List<Client>> = clientsRepo.observeActive().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

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
                    mediciones = existing?.mediciones ?: emptyList(),
                    clientId = existing?.clientId,
                    clientIdNumber = existing?.clientIdNumber ?: "",
                    clientDireccion = existing?.clientDireccion ?: "",
                    clientTelefono = existing?.clientTelefono ?: "",
                    clientEmail = existing?.clientEmail ?: "",
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
                (item.damagePhotos + item.repairPhotos).forEach { url -> deletePhotoAsync(url) }
            }
            is EstimadoEvent.RemoveMedicion -> {
                _state.update { s ->
                    s.copy(
                        mediciones = s.mediciones.filterNot { it.timestamp == event.timestamp },
                        isDirty = true,
                        savedMessage = null,
                    )
                }
            }
            is EstimadoEvent.RemoveDamagePhoto -> {
                deletePhotoAsync(event.url)
                _state.update { s ->
                    s.copy(damages = s.damages.map {
                        if (it.id == event.itemId) it.copy(damagePhotos = it.damagePhotos - event.url) else it
                    })
                }
            }
            is EstimadoEvent.RemoveRepairPhoto -> {
                deletePhotoAsync(event.url)
                _state.update { s ->
                    s.copy(damages = s.damages.map {
                        if (it.id == event.itemId) it.copy(repairPhotos = it.repairPhotos - event.url) else it
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

        // Marca cambios sin guardar para avisar al salir. Los eventos de sheets
        // y los *Change de campos pendientes no modifican datos confirmados
        // (los early-return de arriba también evitan marcas falsas).
        when (event) {
            is EstimadoEvent.ShowSheet,
            EstimadoEvent.DismissSheet,
            is EstimadoEvent.DamageDescriptionChange,
            is EstimadoEvent.RepairActionChange,
            is EstimadoEvent.LaborCostChange,
            is EstimadoEvent.MaterialCostChange -> Unit
            else -> _state.update { it.copy(isDirty = true) }
        }
    }

    fun addDamagePhoto(itemId: String, uri: Uri) = uploadPhoto(itemId, isDano = true, uri = uri)
    fun addRepairPhoto(itemId: String, uri: Uri) = uploadPhoto(itemId, isDano = false, uri = uri)

    private fun uploadPhoto(itemId: String, isDano: Boolean, uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(isUploadingPhoto = true, errorMessage = null) }
            val bytes = withContext(Dispatchers.IO) {
                // Si la compresión falla (formato exótico), suben los bytes originales.
                compressForUpload(uri)
                    ?: context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }
            if (bytes == null) {
                _state.update { it.copy(isUploadingPhoto = false, errorMessage = "No se pudo leer la foto") }
                return@launch
            }
            runCatching {
                estimadosRepo.uploadItemPhoto(inspectionId, itemId, isDano, bytes)
            }.onSuccess { url ->
                // Precalienta la caché de disco de Coil con la conexión aún viva:
                // la foto recién subida queda visible offline y tras reiniciar la
                // app, sin depender de una re-descarga posterior.
                coil3.SingletonImageLoader.get(context).enqueue(
                    coil3.request.ImageRequest.Builder(context).data(url).build(),
                )
                _state.update { s ->
                    s.copy(
                        isUploadingPhoto = false,
                        isDirty = true,
                        damages = s.damages.map { item ->
                            if (item.id != itemId) item
                            // Ignora la foto si el grupo ya llegó al máximo (defensa extra;
                            // la UI ya oculta el botón al alcanzar MAX_FOTOS_POR_GRUPO).
                            else if (isDano && item.damagePhotos.size < MAX_FOTOS_POR_GRUPO)
                                item.copy(damagePhotos = item.damagePhotos + url)
                            else if (!isDano && item.repairPhotos.size < MAX_FOTOS_POR_GRUPO)
                                item.copy(repairPhotos = item.repairPhotos + url)
                            else item
                        },
                    )
                }
            }.onFailure { error ->
                _state.update { it.copy(isUploadingPhoto = false, errorMessage = error.message ?: "Error al subir foto") }
            }
        }
    }

    /** Asigna un cliente del catálogo: referencia + snapshot congelado. */
    fun selectClient(client: Client) {
        _state.update {
            it.copy(
                clientId = client.id,
                clientName = client.razonSocial,
                clientIdNumber = client.idNumber,
                clientDireccion = client.direccion,
                clientTelefono = client.telefono,
                clientEmail = client.email,
                isDirty = true,
                savedMessage = null,
            )
        }
    }

    /** Crea el cliente en el catálogo y lo asigna al estimado. */
    fun createClientAndSelect(client: Client, onDone: () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(isSavingClient = true) }
            runCatching { clientsRepo.save(client) }
                .onSuccess { id ->
                    selectClient(client.copy(id = id))
                    _state.update { it.copy(isSavingClient = false) }
                    onDone()
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(isSavingClient = false, errorMessage = e.message ?: "Error al guardar el cliente")
                    }
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

        // EXIF es mejor-esfuerzo: PNG/HEIC u otros formatos pueden lanzar al leer
        // metadatos y eso NO debe descartar la foto (bug: "No se pudo leer la foto").
        val orientation = runCatching {
            resolver.openInputStream(uri)?.use {
                android.media.ExifInterface(it).getAttributeInt(
                    android.media.ExifInterface.TAG_ORIENTATION,
                    android.media.ExifInterface.ORIENTATION_NORMAL,
                )
            }
        }.getOrNull() ?: android.media.ExifInterface.ORIENTATION_NORMAL
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

        // inSampleSize solo reduce por potencias de 2: una foto de 4000px quedaba en
        // 2000px y pesaba 1-3 MB (lento de subir Y de volver a bajar — fotos "que no
        // se ven" con datos móviles). Escalado final exacto: garantiza ≤1600px.
        val mayor = maxOf(bmp.width, bmp.height)
        if (mayor > MAX_PHOTO_DIM) {
            val factor = MAX_PHOTO_DIM.toFloat() / mayor
            val scaled = android.graphics.Bitmap.createScaledBitmap(
                bmp,
                (bmp.width * factor).toInt().coerceAtLeast(1),
                (bmp.height * factor).toInt().coerceAtLeast(1),
                true,
            )
            if (scaled !== bmp) bmp.recycle()
            bmp = scaled
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
                clientId = current.clientId,
                clientIdNumber = current.clientIdNumber,
                clientDireccion = current.clientDireccion,
                clientTelefono = current.clientTelefono,
                clientEmail = current.clientEmail,
                location = current.location.trim(),
                technicianId = 0,
                technicianName = current.technicianName,
                createdAt = if (current.estimadoId == 0L) now else current.createdAt,
                approvedAt = current.approvedAt,
                closedAt = if (allReparado) now else null,
                status = if (allReparado) EstimadoStatus.CERRADO else EstimadoStatus.ABIERTO,
                damages = current.damages,
                mediciones = current.mediciones,
                hasIva = current.hasIva,
            )
            runCatching { estimadosRepo.save(estimado) }
                .onSuccess { savedId ->
                    _state.update {
                        it.copy(
                            isSaving = false,
                            isDirty = false,
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
                clientId = current.clientId,
                clientIdNumber = current.clientIdNumber,
                clientDireccion = current.clientDireccion,
                clientTelefono = current.clientTelefono,
                clientEmail = current.clientEmail,
                location = current.location,
                technicianName = current.technicianName,
                createdAt = current.createdAt,
                approvedAt = current.approvedAt,
                status = current.status,
                damages = current.damages,
                mediciones = current.mediciones,
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
