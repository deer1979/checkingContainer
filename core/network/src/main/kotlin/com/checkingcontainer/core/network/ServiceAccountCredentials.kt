package com.checkingcontainer.core.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Mapea la estructura del archivo credentials.json de Service Account de Google.
 *
 * El archivo se lee desde [app/src/main/res/raw/credentials.json] y se parsea
 * con [kotlinx.serialization.json.Json] en [AppModule].
 *
 * ⚠️ SEGURIDAD: No loguear esta clase ni sus campos. Contiene la clave privada RSA.
 */
@Serializable
data class ServiceAccountCredentials(
    val type: String = "",
    @SerialName("project_id")   val projectId:   String = "",
    @SerialName("private_key_id") val privateKeyId: String = "",
    @SerialName("private_key")  val privateKey:  String = "",
    @SerialName("client_email") val clientEmail: String = "",
    @SerialName("client_id")    val clientId:    String = "",
    @SerialName("auth_uri")     val authUri:     String = "",
    @SerialName("token_uri")    val tokenUri:    String = "",
)
