package com.checkingcontainer.feature.units

import com.checkingcontainer.core.reporting.EstimadoPdfGenerator
import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.checkingcontainer.core.domain.AuthRepository
import com.checkingcontainer.core.domain.AuthState
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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
    private val authRepository: AuthRepository,
    private val pdfGenerator: EstimadoPdfGenerator,
    private val compresorDeFotos: CompresorDeFotos,
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
    private var pendingCantidad = ""
    private var pendingPrecioUnitario = ""
    private var pendingManoDeObra = ""
    private var pendingNombreItem = ""
    private var activePhotoUploads = 0

    init {
        viewModelScope.launch {
            try {
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
                        fichaTecnica = unit?.fichaTecnica ?: emptyList(),
                        estimadoId = existing?.id ?: 0L,
                        clientName = existing?.clientName ?: "",
                        location = existing?.location ?: inspection?.location ?: "",
                        createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                        approvedAt = existing?.approvedAt,
                        status = existing?.status ?: EstimadoStatus.ABIERTO,
                        damages = existing?.damages ?: emptyList(),
                        mediciones = existing?.mediciones ?: emptyList(),
                        manoDeObraTotal = existing?.manoDeObraTotal,
                        clientId = existing?.clientId,
                        clientIdNumber = existing?.clientIdNumber ?: "",
                        clientDireccion = existing?.clientDireccion ?: "",
                        clientTelefono = existing?.clientTelefono ?: "",
                        clientEmail = existing?.clientEmail ?: "",
                        sitioClienteId = existing?.sitioClienteId,
                        sitioNombre = existing?.sitioNombre ?: "",
                        ordenTrabajo = existing?.ordenTrabajo ?: "",
                        hasIva = existing?.hasIva ?: false,
                        pendienteDeSubir = existing?.pendienteDeSubir ?: false,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "No se pudo cargar el estimado",
                    )
                }
            }
        }
    }

    fun onEvent(event: EstimadoEvent) {
        when (event) {
            is EstimadoEvent.ClientNameChange ->
                _state.update { it.copy(clientName = event.value, savedMessage = null) }
            is EstimadoEvent.LocationChange ->
                _state.update { it.copy(location = event.value, savedMessage = null) }
            is EstimadoEvent.OrdenTrabajoChange ->
                _state.update { it.copy(ordenTrabajo = event.value, isDirty = true, savedMessage = null) }
            EstimadoEvent.ClearSitio ->
                _state.update { it.copy(sitioClienteId = null, sitioNombre = "", isDirty = true) }
            is EstimadoEvent.ShowSheet -> {
                when (val sheet = event.sheet) {
                    is EstimadoSheet.AddDamage -> {
                        pendingDamageDescription = ""
                        pendingNombreItem = ""
                        _state.update { it.copy(activeSheet = sheet) }
                    }
                    is EstimadoSheet.EditDamage -> {
                        val item = _state.value.damages.find { it.id == sheet.itemId }
                        pendingDamageDescription = item?.damageDescription ?: ""
                        pendingNombreItem = item?.nombre ?: ""
                        _state.update { it.copy(activeSheet = sheet) }
                    }
                    is EstimadoSheet.RepairItem -> {
                        pendingRepairAction = _state.value.damages
                            .find { it.id == sheet.itemId }?.repairAction ?: ""
                        _state.update { it.copy(activeSheet = sheet) }
                    }
                    is EstimadoSheet.EditValor -> {
                        val item = _state.value.damages.find { it.id == sheet.itemId }
                        pendingCantidad = (item?.cantidad ?: 1).toString()
                        pendingPrecioUnitario = item?.precioUnitario?.toString() ?: ""
                        _state.update { it.copy(activeSheet = sheet) }
                    }
                    EstimadoSheet.EditManoDeObra -> {
                        pendingManoDeObra = _state.value.manoDeObraTotal?.toString() ?: ""
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
                // Basta con uno de los dos: un servicio puede no necesitar
                // descripción, y un daño puede registrarse antes de saber la pieza.
                if (pendingNombreItem.isBlank() && pendingDamageDescription.isBlank()) return
                val newItem = DamageItem(
                    id = UUID.randomUUID().toString(),
                    nombre = pendingNombreItem.trim(),
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
                if (pendingNombreItem.isBlank() && pendingDamageDescription.isBlank()) return
                _state.update { s ->
                    s.copy(
                        damages = s.damages.map { item ->
                            if (item.id == event.itemId)
                                item.copy(
                                    nombre = pendingNombreItem.trim(),
                                    damageDescription = pendingDamageDescription.trim(),
                                )
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
            is EstimadoEvent.CantidadChange -> pendingCantidad = event.value
            is EstimadoEvent.PrecioUnitarioChange -> pendingPrecioUnitario = event.value
            is EstimadoEvent.NombreItemChange -> pendingNombreItem = event.value
            is EstimadoEvent.ManoDeObraChange -> pendingManoDeObra = event.value
            is EstimadoEvent.ConfirmManoDeObra -> {
                _state.update {
                    it.copy(
                        manoDeObraTotal = pendingManoDeObra.replace(',', '.').toDoubleOrNull(),
                        activeSheet = null,
                    )
                }
            }
            is EstimadoEvent.ConfirmValor -> {
                _state.update { s ->
                    s.copy(
                        damages = s.damages.map { item ->
                            if (item.id == event.itemId)
                                item.copy(
                                    cantidad = pendingCantidad.toIntOrNull()?.coerceAtLeast(1) ?: 1,
                                    precioUnitario = pendingPrecioUnitario.replace(',', '.').toDoubleOrNull(),
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
            is EstimadoEvent.CantidadChange,
            is EstimadoEvent.PrecioUnitarioChange,
            is EstimadoEvent.ManoDeObraChange,
            is EstimadoEvent.NombreItemChange -> Unit
            else -> _state.update { it.copy(isDirty = true) }
        }
    }

    fun addDamagePhoto(itemId: String, uri: Uri) = uploadPhoto(itemId, isDano = true, uri = uri)
    fun addRepairPhoto(itemId: String, uri: Uri) = uploadPhoto(itemId, isDano = false, uri = uri)

    private fun uploadPhoto(itemId: String, isDano: Boolean, uri: Uri) {
        viewModelScope.launch {
            activePhotoUploads += 1
            _state.update { it.copy(isUploadingPhoto = true, errorMessage = null) }
            try {
                val bytes = withContext(Dispatchers.IO) {
                    compresorDeFotos.comprimir(uri)
                        ?: context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                }
                if (bytes == null) {
                    _state.update { it.copy(errorMessage = "No se pudo leer la foto") }
                    return@launch
                }
                runCatching {
                    estimadosRepo.uploadItemPhoto(inspectionId, itemId, isDano, bytes)
                }.onSuccess { url ->
                    coil3.SingletonImageLoader.get(context).enqueue(
                        coil3.request.ImageRequest.Builder(context).data(url).build(),
                    )
                    _state.update { state ->
                        state.copy(
                            isDirty = true,
                            damages = state.damages.map { item ->
                                if (item.id != itemId) item
                                else if (isDano && item.damagePhotos.size < MAX_FOTOS_POR_GRUPO)
                                    item.copy(damagePhotos = item.damagePhotos + url)
                                else if (!isDano && item.repairPhotos.size < MAX_FOTOS_POR_GRUPO)
                                    item.copy(repairPhotos = item.repairPhotos + url)
                                else item
                            },
                        )
                    }
                }.onFailure { error ->
                    _state.update {
                        it.copy(errorMessage = error.message ?: "Error al subir foto")
                    }
                }
            } finally {
                activePhotoUploads = (activePhotoUploads - 1).coerceAtLeast(0)
                _state.update { it.copy(isUploadingPhoto = activePhotoUploads > 0) }
            }
        }
    }

    /** Crea el cliente en el catálogo y lo asigna como sitio del trabajo. */
    fun createClientAndSelectSitio(client: Client, onDone: () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(isSavingClient = true) }
            runCatching { clientsRepo.save(client) }
                .onSuccess { id ->
                    selectSitio(client.copy(id = id))
                    _state.update { it.copy(isSavingClient = false) }
                    onDone()
                }
                .onFailure { e ->
                    _state.update { it.copy(isSavingClient = false, errorMessage = e.message) }
                }
        }
    }

    /** Asigna el sitio del trabajo (cliente final, solo nombre). */
    fun selectSitio(client: Client) {
        _state.update {
            it.copy(sitioClienteId = client.id, sitioNombre = client.razonSocial, isDirty = true)
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


    /** Id del técnico con la sesión abierta; 0 solo si no hay sesión. */
    private suspend fun technicianIdActual(): Long =
        (authRepository.state.first() as? AuthState.Authenticated)?.user?.id ?: 0L

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
                sitioClienteId = current.sitioClienteId,
                sitioNombre = current.sitioNombre,
                ordenTrabajo = current.ordenTrabajo.trim(),
                location = current.location.trim(),
                technicianId = technicianIdActual(),
                technicianName = current.technicianName,
                createdAt = if (current.estimadoId == 0L) now else current.createdAt,
                approvedAt = current.approvedAt,
                closedAt = if (allReparado) now else null,
                status = if (allReparado) EstimadoStatus.CERRADO else EstimadoStatus.ABIERTO,
                damages = current.damages,
                mediciones = current.mediciones,
                manoDeObraTotal = current.manoDeObraTotal,
                hasIva = current.hasIva,
            )
            runCatching { estimadosRepo.save(estimado) }
                .onSuccess { savedId ->
                    val idFinal = if (current.estimadoId == 0L) savedId else current.estimadoId
                    // Si la nube no confirmó, el estimado queda marcado como
                    // pendiente y hay que DECIRLO: antes se anunciaba "Guardado"
                    // aunque el trabajo se hubiera quedado solo en el teléfono.
                    val pendiente = estimadosRepo.findById(idFinal)?.pendienteDeSubir ?: false
                    _state.update {
                        it.copy(
                            isSaving = false,
                            isDirty = false,
                            estimadoId = idFinal,
                            createdAt = if (current.estimadoId == 0L) now else current.createdAt,
                            status = estimado.status,
                            pendienteDeSubir = pendiente,
                            savedMessage = when {
                                pendiente -> "Guardado en el teléfono — sin subir. Se reintentará al haber señal."
                                allReparado -> "Estimado cerrado y subido"
                                else -> "Guardado y subido"
                            },
                        )
                    }
                }
                .onFailure { error ->
                    _state.update { it.copy(isSaving = false, errorMessage = error.message ?: "Error al guardar") }
                }
        }
    }

    /** Reintenta subir este estimado a la nube (botón del aviso). */
    fun reintentarSubida() {
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            val subidos = runCatching { estimadosRepo.subirPendientes() }.getOrDefault(0)
            val sigueP = estimadosRepo.findById(_state.value.estimadoId)?.pendienteDeSubir ?: false
            _state.update {
                it.copy(
                    isSaving = false,
                    pendienteDeSubir = sigueP,
                    savedMessage = if (subidos > 0 && !sigueP) "Subido a la nube" else null,
                    errorMessage = if (sigueP) "Todavía sin señal. Se reintentará solo." else null,
                )
            }
        }
    }

    fun getPendingDamageDescription() = pendingDamageDescription
    fun getPendingRepairAction() = pendingRepairAction
    fun getPendingCantidad() = pendingCantidad
    fun getPendingPrecioUnitario() = pendingPrecioUnitario
    fun getPendingManoDeObra() = pendingManoDeObra
    fun getPendingNombreItem() = pendingNombreItem

    fun generateAndSharePdf() {
        val current = _state.value
        // Antes exigía daños; ahora también genera si solo hay mediciones (un
        // mantenimiento puede ser solo lecturas del manómetro, sin ítems de daño).
        if (current.estimadoId == 0L ||
            (current.damages.isEmpty() && current.mediciones.isEmpty())
        ) {
            return
        }
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
                sitioClienteId = current.sitioClienteId,
                sitioNombre = current.sitioNombre,
                ordenTrabajo = current.ordenTrabajo,
                location = current.location,
                technicianName = current.technicianName,
                createdAt = current.createdAt,
                approvedAt = current.approvedAt,
                status = current.status,
                damages = current.damages,
                mediciones = current.mediciones,
                manoDeObraTotal = current.manoDeObraTotal,
                hasIva = current.hasIva,
            )
            runCatching { pdfGenerator.generate(estimado, current.fichaTecnica) }
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

