package com.example.myapplication.ui.base

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.example.myapplication.ui.common.navigation.AppNavigator
import com.example.myapplication.ui.history.view.HistoryFragment

/**
 * 简化的基础 Activity
 * 
 * 职责：
 * - 提供通用的 Activity 功能
 * - WindowInsets 处理（通过 WindowInsetsHelper）
 * - 生命周期管理
 * - 统一状态栏样式
 * 
 * 设计原则：
 * - 单一职责：只提供最基础的通用功能
 * - 组合优于继承：具体功能通过管理器类实现
 * - 避免过度抽象：不强制子类实现不需要的方法
 */
abstract class BaseActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 统一设置状态栏样式
        setupStatusBar()
    }

    /**
     * 设置状态栏样式
     * 统一使用白色背景 + 深色图标
     */
    private fun setupStatusBar() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.WHITE
        window.navigationBarColor = android.graphics.Color.WHITE
        
        // 设置状态栏图标为深色（适配白色背景）
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true  // 深色图标
            isAppearanceLightNavigationBars = true  // 深色导航栏图标
        }
    }

    /**
     * 设置 WindowInsets 适配
     * 子类可以根据需要调用此方法
     */
    protected fun setupWindowInsets() {
        // 子类可以重写此方法以自定义 WindowInsets 处理
        // 或者直接使用 WindowInsetsHelper 的静态方法
    }
}

/**
 * 带历史对话功能的基础 Activity
 * 
 * 提供 HistoryFragment.Listener 的默认实现，避免在多个 Activity 中重复代码
 * 
 * 使用方式：
 * ```kotlin
 * class MyActivity : BaseHistoryActivity() {
 *     // 自动获得 HistoryFragment.Listener 的默认实现
 *     // 如需自定义，可以重写相应方法
 * }
 * ```
 */
abstract class BaseHistoryActivity : BaseActivity(), HistoryFragment.Listener {
    
    /**
     * 当历史对话被选中时调用
     * 默认实现：跳转到对应的聊天页面
     * 
     * @param conversationId 被选中的对话 ID
     */
    override fun onHistorySelected(conversationId: String) {
        AppNavigator.navigateToHistoryChat(this, conversationId)
    }
    
    /**
     * 当对话被删除时调用
     * 默认实现：无操作（列表会自动刷新）
     * 
     * @param conversationId 被删除的对话 ID
     */
    override fun onConversationDeleted(conversationId: String) {
        // 默认无需特殊处理，HistoryFragment 会自动刷新列表
    }
}
