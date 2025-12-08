package com.example.myapplication.ui


import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.R
import com.example.myapplication.adapter.ChatMessageAdapter
import com.example.myapplication.databinding.ActivityChatBinding
import com.example.myapplication.model.MediaType
import com.example.myapplication.model.ModelConfig
import com.example.myapplication.model.ModelRegistry
import com.example.myapplication.model.SelectedMedia
import com.example.myapplication.viewmodel.ChatViewModel
import com.example.myapplication.viewmodel.ChatViewModel.OCRProgress
import com.example.myapplication.viewmodel.DialogueViewModel
import com.example.myapplication.viewmodel.HistoryViewModel
import com.example.myapplication.viewmodel.MessageUpdateEvent
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.noties.markwon.Markwon
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.image.ImagesPlugin
import io.noties.markwon.linkify.LinkifyPlugin
import kotlin.math.abs
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.model.ChatMessage
import com.google.android.datatransport.BuildConfig
import com.example.myapplication.ui.widget.ChatInputView
import kotlinx.coroutines.launch

class ChatActivity : BaseAttachmentActivity(), MessageActionsBottomSheet.Listener, HistoryFragment.Listener{

    companion object {
        const val EXTRA_CONVERSATION_ID = "extra_conversation_id"
        const val EXTRA_INITIAL_QUESTION = "extra_initial_question"
        const val REQUEST_RECORD_AUDIO_PERMISSION = 1
    }

    private var isKeyboardMode = true // 默认为键盘模式
    private var isGeneratingResponse = false
    private var ocrProgressDialog: AlertDialog? = null
    private var previousMessageCount = 0

    private var shouldAutoScroll = true
    private var isUserDragging = false
    private lateinit var chatMessageAdapter: ChatMessageAdapter
    private lateinit var viewModel: ChatViewModel
    private lateinit var historyViewModel: HistoryViewModel
    private lateinit var dialogueViewModel: DialogueViewModel
    private lateinit var binding: ActivityChatBinding
    
    // Markwon 实例 - Activity 级别单例，注入到 Adapter
    // 暂时不使用代码高亮以提升性能，编译成功后可以添加
    private val markwon: Markwon by lazy {
        Markwon.builder(this)
                .usePlugin(HtmlPlugin.create())
                .usePlugin(ImagesPlugin.create())
                .usePlugin(LinkifyPlugin.create()) // 自动识别 URL
                .usePlugin(TablePlugin.create(this))
                .usePlugin(StrikethroughPlugin.create())
                .usePlugin(TaskListPlugin.create(this))
                .build()
    }

    override fun getChatInputView(): ChatInputView {
        return binding.chatInputView
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize ViewModels
        viewModel = ViewModelProvider(this)[ChatViewModel::class.java]
        historyViewModel = ViewModelProvider(this)[HistoryViewModel::class.java]
        dialogueViewModel = ViewModelProvider(this)[DialogueViewModel::class.java]

        // 初始化 RecyclerView 用于预览（继承自基类）
        rvPreview = binding.chatInputView.getPreviewRecyclerView()
        setupPreviewAdapter()
        setupWindowInsets()
        setupClickListeners()
        setupChatRecyclerView()
        setupHistoryRecyclerView()
        observeViewModel()

        // 初始化科大讯飞语音识别
        initXunfeiSpeechRecognizer()

        // 设置联网搜索监听器（继承自基类）
        setupWebSearchListener()

        // 观察历史对话数据ID并设置到ViewModel
        val conversationId = intent.getStringExtra(EXTRA_CONVERSATION_ID) ?: ""
        if (conversationId.isNotEmpty()) {
            viewModel.setConversationId(conversationId)
            (supportFragmentManager.findFragmentById(R.id.history_fragment_container) as? HistoryFragment)
                ?.updateCurrentConversation(conversationId)
        }
        
        // 处理联网搜索开关状态（必须在发送消息之前设置）
        val isWebSearchEnabled = intent.getBooleanExtra("is_web_search_enabled", false)
        if (isWebSearchEnabled) {
            setNetworkSearchEnabled(true) // 使用基类方法
        }

        // 处理初始问题与附件（需在搜索状态设置之后）
        val initialQuestion = intent.getStringExtra(EXTRA_INITIAL_QUESTION)
        val imageUriStrings = intent.getStringArrayListExtra("image_uris")
        val fileUriStrings = intent.getStringArrayListExtra("file_uris")
        val hasInitialAttachments =
            !imageUriStrings.isNullOrEmpty() || !fileUriStrings.isNullOrEmpty()
        val shouldAutoSendInitialPayload =
            savedInstanceState == null && (viewModel.messages.value?.isEmpty() == true)

        if (shouldAutoSendInitialPayload &&
            (!initialQuestion.isNullOrBlank() || hasInitialAttachments)) {
            autoSendInitialPayload(
                initialQuestion,
                imageUriStrings?.toList() ?: emptyList(),
                fileUriStrings?.toList() ?: emptyList()
            )
        } else if (hasInitialAttachments) {
            val attachments = mutableListOf<SelectedMedia>()
            imageUriStrings?.forEach { uriString ->
                attachments.add(SelectedMedia(android.net.Uri.parse(uriString), MediaType.IMAGE))
            }
            fileUriStrings?.forEach { uriString ->
                attachments.add(SelectedMedia(android.net.Uri.parse(uriString), MediaType.FILE))
            }
            if (attachments.isNotEmpty()) {
                addMediaItems(attachments)
            }
        }

        // 处理初始语音模式状态
        val isInitialVoiceMode = intent.getBooleanExtra("is_voice_mode", false)
        isKeyboardMode = !isInitialVoiceMode // 默认为键盘模式
        
        binding.chatInputView.setVoiceMode(!isKeyboardMode)

        // 假数据开关（防止每次都重复造）
        if (BuildConfig.DEBUG) {
            // 确保先有 conversationId
            if (viewModel.currentConversationId.isEmpty()) {
                // 给一个新的空对话
                lifecycleScope.launch {
                    val id = viewModel.currentConversationId
                    // 如果你想指定某个对话，也可以手动传
                    viewModel.debugSeedFakeConversation(rounds = 150)
                }
            } else {
                viewModel.debugSeedFakeConversation(rounds = 150)
            }
        }
    }

