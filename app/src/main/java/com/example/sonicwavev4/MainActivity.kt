package com.example.sonicwavev4

import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.content.res.Resources
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.Guideline
import androidx.core.net.toUri
import androidx.core.view.forEach
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.sonicwavev4.databinding.ActivityMainBinding
import com.example.sonicwavev4.ui.login.LoginDialogFragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.example.sonicwavev4.DownloadedMusicItem
import com.example.sonicwavev4.DownloadedMusicRepository
import com.example.sonicwavev4.MusicItem

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private lateinit var appBarConfiguration: AppBarConfiguration

    private var musicAreaLayout: ConstraintLayout? = null
    private var musicRecyclerView: RecyclerView? = null
    private var playButton: ImageButton? = null
    private lateinit var musicAdapter: MusicAdapter
    private var mediaPlayer: MediaPlayer? = null // MediaPlayer instance
    private var currentPlayingUri: Uri? = null // To keep track of currently playing song
    private var isPlaying: Boolean = false // Playback state
    private lateinit var musicDownloader: MusicDownloader // Declare MusicDownloader
    private lateinit var downloadedMusicRepository: DownloadedMusicRepository // Declare DownloadedMusicRepository

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                // Permission is granted. Continue the action or workflow in your app.
                Toast.makeText(this, "存储权限已授予", Toast.LENGTH_SHORT).show()
                loadMusic() // Load music after permission is granted
            } else {
                // Explain to the user that the feature is unavailable because the
                // features requires a permission that the user has denied.
                Toast.makeText(this, "存储权限被拒绝，无法加载音乐", Toast.LENGTH_LONG).show()
            }
        }

    private fun checkAndRequestPermissions() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            android.Manifest.permission.READ_MEDIA_AUDIO
        } else {
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        }

        when {
            checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED -> {
                // You can use the API that requires the permission.
                loadMusic()
            }
            shouldShowRequestPermissionRationale(permission) -> {
                // In an educational UI, explain to the user why your app requires this
                // permission for a specific feature.
                Toast.makeText(this, "需要存储权限才能读取设备上的音乐文件", Toast.LENGTH_LONG).show()
                requestPermissionLauncher.launch(permission)
            }
            else -> {
                // You can directly ask for the permission.
                requestPermissionLauncher.launch(permission)
            }
        }
    }

    // 移除了之前用于记录拖动初始状态的变量
    // private var lastTouchX: Float = 0f
    // ...

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
        navController = navHostFragment.navController
        appBarConfiguration = AppBarConfiguration(setOf(R.id.navigation_home, R.id.navigation_persetmode))
        setupActionBarWithNavController(navController, appBarConfiguration)

        setupCustomNavigationRail()
        setupDragListeners()

        // Initialize music area views and setup
        musicAreaLayout = binding.mainContentConstraintLayout?.findViewById(R.id.fragment_bottom_left)
        musicRecyclerView = musicAreaLayout?.findViewById(R.id.music_list_recyclerview)
        playButton = musicAreaLayout?.findViewById(R.id.play_button)
        setupMusicArea()

        // Initialize MusicDownloader
        musicDownloader = MusicDownloader(this)
        // Initialize DownloadedMusicRepository
        downloadedMusicRepository = DownloadedMusicRepository(this)

        // Add a download button to musicAreaLayout
        musicAreaLayout?.let { musicArea ->
            val downloadImageButton = ImageButton(this).apply {
                id = View.generateViewId()
                setImageResource(android.R.drawable.stat_sys_download) // Use a download icon
                setBackgroundResource(android.R.color.transparent) // Make background transparent
                layoutParams = ConstraintLayout.LayoutParams(
                    ConstraintLayout.LayoutParams.WRAP_CONTENT,
                    ConstraintLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topToTop = ConstraintLayout.LayoutParams.PARENT_ID
                    endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
                    setMargins(0, 16.dpToPx(), 16.dpToPx(), 0) // Top and right margin
                }
            }
            musicArea.addView(downloadImageButton)

            downloadImageButton.setOnClickListener {
                val musicUrl = "http://10.0.2.2:3000/music/sample_audio.mp3" // Replace with your actual music file name
                val fileName = "downloaded_music.txt" // Name to save the file as

                CoroutineScope(Dispatchers.Main).launch {
                    Toast.makeText(this@MainActivity, "开始下载...", Toast.LENGTH_SHORT).show()
                    val downloadedFile = musicDownloader.downloadMusic(musicUrl, fileName)
                    if (downloadedFile != null) {
                        Toast.makeText(this@MainActivity, "下载成功: ${downloadedFile.absolutePath}", Toast.LENGTH_LONG).show()
                        // Add downloaded music to repository and refresh list
                        val downloadedItem = DownloadedMusicItem(
                            fileName = downloadedFile.name,
                            title = downloadedFile.nameWithoutExtension, // Simple title for now
                            artist = "Downloaded", // Default artist for now
                            internalPath = downloadedFile.absolutePath
                        )
                        downloadedMusicRepository.addDownloadedMusic(downloadedItem)
                        loadMusic() // Reload music to update the list
                    } else {
                        Toast.makeText(this@MainActivity, "下载失败", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        checkAndRequestPermissions() // Call to check and request permissions

        
    }

    

    private fun setupMusicArea() {
        // Initialize musicAdapter with an empty list initially
        musicAdapter = MusicAdapter(emptyList()) { uri ->
            playMusic(uri)
        }
        musicRecyclerView?.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = musicAdapter
        }

        playButton?.setOnClickListener {
            togglePlayPause()
        }

        // Ensure the music area is visible
        musicAreaLayout?.visibility = View.VISIBLE
    }

    private fun Int.dpToPx(): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            this.toFloat(),
            Resources.getSystem().displayMetrics
        ).toInt()
    }

    // --- 【核心修改区域：全新的 handleDrag 函数】 ---
    private fun handleDrag(event: MotionEvent, parentView: View, guideline: Guideline, isHorizontal: Boolean): Boolean {
        // 获取父容器的尺寸，如果为0则不处理
        val parentDimension = if (isHorizontal) parentView.width.toFloat() else parentView.height.toFloat()
        if (parentDimension == 0f) return true

        val params = guideline.layoutParams as ConstraintLayout.LayoutParams
        val location = IntArray(2)
        parentView.getLocationOnScreen(location) // 获取父容器在屏幕上的起始坐标

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                // 按下时我们什么都不用做，只需返回true表示我们处理了这个事件
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val newPercent: Float
                if (isHorizontal) {
                    val parentStartX = location[0]
                    // 计算手指相对于父容器的X坐标
                    val relativeX = event.rawX - parentStartX
                    // 直接将相对坐标转换为百分比
                    newPercent = relativeX / parentDimension

                    val minWidthPx = 660.dpToPx()
                    val minWidthPercent = minWidthPx / parentDimension
                    params.guidePercent = newPercent.coerceIn(minWidthPercent, 0.9f)
                } else {
                    val parentStartY = location[1]
                    // 计算手指相对于父容器的Y坐标
                    val relativeY = event.rawY - parentStartY
                    // 直接将相对坐标转换为百分比
                    newPercent = relativeY / parentDimension

                    val minHeightPx = 315.dpToPx()
                    //限定水平把手的
                    val minHeightPercent = minHeightPx / parentDimension
                    params.guidePercent = newPercent.coerceIn(minHeightPercent, 0.9f)
                }
                guideline.layoutParams = params
                return true
            }
        }
        return false
    }
    // --- 【修改结束】 ---

    @SuppressLint("ClickableViewAccessibility")
    private fun setupDragListeners() {
        binding.mainContentConstraintLayout?.let { mainContentLayout ->
            val verticalGuideline: Guideline? = mainContentLayout.findViewById(R.id.vertical_divider_guideline)
            val horizontalGuideline: Guideline? = mainContentLayout.findViewById(R.id.horizontal_divider_guideline)
            val verticalDragHandle: View? = mainContentLayout.findViewById(R.id.vertical_drag_handle)
            val horizontalDragHandle: View? = mainContentLayout.findViewById(R.id.horizontal_drag_handle)

            if (verticalGuideline != null && verticalDragHandle != null) {
                verticalDragHandle.setOnTouchListener { _, event ->
                    // 【重要】将父容器(mainContentLayout)传递给处理函数
                    handleDrag(event, mainContentLayout, verticalGuideline, isHorizontal = true)
                }
            }
            if (horizontalGuideline != null && horizontalDragHandle != null) {
                horizontalDragHandle.setOnTouchListener { _, event ->
                    // 【重要】将父容器(mainContentLayout)传递给处理函数
                    handleDrag(event, mainContentLayout, horizontalGuideline, isHorizontal = false)
                }
            }
        }
    }

    // ... 其他函数 (setupCustomNavigationRail, createNavRailButton, 等) 保持不变 ...
    private fun setupCustomNavigationRail() {
        val topSectionContainer: LinearLayout? = binding.mainContentConstraintLayout?.findViewById(R.id.nav_rail_top_section)
        val bottomSectionContainer: LinearLayout? = binding.mainContentConstraintLayout?.findViewById(R.id.nav_rail_bottom_section)

        topSectionContainer?.let { topSection ->
            topSection.removeAllViews()
            val topMenu = PopupMenu(this, topSection).menu
            menuInflater.inflate(R.menu.nav_rail_top_menu, topMenu)
            topMenu.forEach { menuItem ->
                createNavRailButton(menuItem, topSection)?.let { buttonView ->
                    topSection.addView(buttonView)
                }
            }
        }

        bottomSectionContainer?.let { bottomSection ->
            bottomSection.removeAllViews()
            val bottomMenu = PopupMenu(this, bottomSection).menu
            menuInflater.inflate(R.menu.nav_rail_bottom_menu, bottomMenu)
            bottomMenu.forEach { menuItem ->
                createNavRailButton(menuItem, bottomSection)?.let { buttonView ->
                    bottomSection.addView(buttonView)
                }
            }
        }
    }

    private fun createNavRailButton(item: MenuItem, parent: ViewGroup): View? {
        val buttonView = LayoutInflater.from(this)
            .inflate(R.layout.custom_nav_rail_item, parent, false)

        val icon: ImageButton? = buttonView.findViewById(R.id.nav_item_icon)
        val title: TextView? = buttonView.findViewById(R.id.nav_item_title)

        return if (icon != null && title != null) {
            icon.setImageDrawable(item.icon)
            title.text = item.title
            buttonView.setOnClickListener { handleNavigation(item) }
            buttonView
        } else {
            null
        }
    }

    private fun handleNavigation(item: MenuItem) {
        when (item.itemId) {
            R.id.navigation_home, R.id.navigation_persetmode -> {
                navController.navigate(item.itemId)
                // No explicit visibility changes needed here for musicAreaLayout or navHostFragmentView
                // as musicAreaLayout is always visible and navHostFragmentView is managed by NavController
            }
            R.id.navigation_music -> {
                Toast.makeText(this, "音乐按钮被点击", Toast.LENGTH_SHORT).show()
                // If you want to navigate to a specific fragment in the top-left when music is selected,
                // you would do it here, e.g., navController.navigate(R.id.navigation_home)
                // For now, it will just show the toast and keep the current top-left fragment.
            }
        }
    }

    private fun showLoginDialog() { LoginDialogFragment().show(supportFragmentManager, "LoginDialog") }
    override fun onCreateOptionsMenu(menu: Menu): Boolean { menuInflater.inflate(R.menu.toolbar_menu, menu); return true }
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_login_toolbar -> { showLoginDialog(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun playMusic(uri: Uri) {
        if (mediaPlayer == null) {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(this@MainActivity, uri)
                prepareAsync() // Prepare asynchronously to avoid blocking UI thread
                setOnPreparedListener {
                    it.start()
                    this@MainActivity.isPlaying = true
                    playButton?.setImageResource(android.R.drawable.ic_media_pause)
                    Toast.makeText(this@MainActivity, "开始播放: ${uri.lastPathSegment}", Toast.LENGTH_SHORT).show()
                }
                setOnCompletionListener {
                    this@MainActivity.isPlaying = false
                    playButton?.setImageResource(android.R.drawable.ic_media_play)
                    Toast.makeText(this@MainActivity, "播放完成", Toast.LENGTH_SHORT).show()
                    it.release() // Release MediaPlayer when done
                    this@MainActivity.mediaPlayer = null
                    currentPlayingUri = null
                }
                setOnErrorListener { mp, what, extra ->
                    Toast.makeText(this@MainActivity, "播放错误: $what, $extra", Toast.LENGTH_LONG).show()
                    this@MainActivity.isPlaying = false
                    playButton?.setImageResource(android.R.drawable.ic_media_play)
                    mp.release()
                    this@MainActivity.mediaPlayer = null
                    currentPlayingUri = null
                    true // Indicate that the error was handled
                }
            }
        } else {
            // If a different song is selected, stop current and play new one
            if (currentPlayingUri != uri) {
                mediaPlayer?.release()
                mediaPlayer = null
                isPlaying = false
                currentPlayingUri = null
                playMusic(uri) // Play the new song
            } else {
                // If the same song is selected, just toggle play/pause
                togglePlayPause()
            }
        }
        currentPlayingUri = uri
    }

    private fun togglePlayPause() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                isPlaying = false
                playButton?.setImageResource(android.R.drawable.ic_media_play)
                Toast.makeText(this, "暂停", Toast.LENGTH_SHORT).show()
            } else {
                it.start()
                isPlaying = true
                playButton?.setImageResource(android.R.drawable.ic_media_pause)
                Toast.makeText(this, "继续播放", Toast.LENGTH_SHORT).show()
            }
        } ?: run {
            // If mediaPlayer is null, start playing the first dummy song
            val firstSongUri = musicAdapter.musicList.firstOrNull()?.uri
            firstSongUri?.let { playMusic(it) } ?: Toast.makeText(this, "没有可播放的音乐", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadMusic() {
        val musicList = mutableListOf<MusicItem>()
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(
                MediaStore.VOLUME_EXTERNAL
            )
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DATA // Path to the file (deprecated in Q, but useful for older versions)
        )

        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        contentResolver.query(
            collection,
            projection,
            null,
            null,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA) // For older Android versions

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val title = cursor.getString(titleColumn)
                val artist = cursor.getString(artistColumn)
                val contentUri: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // For Android 10 (Q) and above, use content URI
                    Uri.withAppendedPath(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id.toString())
                } else {
                    // For older versions, use file path
                    cursor.getString(dataColumn).toUri()
                }

                // Filter out non-music files (e.g., ringtones, notifications) if necessary
                // This is a basic filter, more robust filtering might be needed
                if (title != null && artist != null && !title.startsWith(".")) { // Simple filter for hidden files
                    musicList.add(MusicItem(title, artist, contentUri))
                }
            }
        }

        


        // Load downloaded music from internal storage
        val downloadedMusic = downloadedMusicRepository.loadDownloadedMusic()
        downloadedMusic.forEach { downloadedItem ->
            musicList.add(downloadedItem.toMusicItem())
        }


        if (musicList.isNotEmpty()) {
            musicAdapter.updateMusicList(musicList) // Need to add updateMusicList to adapter
            Toast.makeText(this, "已加载 ${musicList.size} 首音乐", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "未找到音乐文件", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    

    // RecyclerView Adapter
    inner class MusicAdapter(var musicList: List<MusicItem>, private val onMusicItemClick: (Uri) -> Unit) : RecyclerView.Adapter<MusicAdapter.MusicViewHolder>() {

        inner class MusicViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val titleTextView: TextView = view.findViewById(R.id.text_view_song_title)
            val artistTextView: TextView = view.findViewById(R.id.text_view_artist)

            init {
                view.setOnClickListener {
                    val position = adapterPosition
                    if (position != RecyclerView.NO_POSITION) {
                        onMusicItemClick(musicList[position].uri)
                    }
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MusicViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_music_track, parent, false)
            return MusicViewHolder(view)
        }

        override fun onBindViewHolder(holder: MusicViewHolder, position: Int) {
            val item = musicList[position]
            holder.titleTextView.text = item.title
            holder.artistTextView.text = item.artist
        }

        override fun getItemCount(): Int = musicList.size

        @SuppressLint("NotifyDataSetChanged")
        fun updateMusicList(newList: List<MusicItem>) {
            musicList = newList
            notifyDataSetChanged()
        }
    }
}