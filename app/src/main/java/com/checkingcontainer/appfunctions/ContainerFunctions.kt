package com.checkingcontainer.appfunctions

import androidx.appfunctions.AppFunctionContext
import androidx.appfunctions.AppFunctionElementNotFoundException
import androidx.appfunctions.AppFunctionSerializable
import androidx.appfunctions.service.AppFunction
import com.checkingcontainer.core.domain.EstimadosRepository
import com.checkingcontainer.core.domain.InspectionRepository
import com.checkingcontainer.core.domain.ReeferEquipmentRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * AppFunctions: expone los flujos de negocio principales de la app como
 * funciones invocables por el sistema Android y agentes de IA (la app actúa
 * como servidor MCP local). Disponible en dispositivos con Android 16+;
 * en versiones anteriores simplemente no se registran.
 */
@Singleton
class ContainerFunctions @Inject constructor(
    private val equipmentRepo: ReeferEquipmentRepository,
    private val inspectionRepo: InspectionRepository,
    private val estimadosRepo: EstimadosRepository,
) {

    /** Parámetros para consultar un contenedor refrigerado. */
    @AppFunctionSerializable
    data class ConsultaContenedorParams(
        /** Número de contenedor ISO 6346, por ejemplo "CSQU3054383". */
        val containerNo: String,
    )

    /** Ficha de un contenedor refrigerado con su historial de inspecciones. */
    @AppFunctionSerializable
    data class FichaContenedor(
        /** Número de contenedor. */
        val containerNo: String,
        /** Fabricante del equipo frigorífico. */
        val manufacturer: String,
        /** Modelo de la unidad. */
        val unitModel: String,
        /** Número de serie de la unidad. */
        val unitSerialNo: String,
        /** Año de fabricación. */
        val yearOfBuilt: String,
        /** Total de inspecciones registradas para este contenedor. */
        val totalInspecciones: Long,
        /** Estado de la inspección más reciente (INSP, PTI, APROBADO) o vacío. */
        val ultimaInspeccionStatus: String,
    )

    /** Resumen de los estimados de reparación abiertos. */
    @AppFunctionSerializable
    data class ResumenEstimados(
        /** Cantidad de estimados abiertos (pendientes de cierre). */
        val abiertos: Long,
        /** Números de contenedor con estimado abierto. */
        val contenedores: List<String>,
    )

    /**
     * Busca un contenedor refrigerado por su número y devuelve su ficha técnica
     * con el total de inspecciones y el estado de la más reciente.
     *
     * @param params El número de contenedor a consultar.
     */
    @AppFunction
    suspend fun consultarContenedor(
        appFunctionContext: AppFunctionContext,
        params: ConsultaContenedorParams,
    ): FichaContenedor {
        val containerNo = params.containerNo.trim().uppercase()
        val equipment = equipmentRepo.findByContainerNo(containerNo)
            ?: throw AppFunctionElementNotFoundException(
                "No existe el contenedor $containerNo en la base local",
            )
        val total = inspectionRepo.countByContainerNo(containerNo)
        val latest = inspectionRepo.getLatest2ByContainerNo(containerNo).firstOrNull()
        return FichaContenedor(
            containerNo = equipment.containerNo,
            manufacturer = equipment.manufacturer,
            unitModel = equipment.unitModel,
            unitSerialNo = equipment.unitSerialNo,
            yearOfBuilt = equipment.yearOfBuilt,
            totalInspecciones = total.toLong(),
            ultimaInspeccionStatus = latest?.status?.name ?: "",
        )
    }

    /**
     * Devuelve cuántos estimados de reparación siguen abiertos y para qué
     * contenedores, para dar seguimiento sin abrir la app.
     */
    @AppFunction
    suspend fun resumenEstimadosAbiertos(
        appFunctionContext: AppFunctionContext,
    ): ResumenEstimados {
        val abiertos = estimadosRepo.observeOpen().first()
        return ResumenEstimados(
            abiertos = abiertos.size.toLong(),
            contenedores = abiertos.map { it.containerNo },
        )
    }
}
