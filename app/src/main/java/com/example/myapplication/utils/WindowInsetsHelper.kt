package com.example.myapplication.utils

import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.drawerlayout.widget.DrawerLayout

/**
 * WindowInsets适配工具类
 * 
 * 提供统一的WindowInsets适配方法，用于处理：
 * - DrawerLayout的状态栏适配
 * - 输入布局的键盘适配
 * - 侧边栏的状态栏适配
 * 
 * 使用示例：
 * ```kotlin
 * WindowInsetsHelper.setupDrawerLayoutInsets(binding.drawerLayout)
 * WindowInsetsHelper.setupInputLayoutInsets(binding.includeBottomBar.root)
 * WindowInsetsHelper.setupSidebarInsets(binding.navDrawerLayout)
 * ```
 */
object WindowInsetsHelper {
    
    /**
     * 设置DrawerLayout的WindowInsets适配
     * 
     * 确保DrawerLayout不会被状态栏遮挡
     * 
     * @param drawerLayout DrawerLayout实例
     */
    fun setupDrawerLayoutInsets(drawerLayout: DrawerLayout) {
        ViewCompat.setOnApplyWindowInsetsListener(drawerLayout) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            insets
        }
    }
    
    /**
     * 设置输入布局的WindowInsets适配
     * 
     * 确保输入布局在键盘显示时保持可见，并正确适配系统栏
     * 
     * @param inputLayout 输入布局的根View
     */
    fun setupInputLayoutInsets(inputLayout: View) {
        // 给view设置监听器
        ViewCompat.setOnApplyWindowInsetsListener(inputLayout) { v, insets ->
            // 获取系统栏和键盘高度
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
            
            // 取键盘高度和系统栏高度的最大值
            val bottomPadding = maxOf(imeInsets.bottom, systemBars.bottom)
            
            // 设置输入框底部边距
            // layoutParams这个布局参数是可以调整margin外边距的类型
            val params = v.layoutParams as ViewGroup.MarginLayoutParams
            params.bottomMargin = bottomPadding
            v.layoutParams = params
            
            // 返回insets
            insets
        }
    }
    
    /**
     * 设置侧边栏的WindowInsets适配
     * 
     * 确保侧边栏内容不会被状态栏遮挡，并添加适当的顶部间距
     * 
     * @param sidebarLayout 侧边栏布局的根View
     */
    fun setupSidebarInsets(sidebarLayout: View) {
        ViewCompat.setOnApplyWindowInsetsListener(sidebarLayout) { v, insets ->
            // 获取系统栏的高度
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            // 添加24dp的额外顶部间距，使内容更美观
            val extraTopPadding = (24 * v.resources.displayMetrics.density).toInt()
            // 设置新的 padding
            v.setPadding(
                v.paddingLeft,
                systemBars.top + extraTopPadding,
                v.paddingRight,
                v.paddingBottom
            )
            insets
        }
    }
}
