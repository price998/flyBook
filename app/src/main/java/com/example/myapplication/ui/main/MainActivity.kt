package com.example.myapplication.ui.main

import android.content.pm.PackageManager
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.R
import com.example.myapplication.databinding.ActivityMainBinding
import com.example.myapplication.ui.base.BaseHistoryActivity
import com.example.myapplication.ui.common.fragments.InputBarFragment
import com.example.myapplication.ui.common.managers.AttachmentSelectionManager
import com.example.myapplication.ui.common.managers.ModelManager
import com.example.myapplication.ui.common.managers.SidebarManager
import com.example.myapplication.ui.common.managers.VoiceRecognitionManager
import com.example.myapplication.ui.common.navigation.AppNavigator
import com.example.myapplication.ui.history.HistoryFragment
import com.example.myapplication.ui.main.adapters.TopicAdapter
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 主页对话Activity
 *
 * 职责：
 * - 展示推荐话题列表
 * - 处理话题自动滚动
 * - 创建新对话并跳转到 ChatActivity
 * - 管理输入栏和附件选择
 *
 * 使用组合模式管理功能：
 * - AttachmentManager: 附件管理
 * - VoiceRecognitionManager: 语音识别
 * - SidebarManager: 侧边栏管理
 */
class MainActivity : BaseHistoryActivity(), InputBarFragment.InputBarListener {

    companion object {
        /** Intent 参数：是否重置输入模式 */
        const val EXTRA_RESET_INPUT_MODE = "extra_reset_input_mode"
        /** 录音权限请求码 */
        const val REQUEST_RECORD_AUDIO_PERMISSION = 2
    }

    // UI Components
    private lateinit var binding: ActivityMainBinding
    private lateinit var topicAdapter: TopicAdapter
    private lateinit var inputBarFragment: InputBarFragment

    // ViewModels
    private lateinit var viewModel: MainViewModel
    private lateinit var historyViewModel: com.example.myapplication.ui.history.HistoryViewModel

    // Managers (组合模式)
    private lateinit var attachmentManager: AttachmentSelectionManager
    private lateinit var voiceRecognitionManager: VoiceRecognitionManager
    private lateinit var sidebarManager: SidebarManager

    /** 话题列表自动滚动任务 */
    private var autoScrollJob: Job? = null
    /** 用户是否正在与话题列表交互 */
    private var isUserInteracting = false

    // ========== HistoryFragment.Listener 实现（覆盖基类默认实现） ==========
    override fun onHistorySelected(conversationId: String) {
        sidebarManager.closeDrawer()
        AppNavigator.navigateToHistoryChat(this, conversationId)
    }

    // ========== Activity 生命周期 ==========

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        android.util.Log.d("MainActivity", "onCreate 开始")
        enableEdgeToEdge()

        // 重置会话状态（仅在首次创建时）
        if (savedInstanceState == null) {
            com.example.myapplication.utils.AppPreferences.resetSessionState()
            android.util.Log.d("MainActivity", "会话状态已重置")
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[MainViewModel::class.java]
        historyViewModel =
                ViewModelProvider(this)[
                        com.example.myapplication.ui.history.HistoryViewModel::class.java]
        android.util.Log.d("MainActivity", "ViewModels 初始化完成")

        // 初始化 InputBarFragment（必须在 setupWindowInsets 之前）
        supportFragmentManager.executePendingTransactions()
        inputBarFragment =
                supportFragmentManager.findFragmentById(R.id.input_bar_fragment) as InputBarFragment
        inputBarFragment.setListener(this)

        // 初始化 AttachmentSelectionManager
        attachmentManager = AttachmentSelectionManager(this)
        attachmentManager.setOnMediaSelectedListener { items ->
            inputBarFragment.addMediaItems(items)
        }

        // 初始化 VoiceRecognitionManager
        voiceRecognitionManager = VoiceRecognitionManager(this)
        voiceRecognitionManager.init()
        voiceRecognitionManager.setOnResultListener { text -> onVoiceRecognitionResult(text) }
        voiceRecognitionManager.setOnPermissionNeededListener { onRecordAudioPermissionNeeded() }

        setupWindowInsetsForDialogue()
        setupSidebar()
        setupClickListeners()
        setupHistoryFragment(null)
        setupTopics()
        observeViewModel()

        android.util.Log.d("MainActivity", "onCreate 完成")
    }

