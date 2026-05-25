package com.checkingcontainer.core.model

data class Announcement(
    val id: String,
    val title: String,
    val summary: String,
    val body: String,
    val authorName: String,
    val publishedAt: Long,
)
