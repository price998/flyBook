package com.example.myapplication.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.DividerItemDecoration
import android.graphics.drawable.ColorDrawable
import com.example.myapplication.R
import com.example.myapplication.adapter.ChatMessageAdapter
import com.example.myapplication.adapter.HistoryAdapter
import com.example.myapplication.adapter.ModelAdapter
import com.example.myapplication.databinding.ActivityChatBinding
import com.example.myapplication.databinding.DialogModelSelectorBinding
import com.example.myapplication.databinding.ItemDialogMenuBinding
import com.example.myapplication.model.ModelRegistry
import com.example.myapplication.utils.DialogHelper
import com.example.myapplication.viewmodel.ChatViewModel
import com.example.myapplication.viewmodel.HistoryViewModel
import com.example.myapplication.viewmodel.MessageUpdateEvent
import com.google.android.material.bottomsheet.BottomSheetDialog
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
import android.speech.tts.TextToSpeech
import java.util.Locale
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.model.ChatMessage
import android.content.Context

class ChatActivity : AppCompatActivity() ,MessageActionsBottomSheet.Listener{

    companion object {
        const val EXTRA_CONVERSATION_ID = "extra_conversation_id"
        const val EXTRA_INITIAL_QUESTION = "extra_initial_question"
        const val EXTRA_ENABLE_SEARCH = "extra_enable_search"
    }

    private var isKeyboardMode = false
    private var previousMessageCount = 0
    private var isNetworkSearchEnabled = false
    private var shouldAutoScroll = true
    private var isUserDragging = false
    private lateinit var chatMessageAdapter: ChatMessageAdapter
    private lateinit var viewModel: ChatViewModel
    private lateinit var historyViewModel: HistoryViewModel
    private lateinit var binding: ActivityChatBinding
    private lateinit var historyAdapter: HistoryAdapter

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

        viewModel = ViewModelProvider(this)[ChatViewModel::class.java]
        historyViewModel = ViewModelProvider(this)[HistoryViewModel::class.java]

        setupWindowInsets()

        // 获取对话ID并设置到ViewModel
        val conversationId = intent.getStringExtra(EXTRA_CONVERSATION_ID) ?: ""
        if (conversationId.isNotEmpty()) {
            viewModel.setConversationId(conversationId)
        }

        // 检查是否开启联网搜索
        if (intent.getBooleanExtra(EXTRA_ENABLE_SEARCH, false)) {
            viewModel.toggleSearch(true)
        }

