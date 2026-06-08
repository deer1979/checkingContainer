package com.checkingcontainer.core.database.entity

import com.checkingcontainer.core.model.Attachment
import org.json.JSONArray
import org.json.JSONObject

/**
 * (De)serializa la lista de adjuntos a JSON para guardarla como una sola columna
 * de texto en Room (y en Firestore). Evita una tabla hija y dependencias extra:
 * org.json viene incluido en Android.
 */
internal object AttachmentJson {

    fun encode(attachments: List<Attachment>): String {
        val array = JSONArray()
        attachments.forEach { a ->
            array.put(
                JSONObject()
                    .put("url", a.url)
                    .put("name", a.name)
                    .put("contentType", a.contentType)
                    .put("sizeBytes", a.sizeBytes),
            )
        }
        return array.toString()
    }

    fun decode(json: String): List<Attachment> {
        if (json.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                Attachment(
                    url = o.optString("url"),
                    name = o.optString("name"),
                    contentType = o.optString("contentType"),
                    sizeBytes = o.optLong("sizeBytes"),
                )
            }
        }.getOrDefault(emptyList())
    }
}