    /** 设置点击监听器 */
    private fun setupClickListeners() {
        // 菜单按钮
        binding.ivMenu.setOnClickListener {
            binding.chatDrawerLayout.openDrawer(GravityCompat.END)
            updateSidebarSelection(isNewChat = false)
        }

        // 新对话按钮 (主界面图标)
        binding.icNewChat.setOnClickListener { startNewChat() }

        // 菜单-搜索按钮 (侧边栏)
                    binding.includeSidebar.btnSidebarSearch.setOnClickListener {
            binding.chatDrawerLayout.closeDrawers()
            startActivity(Intent(this, SearchActivity::class.java))
        }

        // 菜单-新对话按钮 (侧边栏)
                    binding.includeSidebar.btnNewChat.setOnClickListener {
            binding.chatDrawerLayout.closeDrawers()
            startNewChat()
        }

        // 菜单-知识库按钮
                    binding.includeSidebar.btnKnowledgeBase.setOnClickListener {
            binding.chatDrawerLayout.closeDrawers()
            Toast.makeText(this, "知识库", Toast.LENGTH_SHORT).show()
            updateSidebarSelection(isKnowledgeBase = true)

            val intent = Intent(this, DialogueActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            intent.putExtra(DialogueActivity.EXTRA_RESET_INPUT_MODE, true)
            startActivity(intent)
            finish()
        }
// 菜单-生成假数据按钮 (侧边栏，仅调试用)
        // 菜单 - 生成假数据按钮（侧边栏）
                    binding.includeSidebar.btnGenerateFakeData.setOnClickListener {
            binding.chatDrawerLayout.closeDrawers()
            Toast.makeText(this, "正在生成假数据对话…", Toast.LENGTH_SHORT).show()

            historyViewModel.generateFakeConversation { conversationId ->
                // 切换当前 ViewModel 到这条长对话
                viewModel.setConversationId(conversationId)
                (supportFragmentManager.findFragmentById(R.id.history_fragment_container) as? HistoryFragment)
                    ?.updateCurrentConversation(conversationId)
                // 更新历史列表选中状态
                // 历史列表由 Fragment 管理，选中状态由 Fragment 维护
                updateSidebarSelection(isNewChat = false, isKnowledgeBase = false)

                Toast.makeText(this, "假数据对话已生成", Toast.LENGTH_SHORT).show()

                // 滚动到底部，看到最新一轮
                val size = viewModel.messages.value?.size ?: 0
                if (size > 0) {
                    binding.chatMessagesRecyclerview.scrollToPosition(size - 1)
                }
            }
        }


        binding.chatInputView.onMoreClickListener = { showAttachmentOptions() }
        binding.chatInputView.onVoiceModeChangeListener = { isVoiceMode ->
            isKeyboardMode = !isVoiceMode
        }

        // 设置语音输入的触摸监听
        setupVoiceInputListener()

        binding.chatInputView.onModelSwitchClickListener = { showModelSelectorDialog() }

        // 联网搜索已通过 setupWebSearchListener() 设置

        binding.chatInputView.onSendClickListener = { content -> sendMessage(content) }

        binding.chatInputView.onStopClickListener = {
            viewModel.stopGeneration()
            Toast.makeText(this, "正在停止生成...", Toast.LENGTH_SHORT).show()
        }
    }

    /** 设置聊天RecyclerView */
    private fun setupChatRecyclerView() {
        binding.chatMessagesRecyclerview.apply {
            val linearLayoutManager = LinearLayoutManager(this@ChatActivity).apply {
                // 列表从底部开始堆叠，最后一条自然贴着底部 / 键盘
                stackFromEnd = false
            }
            layoutManager = linearLayoutManager

            val initialList = viewModel.messages.value ?: mutableListOf()

            adapter = ChatMessageAdapter(
                messages = initialList,
                markwon = markwon,
                onAiMessageLongClick = { message, anchorView, rawX, rawY ->
                    // 长按 AI 消息，弹出对话框
                    showMessageActionsDialog(message, anchorView, rawX, rawY)
                },
                onShareClick = { message ->
                    shareText(message.content)
                },
                onLikeClick = { message ->
                    updateLikeState(message, isLike = true)
                },
                onDislikeClick = { message ->
                    updateLikeState(message, isLike = false)
                },
                onReloadClick = { message ->
                    reloadAnswer(message)
                }
            ).also { chatMessageAdapter = it }

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
            addOnScrollListener(object : RecyclerView.OnScrollListener() {

                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
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
                            val lm = recyclerView.layoutManager as? LinearLayoutManager ?: return
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
            })
        }




        // 设置下拉刷新监听器
        binding.swipeRefreshLayout.setOnRefreshListener { viewModel.loadMoreHistory() }

        // 设置历史对话列表
        setupHistoryRecyclerView()
        
        // 历史列表改为Fragment托管
        
        // 观察AI生成状态，控制停止按钮的显示
        viewModel.isGenerating.observe(this) { isGenerating ->
            binding.chatInputView.setGenerating(isGenerating)
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
                currentCount > 0 && previousMessageCount > 0 && 
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

                    // 只有当前处于“自动跟随”状态，才让新消息把列表带到底部
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
                    // Toast.makeText(this, "已加载 ${event.count} 条历史消息", Toast.LENGTH_SHORT).show()
                }

                is MessageUpdateEvent.NoMoreHistory -> {
                    binding.swipeRefreshLayout.isRefreshing = false
                    Toast.makeText(this, "没有更多历史消息了", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun autoSendInitialPayload(
        initialQuestion: String?,
        imageUriStrings: List<String>,
        fileUriStrings: List<String>
    ) {
        val question = initialQuestion?.trim().orEmpty()
        val hasQuestion = question.isNotEmpty()
        val imageUris = imageUriStrings.map { android.net.Uri.parse(it) }
        val fileUris = fileUriStrings.map { android.net.Uri.parse(it) }

        if (!hasQuestion && imageUris.isEmpty() && fileUris.isEmpty()) {
            return
        }

        if (imageUris.isEmpty() && fileUris.isEmpty()) {
            viewModel.sendMessage(question)
        } else {
            if (imageUris.isNotEmpty()) {
                showOCRProgressDialog()
            }
            viewModel.sendMessageWithAttachments(question, imageUris, fileUris)
        }
    }

    override fun onResume() {
        super.onResume()
        // 每次恢复时重新加载历史列表，确保数据同步
        historyViewModel.loadHistory()
    }

    private fun startNewChat() {
        val intent = Intent(this, DialogueActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        intent.putExtra(DialogueActivity.EXTRA_RESET_INPUT_MODE, true)
        startActivity(intent)
        finish()
    }

    private fun observeViewModel() {
        // This function wraps all the observe calls that are currently in setupChatRecyclerView
        // The actual observation code is already in place in setupChatRecyclerView
    }

    private fun setupWindowInsets() {
        // 确保DrawerLayout不会被状态栏遮挡
        ViewCompat.setOnApplyWindowInsetsListener(binding.chatDrawerLayout) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            insets
        }

        // 确保输入布局在键盘显示时保持可见
        ViewCompat.setOnApplyWindowInsetsListener(binding.chatInputView) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
            val bottomPadding = maxOf(imeInsets.bottom, systemBars.bottom)
            val params = v.layoutParams as android.view.ViewGroup.MarginLayoutParams
            params.bottomMargin = bottomPadding
            v.layoutParams = params
            
            insets
        }
        
        // 适配侧边栏
         ViewCompat.setOnApplyWindowInsetsListener(binding.navDrawerLayout) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, systemBars.top + 24, v.paddingRight, v.paddingBottom)
            insets
        }
    }



    // 模型选择对话框已在基类实现，直接使用 binding.ivMoreIcon.setOnClickListener { showModelSelectorDialog() }
    // 联网搜索功能已在基类实现，通过 setupWebSearchListener() 设置点击监听

    private fun setupHistoryRecyclerView() {
        // 将历史列表交由 Fragment 管理
        val currentConversationId = intent.getStringExtra(EXTRA_CONVERSATION_ID)
        val fragment = HistoryFragment.newInstance(currentConversationId)
        supportFragmentManager.beginTransaction()
            .replace(R.id.history_fragment_container, fragment)
            .commit()
    }
    
    private fun updateSidebarSelection(
            isNewChat: Boolean = false,
            isKnowledgeBase: Boolean = false
    ) {
        val highlightColor = android.graphics.Color.parseColor("#E3F2FD")
        val normalColor = android.graphics.Color.parseColor("#F0F0F0")
        
        binding.includeSidebar.btnNewChat.setBackgroundColor(
                if (isNewChat) highlightColor else normalColor
        )
        binding.includeSidebar.btnKnowledgeBase.setBackgroundColor(
                if (isKnowledgeBase) highlightColor else normalColor
        )
        
        if (isNewChat || isKnowledgeBase) {
            // 历史列表由 Fragment 管理，不在此重置选中状态
        }
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

        MessageActionsBottomSheet
            .newInstance(message, rawX, anchorBottomY, this)
            .show(supportFragmentManager, "message_actions")
    }

    private fun sendMessage(content: String? = null) {
        try {
            val message = content ?: binding.chatInputView.getInputText()

            // 如果没有输入文本也没有选中附件，提示用户
            if (message.isEmpty() && selectedItems.isEmpty()) {
                Toast.makeText(this, "请输入消息或选择附件", Toast.LENGTH_SHORT).show()
                return
            }

            // 清空输入框
            binding.chatInputView.clearInput()

            val imageUris = selectedItems.filter { it.type == MediaType.IMAGE }.map { it.uri }
            val fileUris = selectedItems.filter { it.type == MediaType.FILE }.map { it.uri }
            // ✅ 用户主动发出问题：无论当前在不在底部，都恢复自动跟随，并准备滚到底部
            shouldAutoScroll = true
            isUserDragging = false

            if (imageUris.isEmpty() && fileUris.isEmpty()) {
                // 只有文本，直接发送
                viewModel.sendMessage(message)
            } else {
                if (imageUris.isNotEmpty()) {
                    showOCRProgressDialog()
                }
                viewModel.sendMessageWithAttachments(message, imageUris, fileUris)

                // 没有图片时无需等待OCR回调，可直接清空预览
                if (imageUris.isEmpty()) {
                    clearSelectedMedia()
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("ChatActivity", "发送消息时发生错误", e)
            Toast.makeText(this, "发送失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // 图片/文件选择、附件管理等方法已移至基类 BaseAttachmentActivity

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
                clearSelectedMedia()
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



    // 语音识别相关方法已移至基类 BaseAttachmentActivity

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

    // ========== 基类抽象方法实现 ==========
    
    override fun onVoiceRecognitionResult(text: String) {
        // 自动切换到键盘模式，这样可以在输入框看到识别内容
        if (!isKeyboardMode) {
            isKeyboardMode = true
            binding.chatInputView.setVoiceMode(false)
        }
        // 将识别结果填入输入框
        binding.chatInputView.setInputText(text)
    }
    
    override fun onRecordAudioPermissionNeeded() {
        requestRecordAudioPermission(REQUEST_RECORD_AUDIO_PERMISSION)
    }

    override fun getCurrentModelId(): String {
        return viewModel.currentModel.value?.id ?: ModelRegistry.DEFAULT_MODEL.id
    }

    override fun onModelSwitch(modelConfig: ModelConfig) {
        viewModel.switchModel(modelConfig)
    }

    override fun onWebSearchToggle(isEnabled: Boolean) {
        // 同步状态到 ViewModel
        viewModel.toggleSearch(isEnabled)
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
        val newMsg = if (isLike) {
            old.copy(
                isLiked = !old.isLiked,
                isDisliked = false
            )
        } else {
            old.copy(
                isLiked = false,
                isDisliked = !old.isDisliked
            )
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

        // 只允许删除 AI 消息，如果你也支持删用户消息可以放开这个判断
        // if (message.isUser) return

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
        positionsToRemove
            .distinct()
            .sortedDescending()
            .forEach { pos ->
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
        (supportFragmentManager.findFragmentById(R.id.history_fragment_container) as? HistoryFragment)
            ?.updateCurrentConversation(conversationId)
        updateSidebarSelection(isNewChat = false, isKnowledgeBase = false)
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
        SelectTextDialogFragment
            .newInstance(message.content)
            .show(supportFragmentManager, "select_text")
    }

    override fun onTranslate(message: ChatMessage) {
        val text = "请翻译下文：\n${message.content}"
        viewModel.sendMessage(text)
    }
    // 分享文本
    private fun shareText(text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
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
}
