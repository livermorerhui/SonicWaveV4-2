package com.example.sonicwavev4.data.remote.dto

import com.google.gson.annotations.SerializedName

data class CloudMusicCategoryDto(
    @SerializedName("id") val id: Long,
    @SerializedName("name") val name: String,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("updated_at") val updatedAt: String? = null
)

data class CloudMusicCategoriesResponse(
    @SerializedName("message") val message: String? = null,
    @SerializedName("categories") val categories: List<CloudMusicCategoryDto> = emptyList()
)

data class CloudMusicTrackDto(
    @SerializedName("id") val id: Long,
    @SerializedName("title") val title: String,
    @SerializedName("artist") val artist: String,
    @SerializedName("file_key") val fileKey: String,
    @SerializedName("category_id") val categoryId: Long? = null,
    @SerializedName("category_name") val categoryName: String? = null,
    @SerializedName("created_at") val createdAt: String? = null
)

data class CloudMusicTracksResponse(
    @SerializedName("message") val message: String? = null,
    @SerializedName("tracks") val tracks: List<CloudMusicTrackDto> = emptyList()
)
