package com.example.myapplication.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.R
import com.example.myapplication.databinding.ActivityChatBinding
import com.example.myapplication.domain.ChatMessage
import com.example.myapplication.ui.base.BaseHistoryActivity
import com.example.myapplication.ui.chat.ChatViewModel.OCRProgress
import com.example.myapplication.ui.chat.adapters.ChatMessageAdapter
import com.example.myapplication.ui.common.dialogs.MessageActionsBottomSheet
import com.example.myapplication.ui.common.dialogs.SelectTextDialogFragment
import com.example.myapplication.ui.common.fragments.InputBarFragment
import com.example.myapplication.ui.common.managers.AttachmentSelectionManager
import com.example.myapplication.ui.common.managers.ModelManager
import com.example.myapplication.ui.common.managers.SidebarManager
import com.example.myapplication.ui.common.managers.TTSManager
import com.example.myapplication.ui.common.managers.VoiceRecognitionManager
import com.example.myapplication.ui.common.model.MediaType
import com.example.myapplication.ui.common.model.SelectedMedia
import com.example.myapplication.ui.common.navigation.AppNavigator
import com.example.myapplication.ui.history.HistoryFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.noties.markwon.Markwon
import kotlin.math.abs

/**
 * 聊天对话Activity
 *
 * 职责：
 * - 展示聊天消息列表
 * - 处理消息发送（文本、图片、文件）
 * - 管理消息的点赞、分享、重新生成等操作
 * - 处理 OCR 图片识别
 * - 管理消息列表的自动滚动
 *
 * 使用组合模式管理功能：
 * - AttachmentManager: 附件管理
 * - VoiceRecognitionManager: 语音识别
 * - SidebarManager: 侧边栏管理
 */
