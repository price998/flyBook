package com.example.myapplication.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import com.example.myapplication.R
import com.example.myapplication.databinding.ActivityChatBinding
import com.example.myapplication.domain.ChatMessage
import com.example.myapplication.ui.base.BaseChatActivity
import com.example.myapplication.ui.chat.ChatViewModel.OCRProgress
import com.example.myapplication.ui.chat.adapters.ChatMessageAdapter
import com.example.myapplication.ui.common.dialogs.MessageActionsBottomSheet
import com.example.myapplication.ui.common.dialogs.SelectTextDialogFragment
import com.example.myapplication.ui.common.managers.SidebarManager
import com.example.myapplication.ui.common.navigation.AppNavigator
import com.example.myapplication.ui.history.view.HistoryFragment
import com.example.myapplication.ui.inputbar.InputBarViewModel
import com.example.myapplication.ui.inputbar.model.MediaType
import com.example.myapplication.ui.inputbar.model.SelectedMedia
import com.example.myapplication.utils.XunfeiSpeechSynthesizer
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
 * 继承 BaseChatActivity 以复用 Sidebar 和 InputBar 逻辑
 */
class ChatActivity :
        BaseChatActivity(),
        MessageActionsBottomSheet.Listener {

    companion object {
        /** Intent 参数：对话 ID */
        const val EXTRA_CONVERSATION_ID = "extra_conversation_id"
        /** Intent 参数：初始问题 */
        const val EXTRA_INITIAL_QUESTION = "extra_initial_question"
    }

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

    // ViewModels
    private lateinit var viewModel: ChatViewModel

    // Managers (组合模式)
    private lateinit var speechSynthesizer: XunfeiSpeechSynthesizer

    // Markwon 实例 - Activity 级别单例，注入到 Adapter
    // 使用优化的 MarkwonFactory 创建，支持代码高亮且性能优化
    private val markwon: Markwon by lazy {
        com.example.myapplication.utils.MarkwonFactory.create(
                context = this,
                enableCodeHighlight = true // 启用代码高亮（已优化性能）
        )
    }

    // ========== BaseChatActivity 抽象方法实现 ==========

    override fun getDrawerLayout(): DrawerLayout = binding.chatDrawerLayout
    override fun getSidebarBinding(): com.example.myapplication.databinding.IncludeSidebarCommonBinding = binding.includeSidebar
    override fun getInputBarContainerId(): Int = R.id.input_bar_fragment

    override fun onHistorySelected(conversationId: String) {
        binding.chatDrawerLayout.closeDrawers()
        viewModel.setConversationId(conversationId)
        // 高亮此处的历史记录
        (supportFragmentManager.findFragmentById(R.id.history_fragment_container) as?
                        HistoryFragment)
                ?.updateCurrentConversation(conversationId)
        // 取消其它的高亮
        sidebarManager.updateSelection(null)
    }

    // 点击新页面
    override fun onNewChatClick() {
        startNewChat()
    }

    // 生成假数据
    override fun onGenerateFakeData() {
        Toast.makeText(this@ChatActivity, "正在生成假数据对话…", Toast.LENGTH_SHORT).show()

        viewModel.generateFakeConversation { conversationId ->
            // 切换当前ViewModel到这条长对话
            viewModel.setConversationId(conversationId)
            // 高亮此处的历史记录
            (supportFragmentManager.findFragmentById(R.id.history_fragment_container) as?
                            HistoryFragment)
                    ?.updateCurrentConversation(conversationId)
            // 取消其它选项的高亮
            sidebarManager.updateSelection(null)

            Toast.makeText(this@ChatActivity, "假数据对话已生成", Toast.LENGTH_SHORT).show()

            // 滚动到底部，看到最新一轮
            val size = viewModel.messages.value?.size ?: 0
            if (size > 0) {
                binding.chatMessagesRecyclerview.scrollToPosition(size - 1)
            }
        }
    }

    // ========== Activity 生命周期 ==========

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize ViewModels
        viewModel = ViewModelProvider(this)[ChatViewModel::class.java]

        // 初始化公共UI组件（侧边栏、输入框）
        setupCommonUI()

        // 初始化语音合成器
        speechSynthesizer = XunfeiSpeechSynthesizer(this)
        speechSynthesizer.init()

        setupWindowInsetsForChat()
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

        // 处理初始问题（在搜索状态设置之后）
        val initialQuestion = intent.getStringExtra(EXTRA_INITIAL_QUESTION)
        initialQuestion?.takeIf { it.isNotBlank() }?.let { question ->
            if (savedInstanceState == null && (viewModel.messages.value?.isEmpty() == true)) {
                viewModel.sendMessage(question)
            }
        }

        // 处理初始语音模式状态
        val isInitialVoiceMode = intent.getBooleanExtra("is_voice_mode", false)
        val isKeyboardMode = !isInitialVoiceMode // 默认为键盘模式
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

    override fun onResume() {
        super.onResume()
        // 基类已处理 InputBar 状态同步
    }

    override fun onDestroy() {
        super.onDestroy()
        // 清理语音合成器资源
        speechSynthesizer.destroy()

        // 清理 RecyclerView 监听器，避免内存泄漏
        binding.chatMessagesRecyclerview.clearOnScrollListeners()
    }

    // ========== 初始化方法 ==========

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
        }

        // 观察OCR解析进度
        viewModel.ocrProgress.observe(this) { progress -> handleOCRProgress(progress) }

        // 观察聊天状态事件（UI 文案由 View 层控制）
        viewModel.chatStatus.observe(this) { status -> handleChatStatus(status) }

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
            android.util.Log.d(
                    "ChatActivity",
                    "准备发送消息 - 文本长度: ${text.length}, 图片数: ${imageUris.size}, 文件数: ${fileUris.size}"
            )

            // 如果没有输入文本也没有选中附件，提示用户
            if (text.isEmpty() && imageUris.isEmpty() && fileUris.isEmpty()) {
                android.util.Log.w("ChatActivity", "发送失败：没有内容")
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
                android.util.Log.d("ChatActivity", "发送纯文本消息")
                viewModel.sendMessage(text)
            } else {
                android.util.Log.d(
                        "ChatActivity",
                        "发送带附件的消息 - 图片: ${imageUris.size}, 文件: ${fileUris.size}"
                )
                imageUris.forEachIndexed { index, uri ->
                    android.util.Log.d("ChatActivity", "图片 $index: $uri")
                }
                fileUris.forEachIndexed { index, uri ->
                    android.util.Log.d("ChatActivity", "文件 $index: $uri")
                }

                if (imageUris.isNotEmpty()) {
                    android.util.Log.d("ChatActivity", "显示OCR进度对话框")
                    showOCRProgressDialog()
                }
                viewModel.sendMessageWithAttachments(text, imageUris, fileUris)

                // 没有图片时无需等待OCR回调，可直接清空预览
                if (imageUris.isEmpty()) {
                    android.util.Log.d("ChatActivity", "无图片，直接清空附件预览")
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
                android.util.Log.e("ChatActivity", "OCR错误: ${progress.message}")
                Toast.makeText(this, "错误: ${progress.message}", Toast.LENGTH_LONG).show()
                // 错误时不清空附件，让用户可以重试
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

    /** 处理聊天状态事件（UI 文案由 View 层控制） */
    private fun handleChatStatus(status: ChatStatusEvent) {
        val messages = viewModel.messages.value ?: return

        when (status) {
            is ChatStatusEvent.WebSearching -> {
                // 显示搜索状态文案（从资源文件获取）
                if (status.position < messages.size) {
                    val searchingText = getString(R.string.chat_searching_web)
                    messages[status.position] =
                            ChatMessage(
                                    content = searchingText,
                                    isUser = false,
                                    isComplete = false,
                                    timestamp = status.timestamp
                            )
                    chatMessageAdapter.notifyItemChanged(status.position)
                }
            }
            is ChatStatusEvent.SearchComplete -> {
                // 搜索完成，清空占位文字，准备流式输出
                if (status.position < messages.size) {
                    messages[status.position] =
                            ChatMessage(
                                    content = "",
                                    isUser = false,
                                    isComplete = false,
                                    timestamp = status.timestamp
                            )
                    chatMessageAdapter.notifyItemChanged(status.position)
                }
            }
            is ChatStatusEvent.Idle -> {
                // 空闲状态，无需处理
            }
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

    // 重新加载：删除当前 AI 回复，重新生成（不添加新的用户消息）
    private fun reloadAnswer(message: ChatMessage) {
        val list = chatMessageAdapter.messages
        val index = list.indexOf(message)
        if (index <= 0) return

        // 先删除当前的 AI 回复（只删除 AI 回复，保留用户问题）
        list.removeAt(index)
        chatMessageAdapter.notifyItemRemoved(index)

        // 从数据库删除这条 AI 消息
        viewModel.deleteMessagePair(listOf(message.timestamp))

        // 重新生成回复（不会添加新的用户消息）
        viewModel.regenerateAnswer()
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

    // ========== 语音播放 ==========

    /** 播放 AI 消息内容 */
    private fun speakMessage(message: ChatMessage) {
        if (message.content.isBlank()) {
            Toast.makeText(this, "没有可播放的内容", Toast.LENGTH_SHORT).show()
            return
        }

        // 如果正在播放，则停止
        if (speechSynthesizer.isSpeaking()) {
            speechSynthesizer.stop()
            Toast.makeText(this, "已停止播放", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "开始播放...", Toast.LENGTH_SHORT).show()
            speechSynthesizer.speak(message.content)
        }
    }
}