    override fun onResume() {
        super.onResume()
        historyViewModel.loadHistory()
        sidebarManager.updateSelection(SidebarManager.SidebarItem.NEW_CHAT)
        startAutoScroll()

        // 同步联网搜索状态（从 ChatActivity 返回时可能已改变）
        val isWebSearchEnabled =
                com.example.myapplication.utils.AppPreferences.getWebSearchEnabled()
        inputBarFragment.setWebSearchEnabled(isWebSearchEnabled)
    }

    override fun onPause() {
        super.onPause()
        stopAutoScroll()
    }

    override fun onDestroy() {
        super.onDestroy()
        voiceRecognitionManager.destroy()
    }

    // ========== 初始化方法 ==========

    /** 设置 WindowInsets 适配 */
    private fun setupWindowInsetsForDialogue() {
        com.example.myapplication.utils.WindowInsetsHelper.setupDrawerLayoutInsets(
                binding.drawerLayout
        )
        com.example.myapplication.utils.WindowInsetsHelper.setupInputLayoutInsets(
                binding.inputBarFragment
        )
        com.example.myapplication.utils.WindowInsetsHelper.setupSidebarInsets(
                binding.includeSidebar.root
        )
    }

    /** 设置侧边栏管理器 */
    private fun setupSidebar() {
        sidebarManager =
                SidebarManager(
                        drawerLayout = binding.drawerLayout,
                        sidebarBinding = binding.includeSidebar,
                        listener =
                                object : SidebarManager.Listener {
                                    override fun onItemClick(item: SidebarManager.SidebarItem) {
                                        onSidebarItemClick(item)
                                    }
                                }
                )
        sidebarManager.setup()
    }

    /** 设置历史对话列表 Fragment */
    private fun setupHistoryFragment(currentConversationId: String?) {
        val fragment = HistoryFragment.newInstance(currentConversationId)
        supportFragmentManager
                .beginTransaction()
                .replace(R.id.history_fragment_container, fragment)
                .commit()
    }

    /** 处理侧边栏按钮点击事件 */
    private fun onSidebarItemClick(item: SidebarManager.SidebarItem) {
        when (item) {
            SidebarManager.SidebarItem.TRASH -> {
                AppNavigator.navigateToTrash(this)
            }
            SidebarManager.SidebarItem.SEARCH -> {
                AppNavigator.navigateToSearch(this)
            }
            SidebarManager.SidebarItem.NEW_CHAT -> {
                // 清空输入框和附件
                inputBarFragment.clearInput()
                inputBarFragment.clearMediaItems()
                // 重置侧边栏选中状态
                sidebarManager.updateSelection(SidebarManager.SidebarItem.NEW_CHAT)
                Toast.makeText(this, "新建对话", Toast.LENGTH_SHORT).show()
            }
            SidebarManager.SidebarItem.KNOWLEDGE_BASE -> {
                sidebarManager.updateSelection(SidebarManager.SidebarItem.KNOWLEDGE_BASE)
                Toast.makeText(this, "知识库", Toast.LENGTH_SHORT).show()
            }
            SidebarManager.SidebarItem.GENERATE_FAKE_DATA -> {
                Toast.makeText(this, "正在生成假数据对话…", Toast.LENGTH_SHORT).show()

                historyViewModel.generateFakeConversation { conversationId ->
                    // 生成完成后直接跳到 ChatActivity 展示这条长对话
                    AppNavigator.navigateToChat(this, conversationId = conversationId)
                }
            }
        }
    }

    /** 设置点击监听器 */
    private fun setupClickListeners() {
        // 点击菜单按钮：显示侧边栏
        binding.ivMenu.setOnClickListener { sidebarManager.openDrawer() }
    }

