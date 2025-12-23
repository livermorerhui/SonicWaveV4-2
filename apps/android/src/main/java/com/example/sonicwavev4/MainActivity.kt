package com.example.sonicwavev4

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Rect
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.content.res.Resources
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.forEach
import androidx.core.widget.ImageViewCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import android.content.Intent
import com.example.sonicwavev4.databinding.ActivityMainBinding
import com.example.sonicwavev4.network.AppUsageRequest
import com.example.sonicwavev4.network.RetrofitClient
import com.example.sonicwavev4.ui.customer.CustomerViewModel
import com.example.sonicwavev4.ui.notifications.NotificationDialogFragment
import com.example.sonicwavev4.ui.music.MusicDialogFragment
import com.example.sonicwavev4.ui.music.MusicPlayerViewModel
import com.example.sonicwavev4.utils.DeviceIdentityProvider
import com.example.sonicwavev4.utils.GlobalLogoutManager
import com.example.sonicwavev4.utils.OfflineForceExitManager
import com.example.sonicwavev4.utils.SessionManager
import com.example.sonicwavev4.utils.TestToneSettings
import com.example.sonicwavev4.MusicDownloadEvent
import com.example.sonicwavev4.MusicDownloadEventBus
import com.example.sonicwavev4.core.account.AuthIntent
import com.example.sonicwavev4.ui.login.LoginViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

interface SoftReduceTouchHost {
    fun setSoftReduceTouchListener(listener: ((MotionEvent) -> Boolean)?)
}

class MainActivity : AppCompatActivity(), MusicDownloadDialogFragment.DownloadListener, SoftReduceTouchHost {
    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private lateinit var appBarConfiguration: AppBarConfiguration
    private val isTablet by lazy { resources.getBoolean(R.bool.is_tablet) }
    private val loginViewModel: LoginViewModel by viewModels()

    private val customerViewModel: CustomerViewModel by viewModels()
    private val musicViewModel: MusicPlayerViewModel by viewModels()

    private lateinit var sessionManager: SessionManager
    private var musicAreaLayout: ConstraintLayout? = null
    private var musicRecyclerView: RecyclerView? = null
    private var downloadButton: ImageButton? = null
    private var playPauseButton: ImageButton? = null
    private var prevButton: ImageButton? = null
    private var nextButton: ImageButton? = null
    private var vinylDiscView: ImageView? = null
    private var tonearmView: ImageView? = null
    private var playingAnimView: ImageView? = null
    private var playbackSeekBar: SeekBar? = null
    private var playbackTimeText: TextView? = null
    private var playbackTitleText: TextView? = null
    private lateinit var musicAdapter: MusicAdapter // 【修改点 1】Adapter类型将在后面定义为ListAdapter
    private lateinit var musicDownloader: MusicDownloader
    private lateinit var downloadedMusicRepository: DownloadedMusicRepository
    private var vinylAnimator: ObjectAnimator? = null
    private var tonearmAnimator: ObjectAnimator? = null
    private val tonearmRestRotation = 0f   //
    private val tonearmPlayRotation = 15f   //旋转角度
    private val tonearmPivotXRatio = 0.5f  //旋转中心横坐标，百分比
    private val tonearmPivotYRatio = 0.29f  //旋转中心纵坐标，百分比
    private var isMusicUiEnabled: Boolean = false
    private var isUserSeeking = false
    private var lastRenderedPlaying: Boolean? = null
    private var lastRenderedTitle: String? = null
    private var forceExitDialog: AlertDialog? = null
    private val navButtonViews = mutableMapOf<Int, Pair<ImageButton, TextView>>()
    private var customPresetItemView: View? = null
    private var currentNavItemId: Int = R.id.navigation_home
    private var softReduceTouchListener: ((MotionEvent) -> Boolean)? = null

    private fun hasRightPanel(): Boolean = findViewById<View?>(R.id.fragment_right_main) != null
    private fun isPhone(): Boolean = !hasRightPanel()

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
        installSplashScreen()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sessionManager = SessionManager(this)

        if (isPhone()) {
            TestToneSettings.setSineToneEnabled(true)
            loginViewModel.handleIntent(AuthIntent.EnterOfflineModeSilently)
        }

