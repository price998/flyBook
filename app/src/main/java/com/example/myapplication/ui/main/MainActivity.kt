package com.example.myapplication.ui.main

import android.os.Bundle
import android.view.MotionEvent
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import com.example.myapplication.R
import com.example.myapplication.databinding.ActivityMainBinding
import com.example.myapplication.ui.base.BaseChatActivity
import com.example.myapplication.ui.common.managers.SidebarManager
import com.example.myapplication.ui.common.navigation.AppNavigator
import com.example.myapplication.ui.history.view.HistoryFragment
import com.example.myapplication.ui.inputbar.InputBarViewModel
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
 *
 * 继承 BaseChatActivity 以复用 Sidebar 和 InputBar 逻辑
 */
class MainActivity : BaseChatActivity() {

    companion object {
        /** Intent 参数：是否重置输入模式 */
        const val EXTRA_RESET_INPUT_MODE = "extra_reset_input_mode"
    }

    // UI Components
    private lateinit var binding: ActivityMainBinding
    private lateinit var topicAdapter: TopicAdapter

    // ViewModels
    private lateinit var viewModel: MainViewModel

    /** 话题列表自动滚动任务 */
    private var autoScrollJob: Job? = null
    /** 用户是否正在与话题列表交互 */
    private var isUserInteracting = false

    // ========== BaseChatActivity 抽象方法实现 ==========

    override fun getDrawerLayout(): DrawerLayout = binding.drawerLayout
    override fun getSidebarBinding(): com.example.myapplication.databinding.IncludeSidebarCommonBinding = binding.includeSidebar
    override fun getInputBarContainerId(): Int = R.id.input_bar_fragment

    // 历史对话跳转
    override fun onHistorySelected(conversationId: String) {
        sidebarManager.closeDrawer()
        AppNavigator.navigateToHistoryChat(this, conversationId)
    }

    // 新建对话逻辑
    override fun onNewChatClick() {
        // 清空输入框和附件
        inputBarFragment.clearInput()
        inputBarFragment.clearMediaItems()
        // 重置侧边栏选中状态
        sidebarManager.updateSelection(SidebarManager.SidebarItem.NEW_CHAT)
        Toast.makeText(this, "新建对话", Toast.LENGTH_SHORT).show()
    }

    // 生成假数据逻辑
    override fun onGenerateFakeData() {
        Toast.makeText(this, "正在生成假数据对话…", Toast.LENGTH_SHORT).show()
        viewModel.generateFakeConversation { conversationId ->
            // 生成完成后直接跳到ChatActivity展示这条长对话
            sidebarManager.closeDrawer()
            AppNavigator.navigateToChat(this, conversationId = conversationId)
        }
    }

    // ========== Activity 生命周期 ==========

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        android.util.Log.d("MainActivity", "onCreate 开始")
        enableEdgeToEdge()

        // 重置会话状态（仅在首次创建时）
        if (savedInstanceState == null) {
            InputBarViewModel.resetSessionState(this)
            android.util.Log.d("MainActivity", "会话状态已重置")
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[MainViewModel::class.java]
        android.util.Log.d("MainActivity", "ViewModels 初始化完成")

        setupWindowInsetsForDialogue()
        setupCommonUI()
        setupClickListeners()
        setupHistoryFragment(null)
        setupTopics()
        observeViewModel()

        android.util.Log.d("MainActivity", "onCreate 完成")
    }

    override fun onResume() {
        super.onResume()
        // 基类已处理 InputBar 状态同步
        sidebarManager.updateSelection(SidebarManager.SidebarItem.NEW_CHAT)
        startAutoScroll()
    }

    override fun onPause() {
        super.onPause()
        stopAutoScroll()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAutoScroll()
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

    /** 设置历史对话列表 Fragment */
    private fun setupHistoryFragment(currentConversationId: String?) {
        val fragment = HistoryFragment.newInstance(currentConversationId)
        supportFragmentManager
                .beginTransaction()
                .replace(R.id.history_fragment_container, fragment)
                .commit()
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
                val isVoiceMode = !(inputBarViewModel.isKeyboardMode.value ?: true)

                android.util.Log.d("MainActivity", "开始创建话题对话: $content")
                viewModel.createNewConversation(content) { conversationId ->
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
            // 布局设置
            layoutManager =
                    androidx.recyclerview.widget.StaggeredGridLayoutManager(
                            2,
                            androidx.recyclerview.widget.StaggeredGridLayoutManager.HORIZONTAL
                    )

            // 绑定adapter
            adapter = topicAdapter

            android.util.Log.d("MainActivity", "RecyclerView 设置完成，adapter: ${adapter != null}")

            // 添加触摸事件监听器
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
        viewModel.topicList.observe(this) { topics ->
            android.util.Log.d("MainActivity", "话题列表更新，数量: ${topics.size}")
            topics.forEachIndexed { index, topic ->
                android.util.Log.d("MainActivity", "话题[$index]: ${topic.title}")
            }
            topicAdapter.updateData(topics)
        }

        // 观察toast消息
        viewModel.toastMessage.observe(this) { message ->
            message?.let {
                Toast.makeText(this, it, Toast.LENGTH_SHORT).show()
                viewModel.onToastShown()
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
        // 在数据库里创建新对话
        viewModel.createNewConversation(displayTitle) { conversationId ->
            android.util.Log.d("MainActivity", "对话创建成功，ID: $conversationId")

            // 如果开启了联网搜索，给用户提示
            if (InputBarViewModel.getWebSearchEnabled(this@MainActivity) && text.isNotEmpty()) {
                Toast.makeText(this@MainActivity, "🔍 将使用联网搜索回答您的问题", Toast.LENGTH_SHORT).show()
            }

            // 使用 AppNavigator 统一导航
            AppNavigator.navigateToChat(
                    activity = this@MainActivity,
                    conversationId = conversationId,
                    initialQuestion = text.takeIf { it.isNotEmpty() },
                    isVoiceMode = !(inputBarViewModel.isKeyboardMode.value ?: true),
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
}
