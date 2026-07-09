package com.checkingcontainer.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.checkingcontainer.core.model.Client
import com.checkingcontainer.core.model.ClientIdType

@Entity(tableName = "clients")
data class ClientEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val razonSocial: String,
    val idType: ClientIdType = ClientIdType.RUC,
    val idNumber: String = "",
    val email: String = "",
    val direccion: String = "",
    val telefono: String = "",
    val contacto: String = "",
    val notas: String = "",
    val isActive: Int = 1,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
) {
    fun toDomain(): Client = Client(
        id = id,
        razonSocial = razonSocial,
        idType = idType,
        idNumber = idNumber,
        email = email,
        direccion = direccion,
        telefono = telefono,
        contacto = contacto,
        notas = notas,
        isActive = isActive != 0,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}

fun Client.toEntity(): ClientEntity = ClientEntity(
    id = id,
    razonSocial = razonSocial,
    idType = idType,
    idNumber = idNumber,
    email = email,
    direccion = direccion,
    telefono = telefono,
    contacto = contacto,
    notas = notas,
    isActive = if (isActive) 1 else 0,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
