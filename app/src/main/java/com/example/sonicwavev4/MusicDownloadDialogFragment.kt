package com.example.sonicwavev4

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
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

    interface DownloadListener {
        fun onDownloadSelected(files: List<String>)
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
        val request = Request.Builder().url("http://192.168.31.217:3000/music/list").build()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    if (responseBody != null) {
                        val type = object : TypeToken<List<String>>() {}.type
                        val musicFiles: List<String> = gson.fromJson(responseBody, type)
                        
                        val downloadedMusicRepo = DownloadedMusicRepository(requireContext())
                        val downloadedFiles = downloadedMusicRepo.loadDownloadedMusic().map { it.fileName }

                        val downloadableFiles = musicFiles.map { fileName ->
                            val isDownloaded = downloadedFiles.contains(fileName)
                            DownloadableFile(fileName, isDownloaded, isDownloaded)
                        }

                        withContext(Dispatchers.Main) {
                            downloadableMusicAdapter = DownloadableMusicAdapter(downloadableFiles.toMutableList())
                            recyclerView.adapter = downloadableMusicAdapter
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "无法获取音乐列表", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: IOException) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "网络错误: ${e.message}", Toast.LENGTH_SHORT).show()
                }
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