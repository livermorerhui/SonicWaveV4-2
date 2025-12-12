package com.example.sonicwavev4.network

import com.google.gson.annotations.SerializedName

data class MusicCategoryResponse(
    @SerializedName("categories")
    val categories: List<MusicCategoryDto> = emptyList()
)

data class MusicCategoryDto(
    @SerializedName("id") val id: Long,
    @SerializedName("code") val code: String,
    @SerializedName("name") val name: String,
    @SerializedName("sort_order") val sortOrder: Int? = null
)
