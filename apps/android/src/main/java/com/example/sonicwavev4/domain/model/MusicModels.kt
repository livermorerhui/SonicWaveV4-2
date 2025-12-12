package com.example.sonicwavev4.domain.model

data class CloudMusicCategory(
    val id: Long,
    val name: String
)

data class CloudMusicTrack(
    val id: Long,
    val title: String,
    val artist: String,
    val categoryId: Long?,
    val categoryName: String?,
    val fileUrl: String,
    val createdAt: String?
)