        setSupportActionBar(binding.toolbar)
        if (!isTablet) {
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            binding.root.setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    val focused = currentFocus
                    if (focused is EditText) {
                        val outRect = Rect()
                        focused.getGlobalVisibleRect(outRect)
                        if (!outRect.contains(event.rawX.toInt(), event.rawY.toInt())) {
                            focused.clearFocus()
                            hideKeyboard(focused)
                        }
                    }
                }
                false
            }
        }
        // Use custom toolbar view instead of NavigationUI-provided titles
        supportActionBar?.setDisplayShowTitleEnabled(false)
        binding.toolbarTitle?.text = ""

        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
        navController = navHostFragment.navController
        appBarConfiguration = AppBarConfiguration(setOf(R.id.navigation_home, R.id.navigation_persetmode, R.id.navigation_custom_preset, R.id.navigation_me))
        setupActionBarWithNavController(navController, appBarConfiguration)
        navController.addOnDestinationChangedListener { _, destination, _ ->
            when (destination.id) {
                R.id.navigation_home,
                R.id.navigation_persetmode,
                R.id.navigation_custom_preset -> updateNavRailSelection(destination.id)
            }
        }

        binding.bottomNav?.let { bottomNav ->
            bottomNav.selectedItemId = R.id.navigation_home
            bottomNav.setOnItemSelectedListener { item ->
                when (item.itemId) {
                    R.id.navigation_home -> {
                        val popped = navController.popBackStack(R.id.navigation_home, false)
                        if (!popped && navController.currentDestination?.id != R.id.navigation_home) {
                            navController.navigate(R.id.navigation_home)
                        }
                        true
                    }
                    R.id.menu_me -> {
                        if (navController.currentDestination?.id != R.id.navigation_me) {
                            navController.navigate(R.id.navigation_me)
                        }
                        true
                    }
                    else -> false
                }
            }
        }

        setupCustomNavigationRail()
        observeSelectedCustomerContext()

        val musicArea = binding.root.findViewById<View?>(R.id.fragment_bottom_left)
        isMusicUiEnabled = musicArea != null
        if (isMusicUiEnabled) {
            musicAreaLayout = musicArea as? ConstraintLayout
            musicDownloader = MusicDownloader(this)
            downloadedMusicRepository = DownloadedMusicRepository(this)
            setupMusicArea()
            observeMusicPlayer()
            checkAndRequestPermissions()
        }

        // Record app launch time
        val launchTime = System.currentTimeMillis()
        val userId = sessionManager.fetchUserId()
        if (!sessionManager.isOfflineTestMode()) {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val deviceProfile = DeviceIdentityProvider.buildProfile()
                    val request = AppUsageRequest(
                        launchTime = launchTime,
                        userId = userId,
                        deviceId = deviceProfile.deviceId,
                        ipAddress = deviceProfile.localIpAddress,
                        deviceModel = deviceProfile.deviceModel,
                        osVersion = deviceProfile.osVersion,
                        appVersion = deviceProfile.appVersion
                    )
                    RetrofitClient.api.recordAppUsage(request)
                } catch (e: Exception) {
                    // Log error or handle it appropriately
                    e.printStackTrace()
                }
            }
        }

        observeForceExitCountdown()
        observeGlobalLogout()
    }

    override fun onResume() {
        super.onResume()
        if (isMusicUiEnabled && musicViewModel.isPlaying.value) {
            spinVinyl()
        }
    }

    override fun onPause() {
        super.onPause()
        if (isMusicUiEnabled) {
            pauseVinyl()
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (isTablet) {
            softReduceTouchListener?.invoke(ev)
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun setSoftReduceTouchListener(listener: ((MotionEvent) -> Boolean)?) {
        if (!isTablet) {
            softReduceTouchListener = null
            return
        }
        softReduceTouchListener = listener
    }

    private fun hideKeyboard(target: View) {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(target.windowToken, 0)
    }

    // 【修改点 2】修复了下载功能的线程问题，防止UI卡死
    override fun onDownloadSelected(files: List<DownloadableFile>) {
        lifecycleScope.launch {
            Toast.makeText(this@MainActivity, "开始下载 ${files.size} 个文件...", Toast.LENGTH_SHORT).show()
            val accessToken = sessionManager.fetchAccessToken()

            var lastDownloadError: MusicDownloadError? = null
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
                            internalPath = downloadedFile.absolutePath,
                            cloudTrackId = file.id
                        )
                        downloadedMusicRepository.addDownloadedMusic(downloadedItem)
                        MusicDownloadEventBus.emit(MusicDownloadEvent.Success(file.downloadUrl))
                        null
                    } else {
                        lastDownloadError = musicDownloader.lastError
                        file.fileName
                    }
                }
            }

            if (failedDownloads.isEmpty()) {
                Toast.makeText(
                    this@MainActivity,
                    "音乐下载完成，共 ${files.size} 首。",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                val baseMessage = when (val error = lastDownloadError) {
                    is MusicDownloadError.Http -> {
                        if (error.code == 401 || error.code == 403) {
                            "下载失败：登录已过期或权限不足，请重新登录后重试。"
                        } else {
                            "下载失败：服务器返回错误（HTTP ${error.code}）。"
                        }
                    }
                    is MusicDownloadError.Network -> "下载失败：网络异常，请检查网络连接。"
                    is MusicDownloadError.Io -> "下载失败：存储或服务器错误，请稍后再试。"
                    else -> "部分音乐下载失败。"
                }

                val failedNames = failedDownloads.joinToString(separator = ", ")
                val message = if (files.size > failedDownloads.size) {
                    "$baseMessage 已成功下载 ${files.size - failedDownloads.size} 首；失败：$failedNames"
                } else {
                    "$baseMessage 失败曲目：$failedNames"
                }

                Toast.makeText(
                    this@MainActivity,
                    message,
                    Toast.LENGTH_LONG
                ).show()
            }
            loadMusic() // 下载完成后，在主线程刷新列表
        }
    }

    private fun observeForceExitCountdown() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                OfflineForceExitManager.countdownSeconds.collect { seconds ->
                    if (seconds == null) {
                        dismissForceExitDialog()
                    } else {
                        showForceExitDialog(seconds)
                    }
                }
            }
        }
    }

    private fun showForceExitDialog(seconds: Int) {
        val message = getString(R.string.force_exit_dialog_message, seconds)
        if (forceExitDialog == null) {
            forceExitDialog = AlertDialog.Builder(this)
                .setTitle(R.string.force_exit_dialog_title)
                .setMessage(message)
                .setCancelable(false)
                .create()
        } else {
            forceExitDialog?.setMessage(message)
        }
        forceExitDialog?.show()
    }

    private fun dismissForceExitDialog() {
        forceExitDialog?.dismiss()
        forceExitDialog = null
    }

    private fun observeGlobalLogout() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                GlobalLogoutManager.logoutEvent.collect {
                    showLoginFragment()
                }
            }
        }
    }

    private fun observeMusicPlayer() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    musicViewModel.miniPlayerUiState.collect { state ->
                        renderMiniMusicBar(state)
                    }
                }
                launch {
                    musicViewModel.currentIndex.collect { index ->
                        musicAdapter.setSelectedPosition(if (index != -1) index else RecyclerView.NO_POSITION)
                    }
                }
            }
        }
    }

    private fun renderMiniMusicBar(state: com.example.sonicwavev4.ui.music.MiniPlayerUiState) {
        if (!state.hasPlaylist) {
            resetPlaybackUi()
            playPauseButton?.setImageResource(R.drawable.ic_play_24)
            playbackTitleText?.text = getString(R.string.now_playing_placeholder)
            lastRenderedPlaying = false
            lastRenderedTitle = ""
            return
        }

        val title = state.title
        val displayTitle = if (title.isNotBlank()) title else getString(R.string.now_playing_placeholder)
        if (lastRenderedTitle != displayTitle) {
            playbackTitleText?.text = displayTitle
            lastRenderedTitle = displayTitle
        }

        if (lastRenderedPlaying != state.isPlaying) {
            playPauseButton?.setImageResource(
                if (state.isPlaying) R.drawable.ic_pause_24 else R.drawable.ic_play_24
            )
            if (state.isPlaying) {
                spinVinyl()
                playingAnimView?.alpha = 1f
            } else {
                pauseVinyl()
                playingAnimView?.alpha = 0.5f
            }
            lastRenderedPlaying = state.isPlaying
        }

        if (!isUserSeeking) {
            playbackSeekBar?.max = state.durationMs
            playbackSeekBar?.isEnabled = state.durationMs > 0
            playbackSeekBar?.progress = state.positionMs
        }

        if (state.durationMs > 0) {
            updateRemainingTime(state.remainingMs)
        } else {
            playbackTimeText?.text = getString(R.string.remaining_time_format_placeholder)
        }
    }

    private fun showLoginFragment() {
        val container = findViewById<View?>(R.id.fragment_right_main)
        if (container == null) {
            if (this::navController.isInitialized) {
                navController.navigate(R.id.navigation_me)
            }
            return
        }
        val fragmentManager = supportFragmentManager
        val current = fragmentManager.findFragmentById(container.id)
        if (current is com.example.sonicwavev4.ui.login.LoginFragment) return
        fragmentManager.beginTransaction()
            .replace(container.id, com.example.sonicwavev4.ui.login.LoginFragment())
            .commitAllowingStateLoss()
    }

    private fun setupMusicArea() {
        // 【修改点 3】初始化新的ListAdapter
        musicAdapter = MusicAdapter { position ->
            onMusicItemSelected(position)
        }
        val area = musicAreaLayout ?: return
        musicRecyclerView = area.findViewById(R.id.music_list_recyclerview)
        downloadButton = area.findViewById(R.id.download_music_button)
        playPauseButton = area.findViewById(R.id.btnPlayPause)
        prevButton = area.findViewById(R.id.btnPrev)
        nextButton = area.findViewById(R.id.btnNext)
        vinylDiscView = area.findViewById(R.id.vinyl_disc_view)
        tonearmView = area.findViewById(R.id.tonearm_view)
        playingAnimView = area.findViewById(R.id.imgPlayingAnim)
        playbackSeekBar = area.findViewById(R.id.seekBarMusic)
        playbackTimeText = area.findViewById(R.id.tvRemainingTime)
        playbackTitleText = area.findViewById(R.id.tvNowPlayingTitle)
        playbackTitleText?.text = getString(R.string.now_playing_placeholder)

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
                }

                override fun onStopTrackingTouch(seekBar: SeekBar) {
                    val target = seekBar.progress
                    musicViewModel.seekTo(target)
                    isUserSeeking = false
                    updateRemainingTime((seekBar.max - target).coerceAtLeast(0))
                }
            })
        }
        playbackTimeText?.text = getString(R.string.remaining_time_format_placeholder)

        musicRecyclerView?.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = musicAdapter
        }

        downloadButton?.setOnClickListener {
            showMusicDownloadDialog()
        }
        playPauseButton?.setOnClickListener { musicViewModel.togglePlayPause() }
        prevButton?.setOnClickListener { musicViewModel.playPrevious() }
        nextButton?.setOnClickListener { musicViewModel.playNext() }

        resetPlaybackUi()
        area.visibility = View.VISIBLE
    }

    private fun onMusicItemSelected(position: Int) {
        musicAdapter.setSelectedPosition(position)
        musicViewModel.playAt(position)
    }

    fun showMusicDownloadDialog() {
        val dialog = MusicDownloadDialogFragment()
        dialog.listener = this
        dialog.show(supportFragmentManager, "MusicDownloadDialogFragment")
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
                    musicViewModel.setPlaylist(musicList)
                    Toast.makeText(this@MainActivity, "已加载 ${musicList.size} 首音乐", Toast.LENGTH_SHORT).show()
                } else {
                    musicAdapter.submitList(emptyList())
                    musicViewModel.setPlaylist(emptyList())
                    Toast.makeText(this@MainActivity, "未找到音乐文件", Toast.LENGTH_SHORT).show()
                }
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
                    val position = bindingAdapterPosition
                    if (position != RecyclerView.NO_POSITION) {
                        onMusicItemSelected(position)
                    }
                }
            }

            fun bind(item: MusicItem, isSelected: Boolean) {
                titleTextView.text = item.title
                artistTextView.text = item.artist
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
            holder.bind(getItem(position), position == selectedPosition)
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

    /**
     * Initializes the left navigation menu.
     *
     * Stage 0:
     *  - Keep behavior as-is, including always showing the "custom preset" entry.
     *
     * Future:
     *  - Control "custom preset" visibility based on selectedCustomer
     *    (only show it when in a customer detail context).
     */
    private fun setupCustomNavigationRail() {
        navButtonViews.clear()
        val topSectionContainer: LinearLayout? = binding.mainContentConstraintLayout?.findViewById(R.id.nav_rail_top_section)
        val bottomSectionContainer: LinearLayout? = binding.mainContentConstraintLayout?.findViewById(R.id.nav_rail_bottom_section)

        topSectionContainer?.let { topSection ->
            topSection.removeAllViews()
            val topMenu = PopupMenu(this, topSection).menu
            menuInflater.inflate(R.menu.nav_rail_top_menu, topMenu)
            topMenu.forEach { menuItem ->
                createNavRailButton(menuItem, topSection)?.let { buttonView ->
                    if (menuItem.itemId == R.id.navigation_custom_preset) {
                        customPresetItemView = buttonView
                    }
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
        updateNavRailSelection(currentNavItemId)
        applyCustomPresetVisibility(customerViewModel.selectedCustomer.value)
    }

    private fun createNavRailButton(item: MenuItem, parent: ViewGroup): View? {
        val buttonView = LayoutInflater.from(this)
            .inflate(R.layout.custom_nav_rail_item, parent, false)

        val icon: ImageButton? = buttonView.findViewById(R.id.nav_item_icon)
        val title: TextView? = buttonView.findViewById(R.id.nav_item_title)

        if (icon == null || title == null) return null

        icon.setImageDrawable(item.icon)
        title.text = item.title

        val isMusicItem = item.itemId == R.id.navigation_music
        val root = buttonView.findViewById<View>(R.id.nav_item_root)
        if (isMusicItem) {
            root.isClickable = true
            root.isFocusable = true
            root.setOnClickListener { showMusicDialog() }
            val accentColor = ContextCompat.getColor(this, R.color.nav_music)
            icon.imageTintList = ColorStateList.valueOf(accentColor)
            title.setTextColor(accentColor)
        } else {
            root.isClickable = true
            root.isFocusable = true
            navButtonViews[item.itemId] = icon to title
            val isSelected = item.itemId == currentNavItemId
            applyNavButtonColors(icon, title, isSelected)
            root.setOnClickListener {
                handleNavigation(item)
                updateNavRailSelection(item.itemId)
            }
        }
        return buttonView
    }

    private fun handleNavigation(item: MenuItem) {
        when (item.itemId) {
            R.id.navigation_home, R.id.navigation_persetmode, R.id.navigation_custom_preset -> {
                navController.navigate(item.itemId)
            }
            R.id.navigation_music -> showMusicDialog()
        }
    }

    private fun updateNavRailSelection(activeItemId: Int) {
        currentNavItemId = activeItemId
        navButtonViews.forEach { (id, pair) ->
            val (icon, title) = pair
            val isSelected = id == activeItemId
            applyNavButtonColors(icon, title, isSelected)
        }
    }

    private fun applyNavButtonColors(icon: ImageButton, title: TextView, isSelected: Boolean) {
        val colorRes = if (isSelected) R.color.nav_item_active else R.color.nav_item_inactive
        val color = ContextCompat.getColor(this, colorRes)
        ImageViewCompat.setImageTintList(icon, ColorStateList.valueOf(color))
        title.setTextColor(color)
    }

    private fun observeSelectedCustomerContext() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                customerViewModel.selectedCustomer.collect { customer ->
                    applyCustomPresetVisibility(customer)
                    val currentDest = navController.currentDestination?.id
                    if (customer == null && currentDest == R.id.navigation_custom_preset) {
                        navController.navigate(R.id.navigation_persetmode)
                        updateNavRailSelection(R.id.navigation_persetmode)
                    }
                }
            }
        }
    }

    private fun applyCustomPresetVisibility(customer: Any?) {
        val hasCustomer = customer != null
        customPresetItemView?.visibility = if (hasCustomer) View.VISIBLE else View.GONE
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
        } else
            animator.resume()
    }

    private fun pauseVinyl() {
        vinylAnimator?.pause()
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

    private fun updateRemainingTime(millis: Int) {
        playbackTimeText?.text = getString(
            R.string.remaining_time_format,
            formatTime(millis.coerceAtLeast(0))
        )
    }

    private fun resetPlaybackUi() {
        playbackSeekBar?.apply {
            progress = 0
            max = 0
            isEnabled = false
        }
        playbackTimeText?.text = getString(R.string.remaining_time_format_placeholder)
        playbackTitleText?.text = getString(R.string.now_playing_placeholder)
        lastRenderedTitle = getString(R.string.now_playing_placeholder)
        playingAnimView?.alpha = 0.5f
    }

    private fun formatTime(millis: Int): String {
        val totalSeconds = millis / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }

    override fun onDestroy() {
        super.onDestroy()
        dismissForceExitDialog()
        vinylAnimator?.cancel()
        vinylAnimator = null
        tonearmAnimator?.cancel()
        tonearmAnimator = null
        resetPlaybackUi()
        musicAreaLayout = null
        musicRecyclerView = null
        downloadButton = null
        playPauseButton = null
        prevButton = null
        nextButton = null
        vinylDiscView = null
        tonearmView = null
        playbackSeekBar = null
        playbackTimeText = null
        playbackTitleText = null
        lastRenderedPlaying = null
        lastRenderedTitle = null
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    private fun showMusicDialog() {
        val dialog = MusicDialogFragment()
        dialog.show(supportFragmentManager, "MusicDialog")
    }
}
