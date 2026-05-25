package com.testo3.core.model

/**
 * Domain model — pure Kotlin, no Room/Android types. The data layer maps
 * its DB entities to/from this so the UI never touches persistence concerns.
 */
data class Task(
    val id: Long,
    val title: String,
    val isDone: Boolean,
    val createdAt: Long,
)