class ChatActivity :
        BaseHistoryActivity(),
        MessageActionsBottomSheet.Listener,
        InputBarFragment.InputBarListener {

    companion object {
        /** Intent 参数：对话 ID */
        const val EXTRA_CONVERSATION_ID = "extra_conversation_id"
        /** Intent 参数：初始问题 */
        const val EXTRA_INITIAL_QUESTION = "extra_initial_question"
        /** 录音权限请求码 */
        const val REQUEST_RECORD_AUDIO_PERMISSION = 1
    }

    /** 是否为键盘输入模式（false 为语音模式） */
    private var isKeyboardMode = true
    /** 是否正在生成回复 */
    private var isGeneratingResponse = false
    /** OCR 进度对话框 */
    private var ocrProgressDialog: AlertDialog? = null
    /** 上一次消息列表的数量，用于检测列表变化 */
    private var previousMessageCount = 0

    /** 是否应该自动滚动到底部 */
    private var shouldAutoScroll = true
    /** 用户是否正在拖动列表 */
    private var isUserDragging = false

    // UI Components
    private lateinit var binding: ActivityChatBinding
    private lateinit var chatMessageAdapter: ChatMessageAdapter
    private lateinit var inputBarFragment: InputBarFragment

    // ViewModels
    private lateinit var viewModel: ChatViewModel
    private lateinit var historyViewModel: com.example.myapplication.ui.history.HistoryViewModel

    // Managers (组合模式)
    private lateinit var attachmentManager: AttachmentSelectionManager
    private lateinit var voiceRecognitionManager: VoiceRecognitionManager
    private lateinit var sidebarManager: SidebarManager
    private lateinit var ttsManager: TTSManager

    // Markwon 实例 - Activity 级别单例，注入到 Adapter
    // 使用优化的 MarkwonFactory 创建，支持代码高亮且性能优化
    private val markwon: Markwon by lazy {
        com.example.myapplication.utils.MarkwonFactory.create(
                context = this,
                enableCodeHighlight = true // 启用代码高亮（已优化性能）
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize ViewModels
        viewModel = ViewModelProvider(this)[ChatViewModel::class.java]
        historyViewModel =
                ViewModelProvider(this)[
                        com.example.myapplication.ui.history.HistoryViewModel::class.java]

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

        // 初始化 TTSManager（语音播放）
        ttsManager = TTSManager(this)
        ttsManager.init()

        setupWindowInsetsForChat()
        setupSidebar()
        setupClickListeners()
        setupChatRecyclerView()
        setupHistoryFragment(intent.getStringExtra(EXTRA_CONVERSATION_ID))
        observeViewModel()

        // 观察历史对话数据ID并设置到ViewModel
        val conversationId = intent.getStringExtra(EXTRA_CONVERSATION_ID) ?: ""
        if (conversationId.isNotEmpty()) {
            viewModel.setConversationId(conversationId)
            (supportFragmentManager.findFragmentById(R.id.history_fragment_container) as?
                            HistoryFragment)
                    ?.updateCurrentConversation(conversationId)
        }

        // 处理联网搜索开关状态（必须在发送消息之前设置）
        // 从会话状态管理器读取状态，而不是从 Intent
        val isWebSearchEnabled =
                com.example.myapplication.utils.AppPreferences.getWebSearchEnabled()
        inputBarFragment.setWebSearchEnabled(isWebSearchEnabled)
        // 同步状态到 ViewModel
        viewModel.toggleSearch(isWebSearchEnabled)
        android.util.Log.d("ChatActivity", "初始化联网搜索状态: $isWebSearchEnabled")

        // 处理初始问题（在搜索状态设置之后）
        val initialQuestion = intent.getStringExtra(EXTRA_INITIAL_QUESTION)
        initialQuestion?.takeIf { it.isNotBlank() }?.let { question ->
            if (savedInstanceState == null && (viewModel.messages.value?.isEmpty() == true)) {
                viewModel.sendMessage(question)
            }
        }

        // 处理初始语音模式状态
        val isInitialVoiceMode = intent.getBooleanExtra("is_voice_mode", false)
        isKeyboardMode = !isInitialVoiceMode // 默认为键盘模式
        inputBarFragment.setInputMode(isKeyboardMode)

        // 处理从MainActivity传递过来的附件
        val imageUris = intent.getStringArrayListExtra("image_uris")
        val fileUris = intent.getStringArrayListExtra("file_uris")
        if (!imageUris.isNullOrEmpty() || !fileUris.isNullOrEmpty()) {
            val attachments = mutableListOf<SelectedMedia>()
            imageUris?.forEach { uriString ->
                attachments.add(SelectedMedia(android.net.Uri.parse(uriString), MediaType.IMAGE))
            }
            fileUris?.forEach { uriString ->
                attachments.add(SelectedMedia(android.net.Uri.parse(uriString), MediaType.FILE))
            }
            if (attachments.isNotEmpty()) {
                inputBarFragment.addMediaItems(attachments)
            }
        }
    }

    /** 设置 WindowInsets 适配 */
    private fun setupWindowInsetsForChat() {
        com.example.myapplication.utils.WindowInsetsHelper.setupDrawerLayoutInsets(
                binding.chatDrawerLayout
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
                        drawerLayout = binding.chatDrawerLayout,
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
                startNewChat()
            }
            SidebarManager.SidebarItem.KNOWLEDGE_BASE -> {
                Toast.makeText(this@ChatActivity, "知识库", Toast.LENGTH_SHORT).show()
                sidebarManager.updateSelection(SidebarManager.SidebarItem.KNOWLEDGE_BASE)
                AppNavigator.navigateToNewChat(this, resetInputMode = true, finishCurrent = true)
            }
            SidebarManager.SidebarItem.GENERATE_FAKE_DATA -> {
                Toast.makeText(this@ChatActivity, "正在生成假数据对话…", Toast.LENGTH_SHORT).show()

                historyViewModel.generateFakeConversation { conversationId ->
                    // 切换当前 ViewModel 到这条长对话
                    viewModel.setConversationId(conversationId)
                    (supportFragmentManager.findFragmentById(R.id.history_fragment_container) as?
                                    HistoryFragment)
                            ?.updateCurrentConversation(conversationId)
                    // 更新历史列表选中状态
                    sidebarManager.updateSelection(null)

                    Toast.makeText(this@ChatActivity, "假数据对话已生成", Toast.LENGTH_SHORT).show()

                    // 滚动到底部，看到最新一轮
                    val size = viewModel.messages.value?.size ?: 0
                    if (size > 0) {
                        binding.chatMessagesRecyclerview.scrollToPosition(size - 1)
                    }
                }
            }
        }
    }

    /** 更新侧边栏选中状态 */
    private fun updateSidebarSelection(item: SidebarManager.SidebarItem?) {
        sidebarManager.updateSelection(item)
    }

    /** 设置点击监听器 */
    private fun setupClickListeners() {
        // 菜单按钮
        binding.ivMenu.setOnClickListener {
            sidebarManager.openDrawer()
            sidebarManager.updateSelection(null)
        }

        // 新对话按钮 (主界面图标)
        binding.icNewChat.setOnClickListener { startNewChat() }
    }

    /** 设置聊天RecyclerView */
    private fun setupChatRecyclerView() {
        binding.chatMessagesRecyclerview.apply {
            val linearLayoutManager =
                    LinearLayoutManager(this@ChatActivity).apply {
                        // 列表从底部开始堆叠，最后一条自然贴着底部 / 键盘
                        stackFromEnd = false
                    }
            layoutManager = linearLayoutManager

            val initialList = viewModel.messages.value ?: mutableListOf()

            adapter =
                    ChatMessageAdapter(
                                    messages = initialList,
                                    markwon = markwon,
                                    onAiMessageLongClick = { message, anchorView, rawX, rawY ->
                                        // 长按 AI 消息，弹出对话框
                                        showMessageActionsDialog(message, anchorView, rawX, rawY)
                                    },
                                    onShareClick = { message -> shareText(message.content) },
                                    onLikeClick = { message ->
                                        updateLikeState(message, isLike = true)
                                    },
                                    onDislikeClick = { message ->
                                        updateLikeState(message, isLike = false)
                                    },
                                    onReloadClick = { message -> reloadAnswer(message) },
                                    onSpeakClick = { message -> speakMessage(message) }
                            )
                            .also { chatMessageAdapter = it }

            setHasFixedSize(true)
            setItemViewCacheSize(20)
            (itemAnimator as? androidx.recyclerview.widget.SimpleItemAnimator)
                    ?.supportsChangeAnimations = false

            // 键盘弹出时，如果本来在底部，就把最后一条挪到键盘上方
            addOnLayoutChangeListener { _, _, _, _, bottom, _, _, _, oldBottom ->
                if (bottom < oldBottom && shouldAutoScroll && !isUserDragging) {
                    scrollToPosition(chatMessageAdapter.itemCount - 1)
                }
            }

            // 监听用户拖动 & 顶部加载历史
            addOnScrollListener(
                    object : RecyclerView.OnScrollListener() {

                        override fun onScrollStateChanged(
                                recyclerView: RecyclerView,
                                newState: Int
                        ) {
                            super.onScrollStateChanged(recyclerView, newState)

                            when (newState) {
                                RecyclerView.SCROLL_STATE_DRAGGING -> {
                                    // 用户开始拖动，关闭自动跟随
                                    isUserDragging = true
                                    shouldAutoScroll = false
                                }
                                RecyclerView.SCROLL_STATE_IDLE -> {
                                    // 停止拖动，判断是否回到底部，是的话重新开启自动滚动
                                    isUserDragging = false
                                    val lm =
                                            recyclerView.layoutManager as? LinearLayoutManager
                                                    ?: return
                                    val lastVisible = lm.findLastVisibleItemPosition()
                                    val itemCount = chatMessageAdapter.itemCount
                                    shouldAutoScroll = itemCount > 0 && lastVisible >= itemCount - 1
                                }
                            }
                        }

                        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                            super.onScrolled(recyclerView, dx, dy)
                            val lm = recyclerView.layoutManager as? LinearLayoutManager ?: return

                            // 上拉到列表顶部时自动加载历史消息
                            if (dy < 0 &&
                                            lm.findFirstVisibleItemPosition() == 0 &&
                                            !binding.swipeRefreshLayout.isRefreshing
                            ) {
                                binding.swipeRefreshLayout.isRefreshing = true
                                viewModel.loadMoreHistory()
                            }
                        }
                    }
            )
        }

        // 设置下拉刷新监听器
        binding.swipeRefreshLayout.setOnRefreshListener { viewModel.loadMoreHistory() }

        // 历史列表由 HistoryFragment 托管，在 setupHistoryFragment() 中初始化

        // 观察AI生成状态，控制停止按钮的显示
        viewModel.isGenerating.observe(this) { isGenerating ->
            inputBarFragment.setGenerating(isGenerating)
            isGeneratingResponse = isGenerating
        }

        // 观察OCR解析进度
        viewModel.ocrProgress.observe(this) { progress -> handleOCRProgress(progress) }

        // 观察消息列表变化（用于切换对话时的刷新）
        viewModel.messages.observe(this) { messages ->
            val currentCount = messages.size

            when {
                // 列表被清空（切换对话时）
                currentCount == 0 && previousMessageCount > 0 -> {
                    chatMessageAdapter.notifyItemRangeRemoved(0, previousMessageCount)
                }
                // 列表从空变为有数据（加载新对话）
                currentCount > 0 && previousMessageCount == 0 -> {
                    chatMessageAdapter.notifyItemRangeInserted(0, currentCount)
                    // 这批是从数据库加载的历史消息，不需要打字机动画
                    chatMessageAdapter.markMessagesAsAnimated(0, currentCount)
                    // 滚动到底部
                    binding.chatMessagesRecyclerview.post {
                        binding.chatMessagesRecyclerview.scrollToPosition(currentCount - 1)
                    }
                }
                // 列表大小显著变化（可能是切换对话后加载了历史消息）
                currentCount > 0 &&
                        previousMessageCount > 0 &&
                        abs(currentCount - previousMessageCount) > 1 -> {
                    // 使用更具体的通知方法而不是 notifyDataSetChanged
                    if (currentCount > previousMessageCount) {
                        // 添加了消息
                        chatMessageAdapter.notifyItemRangeInserted(
                                previousMessageCount,
                                currentCount - previousMessageCount
                        )
                    } else {
                        // 移除了消息
                        chatMessageAdapter.notifyItemRangeRemoved(
                                currentCount,
                                previousMessageCount - currentCount
                        )
                    }
                    // 滚动到底部
                    binding.chatMessagesRecyclerview.post {
                        binding.chatMessagesRecyclerview.scrollToPosition(currentCount - 1)
                    }
                }
            }

            previousMessageCount = currentCount
        }

        // 观察消息更新事件
        viewModel.messageUpdate.observe(this) { event ->
            when (event) {
                is MessageUpdateEvent.ItemInserted -> {
                    chatMessageAdapter.notifyItemInserted(event.position)

                    // 只有当前处于"自动跟随"状态，才让新消息把列表带到底部
                    if (!isUserDragging && shouldAutoScroll) {
                        binding.chatMessagesRecyclerview.scrollToPosition(
                                chatMessageAdapter.itemCount - 1
                        )
                    }
                }
                is MessageUpdateEvent.ItemChanged -> {
                    chatMessageAdapter.notifyItemChanged(
                            event.position,
                            ChatMessageAdapter.PAYLOAD_CONTENT_UPDATE
                    )

                    // 如果用户已经手动滑走，就完全不要动他的视图
                    if (!shouldAutoScroll || isUserDragging) return@observe

                    val rv = binding.chatMessagesRecyclerview

                    rv.post {
                        // 三个值分别是：
                        // extent: 当前屏幕（可见区域）的高度
                        // range : 整个列表内容的总高度
                        // offset: 当前已滚动的偏移量
                        val extent = rv.computeVerticalScrollExtent()
                        val range = rv.computeVerticalScrollRange()
                        val offset = rv.computeVerticalScrollOffset()

                        // diff = 底部还差多少像素
                        val diff = range - extent - offset

                        if (diff > 0) {
                            // 给个上限，防止意外大跳（比如布局刚刷新）
                            val maxAutoScroll = (80 * resources.displayMetrics.density).toInt()
                            val dy = diff.coerceAtMost(maxAutoScroll)

                            // 真正补齐到底部
                            rv.scrollBy(0, dy)
                        }
                    }
                }
                is MessageUpdateEvent.HistoryLoaded -> {
                    binding.swipeRefreshLayout.isRefreshing = false
                    chatMessageAdapter.notifyItemRangeInserted(0, event.count)
                    // 顶部新插入的是更早的历史消息，同样不需要打字机动画
                    chatMessageAdapter.markMessagesAsAnimated(0, event.count)
                    binding.chatMessagesRecyclerview.scrollToPosition(event.count)
                }
                is MessageUpdateEvent.NoMoreHistory -> {
                    binding.swipeRefreshLayout.isRefreshing = false
                    Toast.makeText(this, "没有更多历史消息了", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 每次恢复时重新加载历史列表，确保数据同步
        historyViewModel.loadHistory()

        // 同步联网搜索状态（从其他页面返回时可能已改变）
        val isWebSearchEnabled =
                com.example.myapplication.utils.AppPreferences.getWebSearchEnabled()
        inputBarFragment.setWebSearchEnabled(isWebSearchEnabled)
        viewModel.toggleSearch(isWebSearchEnabled)
    }

    private fun startNewChat() {
        AppNavigator.navigateToNewChat(this, resetInputMode = true, finishCurrent = true)
    }

    private fun observeViewModel() {
        // This function wraps all the observe calls that are currently in setupChatRecyclerView
        // The actual observation code is already in place in setupChatRecyclerView
    }

    // ========== InputBarFragment.InputBarListener 实现 ==========

    override fun onSendClick(
            text: String,
            imageUris: List<android.net.Uri>,
            fileUris: List<android.net.Uri>
    ) {
        sendMessage(text, imageUris, fileUris)
    }

    override fun onStopClick() {
        viewModel.stopGeneration()
        Toast.makeText(this, "正在停止生成...", Toast.LENGTH_SHORT).show()
    }

    override fun onAttachmentClick(anchor: View) {
        attachmentManager.showAttachmentOptions(anchor)
    }

    override fun onInputModeToggle(isKeyboardMode: Boolean) {
        this.isKeyboardMode = isKeyboardMode
        if (!isKeyboardMode) {
            // 隐藏键盘
            val imm =
                    getSystemService(Context.INPUT_METHOD_SERVICE) as
                            android.view.inputmethod.InputMethodManager
            imm.hideSoftInputFromWindow(window.decorView.windowToken, 0)
        }
    }

    override fun onModelSelectorClick() {
        showModelSelectorDialog()
    }

    override fun onWebSearchToggle(isEnabled: Boolean) {
        android.util.Log.d("ChatActivity", "onWebSearchToggle 被调用，isEnabled: $isEnabled")

        // 同步状态到 ViewModel
        viewModel.toggleSearch(isEnabled)
        android.util.Log.d("ChatActivity", "已调用 viewModel.toggleSearch($isEnabled)")

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

    // 弹出对话框
    private fun showMessageActionsDialog(
            message: ChatMessage,
            anchorView: View,
            rawX: Int,
            @Suppress("UNUSED_PARAMETER") rawY: Int
    ) {
        val location = IntArray(2)
        anchorView.getLocationOnScreen(location)
        val anchorBottomY = location[1] + anchorView.height

        MessageActionsBottomSheet.newInstance(message, rawX, anchorBottomY, this)
                .show(supportFragmentManager, "message_actions")
    }

    private fun sendMessage(
            text: String,
            imageUris: List<android.net.Uri>,
            fileUris: List<android.net.Uri>
    ) {
        try {
            // 如果没有输入文本也没有选中附件，提示用户
            if (text.isEmpty() && imageUris.isEmpty() && fileUris.isEmpty()) {
                Toast.makeText(this, "请输入消息或选择附件", Toast.LENGTH_SHORT).show()
                return
            }

            // 清空输入框
            inputBarFragment.clearInput()

            // ✅ 用户主动发出问题：无论当前在不在底部，都恢复自动跟随，并准备滚到底部
            shouldAutoScroll = true
            isUserDragging = false

            if (imageUris.isEmpty() && fileUris.isEmpty()) {
                // 只有文本，直接发送
                viewModel.sendMessage(text)
            } else {
                if (imageUris.isNotEmpty()) {
                    showOCRProgressDialog()
                }
                viewModel.sendMessageWithAttachments(text, imageUris, fileUris)

                // 没有图片时无需等待OCR回调，可直接清空预览
                if (imageUris.isEmpty()) {
                    inputBarFragment.clearMediaItems()
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("ChatActivity", "发送消息时发生错误", e)
            Toast.makeText(this, "发送失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // 图片/文件选择、附件管理等方法已移至 AttachmentManager

    /** 处理OCR解析进度 */
    private fun handleOCRProgress(progress: OCRProgress) {
        when (progress) {
            is OCRProgress.Idle -> {
                ocrProgressDialog?.dismiss()
                ocrProgressDialog = null
            }
            is OCRProgress.Recognizing -> {
                // 更新进度对话框显示当前识别进度
                ocrProgressDialog?.setMessage("正在识别图片文字... (${progress.current}/${progress.total})")
            }
            is OCRProgress.Success -> {
                ocrProgressDialog?.dismiss()
                ocrProgressDialog = null
                // 清空选中的附件列表
                inputBarFragment.clearMediaItems()
                Toast.makeText(this, "识别成功，已发送", Toast.LENGTH_SHORT).show()
            }
            is OCRProgress.Error -> {
                ocrProgressDialog?.dismiss()
                ocrProgressDialog = null
                Toast.makeText(this, progress.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    /** 显示OCR解析进度对话框 */
    private fun showOCRProgressDialog() {
        ocrProgressDialog =
                MaterialAlertDialogBuilder(this)
                        .setTitle("正在识别图片文字...")
                        .setMessage("请稍候，正在使用OCR识别图片中的文字")
                        .setCancelable(false)
                        .create()
        ocrProgressDialog?.show()
    }

    // 语音识别相关方法已移至 VoiceRecognitionManager

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

    // ========== 语音识别回调 ==========

    private fun onVoiceRecognitionResult(text: String) {
        // 自动切换到键盘模式，这样可以在输入框看到识别内容
        if (!isKeyboardMode) {
            isKeyboardMode = true
            inputBarFragment.setInputMode(true)
        }
        // 将识别结果填入输入框
        inputBarFragment.setInputText(text)
    }

    private fun onRecordAudioPermissionNeeded() {
        requestPermissions(
                arrayOf(android.Manifest.permission.RECORD_AUDIO),
                REQUEST_RECORD_AUDIO_PERMISSION
        )
    }

    // ========== 模型选择 ==========

    private fun showModelSelectorDialog() {
        ModelManager.showSelector(this) { modelConfig ->
            Toast.makeText(this, "已切换到: ${modelConfig.displayName}", Toast.LENGTH_SHORT).show()
        }
    }
    // 复制
    private fun copyToClipboard(text: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("chat", text))
        Toast.makeText(this, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
    }

    // 点赞 / 点踩：更新内存中的列表 + 刷新对应 item
    private fun updateLikeState(message: ChatMessage, isLike: Boolean) {
        val list = chatMessageAdapter.messages
        val index = list.indexOf(message)
        if (index == -1) return

        val old = list[index]
        val newMsg =
                if (isLike) {
                    old.copy(isLiked = !old.isLiked, isDisliked = false)
                } else {
                    old.copy(isLiked = false, isDisliked = !old.isDisliked)
                }

        list[index] = newMsg
        chatMessageAdapter.notifyItemChanged(index)
        viewModel.updateMessageLikeState(newMsg)
    }

    // 重新加载：找到这条 AI 回复上面最近的用户问题，用它重新发起一次请求
    private fun reloadAnswer(message: ChatMessage) {
        val list = chatMessageAdapter.messages
        val index = list.indexOf(message)
        if (index <= 0) return

        val userIndex = (index - 1 downTo 0).firstOrNull { list[it].isUser } ?: return
        val question = list[userIndex].content

        viewModel.sendMessage(question)
        Toast.makeText(this, "正在重新生成回答...", Toast.LENGTH_SHORT).show()
    }

    // 删除当前这条 AI 消息（如果你有数据库，也可以在 ViewModel 里同步删）
    // 删除这一组：上面最近的一条用户问题 + 当前这条 AI 回复
    // 🆕 成对删除：删除这条 AI 以及它上面最近的一条用户消息
    private fun deleteMessage(message: ChatMessage) {
        val list = chatMessageAdapter.messages
        val index = list.indexOf(message)
        if (index == -1) return

        val timestampsToDelete = mutableListOf<Long>()
        val positionsToRemove = mutableListOf<Int>()

        // 1. 当前这条（通常是 AI 回复）
        timestampsToDelete.add(list[index].timestamp)
        positionsToRemove.add(index)

        // 2. 向上找最近的一条用户消息，当作「这条回复对应的问题」
        val userIndex = (index - 1 downTo 0).firstOrNull { list[it].isUser }
        if (userIndex != null) {
            timestampsToDelete.add(list[userIndex].timestamp)
            positionsToRemove.add(userIndex)
        }

        // 先按位置从大到小删除，避免下标错乱
        positionsToRemove.distinct().sortedDescending().forEach { pos ->
            list.removeAt(pos)
            chatMessageAdapter.notifyItemRemoved(pos)
        }

        // 同步删除到数据库
        viewModel.deleteMessagePair(timestampsToDelete)
    }

    // ====== MessageActionsBottomSheet.Listener 实现 ======

    // HistoryFragment.Listener
    override fun onHistorySelected(conversationId: String) {
        binding.chatDrawerLayout.closeDrawers()
        viewModel.setConversationId(conversationId)
        (supportFragmentManager.findFragmentById(R.id.history_fragment_container) as?
                        HistoryFragment)
                ?.updateCurrentConversation(conversationId)
        updateSidebarSelection(null)
    }

    override fun onConversationDeleted(conversationId: String) {
        if (conversationId == viewModel.currentConversationId) {
            finish()
        }
    }

    override fun onCopy(message: ChatMessage) {
        copyToClipboard(message.content)
    }

    override fun onSelectText(message: ChatMessage) {
        SelectTextDialogFragment.newInstance(message.content)
                .show(supportFragmentManager, "select_text")
    }

    override fun onSpeak(message: ChatMessage) {
        speakMessage(message)
    }

    override fun onTranslate(message: ChatMessage) {
        val text = "请翻译下文：\n${message.content}"
        viewModel.sendMessage(text)
    }
    // 分享文本
    private fun shareText(text: String) {
        val intent =
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                }
        startActivity(Intent.createChooser(intent, "分享对话内容"))
    }

    override fun onLike(message: ChatMessage) {
        updateLikeState(message, isLike = true)
    }

    override fun onDislike(message: ChatMessage) {
        updateLikeState(message, isLike = false)
    }

    override fun onReload(message: ChatMessage) {
        reloadAnswer(message)
    }

    override fun onDelete(message: ChatMessage) {
        deleteMessage(message)
    }

    override fun onDestroy() {
        super.onDestroy()
        voiceRecognitionManager.destroy()
        ttsManager.destroy()
    }

    // ========== 语音播放 ==========

    /** 播放 AI 消息内容 */
    private fun speakMessage(message: ChatMessage) {
        if (message.content.isBlank()) {
            Toast.makeText(this, "没有可播放的内容", Toast.LENGTH_SHORT).show()
            return
        }

        // 如果正在播放，则停止
        if (ttsManager.isSpeaking()) {
            ttsManager.stop()
            Toast.makeText(this, "已停止播放", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "开始播放...", Toast.LENGTH_SHORT).show()
            ttsManager.speak(message.content)
        }
    }
}