    /** 设置话题列表 */
    private fun setupTopics() {
        topicAdapter = TopicAdapter { topic ->
            android.util.Log.d("MainActivity", "话题被点击: ${topic.title}")
            val content = topic.prompt
            if (content.isNotEmpty()) {
                val isVoiceMode = viewModel.isVoiceMode.value ?: false

                android.util.Log.d("MainActivity", "开始创建话题对话: $content")
                historyViewModel.createNewConversation(content) { conversationId ->
                    android.util.Log.d("MainActivity", "话题对话创建成功，ID: $conversationId")
                    runOnUiThread {
                        try {
                            AppNavigator.navigateToChat(
                                    activity = this@MainActivity,
                                    conversationId = conversationId,
                                    initialQuestion = content,
                                    isVoiceMode = isVoiceMode
                            )
                            inputBarFragment.clearInput()
                        } catch (e: Exception) {
                            android.util.Log.e("MainActivity", "启动对话失败", e)
                            e.printStackTrace()
                            Toast.makeText(
                                            this@MainActivity,
                                            "启动对话失败: ${e.message}",
                                            Toast.LENGTH_SHORT
                                    )
                                    .show()
                        }
                    }
                }
            } else {
                android.util.Log.w("MainActivity", "话题内容为空")
            }
        }

        binding.topicRecyclerview.apply {
            layoutManager =
                    androidx.recyclerview.widget.StaggeredGridLayoutManager(
                            2,
                            androidx.recyclerview.widget.StaggeredGridLayoutManager.HORIZONTAL
                    )
            adapter = topicAdapter

            android.util.Log.d("MainActivity", "RecyclerView 设置完成，adapter: ${adapter != null}")

            addOnItemTouchListener(
                    object : RecyclerView.SimpleOnItemTouchListener() {
                        override fun onInterceptTouchEvent(
                                rv: RecyclerView,
                                e: MotionEvent
                        ): Boolean {
                            when (e.action) {
                                MotionEvent.ACTION_DOWN -> {
                                    isUserInteracting = true
                                    android.util.Log.d("MainActivity", "用户开始触摸话题列表")
                                }
                                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                    isUserInteracting = false
                                    android.util.Log.d("MainActivity", "用户停止触摸话题列表")
                                }
                            }
                            return false
                        }
                    }
            )
        }
    }

    // ========== 话题列表自动滚动 ==========

    /** 开始自动滚动 */
    private fun startAutoScroll() {
        stopAutoScroll()
        autoScrollJob =
                lifecycleScope.launch {
                    while (isActive) {
                        delay(30)
                        if (!isUserInteracting && binding.topicRecyclerview.canScrollHorizontally(1)
                        ) {
                            binding.topicRecyclerview.scrollBy(2, 0)
                        }
                    }
                }
    }

    /** 停止自动滚动 */
    private fun stopAutoScroll() {
        autoScrollJob?.cancel()
        autoScrollJob = null
    }

    // ========== ViewModel 观察 ==========

    /** 观察 ViewModel 的 LiveData，实现数据变化自动更新 UI */
    private fun observeViewModel() {
        // 历史列表由 Fragment 管理，此处不再直接更新 Adapter

        viewModel.topicList.observe(this) { topics ->
            android.util.Log.d("MainActivity", "话题列表更新，数量: ${topics.size}")
            topics.forEachIndexed { index, topic ->
                android.util.Log.d("MainActivity", "话题[$index]: ${topic.title}")
            }
            topicAdapter.updateData(topics)
        }

        // 观察是否为语音模式
        viewModel.isVoiceMode.observe(this) { isVoiceMode ->
            inputBarFragment.setInputMode(isVoiceMode != true)

            // 仅当切换到键盘模式且处于活动状态时才显示键盘
            if (isVoiceMode == false && hasWindowFocus()) {
                inputBarFragment.requestInputFocus()
                val imm =
                        getSystemService(INPUT_METHOD_SERVICE) as
                                android.view.inputmethod.InputMethodManager
                imm.showSoftInput(
                        inputBarFragment.view,
                        android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT
                )
            }
        }

        // 观察toast消息
        viewModel.toastMessage.observe(this) { message ->
            message?.let {
                Toast.makeText(this, it, Toast.LENGTH_SHORT).show()
                viewModel.onToastShown()
            }
        }

        // 观察是否应该清除输入，如果是，清除输入
        viewModel.shouldClearInput.observe(this) { shouldClear ->
            if (shouldClear == true) {
                inputBarFragment.clearInput()
                viewModel.onInputCleared()
            }
        }
    }

    // ========== 权限处理 ==========

    /** 处理权限请求结果 */
    override fun onRequestPermissionsResult(
            requestCode: Int,
            permissions: Array<out String>,
            grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_RECORD_AUDIO_PERMISSION -> {
                if (grantResults.isNotEmpty() &&
                                grantResults[0] == PackageManager.PERMISSION_GRANTED
                ) {
                    Toast.makeText(this, "录音权限已授予", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "需要录音权限才能使用语音输入", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // ========== InputBarFragment.InputBarListener 实现 ==========

    override fun onSendClick(
            text: String,
            imageUris: List<android.net.Uri>,
            fileUris: List<android.net.Uri>
    ) {
        android.util.Log.d("MainActivity", "发送按钮被点击")
        android.util.Log.d(
                "MainActivity",
                "输入内容: $text, 图片数量: ${imageUris.size}, 文件数量: ${fileUris.size}"
        )

        // 如果没有输入文本也没有选中附件，提示用户
        if (text.isEmpty() && imageUris.isEmpty() && fileUris.isEmpty()) {
            Toast.makeText(this, "请输入消息或选择附件", Toast.LENGTH_SHORT).show()
            return
        }

        // 创建新对话
        val displayTitle =
                if (text.isNotEmpty()) {
                    if (text.length > 20) text.substring(0, 20) + "..." else text
                } else {
                    "图片对话"
                }

        android.util.Log.d("MainActivity", "开始创建新对话: $displayTitle")
        historyViewModel.createNewConversation(displayTitle) { conversationId ->
            android.util.Log.d("MainActivity", "对话创建成功，ID: $conversationId")

            // 如果开启了联网搜索，给用户提示
            if (com.example.myapplication.utils.AppPreferences.getWebSearchEnabled() &&
                            text.isNotEmpty()
            ) {
                Toast.makeText(this@MainActivity, "🔍 将使用联网搜索回答您的问题", Toast.LENGTH_SHORT).show()
            }

            // 使用 AppNavigator 统一导航
            AppNavigator.navigateToChat(
                    activity = this@MainActivity,
                    conversationId = conversationId,
                    initialQuestion = text.takeIf { it.isNotEmpty() },
                    isVoiceMode = viewModel.isVoiceMode.value ?: false,
                    imageUris = imageUris.takeIf { it.isNotEmpty() }?.map { it.toString() },
                    fileUris = fileUris.takeIf { it.isNotEmpty() }?.map { it.toString() }
            )

            // 清空输入框和附件
            runOnUiThread {
                inputBarFragment.clearInput()
                inputBarFragment.clearMediaItems()
            }
        }
    }

    override fun onStopClick() {
        // MainActivity 不需要停止生成功能
    }

    override fun onAttachmentClick(anchor: View) {
        attachmentManager.showAttachmentOptions(anchor)
    }

    override fun onInputModeToggle(isKeyboardMode: Boolean) {
        viewModel.setVoiceMode(!isKeyboardMode)
    }

    override fun onModelSelectorClick() {
        showModelSelectorDialog()
    }

    override fun onWebSearchToggle(isEnabled: Boolean) {
        // 不再保存状态到 SharedPreferences，状态仅在当前会话有效

        // 显示提示
        val message = if (isEnabled) "联网搜索已开启" else "联网搜索已关闭"
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onVoiceInputStart() {
        voiceRecognitionManager.startRecognition()
    }

    override fun onVoiceInputEnd() {
        voiceRecognitionManager.stopRecognition()
    }

    override fun onVoiceInputCancel() {
        voiceRecognitionManager.cancelRecognition()
    }

    // ========== 私有辅助方法 ==========

    /** 处理语音识别结果 */
    private fun onVoiceRecognitionResult(text: String) {
        // 自动切换到键盘模式，这样可以在输入框看到识别内容
        viewModel.setVoiceMode(false)
        // 将识别结果填入输入框
        inputBarFragment.setInputText(text)
    }

    /** 请求录音权限 */
    private fun onRecordAudioPermissionNeeded() {
        requestPermissions(
                arrayOf(android.Manifest.permission.RECORD_AUDIO),
                REQUEST_RECORD_AUDIO_PERMISSION
        )
    }

    // ========== 模型选择 ==========

    /** 显示模型选择对话框 */
    private fun showModelSelectorDialog() {
        ModelManager.showSelector(this) { modelConfig ->
            Toast.makeText(this, "已切换到: ${modelConfig.displayName}", Toast.LENGTH_SHORT).show()
        }
    }
}
