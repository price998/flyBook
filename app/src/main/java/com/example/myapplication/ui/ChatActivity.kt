package com.example.myapplication.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.speech.RecognizerIntent
import android.text.Editable
import android.text.TextWatcher
import android.view.MotionEvent
import android.view.View
import android.widget.PopupWindow
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.R
import com.example.myapplication.adapter.ChatMessageAdapter
import com.example.myapplication.adapter.HistoryAdapter
import com.example.myapplication.adapter.ModelAdapter
import com.example.myapplication.adapter.PreviewAdapter
import com.example.myapplication.databinding.ActivityChatBinding
import com.example.myapplication.databinding.DialogAttachmentOptionsBinding
import com.example.myapplication.databinding.DialogModelSelectorBinding
import com.example.myapplication.databinding.ItemDialogMenuBinding
import com.example.myapplication.model.MediaType
import com.example.myapplication.model.ModelRegistry
import com.example.myapplication.model.SelectedMedia
import com.example.myapplication.utils.DialogHelper
import com.example.myapplication.utils.XunfeiSpeechRecognizer
import com.example.myapplication.viewmodel.ChatViewModel
import com.example.myapplication.viewmodel.ChatViewModel.OCRProgress
import com.example.myapplication.viewmodel.DialogueViewModel
import com.example.myapplication.viewmodel.HistoryViewModel
import com.example.myapplication.viewmodel.MessageUpdateEvent
import com.google.android.material.bottomsheet.BottomSheetDialog
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
import kotlinx.coroutines.launch

class ChatActivity : AppCompatActivity() ,MessageActionsBottomSheet.Listener{

    companion object {
        const val EXTRA_CONVERSATION_ID = "extra_conversation_id"
        const val EXTRA_INITIAL_QUESTION = "extra_initial_question"
        const val REQUEST_RECORD_AUDIO_PERMISSION = 1
    }

    private var isKeyboardMode = true // 默认为键盘模式
    private var isGeneratingResponse = false
    private var ocrProgressDialog: AlertDialog? = null
    private var isNetworkSearchEnabled = false
    private var previousMessageCount = 0

    // 语音输入相关
    private var initialY = 0f // 记录按下时的Y坐标
    private var isCancelled = false // 是否取消录音

    private val selectedItems = mutableListOf<SelectedMedia>()
    private lateinit var adapter: PreviewAdapter
    private lateinit var rvPreview: RecyclerView

