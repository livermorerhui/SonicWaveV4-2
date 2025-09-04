package com.example.sonicwavev4

import android.annotation.SuppressLint
import android.os.Bundle
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
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.Guideline
import androidx.core.view.forEach
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import com.example.sonicwavev4.databinding.ActivityMainBinding
import com.example.sonicwavev4.ui.login.LoginDialogFragment

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private lateinit var appBarConfiguration: AppBarConfiguration

    // 拖动操作需要记录的变量
    private var lastTouchX: Float = 0f
    private var lastTouchY: Float = 0f
    private var mainInitialGuidelinePercentX: Float = 0f
    private var mainInitialGuidelinePercentY: Float = 0f
    // navRailInitialGuidelinePercentY 不再需要，因为只有一个水平拖动条

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
    }

    private fun setupCustomNavigationRail() {
        // --- 【核心改动】从主内容布局中查找导航容器 ---
        val topSectionContainer: LinearLayout? = binding.mainContentConstraintLayout?.findViewById(R.id.nav_rail_top_section)
        val bottomSectionContainer: LinearLayout? = binding.mainContentConstraintLayout?.findViewById(R.id.nav_rail_bottom_section)

        topSectionContainer?.let { topSection ->
            topSection.removeAllViews() // 防止重复添加
            val topMenu = PopupMenu(this, topSection).menu
            menuInflater.inflate(R.menu.nav_rail_top_menu, topMenu)
            topMenu.forEach { menuItem ->
                createNavRailButton(menuItem, topSection)?.let { buttonView ->
                    topSection.addView(buttonView)
                }
            }
        }

        bottomSectionContainer?.let { bottomSection ->
            bottomSection.removeAllViews() // 防止重复添加
            val bottomMenu = PopupMenu(this, bottomSection).menu
            menuInflater.inflate(R.menu.nav_rail_bottom_menu, bottomMenu)
            bottomMenu.forEach { menuItem ->
                createNavRailButton(menuItem, bottomSection)?.let { buttonView ->
                    bottomSection.addView(buttonView)
                }
            }
        }
    }

    // 保持您的优秀实现
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
            R.id.navigation_home, R.id.navigation_persetmode -> navController.navigate(item.itemId)
            R.id.navigation_music -> Toast.makeText(this, "音乐按钮被点击", Toast.LENGTH_SHORT).show()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupDragListeners() {
        // --- 【核心改动】直接从 binding 对象获取视图，不再需要 findViewById ---
        // (前提是这些视图 ID 直接位于 activity_main.xml 中)
        binding.mainContentConstraintLayout?.let { mainContentLayout ->

            // vertical_divider_guideline, vertical_drag_handle 等 ID 现在位于 main_content_constraint_layout 内部,
            // ViewBinding 可能无法直接生成它们的引用。所以在这里使用 findViewById 是正确且安全的。
            val verticalGuideline: Guideline? = mainContentLayout.findViewById(R.id.vertical_divider_guideline)
            val horizontalGuideline: Guideline? = mainContentLayout.findViewById(R.id.horizontal_divider_guideline)
            val verticalDragHandle: View? = mainContentLayout.findViewById(R.id.vertical_drag_handle)
            val horizontalDragHandle: View? = mainContentLayout.findViewById(R.id.horizontal_drag_handle)

            if (verticalGuideline != null && verticalDragHandle != null) {
                verticalDragHandle.setOnTouchListener { _, event ->
                    handleDrag(event, mainContentLayout.width.toFloat(), verticalGuideline, isHorizontal = true)
                }
            }
            if (horizontalGuideline != null && horizontalDragHandle != null) {
                horizontalDragHandle.setOnTouchListener { _, event ->
                    handleDrag(event, mainContentLayout.height.toFloat(), horizontalGuideline, isHorizontal = false)
                }
            }
        }
        // 不再需要独立的 navRailLayout 拖动逻辑
    }

    // 通用拖动处理逻辑（微调）
    private fun handleDrag(event: MotionEvent, parentDimension: Float, guideline: Guideline, isHorizontal: Boolean): Boolean {
        if (parentDimension == 0f) return true
        val params = guideline.layoutParams as ConstraintLayout.LayoutParams
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (isHorizontal) {
                    lastTouchX = event.rawX; mainInitialGuidelinePercentX = params.guidePercent
                } else {
                    lastTouchY = event.rawY; mainInitialGuidelinePercentY = params.guidePercent
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val newPercent: Float = if (isHorizontal) {
                    mainInitialGuidelinePercentX + ((event.rawX - lastTouchX) / parentDimension)
                } else {
                    mainInitialGuidelinePercentY + ((event.rawY - lastTouchY) / parentDimension)
                }
                params.guidePercent = newPercent.coerceIn(0.1f, 0.9f)
                guideline.layoutParams = params
                return true
            }
        }
        return false
    }

    // --- 其他函数 (无改动) ---
    private fun showLoginDialog() { LoginDialogFragment().show(supportFragmentManager, "LoginDialog") }
    override fun onCreateOptionsMenu(menu: Menu): Boolean { menuInflater.inflate(R.menu.toolbar_menu, menu); return true }
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_login_toolbar -> { showLoginDialog(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }
    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }
}