        // Initialize RecyclerView for chat messages
        binding.chatMessagesRecyclerview.apply {
            val linearLayoutManager = LinearLayoutManager(this@ChatActivity).apply {
                // 列表从底部开始堆叠，最后一条自然贴着底部 / 键盘
                stackFromEnd = true
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
        }

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
                        chatMessageAdapter.notifyItemRangeInserted(previousMessageCount, currentCount - previousMessageCount)
                    } else {
                        // 移除了消息
                        chatMessageAdapter.notifyItemRangeRemoved(currentCount, previousMessageCount - currentCount)
                    }
                    // 滚动到底部
                    binding.chatMessagesRecyclerview.post {
                        binding.chatMessagesRecyclerview.scrollToPosition(currentCount - 1)
                    }
                }
            }

            previousMessageCount = currentCount
        }

        viewModel.messageUpdate.observe(this) { event ->
            when (event) {
                is MessageUpdateEvent.ItemInserted -> {
                    chatMessageAdapter.notifyItemInserted(event.position)
                    // 新回答开始，允许自动跟随
                    shouldAutoScroll = true
                    if (!isUserDragging) {
                        smoothScrollToBottom(force = true)
                    }
                }

                is MessageUpdateEvent.ItemChanged -> {
                    chatMessageAdapter.notifyItemChanged(
                        event.position,
                        ChatMessageAdapter.PAYLOAD_CONTENT_UPDATE
                    )
                    // 不再 smoothScroll，只在「已经在底部」时用普通 scroll 保持贴底
                    val rv = binding.chatMessagesRecyclerview
                    val lm = rv.layoutManager as? LinearLayoutManager ?: return@observe
                    val lastVisible = lm.findLastVisibleItemPosition()
                    val itemCount = chatMessageAdapter.itemCount
                    val atBottom = itemCount > 0 && lastVisible >= itemCount - 1
                    if (atBottom) {
                        rv.scrollToPosition(itemCount - 1)
                    }
                }

                is MessageUpdateEvent.HistoryLoaded -> {
                    binding.swipeRefreshLayout.isRefreshing = false
                    chatMessageAdapter.notifyItemRangeInserted(0, event.count)
                    binding.chatMessagesRecyclerview.scrollToPosition(event.count)
                    Toast.makeText(this, "已加载 ${event.count} 条历史消息", Toast.LENGTH_SHORT).show()
                }

                is MessageUpdateEvent.NoMoreHistory -> {
                    binding.swipeRefreshLayout.isRefreshing = false
                    Toast.makeText(this, "没有更多历史消息了", Toast.LENGTH_SHORT).show()
                }
            }
        }




        // 处理初始问题
        val initialQuestion = intent.getStringExtra(EXTRA_INITIAL_QUESTION)
        initialQuestion?.takeIf { it.isNotBlank() }?.let { question ->
            if (savedInstanceState == null && (viewModel.messages.value?.isEmpty() == true)) {
                viewModel.sendMessage(question)
            }
        }

        // 处理联网搜索开关状态
        val isWebSearchEnabled = intent.getBooleanExtra("is_web_search_enabled", false)
        if (isWebSearchEnabled) {
            isNetworkSearchEnabled = true
            binding.layoutWebSearch.isSelected = true
        }

        // 处理初始语音模式状态
        val isInitialVoiceMode = intent.getBooleanExtra("is_voice_mode", false)
        isKeyboardMode = !isInitialVoiceMode // 默认为键盘模式

        // 根据初始状态设置 UI
        updateInputModeUI()

        // 菜单按钮
        binding.ivMenu.setOnClickListener {
            binding.chatDrawerLayout.openDrawer(GravityCompat.END)
        }

        // 新对话按钮
        binding.icNewChat.setOnClickListener {
            val intent = Intent(this, DialogueActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            intent.putExtra(DialogueActivity.EXTRA_RESET_INPUT_MODE, true)
            startActivity(intent)
            finish()
        }

        // 菜单-新对话按钮
        binding.btnNewChat.setOnClickListener {
            binding.chatDrawerLayout.closeDrawers()
            Toast.makeText(this, "开始新对话", Toast.LENGTH_SHORT).show()
            val intent = Intent(this, DialogueActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            intent.putExtra(DialogueActivity.EXTRA_RESET_INPUT_MODE, true)
            startActivity(intent)
            finish()
            updateSidebarSelection(isNewChat = true)
        }

        // 菜单-知识库按钮
        binding.btnKnowledgeBase.setOnClickListener {
            binding.chatDrawerLayout.closeDrawers()
            Toast.makeText(this, "知识库", Toast.LENGTH_SHORT).show()
            updateSidebarSelection(isKnowledgeBase = true)
        }

        // 侧边栏搜索按钮
        binding.btnSidebarSearch.setOnClickListener {
            binding.chatDrawerLayout.closeDrawer(GravityCompat.END)
            startActivity(Intent(this, SearchActivity::class.java))
        }

        binding.ivMic.setOnClickListener { toggleInputMode() }

        binding.ivMoreIcon.setOnClickListener { showModelSelectorDialog() }

        binding.layoutWebSearch.setOnClickListener { toggleNetworkSearchBackground() }

        binding.ivSend.setOnClickListener { sendMessage() }

        binding.ivStop.setOnClickListener {
            viewModel.stopGeneration()
            Toast.makeText(this, "正在停止生成...", Toast.LENGTH_SHORT).show()
        }
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
            // XML中Header已有paddingTop=32dp，这里设为0以避免双重间距
            v.setPadding(v.paddingLeft, 0, v.paddingRight, v.paddingBottom)
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
            val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.hideSoftInputFromWindow(binding.etInput.windowToken, 0)
        }
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

        if (isNetworkSearchEnabled) {
            Toast.makeText(this, "联网搜索已开启", Toast.LENGTH_SHORT).show()
        } else {
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

                androidx.appcompat.app.AlertDialog.Builder(this, R.style.RoundedDialogTheme)
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

    private fun updateSidebarSelection(isNewChat: Boolean = false, isKnowledgeBase: Boolean = false) {
        val highlightColor = android.graphics.Color.parseColor("#E3F2FD")
        binding.btnNewChat.setBackgroundColor(if (isNewChat) highlightColor else android.graphics.Color.TRANSPARENT)
        binding.btnKnowledgeBase.setBackgroundColor(if (isKnowledgeBase) highlightColor else android.graphics.Color.TRANSPARENT)

        if (isNewChat || isKnowledgeBase) {
             historyAdapter.setSelectedId(null)
        }
    }
    // 弹出对话框
    private fun showMessageActionsDialog(
        message: ChatMessage,
        anchorView: View,
        rawX: Int,
        rawY: Int
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
        val message = binding.etInput.text.toString().trim()
        if (message.isNotEmpty()) {
            binding.etInput.text.clear()
            viewModel.sendMessage(message)
        } else {
            Toast.makeText(this, "消息不能为空", Toast.LENGTH_SHORT).show()
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
    private fun deleteMessage(message: ChatMessage) {
        val list = chatMessageAdapter.messages
        val index = list.indexOf(message)
        if (index == -1) return
        list.removeAt(index)
        chatMessageAdapter.notifyItemRemoved(index)
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
