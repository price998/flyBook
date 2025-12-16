package com.example.myapplication.ui.base

import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.drawerlayout.widget.DrawerLayout
import com.example.myapplication.ui.common.managers.ModelManager
import com.example.myapplication.ui.common.managers.SidebarManager
import com.example.myapplication.ui.common.navigation.AppNavigator
import com.example.myapplication.ui.inputbar.InputBarFragment
import com.example.myapplication.ui.inputbar.InputBarViewModel

/**
 * 聊天功能基类 Activity
 *
 * 职责：
 * - 统一管理 SidebarManager（侧边栏）
 * - 统一管理 InputBarFragment（输入栏）
 * - 处理公共的权限请求和状态同步
 */
abstract class BaseChatActivity : BaseHistoryActivity(), InputBarFragment.InputBarListener {

    companion object {
        const val REQUEST_RECORD_AUDIO_PERMISSION = 2
    }

    protected lateinit var sidebarManager: SidebarManager
    protected lateinit var inputBarFragment: InputBarFragment
    protected val inputBarViewModel: InputBarViewModel by viewModels()

    // 抽象方法：子类提供必要的视图
    abstract fun getDrawerLayout(): DrawerLayout
    abstract fun getSidebarBinding(): com.example.myapplication.databinding.IncludeSidebarCommonBinding
    abstract fun getInputBarContainerId(): Int

    /**
     * 子类在 setContentView 后调用此方法初始化公共组件
     */
    protected fun setupCommonUI() {
        setupInputBar()
        setupSidebar()
        setupCommonObservers()
    }

    /** 初始化输入栏 */
    private fun setupInputBar() {
        // 立即执行事务，确保Fragment已经加载
        supportFragmentManager.executePendingTransactions()
        // 查找输入栏Fragment
        val fragment = supportFragmentManager.findFragmentById(getInputBarContainerId())
        // 如果找到，则设置监听器
        if (fragment is InputBarFragment) {
            inputBarFragment = fragment
            inputBarFragment.setListener(this)
        }
    }

    /** 初始化侧边栏 */
    private fun setupSidebar() {
        sidebarManager = SidebarManager(
            drawerLayout = getDrawerLayout(),
            sidebarBinding = getSidebarBinding(),
            // 监听器绑定
            listener = object : SidebarManager.Listener {
                override fun onItemClick(item: SidebarManager.SidebarItem) {
                    onSidebarItemClick(item)
                }
            }
        )
        // Manager初始化
        sidebarManager.setup()
    }

    /** 初始化公共的是否需要键盘观察者 */
    private fun setupCommonObservers() {
        // 观察输入模式变化（从 InputBarViewModel）
        inputBarViewModel.isKeyboardMode.observe(this) { isKeyboardMode ->
            // 仅当切换到键盘模式且处于活动状态时才显示键盘
            if (isKeyboardMode == true && hasWindowFocus()) {
                inputBarFragment.requestInputFocus()
                val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                imm.showSoftInput(
                    inputBarFragment.view,
                    android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT
                )
            }
        }
    }

    /** 在Activity恢复时同步联网搜索状态 */
    override fun onResume() {
        super.onResume()
        // 同步联网搜索状态（从持久化存储读取）
        val isWebSearchEnabled = InputBarViewModel.getWebSearchEnabled(this)
        if (this::inputBarFragment.isInitialized) {
            inputBarFragment.setWebSearchEnabled(isWebSearchEnabled)
        }
    }

    // ========== SidebarManager.Listener ==========

    /**
     * 处理侧边栏点击
     * 子类可以重写此方法以拦截特定事件
     */
    protected open fun onSidebarItemClick(item: SidebarManager.SidebarItem) {
        when (item) {
            SidebarManager.SidebarItem.TRASH -> {
                AppNavigator.navigateToTrash(this)
            }
            SidebarManager.SidebarItem.SEARCH -> {
                AppNavigator.navigateToSearch(this)
            }
            SidebarManager.SidebarItem.NEW_CHAT -> {
                onNewChatClick()
            }
            SidebarManager.SidebarItem.KNOWLEDGE_BASE -> {
                sidebarManager.updateSelection(SidebarManager.SidebarItem.KNOWLEDGE_BASE)
                Toast.makeText(this, "知识库", Toast.LENGTH_SHORT).show()
            }
            SidebarManager.SidebarItem.GENERATE_FAKE_DATA -> {
                onGenerateFakeData()
            }
        }
    }

    /** 子类实现“新建对话”的具体逻辑 */
    abstract fun onNewChatClick()

    /** 子类实现“生成假数据”的具体逻辑 */
    abstract fun onGenerateFakeData()

    // ========== InputBarFragment.InputBarListener 公共实现 ==========

    override fun onVoiceResult(text: String) {
        // 语音识别结果：自动切换到键盘模式并填入输入框
        // 确保非空判断
        if (inputBarViewModel.isKeyboardMode.value != true) {
             inputBarFragment.setInputMode(true)
        }
        inputBarFragment.setInputText(text)
    }

    /** 子类实现“停止点击”的具体逻辑 */
    override fun onStopClick() {
        // 默认无操作，MainActivity不需要该方法，而ChatActivity会覆盖此方法
    }

    /** “选择模型”的具体逻辑 */
    override fun onModelSelectorClick() {
        ModelManager.showSelector(this) { modelConfig ->
            Toast.makeText(this, "已切换到: ${modelConfig.displayName}", Toast.LENGTH_SHORT).show()
        }
    }

    /** “联网搜索”的具体逻辑 */
    override fun onWebSearchToggle(isEnabled: Boolean) {
        val message = if (isEnabled) "联网搜索已开启" else "联网搜索已关闭"
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    // ========== 权限处理 ==========

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_RECORD_AUDIO_PERMISSION -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "录音权限已授予", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "需要录音权限才能使用语音输入", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
