package com.example.sonicwavev4

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
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
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.widget.SeekBar
import android.os.Handler
import android.os.Looper
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.Guideline
import androidx.core.net.toUri
import androidx.core.view.forEach
import androidx.lifecycle.lifecycleScope
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
import com.example.sonicwavev4.ui.notifications.NotificationDialogFragment
import com.example.sonicwavev4.network.AppUsageRequest
import com.example.sonicwavev4.network.RetrofitClient
import com.example.sonicwavev4.utils.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

class MainActivity : AppCompatActivity(), MusicDownloadDialogFragment.DownloadListener {
    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private lateinit var appBarConfiguration: AppBarConfiguration

    private lateinit var sessionManager: SessionManager
    private var musicAreaLayout: ConstraintLayout? = null
    private var musicRecyclerView: RecyclerView? = null
    private var downloadButton: ImageButton? = null
    private var playPauseButton: ImageButton? = null
    private var prevButton: ImageButton? = null
    private var nextButton: ImageButton? = null
    private var vinylDiscView: ImageView? = null
    private var tonearmView: ImageView? = null
    private var playbackSeekBar: SeekBar? = null
    private var playbackTimeText: TextView? = null
    private lateinit var musicAdapter: MusicAdapter // 【修改点 1】Adapter类型将在后面定义为ListAdapter
    private var mediaPlayer: MediaPlayer? = null
    private var currentPlayingUri: Uri? = null
    private var currentTrackIndex: Int = -1
    private var pendingSelectionIndex: Int = RecyclerView.NO_POSITION
    private var isPlaying: Boolean = false
    private lateinit var musicDownloader: MusicDownloader
    private lateinit var downloadedMusicRepository: DownloadedMusicRepository
    private var vinylAnimator: ObjectAnimator? = null
    private var tonearmAnimator: ObjectAnimator? = null
    private val tonearmRestRotation = 0f   //
    private val tonearmPlayRotation = 15f   //旋转角度
    private val tonearmPivotXRatio = 0.5f  //旋转中心横坐标，百分比
    private val tonearmPivotYRatio = 0.29f  //旋转中心纵坐标，百分比
    private val playbackUpdateHandler = Handler(Looper.getMainLooper())
    private val playbackUpdateRunnable = object : Runnable {
        override fun run() {
            updatePlaybackProgress()
            playbackUpdateHandler.postDelayed(this, 1000L)
        }
    }
    private var isUserSeeking = false

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

        sessionManager = SessionManager(this)

        setSupportActionBar(binding.toolbar)

        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
        navController = navHostFragment.navController
        appBarConfiguration = AppBarConfiguration(setOf(R.id.navigation_home, R.id.navigation_persetmode))
        setupActionBarWithNavController(navController, appBarConfiguration)

        setupCustomNavigationRail()
        setupDragListeners()

        musicAreaLayout = binding.mainContentConstraintLayout?.findViewById(R.id.fragment_bottom_left)
        musicDownloader = MusicDownloader(this)
        downloadedMusicRepository = DownloadedMusicRepository(this)
        setupMusicArea()
        checkAndRequestPermissions()

