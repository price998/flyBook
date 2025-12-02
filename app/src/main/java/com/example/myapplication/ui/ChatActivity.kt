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
import com.example.myapplication.R
import com.example.myapplication.adapter.ChatMessageAdapter
import com.example.myapplication.adapter.HistoryAdapter
import com.example.myapplication.adapter.ModelAdapter
import com.example.myapplication.databinding.ActivityChatBinding
import com.example.myapplication.databinding.DialogModelSelectorBinding
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

class ChatActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_CONVERSATION_ID = "extra_conversation_id"
        const val EXTRA_INITIAL_QUESTION = "extra_initial_question"
        const val EXTRA_ENABLE_SEARCH = "extra_enable_search"
    }

    private var isKeyboardMode = false
    private var previousMessageCount = 0
    private var isNetworkSearchEnabled = false

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
            layoutManager = LinearLayoutManager(this@ChatActivity)
            // 注入 Markwon 实例到 Adapter
            adapter = ChatMessageAdapter(viewModel.messages.value ?: mutableListOf(), markwon).also {
                chatMessageAdapter = it
            }
            
            setHasFixedSize(true)
            setItemViewCacheSize(20)
            (itemAnimator as? androidx.recyclerview.widget.SimpleItemAnimator)?.supportsChangeAnimations = false
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
                    // 新消息插入时立即滚动到底部
                    binding.chatMessagesRecyclerview.post {
                        binding.chatMessagesRecyclerview.scrollToPosition(event.position)
                    }
                }
                is MessageUpdateEvent.ItemChanged -> {
                    // 使用 payload 进行局部更新，避免完整重新绑定
                    chatMessageAdapter.notifyItemChanged(
                            event.position,
                            ChatMessageAdapter.PAYLOAD_CONTENT_UPDATE
                    )

                    // 流式输出时，只有当最后一项完全可见时才保持在底部
                    val layoutManager =
                            binding.chatMessagesRecyclerview.layoutManager as? LinearLayoutManager
                    if (layoutManager != null) {
                        val lastVisiblePosition = layoutManager.findLastCompletelyVisibleItemPosition()
                        val itemCount = chatMessageAdapter.itemCount
                        
                        // 只有当最后一项完全可见时，才滚动到底部
                        if (lastVisiblePosition == itemCount - 1) {
                            // 使用 scrollToPosition 而不是 smoothScrollToPosition
                            binding.chatMessagesRecyclerview.scrollToPosition(itemCount - 1)
                        }
                    }
                }
                is MessageUpdateEvent.HistoryLoaded -> {
                    binding.swipeRefreshLayout.isRefreshing = false
                    chatMessageAdapter.notifyItemRangeInserted(0, event.count)
                    // 保持在原来的位置
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
                // Handle long click - 显示重命名对话框
                showRenameDialog(history.id, history.title)
            }
        )
        // 更新为新的RecyclerView ID
        binding.historyRecyclerview.layoutManager = LinearLayoutManager(this)
        binding.historyRecyclerview.adapter = historyAdapter
    }
    
    private fun updateSidebarSelection(isNewChat: Boolean = false, isKnowledgeBase: Boolean = false) {
        val highlightColor = android.graphics.Color.parseColor("#E3F2FD")
        binding.btnNewChat.setBackgroundColor(if (isNewChat) highlightColor else android.graphics.Color.TRANSPARENT)
        binding.btnKnowledgeBase.setBackgroundColor(if (isKnowledgeBase) highlightColor else android.graphics.Color.TRANSPARENT)
        
        if (isNewChat || isKnowledgeBase) {
             historyAdapter.setSelectedId(null)
        }
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
}
