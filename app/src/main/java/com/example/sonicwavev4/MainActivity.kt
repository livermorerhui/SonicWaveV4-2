package com.example.sonicwavev4

import android.annotation.SuppressLint
import android.content.res.Resources
import android.os.Bundle
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
            R.id.navigation_home, R.id.navigation_persetmode -> navController.navigate(item.itemId)
            R.id.navigation_music -> Toast.makeText(this, "音乐按钮被点击", Toast.LENGTH_SHORT).show()
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
    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }
}