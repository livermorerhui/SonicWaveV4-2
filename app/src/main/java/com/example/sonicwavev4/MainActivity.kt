package com.example.sonicwavev4

import android.os.Bundle
import android.view.Menu
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.Guideline
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
    private var initialGuidelinePercentX: Float = 0f
    private var initialGuidelinePercentY: Float = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val toolbar: Toolbar = binding.toolbar
        setSupportActionBar(toolbar)

        // 1. 获取 NavHostFragment 实例
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as? NavHostFragment
        // 2. 从 NavHostFragment 实例中获取 NavController
        // 如果 navHostFragment 为 null，则这里会抛出异常，或者你可以在这里处理 null 情况
        navController = navHostFragment?.navController ?: run {
            // 如果 NavHostFragment 未找到或未正确初始化，则采取应对措施
            // 例如，记录错误并返回，或禁用导航功能
            // 这是一个临时的处理，确保应用不会闪退，但根本原因通常是布局或初始化顺序问题
            // 对于这种情况，navHostFragment 不应该为 null
            throw IllegalStateException("NavHostFragment not found or not initialized correctly")
        }


        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.navigation_home,
                R.id.navigation_dashboard
            )
        )

        setupActionBarWithNavController(navController, appBarConfiguration)

        // 关键：设置 NavigationRailView 的监听器，以便处理登录按钮
        binding.navRailView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.action_login_rail -> { // <-- 左侧导航栏的登录按钮
                    showLoginDialog() // 显示登录对话框
                    true // 消费事件
                }
                R.id.navigation_home, R.id.navigation_dashboard -> {
                    // 对于导航图中的目的地，让 NavController 处理
                    navController.navigate(item.itemId)
                    true // 消费事件
                }
                else -> false // 其他未处理的菜单项
            }
        }

        // ---------- 动态调整区域大小的逻辑 (加入空安全检查) ----------

        // 使用 ?.let 块来安全地处理 binding.mainContentConstraintLayout，并在此块内处理所有的拖动逻辑
        binding.mainContentConstraintLayout?.let { parentLayout ->
            val verticalGuideline: Guideline? = parentLayout.findViewById(R.id.vertical_divider_guideline)
            val horizontalGuideline: Guideline? = parentLayout.findViewById(R.id.horizontal_divider_guideline)
            val verticalDragHandle: View? = parentLayout.findViewById(R.id.vertical_drag_handle)
            val horizontalDragHandle: View? = parentLayout.findViewById(R.id.horizontal_drag_handle)

            // 垂直分割线拖动把手监听器
            verticalDragHandle?.setOnTouchListener { v, event ->
                verticalGuideline?.let { vg -> // 安全地访问 verticalGuideline
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            lastTouchX = event.rawX
                            initialGuidelinePercentX = (vg.layoutParams as ConstraintLayout.LayoutParams).guidePercent
                            true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            val dx = event.rawX - lastTouchX
                            val parentWidth = parentLayout.width.toFloat() // parentLayout 在此 let 块中是非空的
                            if (parentWidth == 0f) return@setOnTouchListener true

                            var newPercent = initialGuidelinePercentX + (dx / parentWidth)
                            newPercent = newPercent.coerceIn(0.1f, 0.9f) // 限制 Guideline 移动范围

                            val params = vg.layoutParams as ConstraintLayout.LayoutParams
                            params.guidePercent = newPercent
                            vg.layoutParams = params
                            parentLayout.requestLayout() // parentLayout 在此 let 块中是非空的
                            true
                        }
                        else -> false
                    }
                } ?: false // 如果 verticalGuideline 为 null，则不处理触摸事件
            }

            // 水平分割线拖动把手监听器
            horizontalDragHandle?.setOnTouchListener { v, event ->
                horizontalGuideline?.let { hg -> // 安全地访问 horizontalGuideline
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            lastTouchY = event.rawY
                            initialGuidelinePercentY = (hg.layoutParams as ConstraintLayout.LayoutParams).guidePercent
                            true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            val dy = event.rawY - lastTouchY
                            val parentHeight = parentLayout.height.toFloat() // parentLayout 在此 let 块中是非空的
                            if (parentHeight == 0f) return@setOnTouchListener true

                            var newPercent = initialGuidelinePercentY + (dy / parentHeight)
                            newPercent = newPercent.coerceIn(0.1f, 0.9f) // 限制 Guideline 移动范围

                            val params = hg.layoutParams as ConstraintLayout.LayoutParams
                            params.guidePercent = newPercent
                            hg.layoutParams = params
                            parentLayout.requestLayout() // parentLayout 在此 let 块中是非空的
                            true
                        }
                        else -> false
                    }
                } ?: false // 如果 horizontalGuideline 为 null，则不处理触摸事件
            }
        } // 结束 binding.mainContentConstraintLayout?.let 块
    }

    // 显示登录对话框的方法
    private fun showLoginDialog() {
        val loginDialog = LoginDialogFragment()
        loginDialog.show(supportFragmentManager, "LoginDialog") // "LoginDialog" 是一个 tag
    }

    // 重写此方法来创建Toolbar上的菜单（现在应该没有“登录”按钮了）
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.toolbar_menu, menu) // 引入顶部操作栏菜单 (现在是空的或只含其他项)
        return true
    }

    // 重写此方法来处理Toolbar菜单项的点击事件（现在应该没有“登录”按钮了）
    // override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // 移除 R.id.action_login 的处理
    //    return super.onOptionsItemSelected(item)
    //}

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }
}