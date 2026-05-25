package com.checkingcontainer.core.data

import com.checkingcontainer.core.domain.AnnouncementsRepository
import com.checkingcontainer.core.model.Announcement
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * In-memory store seeded with sample announcements. Swap for a Room DAO or
 * a remote API in a future PR; the [AnnouncementsRepository] contract stays.
 */
@Singleton
class AnnouncementsRepositoryImpl @Inject constructor() : AnnouncementsRepository {

    private val seedTime = System.currentTimeMillis()

    private val _items = MutableStateFlow(
        listOf(
            Announcement(
                id = "a-001",
                title = "Bienvenido a CheckingContainer",
                summary = "Una app de prueba apuntando al stack más moderno de Android.",
                body = "Esta primera versión integra Material 3, Jetpack Compose, Hilt y Room. " +
                    "Próximamente añadiremos ML Kit on-device y un modelo LLM local vía MediaPipe.",
                authorName = "Equipo CheckingContainer",
                publishedAt = seedTime - 86_400_000L,
            ),
            Announcement(
                id = "a-002",
                title = "Cero red, cero costo",
                summary = "Todo el procesamiento se hace en el dispositivo.",
                body = "El compromiso del proyecto es claro: sin facturación, sin API keys, sin " +
                    "servicios en la nube. Aprovechamos la NPU y la GPU del propio teléfono.",
                authorName = "Equipo CheckingContainer",
                publishedAt = seedTime - 3_600_000L,
            ),
            Announcement(
                id = "a-003",
                title = "Arquitectura por capas",
                summary = "Multi-módulo + Hilt + KSP + Room.",
                body = "Cada feature vive en su módulo. La capa de datos está aislada por " +
                    "interfaces; cuando migremos a la nube cambiaremos una sola línea de DI.",
                authorName = "Equipo CheckingContainer",
                publishedAt = seedTime - 600_000L,
            ),
        )
    )

    override fun observeAll() = _items.asStateFlow()
        .map { list -> list.sortedByDescending { it.publishedAt } }

    override suspend fun getById(id: String): Announcement? =
        _items.value.firstOrNull { it.id == id }

    override suspend fun publish(
        title: String,
        summary: String,
        body: String,
        authorName: String,
    ) {
        _items.update { current ->
            current + Announcement(
                id = "a-${current.size + 1}".padStart(5, '0'),
                title = title,
                summary = summary,
                body = body,
                authorName = authorName,
                publishedAt = System.currentTimeMillis(),
            )
        }
    }
}