    // 语音识别启动器（使用Google语音识别作为备用方案）
    private val voiceRecognitionLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK) {
                    val matches =
                            result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                    if (!matches.isNullOrEmpty()) {
                        val recognizedText = matches[0]
                        // 语音模式下直接发送
                        if (!isKeyboardMode) {
                            viewModel.sendMessage(recognizedText)
                        }
                    }
                }
            }

    private var shouldAutoScroll = true
    private var isUserDragging = false
    private lateinit var chatMessageAdapter: ChatMessageAdapter
    private lateinit var viewModel: ChatViewModel
    private lateinit var historyViewModel: HistoryViewModel
    private lateinit var dialogueViewModel: DialogueViewModel
    private lateinit var binding: ActivityChatBinding
    private lateinit var historyAdapter: HistoryAdapter
    
    // 科大讯飞语音识别
    private var xunfeiRecognizer: XunfeiSpeechRecognizer? = null
    private var useXunfeiRecognizer = true // 优先使用科大讯飞，失败时回退到Google

    // Model selector dialog
    private val models = ModelRegistry.ALL_MODELS
    private lateinit var dialog: BottomSheetDialog
    private lateinit var dialogBinding: DialogModelSelectorBinding
    
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize ViewModels
        viewModel = ViewModelProvider(this)[ChatViewModel::class.java]
        historyViewModel = ViewModelProvider(this)[HistoryViewModel::class.java]
        dialogueViewModel = ViewModelProvider(this)[DialogueViewModel::class.java]

        updateSendButtonVisibility()

        // 初始化 RecyclerView 用于预览
        rvPreview = binding.rvPreview
        setupPreviewAdapter()
        setupWindowInsets()
        setupClickListeners()
        setupChatRecyclerView()
        setupHistoryRecyclerView()
        observeViewModel()

        // 初始化科大讯飞语音识别
        initXunfeiSpeechRecognizer()

        // 观察历史对话数据ID并设置到ViewModel
        val conversationId = intent.getStringExtra(EXTRA_CONVERSATION_ID) ?: ""
        if (conversationId.isNotEmpty()) {
            viewModel.setConversationId(conversationId)
        }
        
        // 处理联网搜索开关状态（必须在发送消息之前设置）
        val isWebSearchEnabled = intent.getBooleanExtra("is_web_search_enabled", false)
        if (isWebSearchEnabled) {
            isNetworkSearchEnabled = true
            binding.layoutWebSearch.isSelected = true
            // 重要：同步状态到 ViewModel
            viewModel.toggleSearch(true)
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
        isKeyboardMode = !isInitialVoiceMode // 默认为键盘模式

        // 根据初始状态设置 UI
        updateInputModeUI()

        // 处理从DialogueActivity传递过来的附件
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
                addMediaItems(attachments)
            }
        }
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
        binding.btnSidebarSearch.setOnClickListener {
            binding.chatDrawerLayout.closeDrawers()
            startActivity(Intent(this, SearchActivity::class.java))
        }

        // 菜单-新对话按钮 (侧边栏)
        binding.btnNewChat.setOnClickListener {
            binding.chatDrawerLayout.closeDrawers()
            startNewChat()
        }

        // 菜单-知识库按钮
        binding.btnKnowledgeBase.setOnClickListener {
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
        binding.btnGenerateFakeData.setOnClickListener {
            binding.chatDrawerLayout.closeDrawers()
            Toast.makeText(this, "正在生成假数据对话…", Toast.LENGTH_SHORT).show()

            historyViewModel.generateFakeConversation { conversationId ->
                // 切换当前 ViewModel 到这条长对话
                viewModel.setConversationId(conversationId)
                // 更新历史列表选中状态
                historyAdapter.setSelectedId(conversationId)
                updateSidebarSelection(isNewChat = false, isKnowledgeBase = false)

                Toast.makeText(this, "假数据对话已生成", Toast.LENGTH_SHORT).show()

                // 滚动到底部，看到最新一轮
                val size = viewModel.messages.value?.size ?: 0
                if (size > 0) {
                    binding.chatMessagesRecyclerview.scrollToPosition(size - 1)
                }
            }
        }


        binding.ivMore.setOnClickListener { showAttachmentOptions() }
        binding.ivMic.setOnClickListener { toggleInputMode() }

        // 设置语音输入的触摸监听
        setupVoiceInputListener()

        binding.ivMoreIcon.setOnClickListener { showModelSelectorDialog() }

        binding.layoutWebSearch.setOnClickListener { toggleNetworkSearchBackground() }

        binding.ivSend.setOnClickListener { sendMessage() }

        binding.ivStop.setOnClickListener {
            viewModel.stopGeneration()
            Toast.makeText(this, "正在停止生成...", Toast.LENGTH_SHORT).show()
        }

        setupInputListener()
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
        
        // 观察历史对话数据
        historyViewModel.historyList.observe(this) { history ->
            historyAdapter.updateData(history)
        }
        
        // 观察AI生成状态，控制停止按钮的显示
        viewModel.isGenerating.observe(this) { isGenerating ->
            binding.ivStop.visibility = if (isGenerating) View.VISIBLE else View.GONE
            binding.ivSend.visibility = if (isGenerating) View.GONE else View.VISIBLE
            isGeneratingResponse = isGenerating
            updateSendButtonVisibility()
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
                    Toast.makeText(this, "已加载 ${event.count} 条历史消息", Toast.LENGTH_SHORT).show()
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
        ViewCompat.setOnApplyWindowInsetsListener(binding.layoutInput) { v, insets ->
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

    private fun toggleInputMode() {
        isKeyboardMode = !isKeyboardMode
        updateInputModeUI()
    }

    private fun updateInputModeUI() {
        if (isKeyboardMode) {
            binding.tvHoldToSpeak.visibility = View.GONE
            binding.etInput.visibility = View.VISIBLE
            binding.ivMic.setImageResource(R.drawable.ic_mic)
        } else {
            binding.tvHoldToSpeak.visibility = View.VISIBLE
            binding.etInput.visibility = View.GONE
            binding.ivMic.setImageResource(R.drawable.ic_keyboard)

            // 隐藏键盘
            val imm =
                    getSystemService(Context.INPUT_METHOD_SERVICE) as
                            android.view.inputmethod.InputMethodManager
            imm.hideSoftInputFromWindow(binding.etInput.windowToken, 0)
        }
    }

    private fun updateSendButtonVisibility() {
        binding.ivSend.visibility =
                if (!isGeneratingResponse && isKeyboardMode) View.VISIBLE else View.GONE
    }

    private fun clearSelectedMedia() {
        if (selectedItems.isEmpty()) return
        val itemCount = selectedItems.size
        selectedItems.clear()
        adapter.notifyItemRangeRemoved(0, itemCount)
        updatePreviewVisibility()
    }

    private fun showModelSelectorDialog() {
        // Initialize dialog and binding
        dialogBinding = DialogModelSelectorBinding.inflate(layoutInflater)
        dialog = BottomSheetDialog(this)
        dialog.setContentView(dialogBinding.root)
        
        val currentModelId = viewModel.currentModel.value?.id ?: ModelRegistry.DEFAULT_MODEL.id
        val adapter =
                ModelAdapter(models, currentModelId) { modelConfig ->
                    // 切换模型
                    viewModel.switchModel(modelConfig)
                    Toast.makeText(this, "已切换到: ${modelConfig.displayName}", Toast.LENGTH_SHORT)
                            .show()
                    dialog.dismiss()
                }

        dialogBinding.modelListRecyclerview.layoutManager = LinearLayoutManager(this)
        dialogBinding.modelListRecyclerview.adapter = adapter
        dialog.show()
    }

    private fun toggleNetworkSearchBackground() {
        isNetworkSearchEnabled = !isNetworkSearchEnabled
        binding.layoutWebSearch.isSelected = isNetworkSearchEnabled

        // 重要：通知 ViewModel 搜索状态变化
        viewModel.toggleSearch(isNetworkSearchEnabled)

        // 根据联网搜索状态更新发送按钮样式
        if (isNetworkSearchEnabled) {
            // 开启联网搜索时，清除发送按钮的蓝色背景
            binding.ivSend.background = null
            binding.ivSend.clearColorFilter()
            Toast.makeText(this, "联网搜索已开启", Toast.LENGTH_SHORT).show()
        } else {
            // 关闭联网搜索时，根据输入内容恢复发送按钮样式
                binding.ivSend.background = null
                binding.ivSend.clearColorFilter()
                Toast.makeText(this, "联网搜索已关闭", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupHistoryRecyclerView() {
        // 获取当前的conversationId
        val currentConversationId = intent.getStringExtra(EXTRA_CONVERSATION_ID)
        
        historyAdapter = HistoryAdapter(
            mutableListOf(),
            currentConversationId = currentConversationId,
            onItemClick = { history ->
                // Handle history item click - 切换到选中的对话
                binding.chatDrawerLayout.closeDrawers()

                // 设置新的对话ID，ViewModel会自动清空当前列表并加载新对话的消息
                viewModel.setConversationId(history.id)
                // 更新选中状态
                historyAdapter.setSelectedId(history.id)
                // 清除侧边栏按钮选中
                updateSidebarSelection(isNewChat = false, isKnowledgeBase = false)
            },
            onItemLongClick = { history ->
                // 长按显示菜单：置顶/取消置顶、重命名、删除
                val items = listOf(
                    mapOf("text" to if (history.isPinned) "取消置顶" else "置顶会话", "icon" to R.drawable.icon_pin),
                    mapOf("text" to "重命名会话标题", "icon" to R.drawable.icon_edit),
                    mapOf("text" to "删除会话", "icon" to R.drawable.icon_delete)
                )

                val adapter = object : android.widget.ArrayAdapter<Map<String, Any>>(
                    this,
                    R.layout.item_dialog_menu,
                    items
                ) {
                    override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                        val binding: ItemDialogMenuBinding
                        val view: View

                        if (convertView == null) {
                            binding = ItemDialogMenuBinding.inflate(layoutInflater, parent, false)
                            view = binding.root
                            view.tag = binding
                        } else {
                            view = convertView
                            binding = view.tag as ItemDialogMenuBinding
                        }

                        val item = getItem(position) ?: return view
                        val iconRes = item["icon"] as Int
                        val text = item["text"] as String

                        binding.ivMenuIcon.setImageResource(iconRes)
                        binding.tvMenuText.text = text

                        // 设置红色样式给删除项
                        if (text == "删除会话") {
                            binding.tvMenuText.setTextColor(android.graphics.Color.RED)
                            binding.ivMenuIcon.setColorFilter(android.graphics.Color.RED)
                        } else {
                            binding.tvMenuText.setTextColor(android.graphics.Color.BLACK)
                            binding.ivMenuIcon.setColorFilter(android.graphics.Color.BLACK)
                        }

                        return view
                    }
                }

        AlertDialog.Builder(this, R.style.RoundedDialogTheme)
                .setAdapter(adapter) { _, which ->
                    when (which) {
                        0 -> {
                            // 切换置顶状态
                            historyViewModel.togglePin(history.id, history.isPinned)
                        }
                        1 -> {
                            // 重命名
                            showRenameDialog(history.id, history.title)
                        }
                        2 -> {
                            // 确认删除
                            DialogHelper.showDeleteConfirmDialog(this) {
                                historyViewModel.deleteConversation(history.id)
                                // 如果删除的是当前会话，退出或清空
                                if (history.id == viewModel.currentConversationId) {
                                    finish()
                                }
                            }
                        }
                    }
                }
                .show()
    }
        )
        // 更新为新的RecyclerView ID
        binding.historyRecyclerview.apply {
            layoutManager = LinearLayoutManager(this@ChatActivity)
            adapter = historyAdapter
            // 添加分割线
            val divider = DividerItemDecoration(this@ChatActivity, DividerItemDecoration.VERTICAL)
            divider.setDrawable(ColorDrawable(android.graphics.Color.parseColor("#EEEEEE")))
            addItemDecoration(divider)
        }
    }
    
    private fun updateSidebarSelection(
            isNewChat: Boolean = false,
            isKnowledgeBase: Boolean = false
    ) {
        val highlightColor = android.graphics.Color.parseColor("#E3F2FD")
        binding.btnNewChat.setBackgroundColor(
                if (isNewChat) highlightColor else android.graphics.Color.TRANSPARENT
        )
        binding.btnKnowledgeBase.setBackgroundColor(
                if (isKnowledgeBase) highlightColor else android.graphics.Color.TRANSPARENT
        )
        
        if (isNewChat || isKnowledgeBase) {
            historyAdapter.setSelectedId(null)
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
    private fun showRenameDialog(conversationId: String, currentTitle: String) {
        DialogHelper.showRenameDialog(this, currentTitle) { newTitle ->
            historyViewModel.renameConversation(conversationId, newTitle)
        }
    }

    private fun sendMessage() {
        try {
            val message = binding.etInput.text.toString().trim()

            // 如果没有输入文本也没有选中附件，提示用户
            if (message.isEmpty() && selectedItems.isEmpty()) {
                Toast.makeText(this, "请输入消息或选择附件", Toast.LENGTH_SHORT).show()
                return
            }

            // 清空输入框
            binding.etInput.text.clear()

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

    /** 打开图片选择器 */
    private fun openImagePicker() {
        // 检查权限
        if (checkImagePermission()) {
            launchImagePicker()
        } else {
            requestImagePermission()
        }
    }

    /** 检查图片读取权限 */
    private fun checkImagePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ 使用新的权限
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) ==
                    PackageManager.PERMISSION_GRANTED
        } else {
            // Android 12 及以下
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) ==
                    PackageManager.PERMISSION_GRANTED
        }
    }

    /** 请求图片读取权限 */
    private fun requestImagePermission() {
        val permission =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Manifest.permission.READ_MEDIA_IMAGES
                } else {
                    Manifest.permission.READ_EXTERNAL_STORAGE
                }

        requestPermissionLauncher.launch(permission)
    }

    /** 权限请求结果处理 */
    private val requestPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
                if (isGranted) {
                    launchImagePicker()
                } else {
                    Toast.makeText(this, "需要读取图片权限才能选择图片", Toast.LENGTH_SHORT).show()
                }
            }

    /** 启动图片选择器 */
    private fun launchImagePicker() {
        pickMultipleMedia.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
        )
    }

    private val pickMultipleMedia =
            registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(9)) { uris ->
                if (uris.isNotEmpty()) {
                    val items = uris.map { SelectedMedia(it, MediaType.IMAGE) }
                    addMediaItems(items)
                }
            }

    private val pickFileLauncher =
            registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
                if (uris.isNotEmpty()) {
                    val items = uris.map { SelectedMedia(it, MediaType.FILE) }
                    addMediaItems(items)
                }
            }

    private fun launchFilePicker() {
        pickFileLauncher.launch(arrayOf("*/*"))
    }

    private fun showAttachmentOptions() {
        val view = layoutInflater.inflate(R.layout.dialog_attachment_options, binding.root, false)

        val width = (120 * resources.displayMetrics.density).toInt()

        val popupWindow =
                PopupWindow(
                        view,
                        width,
                        android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                        true
                )

        popupWindow.setBackgroundDrawable(
                ColorDrawable(android.graphics.Color.TRANSPARENT)
        )
        popupWindow.elevation = 10f

        val dialogBinding = DialogAttachmentOptionsBinding.bind(view)
        
        dialogBinding.tvOptionPhoto.setOnClickListener {
            popupWindow.dismiss()
            openImagePicker()
        }

        dialogBinding.tvOptionFile.setOnClickListener {
            popupWindow.dismiss()
            launchFilePicker()
        }

        // Measure view to calculate position
        view.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )

        val popupHeight = view.measuredHeight
        val anchor = binding.ivMore

        // Show above the button
        // y offset: negative of (anchor height + popup height + margin)
        val yOffset = -(anchor.height + popupHeight + 20)

        popupWindow.showAsDropDown(anchor, 0, yOffset)
    }

    private fun setupPreviewAdapter() {
        adapter =
                PreviewAdapter(selectedItems) { position ->
                    // 删除逻辑
                    selectedItems.removeAt(position)
                    adapter.notifyItemRemoved(position)
                    // 如果删光了，更新可见性
                    updatePreviewVisibility()
                }

        rvPreview.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        rvPreview.adapter = adapter
    }

    private fun addMediaItems(newItems: List<SelectedMedia>) {
        val startPos = selectedItems.size
        selectedItems.addAll(newItems)
        adapter.notifyItemRangeInserted(startPos, newItems.size)
        rvPreview.scrollToPosition(selectedItems.size - 1)
        updatePreviewVisibility()
    }

    // 控制 RecyclerView 的显示与隐藏
    private fun updatePreviewVisibility() {
        if (selectedItems.isEmpty()) {
            rvPreview.visibility = View.GONE
        } else {
            rvPreview.visibility = View.VISIBLE
        }
    }

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

    private fun setupInputListener() {
        binding.etInput.addTextChangedListener(
                object : TextWatcher {
                    override fun beforeTextChanged(
                            s: CharSequence?,
                            start: Int,
                            count: Int,
                            after: Int
                    ) {}

                    override fun onTextChanged(
                            s: CharSequence?,
                            start: Int,
                            before: Int,
                            count: Int
                    ) {}

                    override fun afterTextChanged(s: Editable?) {
                        val content = s.toString().trim()
                        // 只有在未开启联网搜索时，才根据输入内容改变发送按钮颜色

                                binding.ivSend.background = null
                                binding.ivSend.clearColorFilter()


                    }
                }
        )
    }

    /** 设置语音输入的触摸监听器 */
    @android.annotation.SuppressLint("ClickableViewAccessibility")
    private fun setupVoiceInputListener() {
        // 使用长按交互：按下开始录音，松开停止，上滑取消
        binding.tvHoldToSpeak.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // 按下时开始录音
                    initialY = event.rawY
                    isCancelled = false

                    if (checkRecordAudioPermission()) {
                        // 如果使用科大讯飞，直接开始录音
                        if (useXunfeiRecognizer && xunfeiRecognizer != null) {
                            try {
                                xunfeiRecognizer?.startListening()
                                binding.tvHoldToSpeak.text = "松开发送，上滑取消"
                                binding.tvHoldToSpeak.setBackgroundColor(
                                        android.graphics.Color.parseColor("#E3F2FD")
                                )
                            } catch (e: Exception) {
                                android.util.Log.e("ChatActivity", "科大讯飞启动失败", e)
                                useXunfeiRecognizer = false
                                startVoiceRecognition() // 回退到Google
                            }
                        } else {
                            // Google 识别使用点击模式
                            startVoiceRecognition()
                        }
                    } else {
                        requestRecordAudioPermission()
                    }
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    // 检测上滑手势
                    if (useXunfeiRecognizer && xunfeiRecognizer != null) {
                        val deltaY = initialY - event.rawY
                        if (deltaY > 150f) {
                            // 上滑超过阈值，显示取消提示
                            isCancelled = true
                            binding.tvHoldToSpeak.text = "松开手指，取消发送"
                            binding.tvHoldToSpeak.setBackgroundColor(
                                    android.graphics.Color.parseColor("#FFCDD2")
                            )
                        } else {
                            // 未超过阈值，显示正常提示
                            isCancelled = false
                            binding.tvHoldToSpeak.text = "松开发送，上滑取消"
                            binding.tvHoldToSpeak.setBackgroundColor(
                                    android.graphics.Color.parseColor("#E3F2FD")
                            )
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    // 松开时停止录音
                    if (useXunfeiRecognizer && xunfeiRecognizer != null) {
                        if (isCancelled) {
                            // 取消录音
                            xunfeiRecognizer?.cancel()
                            Toast.makeText(this, "已取消录音", Toast.LENGTH_SHORT).show()
                        } else {
                            // 正常停止
                            xunfeiRecognizer?.stopListening()
                        }
                        binding.tvHoldToSpeak.text = "按住说话"
                        binding.tvHoldToSpeak.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    }
                    isCancelled = false
                    true
                }
                else -> false
            }
        }
    }

    /** 检查录音权限 */
    private fun checkRecordAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
    }

    /** 请求录音权限 */
    private fun requestRecordAudioPermission() {
        requestPermissions(
                arrayOf(Manifest.permission.RECORD_AUDIO),
                REQUEST_RECORD_AUDIO_PERMISSION
        )
    }

    /** 初始化科大讯飞语音识别 */
    private fun initXunfeiSpeechRecognizer() {
        try {
            xunfeiRecognizer =
                    XunfeiSpeechRecognizer(this).apply {
                        init()

                        // 设置识别结果监听
                        setOnResultListener { text ->
                            if (text.isNotEmpty() && !isCancelled) {
                                // 确保在主线程执行
                                runOnUiThread {
                                    android.util.Log.d("ChatActivity", "识别结果：$text")

                                    // 自动切换到键盘模式，这样可以在输入框看到识别内容
                                    if (!isKeyboardMode) {
                                        isKeyboardMode = true
                                        updateInputModeUI()
                                    }

                                    // 将识别结果填入输入框
                                    binding.etInput.setText(text)
                                    binding.etInput.setSelection(text.length)

                                    Toast.makeText(this@ChatActivity, "识别成功", Toast.LENGTH_SHORT)
                                            .show()
                                }
                            }
                        }

                        // 设置错误监听
                        setOnErrorListener { error ->
                            Toast.makeText(this@ChatActivity, error, Toast.LENGTH_SHORT).show()
                            // 如果科大讯飞失败，回退到Google语音识别
                            useXunfeiRecognizer = false
                        }
                    }
            android.util.Log.d("ChatActivity", "科大讯飞语音识别初始化成功")
        } catch (e: Exception) {
            android.util.Log.e("ChatActivity", "科大讯飞语音识别初始化失败", e)
            useXunfeiRecognizer = false
            Toast.makeText(this, "离线语音识别初始化失败，将使用在线识别", Toast.LENGTH_SHORT).show()
        }
    }

    /** 启动语音识别 */
    private fun startVoiceRecognition() {
        // 优先使用科大讯飞离线识别
        if (useXunfeiRecognizer && xunfeiRecognizer != null) {
            try {
                xunfeiRecognizer?.startListening()
                return
            } catch (e: Exception) {
                android.util.Log.e("ChatActivity", "科大讯飞语音识别启动失败", e)
                useXunfeiRecognizer = false
                Toast.makeText(this, "离线识别失败，切换到在线识别", Toast.LENGTH_SHORT).show()
            }
        }

        // 回退到Google语音识别
        val intent =
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(
                            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                    )
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN") // 中文识别
                    putExtra(RecognizerIntent.EXTRA_PROMPT, "请说话...")
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                }

        try {
            voiceRecognitionLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "您的设备不支持语音识别，请安装 Google 应用", Toast.LENGTH_LONG).show()
        }
    }

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

    override fun onDestroy() {
        super.onDestroy()
        // 释放科大讯飞语音识别资源
        xunfeiRecognizer?.destroy()
        xunfeiRecognizer = null
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
    // 平滑滚到底部：force = true 强制滚动；false 只在接近底部时滚
    @Suppress("SameParameterValue")
    private fun smoothScrollToBottom(force: Boolean) {
        val rv = binding.chatMessagesRecyclerview
        val lm = rv.layoutManager as? LinearLayoutManager ?: return
        val itemCount = chatMessageAdapter.itemCount
        if (itemCount == 0) return

        val lastVisible = lm.findLastVisibleItemPosition()
        // 距离底部 2 条以内就自动跟随
        val nearBottom = lastVisible >= itemCount - 3
        val shouldScroll = force || nearBottom

        if (shouldScroll) {
            rv.post {
                rv.smoothScrollToPosition(itemCount - 1)
            }
        }
    }
}
