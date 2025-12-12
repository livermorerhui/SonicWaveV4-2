package com.example.sonicwavev4.repository

import android.content.Context
import com.example.sonicwavev4.data.remote.dto.CloudMusicCategoriesResponse
import com.example.sonicwavev4.data.remote.dto.CloudMusicTrackDto
import com.example.sonicwavev4.domain.model.CloudMusicCategory
import com.example.sonicwavev4.domain.model.CloudMusicTrack
import com.example.sonicwavev4.network.ApiService
import com.example.sonicwavev4.network.EndpointProvider
import com.example.sonicwavev4.network.RetrofitClient
import com.example.sonicwavev4.utils.SessionManager

interface MusicRepository {
    suspend fun getCloudCategories(): List<CloudMusicCategory>

    suspend fun getCloudTracks(categoryId: Long? = null): List<CloudMusicTrack>
}

class MusicRepositoryImpl(
    context: Context,
    private val apiService: ApiService = RetrofitClient.api,
    private val sessionManager: SessionManager = SessionManager(context.applicationContext)
) : MusicRepository {

    override suspend fun getCloudCategories(): List<CloudMusicCategory> {
        ensureAccessToken()
        val response: CloudMusicCategoriesResponse = apiService.getCloudMusicCategories()
        return response.categories.map { dto ->
            CloudMusicCategory(
                id = dto.id,
                name = dto.name
            )
        }
    }

    override suspend fun getCloudTracks(categoryId: Long?): List<CloudMusicTrack> {
        ensureAccessToken()
        val response = apiService.getCloudMusicTracks(categoryId)
        return response.tracks.mapNotNull { dto ->
            mapTrack(dto)
        }
    }

    private fun ensureAccessToken(): String {
        val token = sessionManager.fetchAccessToken()
        if (token.isNullOrBlank()) {
            throw IllegalStateException("No backend token")
        }
        RetrofitClient.updateToken(token)
        return token
    }

    private fun mapTrack(dto: CloudMusicTrackDto): CloudMusicTrack? {
        if (dto.fileKey.isBlank()) return null
        val fileUrl = buildMusicFileUrl(dto.fileKey)
        return CloudMusicTrack(
            id = dto.id,
            title = dto.title,
            artist = dto.artist,
            categoryId = dto.categoryId,
            categoryName = dto.categoryName,
            fileUrl = fileUrl,
            createdAt = dto.createdAt
        )
    }

    private fun buildMusicFileUrl(fileKey: String): String {
        val baseUrl = EndpointProvider.baseUrl.trimEnd('/')
        return "$baseUrl/${fileKey.trimStart('/')}"
    }
}
