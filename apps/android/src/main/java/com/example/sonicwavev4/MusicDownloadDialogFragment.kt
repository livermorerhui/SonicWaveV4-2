package com.example.sonicwavev4

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.sonicwavev4.network.EndpointProvider
import com.example.sonicwavev4.utils.SessionManager
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

class MusicDownloadDialogFragment : DialogFragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var downloadButton: Button
    private lateinit var downloadableMusicAdapter: DownloadableMusicAdapter
    private val client = OkHttpClient()
    private val gson = Gson()
    private var sessionManager: SessionManager? = null
    private lateinit var appContext: Context

    interface DownloadListener {
        fun onDownloadSelected(files: List<DownloadableFile>)
    }

    var listener: DownloadListener? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_music_download, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        sessionManager = SessionManager(requireContext())
        appContext = requireContext().applicationContext

        recyclerView = view.findViewById(R.id.download_music_recyclerview)
        downloadButton = view.findViewById(R.id.download_button)

        setupRecyclerView()
        fetchMusicList()

        downloadButton.setOnClickListener {
            val selectedFiles = downloadableMusicAdapter.getSelectedFiles()
            if (selectedFiles.isNotEmpty()) {
                listener?.onDownloadSelected(selectedFiles)
                dismiss()
            } else {
                Toast.makeText(context, "请选择要下载的音乐", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupRecyclerView() {
        downloadableMusicAdapter = DownloadableMusicAdapter(mutableListOf())
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = downloadableMusicAdapter
    }

    private fun fetchMusicList() {
        val session = sessionManager
        val accessToken = session?.fetchAccessToken()
        if (session != null && !session.isOfflineTestMode() && accessToken.isNullOrBlank()) {
            Toast.makeText(context, "请先登录后再下载音乐", Toast.LENGTH_SHORT).show()
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { requestMusicList(accessToken) }
            }
            result.onSuccess { files ->
                if (files.isNotEmpty()) {
                    downloadableMusicAdapter.updateData(files)
                } else {
                    Toast.makeText(context, "暂无可下载音乐", Toast.LENGTH_SHORT).show()
                }
            }.onFailure { throwable ->
                Toast.makeText(context, throwable.message ?: "无法获取音乐列表", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun requestMusicList(accessToken: String?): List<DownloadableFile> {
        val baseUrl = EndpointProvider.baseUrl.trimEnd('/')
        val requestBuilder = Request.Builder()
            .url("$baseUrl/api/v1/music")
        if (!accessToken.isNullOrBlank()) {
            requestBuilder.addHeader("Authorization", "Bearer $accessToken")
        }
        val request = requestBuilder.build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("获取音乐列表失败: ${response.code}")
            }
            val body = response.body?.string() ?: throw IOException("服务器返回空数据")
            val musicResponse = gson.fromJson(body, MusicListResponse::class.java)
            val downloadedMusicRepo = DownloadedMusicRepository(appContext)
            val downloadedFiles = downloadedMusicRepo.loadDownloadedMusic()
                .map { it.fileName }
                .toSet()

            return musicResponse.tracks.mapNotNull { track ->
                val fileKey = track.fileKey.orEmpty()
                if (fileKey.isBlank()) return@mapNotNull null
                val fileName = fileKey.substringAfterLast('/')
                val downloadUrl = "$baseUrl/${fileKey.trimStart('/')}"
                val isDownloaded = downloadedFiles.contains(fileName)
                DownloadableFile(
                    id = track.id,
                    title = track.title.orEmpty(),
                    artist = track.artist.orEmpty(),
                    downloadUrl = downloadUrl,
                    fileName = fileName,
                    isDownloaded = isDownloaded,
                    isSelected = isDownloaded
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val displayMetrics = resources.displayMetrics
        val width = (displayMetrics.widthPixels * 0.5).toInt()
        val height = (displayMetrics.heightPixels * 0.5).toInt()
        dialog?.window?.setLayout(width, height)
    }
}

private data class MusicListResponse(
    @SerializedName("tracks") val tracks: List<MusicTrackDto> = emptyList()
)

private data class MusicTrackDto(
    @SerializedName("id") val id: Long = -1,
    @SerializedName("title") val title: String? = null,
    @SerializedName("artist") val artist: String? = null,
    @SerializedName("file_key") val fileKey: String? = null
)
