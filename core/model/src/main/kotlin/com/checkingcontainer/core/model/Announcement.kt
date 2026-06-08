package com.checkingcontainer.core.model

data class Announcement(
    val id: String,
    val title: String,
    val summary: String,
    val body: String,
    val authorName: String,
    val publishedAt: Long,
    val attachments: List<Attachment> = emptyList(),
)

/** Archivo adjunto a un anuncio, alojado en Firebase Storage. */
data class Attachment(
    val url: String,
    val name: String,
    val contentType: String,
    val sizeBytes: Long,
) {
    /** true si es una imagen (se muestra incrustada); si no, es archivo descargable. */
    val isImage: Boolean get() = contentType.startsWith("image/")
}
