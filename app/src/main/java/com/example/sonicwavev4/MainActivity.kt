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
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.sonicwavev4.databinding.ActivityMainBinding
import com.example.sonicwavev4.ui.login.LoginDialogFragment
import com.example.sonicwavev4.ui.notifications.NotificationDialogFragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity(), MusicDownloadDialogFragment.DownloadListener {
    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private lateinit var appBarConfiguration: AppBarConfiguration

    private var musicAreaLayout: ConstraintLayout? = null
    private var musicRecyclerView: RecyclerView? = null
    private var playButton: ImageButton? = null
    private lateinit var musicAdapter: MusicAdapter // 【修改点 1】Adapter类型将在后面定义为ListAdapter
    private var mediaPlayer: MediaPlayer? = null
    private var currentPlayingUri: Uri? = null
    private var isPlaying: Boolean = false
    private lateinit var musicDownloader: MusicDownloader
    private lateinit var downloadedMusicRepository: DownloadedMusicRepository

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                Toast.makeText(this, "存储权限已授予", Toast.LENGTH_SHORT).show()
                loadMusic()
            } else {
                Toast.makeText(this, "存储权限被拒绝，无法加载音乐", Toast.LENGTH_LONG).show()
            }
        }

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

        musicAreaLayout = binding.mainContentConstraintLayout?.findViewById(R.id.fragment_bottom_left)
        musicRecyclerView = musicAreaLayout?.findViewById(R.id.music_list_recyclerview)
        playButton = musicAreaLayout?.findViewById(R.id.play_button)
        setupMusicArea()

        musicDownloader = MusicDownloader(this)
        downloadedMusicRepository = DownloadedMusicRepository(this)

        musicAreaLayout?.let { musicArea ->
            val downloadImageButton = ImageButton(this).apply {
                id = View.generateViewId()
                setImageResource(android.R.drawable.stat_sys_download)
                setBackgroundResource(android.R.color.transparent)
                layoutParams = ConstraintLayout.LayoutParams(
                    ConstraintLayout.LayoutParams.WRAP_CONTENT,
                    ConstraintLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topToTop = ConstraintLayout.LayoutParams.PARENT_ID
                    endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
                    setMargins(0, 16.dpToPx(), 16.dpToPx(), 0)
                }
            }
            musicArea.addView(downloadImageButton)

            downloadImageButton.setOnClickListener {
                val dialog = MusicDownloadDialogFragment()
                dialog.listener = this
                dialog.show(supportFragmentManager, "MusicDownloadDialogFragment")
            }
        }
        checkAndRequestPermissions()
    }

    // 【修改点 2】修复了下载功能的线程问题，防止UI卡死
    override fun onDownloadSelected(files: List<String>) {
        // CoroutineScope现在用于在主线程启动，但在内部切换到IO线程执行耗时操作
        CoroutineScope(Dispatchers.Main).launch {
            Toast.makeText(this@MainActivity, "开始下载 ${files.size} 个文件...", Toast.LENGTH_SHORT).show()

            // 使用 withContext 将网络和文件操作切换到后台IO线程
            withContext(Dispatchers.IO) {
                files.forEach { fileName ->
                    val musicUrl = "${BuildConfig.SERVER_BASE_URL}music/$fileName"
                    val downloadedFile = musicDownloader.downloadMusic(musicUrl, fileName)
                    if (downloadedFile != null) {
                        val downloadedItem = DownloadedMusicItem(
                            fileName = downloadedFile.name,
                            title = downloadedFile.nameWithoutExtension,
                            artist = "Downloaded",
                            internalPath = downloadedFile.absolutePath
                        )
                        downloadedMusicRepository.addDownloadedMusic(downloadedItem)
                    } else {
                        // 在后台线程中显示Toast需要切回主线程
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@MainActivity, "下载失败: $fileName", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }

            Toast.makeText(this@MainActivity, "下载完成", Toast.LENGTH_SHORT).show()
            loadMusic() // 下载完成后，在主线程刷新列表
        }
    }

    private fun setupMusicArea() {
        // 【修改点 3】初始化新的ListAdapter
        musicAdapter = MusicAdapter { uri ->
            playMusic(uri)
        }
        musicRecyclerView?.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = musicAdapter
        }

        playButton?.setOnClickListener {
            togglePlayPause()
        }

        musicAreaLayout?.visibility = View.VISIBLE
    }

    private fun loadMusic() {
        // 使用协程在后台线程加载音乐，在主线程更新UI
        CoroutineScope(Dispatchers.IO).launch {
            val musicList = mutableListOf<MusicItem>()
            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            }

            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.DATA
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
                val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val title = cursor.getString(titleColumn)
                    val artist = cursor.getString(artistColumn)
                    val contentUri: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        Uri.withAppendedPath(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id.toString())
                    } else {
                        cursor.getString(dataColumn).toUri()
                    }

                    if (title != null && artist != null && !title.startsWith(".")) {
                        musicList.add(MusicItem(title, artist, contentUri))
                    }
                }
            }
            // 【修改点 4】修复了此函数中一个多余的右大括号语法错误

            // 加载已下载的音乐
            val downloadedMusic = downloadedMusicRepository.loadDownloadedMusic()
            downloadedMusic.forEach { downloadedItem ->
                musicList.add(downloadedItem.toMusicItem())
            }

            // 切回主线程更新UI
            withContext(Dispatchers.Main) {
                if (musicList.isNotEmpty()) {
                    // 【修改点 5】使用 submitList 更新列表，而不是 notifyDataSetChanged
                    musicAdapter.submitList(musicList)
                    Toast.makeText(this@MainActivity, "已加载 ${musicList.size} 首音乐", Toast.LENGTH_SHORT).show()
                } else {
                    musicAdapter.submitList(emptyList()) // 提交空列表以清空UI
                    Toast.makeText(this@MainActivity, "未找到音乐文件", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // 【修改点 6】将Adapter重构为 ListAdapter，并使用 DiffUtil 提高性能
    inner class MusicAdapter(private val onMusicItemClick: (Uri) -> Unit) :
        ListAdapter<MusicItem, MusicAdapter.MusicViewHolder>(MusicDiffCallback()) {

        inner class MusicViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            private val titleTextView: TextView = view.findViewById(R.id.text_view_song_title)
            private val artistTextView: TextView = view.findViewById(R.id.text_view_artist)

            init {
                view.setOnClickListener {
                    val position = adapterPosition
                    if (position != RecyclerView.NO_POSITION) {
                        // 使用 getItem(position) 来安全地获取当前项
                        onMusicItemClick(getItem(position).uri)
                    }
                }
            }

            fun bind(item: MusicItem) {
                titleTextView.text = item.title
                artistTextView.text = item.artist
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MusicViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_music_track, parent, false)
            return MusicViewHolder(view)
        }

        override fun onBindViewHolder(holder: MusicViewHolder, position: Int) {
            holder.bind(getItem(position))
        }
    }

    // DiffUtil.ItemCallback 用于计算新旧列表之间的差异，是 ListAdapter 的核心
    class MusicDiffCallback : DiffUtil.ItemCallback<MusicItem>() {
        override fun areItemsTheSame(oldItem: MusicItem, newItem: MusicItem): Boolean {
            // 通常使用唯一ID来判断是否是同一个项目
            return oldItem.uri == newItem.uri
        }

        override fun areContentsTheSame(oldItem: MusicItem, newItem: MusicItem): Boolean {
            // 如果项目是同一个，再判断内容是否发生了变化
            // 对于data class，可以直接使用 == 来比较所有字段
            return oldItem == newItem
        }
    }


    // --- 以下是未作重大修改的代码，保持了原有逻辑 ---

    private fun checkAndRequestPermissions() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            android.Manifest.permission.READ_MEDIA_AUDIO
        } else {
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        }

        when {
            checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED -> {
                loadMusic()
            }
            shouldShowRequestPermissionRationale(permission) -> {
                Toast.makeText(this, "需要存储权限才能读取设备上的音乐文件", Toast.LENGTH_LONG).show()
                requestPermissionLauncher.launch(permission)
            }
            else -> {
                requestPermissionLauncher.launch(permission)
            }
        }
    }

    private fun Int.dpToPx(): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            this.toFloat(),
            Resources.getSystem().displayMetrics
        ).toInt()
    }

    private fun handleDrag(event: MotionEvent, parentView: View, guideline: Guideline, isHorizontal: Boolean): Boolean {
        val parentDimension = if (isHorizontal) parentView.width.toFloat() else parentView.height.toFloat()
        if (parentDimension == 0f) return true

        val params = guideline.layoutParams as ConstraintLayout.LayoutParams
        val location = IntArray(2)
        parentView.getLocationOnScreen(location)

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val newPercent: Float
                if (isHorizontal) {
                    val parentStartX = location[0]
                    val relativeX = event.rawX - parentStartX
                    newPercent = relativeX / parentDimension
                    val minWidthPx = 660.dpToPx()
                    val minWidthPercent = minWidthPx / parentDimension
                    params.guidePercent = newPercent.coerceIn(minWidthPercent, 0.9f)
                } else {
                    val parentStartY = location[1]
                    val relativeY = event.rawY - parentStartY
                    newPercent = relativeY / parentDimension
                    val minHeightPx = 315.dpToPx()
                    val minHeightPercent = minHeightPx / parentDimension
                    params.guidePercent = newPercent.coerceIn(minHeightPercent, 0.9f)
                }
                guideline.layoutParams = params
                return true
            }
        }
        return false
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupDragListeners() {
        binding.mainContentConstraintLayout?.let { mainContentLayout ->
            val verticalGuideline: Guideline? = mainContentLayout.findViewById(R.id.vertical_divider_guideline)
            val horizontalGuideline: Guideline? = mainContentLayout.findViewById(R.id.horizontal_divider_guideline)
            val verticalDragHandle: View? = mainContentLayout.findViewById(R.id.vertical_drag_handle)
            val horizontalDragHandle: View? = mainContentLayout.findViewById(R.id.horizontal_drag_handle)

            if (verticalGuideline != null && verticalDragHandle != null) {
                verticalDragHandle.setOnTouchListener { _, event ->
                    handleDrag(event, mainContentLayout, verticalGuideline, isHorizontal = true)
                }
            }
            if (horizontalGuideline != null && horizontalDragHandle != null) {
                horizontalDragHandle.setOnTouchListener { _, event ->
                    handleDrag(event, mainContentLayout, horizontalGuideline, isHorizontal = false)
                }
            }
        }
    }

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
            }
            R.id.navigation_music -> {
                Toast.makeText(this, "音乐按钮被点击", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showLoginDialog() { LoginDialogFragment().show(supportFragmentManager, "LoginDialog") }
    override fun onCreateOptionsMenu(menu: Menu): Boolean { menuInflater.inflate(R.menu.toolbar_menu, menu); return true }
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_notification_toolbar -> {
                NotificationDialogFragment.newInstance().show(supportFragmentManager, NotificationDialogFragment.TAG)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun playMusic(uri: Uri) {
        // ... 播放逻辑保持不变 ...
        if (mediaPlayer == null) {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(this@MainActivity, uri)
                prepareAsync()
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
                    it.release()
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
                    true
                }
            }
        } else {
            if (currentPlayingUri != uri) {
                mediaPlayer?.release()
                mediaPlayer = null
                isPlaying = false
                currentPlayingUri = null
                playMusic(uri)
            } else {
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
            val firstSongUri = musicAdapter.currentList.firstOrNull()?.uri
            firstSongUri?.let { playMusic(it) } ?: Toast.makeText(this, "没有可播放的音乐", Toast.LENGTH_SHORT).show()
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
}
// 【修改点 7】删除了文件末尾所有多余的、导致语法错误的杂乱字符