        // Record app launch time
        val launchTime = System.currentTimeMillis()
        val userId = sessionManager.fetchUserId()
        if (!sessionManager.isOfflineTestMode()) {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val request = AppUsageRequest(launchTime = launchTime, userId = userId)
                    RetrofitClient.api.recordAppUsage(request)
                } catch (e: Exception) {
                    // Log error or handle it appropriately
                    e.printStackTrace()
                }
            }
        }
    }

    // 【修改点 2】修复了下载功能的线程问题，防止UI卡死
    override fun onDownloadSelected(files: List<DownloadableFile>) {
        lifecycleScope.launch {
            Toast.makeText(this@MainActivity, "开始下载 ${files.size} 个文件...", Toast.LENGTH_SHORT).show()
            val accessToken = sessionManager.fetchAccessToken()

            val failedDownloads = withContext(Dispatchers.IO) {
                files.mapNotNull { file ->
                    val downloadedFile = musicDownloader.downloadMusic(
                        file.downloadUrl,
                        file.fileName,
                        accessToken
                    )
                    if (downloadedFile != null) {
                        val downloadedItem = DownloadedMusicItem(
                            fileName = downloadedFile.name,
                            title = file.title.ifBlank { downloadedFile.nameWithoutExtension },
                            artist = file.artist.ifBlank { "Downloaded" },
                            internalPath = downloadedFile.absolutePath
                        )
                        downloadedMusicRepository.addDownloadedMusic(downloadedItem)
                        null
                    } else {
                        file.fileName
                    }
                }
            }

            if (failedDownloads.isEmpty()) {
                Toast.makeText(this@MainActivity, "下载完成", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(
                    this@MainActivity,
                    "部分音乐下载失败: ${failedDownloads.joinToString()}",
                    Toast.LENGTH_LONG
                ).show()
            }
            loadMusic() // 下载完成后，在主线程刷新列表
        }
    }

    private fun setupMusicArea() {
        // 【修改点 3】初始化新的ListAdapter
        musicAdapter = MusicAdapter { position ->
            onMusicItemSelected(position)
        }
        val area = musicAreaLayout ?: return
        musicRecyclerView = area.findViewById(R.id.music_list_recyclerview)
        downloadButton = area.findViewById(R.id.download_music_button)
        playPauseButton = area.findViewById(R.id.play_pause_button)
        prevButton = area.findViewById(R.id.prev_button)
        nextButton = area.findViewById(R.id.next_button)
        vinylDiscView = area.findViewById(R.id.vinyl_disc_view)
        tonearmView = area.findViewById(R.id.tonearm_view)
        playbackSeekBar = area.findViewById(R.id.playback_seekbar)
        playbackTimeText = area.findViewById(R.id.playback_time_text)

        tonearmView?.apply {
            rotation = tonearmRestRotation
            post {
                pivotX = width * tonearmPivotXRatio
                pivotY = height * tonearmPivotYRatio
            }
        }

        playbackSeekBar?.apply {
            isEnabled = false
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        val duration = seekBar?.max ?: 0
                        updateRemainingTime((duration - progress).coerceAtLeast(0))
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar) {
                    isUserSeeking = true
                    stopProgressUpdates()
                }

                override fun onStopTrackingTouch(seekBar: SeekBar) {
                    val target = seekBar.progress
                    mediaPlayer?.seekTo(target)
                    isUserSeeking = false
                    if (mediaPlayer?.isPlaying == true) {
                        startProgressUpdates()
                    } else {
                        updateRemainingTime((seekBar.max - target).coerceAtLeast(0))
                    }
                    updatePlaybackProgress()
                }
            })
        }
        playbackTimeText?.text = getString(R.string.remaining_time_format_placeholder)

        musicRecyclerView?.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = musicAdapter
        }

        downloadButton?.setOnClickListener {
            val dialog = MusicDownloadDialogFragment()
            dialog.listener = this
            dialog.show(supportFragmentManager, "MusicDownloadDialogFragment")
        }
        playPauseButton?.setOnClickListener { togglePlayPause() }
        prevButton?.setOnClickListener { playPreviousTrack() }
        nextButton?.setOnClickListener { playNextTrack() }

        resetPlaybackUi()
        area.visibility = View.VISIBLE
    }

    private fun onMusicItemSelected(position: Int) {
        pendingSelectionIndex = position
        musicAdapter.setSelectedPosition(position)
    }

    private fun ensureSelectionValid(totalSize: Int) {
        if (pendingSelectionIndex != RecyclerView.NO_POSITION && pendingSelectionIndex >= totalSize) {
            pendingSelectionIndex = RecyclerView.NO_POSITION
            musicAdapter.setSelectedPosition(RecyclerView.NO_POSITION)
        }
    }

    private fun loadMusic() {
        // 使用协程在后台线程加载音乐，在主线程更新UI
        lifecycleScope.launch(Dispatchers.IO) {
            SampleMusicSeeder.seedIfNeeded(applicationContext, downloadedMusicRepository)
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

            // 加载已下载的音乐并剔除失效文件
            val downloadedMusic = downloadedMusicRepository.loadDownloadedMusic()
            val validDownloads = mutableListOf<DownloadedMusicItem>()
            downloadedMusic.forEach { downloadedItem ->
                val localItem = downloadedItem.toMusicItem()
                if (localItem != null) {
                    musicList.add(localItem)
                    validDownloads.add(downloadedItem)
                }
            }
            if (validDownloads.size != downloadedMusic.size) {
                downloadedMusicRepository.saveDownloadedMusic(validDownloads)
            }

            // 切回主线程更新UI
            withContext(Dispatchers.Main) {
                if (musicList.isNotEmpty()) {
                    musicAdapter.submitList(musicList)
                    Toast.makeText(this@MainActivity, "已加载 ${musicList.size} 首音乐", Toast.LENGTH_SHORT).show()
                } else {
                    musicAdapter.submitList(emptyList())
                    Toast.makeText(this@MainActivity, "未找到音乐文件", Toast.LENGTH_SHORT).show()
                }
                ensureSelectionValid(musicList.size)
            }
        }
    }

    // 【修改点 6】将Adapter重构为 ListAdapter，并使用 DiffUtil 提高性能
    inner class MusicAdapter(private val onMusicItemSelected: (Int) -> Unit) :
        ListAdapter<MusicItem, MusicAdapter.MusicViewHolder>(MusicDiffCallback()) {

        private var selectedPosition: Int = RecyclerView.NO_POSITION

        inner class MusicViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            private val titleTextView: TextView = view.findViewById(R.id.text_view_song_title)
            private val artistTextView: TextView = view.findViewById(R.id.text_view_artist)

            init {
                view.setOnClickListener {
                    val position = adapterPosition
                    if (position != RecyclerView.NO_POSITION) {
                        onMusicItemSelected(position)
                    }
                }
            }

            fun bind(item: MusicItem) {
                titleTextView.text = item.title
                artistTextView.text = item.artist
                val isSelected = adapterPosition == selectedPosition
                itemView.setBackgroundResource(
                    if (isSelected) R.drawable.bg_music_item_selected else android.R.color.transparent
                )
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

        fun setSelectedPosition(position: Int) {
            if (selectedPosition == position) return
            val previous = selectedPosition
            selectedPosition = position
            if (previous != RecyclerView.NO_POSITION) {
                notifyItemChanged(previous)
            }
            if (position != RecyclerView.NO_POSITION) {
                notifyItemChanged(position)
            }
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
                    val maxPercent = 0.51f
                    params.guidePercent = if (minWidthPercent >= maxPercent) {
                        minWidthPercent.coerceIn(0f, 1f) // 屏幕太窄，直接固定在下限
                    } else {
                        newPercent.coerceIn(minWidthPercent, maxPercent)
                    }
                } else {
                    val parentStartY = location[1]
                    val relativeY = event.rawY - parentStartY
                    newPercent = relativeY / parentDimension
                    val minHeightPx = 315.dpToPx()
                    val minHeightPercent = minHeightPx / parentDimension
                    val maxPercent = 0.56f
                    params.guidePercent = if (minHeightPercent >= maxPercent) {
                        minHeightPercent.coerceIn(0f, 1f)
                    } else {
                        newPercent.coerceIn(minHeightPercent, maxPercent)
                    }
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

    private fun playMusic(uri: Uri, selectedIndex: Int? = null) {
        val targetIndex = selectedIndex ?: musicAdapter.currentList.indexOfFirst { it.uri == uri }
        if (targetIndex == RecyclerView.NO_POSITION) {
            Toast.makeText(this, "无法找到选定的音乐", Toast.LENGTH_SHORT).show()
            return
        }
        pendingSelectionIndex = targetIndex
        currentTrackIndex = targetIndex
        musicAdapter.setSelectedPosition(targetIndex)

        mediaPlayer?.release()
        mediaPlayer = null
        stopPlaybackAnimation()
        resetPlaybackUi()

        mediaPlayer = MediaPlayer().apply {
            setDataSource(this@MainActivity, uri)
            setOnPreparedListener { preparedPlayer ->
                playbackSeekBar?.apply {
                    isEnabled = true
                    max = preparedPlayer.duration
                    progress = preparedPlayer.currentPosition
                }
                updateRemainingTime((preparedPlayer.duration - preparedPlayer.currentPosition).coerceAtLeast(0))
                preparedPlayer.start()
                this@MainActivity.isPlaying = true
                playPauseButton?.setImageResource(android.R.drawable.ic_media_pause)
                startPlaybackAnimation()
                Toast.makeText(this@MainActivity, "开始播放: ${uri.lastPathSegment}", Toast.LENGTH_SHORT).show()
            }
            setOnCompletionListener { completedPlayer ->
                this@MainActivity.isPlaying = false
                playPauseButton?.setImageResource(android.R.drawable.ic_media_play)
                stopPlaybackAnimation()
                resetPlaybackUi()
                Toast.makeText(this@MainActivity, "播放完成", Toast.LENGTH_SHORT).show()
                completedPlayer.release()
                mediaPlayer = null
                currentPlayingUri = null
            }
            setOnErrorListener { mp, what, extra ->
                Toast.makeText(this@MainActivity, "播放错误: $what, $extra", Toast.LENGTH_LONG).show()
                this@MainActivity.isPlaying = false
                playPauseButton?.setImageResource(android.R.drawable.ic_media_play)
                stopPlaybackAnimation()
                resetPlaybackUi()
                mp.release()
                mediaPlayer = null
                currentPlayingUri = null
                true
            }
            prepareAsync()
        }
        currentPlayingUri = uri
    }

    private fun togglePlayPause() {
        mediaPlayer?.let { player ->
            if (player.isPlaying) {
                player.pause()
                isPlaying = false
                playPauseButton?.setImageResource(android.R.drawable.ic_media_play)
                stopPlaybackAnimation()
                Toast.makeText(this, "暂停", Toast.LENGTH_SHORT).show()
            } else {
                val selectedDifferent = pendingSelectionIndex != RecyclerView.NO_POSITION &&
                        pendingSelectionIndex != currentTrackIndex
                if (selectedDifferent) {
                    val item = musicAdapter.currentList.getOrNull(pendingSelectionIndex)
                    if (item != null) {
                        player.release()
                        mediaPlayer = null
                        playMusic(item.uri, pendingSelectionIndex)
                        return
                    }
                }
                player.start()
                isPlaying = true
                playPauseButton?.setImageResource(android.R.drawable.ic_media_pause)
                startPlaybackAnimation()
                Toast.makeText(this, "继续播放", Toast.LENGTH_SHORT).show()
            }
        } ?: run {
            val targetIndex = pendingSelectionIndex.takeIf { it != RecyclerView.NO_POSITION }
                ?: currentTrackIndex.takeIf { it != RecyclerView.NO_POSITION }
            val targetItem = targetIndex?.let { musicAdapter.currentList.getOrNull(it) }
            if (targetItem != null) {
                playMusic(targetItem.uri, targetIndex)
            } else {
                Toast.makeText(this, "请选择音乐", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun playNextTrack() {
        val list = musicAdapter.currentList
        if (list.isEmpty()) {
            Toast.makeText(this, "没有可播放的音乐", Toast.LENGTH_SHORT).show()
            return
        }
        val nextIndex = if (currentTrackIndex == -1) 0 else (currentTrackIndex + 1) % list.size
        val nextItem = list[nextIndex]
        pendingSelectionIndex = nextIndex
        musicAdapter.setSelectedPosition(nextIndex)
        playMusic(nextItem.uri, nextIndex)
    }

    private fun playPreviousTrack() {
        val list = musicAdapter.currentList
        if (list.isEmpty()) {
            Toast.makeText(this, "没有可播放的音乐", Toast.LENGTH_SHORT).show()
            return
        }
        val prevIndex = if (currentTrackIndex <= 0) list.lastIndex else currentTrackIndex - 1
        val prevItem = list[prevIndex]
        pendingSelectionIndex = prevIndex
        musicAdapter.setSelectedPosition(prevIndex)
        playMusic(prevItem.uri, prevIndex)
    }

    private fun startPlaybackAnimation() {
        if (tonearmView == null) {
            spinVinyl()
            startProgressUpdates()
        } else {
            animateTonearm(true) {
                spinVinyl()
                startProgressUpdates()
            }
        }
    }

    private fun stopPlaybackAnimation() {
        stopProgressUpdates()
        pauseVinyl()
        animateTonearm(false)
    }

    private fun spinVinyl() {
        val disc = vinylDiscView ?: return
        val animator = vinylAnimator ?: ObjectAnimator.ofFloat(disc, View.ROTATION, 0f, 360f).apply {
            duration = 12000L    //唱臂旋转速度，这里是时间
            interpolator = LinearInterpolator()
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
        }.also { vinylAnimator = it }
        if (!animator.isStarted) {
            animator.start()
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            animator.resume()
        }
    }

    private fun pauseVinyl() {
        vinylAnimator?.let { animator ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                animator.pause()
            } else {
                animator.cancel()
                vinylAnimator = null
            }
        }
    }

    private fun animateTonearm(engage: Boolean, onEnd: (() -> Unit)? = null) {
        val tonearm = tonearmView ?: run {
            onEnd?.invoke()
            return
        }
        tonearmAnimator?.cancel()
        val targetRotation = if (engage) tonearmPlayRotation else tonearmRestRotation
        var wasCancelled = false
        tonearmAnimator = ObjectAnimator.ofFloat(tonearm, View.ROTATION, tonearm.rotation, targetRotation).apply {
            duration = if (engage) 420L else 360L
            interpolator = DecelerateInterpolator()
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationCancel(animation: Animator) {
                    wasCancelled = true
                }

                override fun onAnimationEnd(animation: Animator) {
                    if (!wasCancelled) {
                        onEnd?.invoke()
                    }
                }
            })
            start()
        }
    }

    private fun startProgressUpdates() {
        if (isUserSeeking) return
        stopProgressUpdates()
        playbackUpdateHandler.post(playbackUpdateRunnable)
    }

    private fun stopProgressUpdates() {
        playbackUpdateHandler.removeCallbacks(playbackUpdateRunnable)
    }

    private fun updatePlaybackProgress() {
        val player = mediaPlayer ?: return
        if (isUserSeeking) return
        val duration = player.duration
        val position = player.currentPosition
        playbackSeekBar?.apply {
            if (max != duration && duration > 0) {
                max = duration
            }
            progress = position
        }
        updateRemainingTime((duration - position).coerceAtLeast(0))
    }

    private fun updateRemainingTime(millis: Int) {
        playbackTimeText?.text = getString(
            R.string.remaining_time_format,
            formatTime(millis.coerceAtLeast(0))
        )
    }

    private fun resetPlaybackUi() {
        stopProgressUpdates()
        playbackSeekBar?.apply {
            progress = 0
            max = 0
            isEnabled = false
        }
        playbackTimeText?.text = getString(R.string.remaining_time_format_placeholder)
    }

    private fun formatTime(millis: Int): String {
        val totalSeconds = millis / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        mediaPlayer = null
        vinylAnimator?.cancel()
        vinylAnimator = null
        tonearmAnimator?.cancel()
        tonearmAnimator = null
        stopProgressUpdates()
        resetPlaybackUi()
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }
}
