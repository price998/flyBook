package com.example.myapplication.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.myapplication.R
import com.example.myapplication.adapter.ChatMessageAdapter
import com.example.myapplication.adapter.HistoryAdapter
import com.example.myapplication.adapter.ModelAdapter
import com.example.myapplication.databinding.ActivityChatBinding
import com.example.myapplication.databinding.DialogModelSelectorBinding
import com.example.myapplication.model.ModelRegistry
import com.example.myapplication.utils.ModelPreferences
import com.example.myapplication.viewmodel.ChatViewModel
import com.example.myapplication.viewmodel.DialogueViewModel
import com.example.myapplication.viewmodel.MessageUpdateEvent
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.bottomsheet.BottomSheetDialog
import io.noties.markwon.Markwon
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.image.ImagesPlugin
import kotlin.math.abs

class ChatActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_CONVERSATION_ID = "extra_conversation_id"
        const val EXTRA_INITIAL_QUESTION = "extra_initial_question"
    }

    private var isKeyboardMode = false
    private var isNetworkSearchEnabled = false
    private var previousMessageCount = 0

    private lateinit var chatMessageAdapter: ChatMessageAdapter
    private lateinit var viewModel: ChatViewModel
    private lateinit var dialogueViewModel: DialogueViewModel
    private lateinit var binding: ActivityChatBinding
    private lateinit var historyAdapter: HistoryAdapter

    private val models = ModelRegistry.ALL_MODELS
    
    // Markwon 实例 - Activity 级别单例，注入到 Adapter
    // 暂时不使用代码高亮以提升性能，编译成功后可以添加
    private val markwon: Markwon by lazy {
        Markwon.builder(this)
            .usePlugin(HtmlPlugin.create())
            .usePlugin(ImagesPlugin.create())
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
        dialogueViewModel = ViewModelProvider(this)[DialogueViewModel::class.java]
        
        // 获取对话ID并设置到ViewModel
        val conversationId = intent.getStringExtra(EXTRA_CONVERSATION_ID) ?: ""
        if (conversationId.isNotEmpty()) {
            viewModel.setConversationId(conversationId)
        }

        // Initialize RecyclerView for chat messages
        binding.chatMessagesRecyclerview.apply {
            layoutManager = LinearLayoutManager(this@ChatActivity)
            // 注入 Markwon 实例到 Adapter
            adapter = ChatMessageAdapter(viewModel.messages.value ?: mutableListOf(), markwon).also {
                chatMessageAdapter = it
            }
            
            // 性能优化配置
            setHasFixedSize(true) // item 高度固定时可以设置，提升性能
            setItemViewCacheSize(20) // 增加缓存大小，减少 onCreateViewHolder 调用
            
            // 禁用闪烁动画，避免流式输出时的视觉抖动
            (itemAnimator as? androidx.recyclerview.widget.SimpleItemAnimator)?.supportsChangeAnimations = false
        }

        // 设置下拉刷新监听器
        binding.swipeRefreshLayout.setOnRefreshListener { viewModel.loadMoreHistory() }
        
        // 设置历史对话列表
        setupHistoryRecyclerView()
        
        // 观察历史对话数据
        dialogueViewModel.historyList.observe(this) { history ->
            historyAdapter.updateData(history)
        }
        
        // 观察AI生成状态，控制停止按钮的显示
        viewModel.isGenerating.observe(this) { isGenerating ->
            binding.chatStopButton.visibility = if (isGenerating) View.VISIBLE else View.GONE
            binding.chatSendButton.visibility = if (isGenerating) View.GONE else View.VISIBLE
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
                    // 只在这种情况下使用 notifyDataSetChanged
                    chatMessageAdapter.notifyDataSetChanged()
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

        // 返回按钮点击事件：回到 DialogueActivity
        binding.backIcon.setOnClickListener {
            android.util.Log.d("ChatActivity", "Back icon clicked")
            val intent = Intent(this, DialogueActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            intent.putExtra(DialogueActivity.EXTRA_RESET_INPUT_MODE, true)
            startActivity(intent)
            finish()
        }

        // 新建对话按钮点击事件 - 跳转到主界面
        binding.newDialogueIcon.setOnClickListener {
            android.util.Log.d("ChatActivity", "New dialogue icon clicked")
            val intent = Intent(this, DialogueActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            intent.putExtra(DialogueActivity.EXTRA_RESET_INPUT_MODE, true)
            startActivity(intent)
            finish()
        }

        // 查看历史对话按钮点击事件
        binding.historyIcon.setOnClickListener {
            android.util.Log.d("ChatActivity", "History icon clicked")
            binding.chatDrawerLayout.openDrawer(GravityCompat.END)
        }
        
        // 历史抽屉中的新建对话按钮
        binding.chatNewDialogueButton.setOnClickListener {
            binding.chatDrawerLayout.closeDrawers()
            val intent = Intent(this, DialogueActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            intent.putExtra(DialogueActivity.EXTRA_RESET_INPUT_MODE, true)
            startActivity(intent)
            finish()
        }
        
        // 历史抽屉中的知识库按钮
        binding.chatKnowledgeBaseButton.setOnClickListener {
            Toast.makeText(this, "知识库", Toast.LENGTH_SHORT).show()
        }

        binding.chatDocumentListIcon.setOnClickListener { toggleInputMode() }

        binding.moreOptionsIconChat.setOnClickListener { showModelSelectorDialog() }

        binding.networkSearchLayoutChat.setOnClickListener { toggleNetworkSearchBackground() }

        binding.chatSendButton.setOnClickListener { sendMessage() }
        
        binding.chatStopButton.setOnClickListener {
            viewModel.stopGeneration()
            Toast.makeText(this, "正在停止生成...", Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleInputMode() {
        isKeyboardMode = !isKeyboardMode

        if (isKeyboardMode) {
            // 切换到键盘输入模式
            binding.chatHoldToSpeakButton.visibility = View.GONE
            binding.chatMessageInputEdittext.visibility = View.VISIBLE
            binding.chatMessageInputEdittext.requestFocus()
            // 切换为麦克风图标
            binding.chatDocumentListIcon.setImageResource(R.drawable.microphone)
        } else {
            // 切换到语音输入模式
            binding.chatHoldToSpeakButton.visibility = View.VISIBLE
            binding.chatMessageInputEdittext.visibility = View.GONE
            // 切换回关键词图标
            binding.chatDocumentListIcon.setImageResource(R.drawable.ic_keyword)
        }
    }

    private fun showModelSelectorDialog() {
        val dialog = BottomSheetDialog(this)
        val dialogBinding = DialogModelSelectorBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)

        dialogBinding.modelListRecyclerview.layoutManager = LinearLayoutManager(this)

        val currentModelId = viewModel.currentModel.value?.id ?: ModelRegistry.DEFAULT_MODEL.id
        val adapter =
                ModelAdapter(models, currentModelId) { modelConfig ->
                    // 切换模型
                    viewModel.switchModel(modelConfig)
                    Toast.makeText(this, "已切换到: ${modelConfig.displayName}", Toast.LENGTH_SHORT)
                            .show()
                    dialog.dismiss()
                }

        dialogBinding.modelListRecyclerview.adapter = adapter
        dialog.show()
    }

    private fun toggleNetworkSearchBackground() {
        isNetworkSearchEnabled = !isNetworkSearchEnabled

        if (isNetworkSearchEnabled) {
            // 切换为蓝色背景
            binding.networkSearchLayoutChat.setBackgroundResource(R.drawable.rounded_corner_blue_background)
        } else {
            // 切换回灰色背景
            binding.networkSearchLayoutChat.setBackgroundResource(R.drawable.rounded_corner_gray_background)
        }
    }
    
    private fun setupHistoryRecyclerView() {
        historyAdapter = HistoryAdapter(
            mutableListOf(),
            onItemClick = { history ->
                // Handle history item click - 切换到选中的对话
                binding.chatDrawerLayout.closeDrawers()
                
                // 设置新的对话ID，ViewModel会自动清空当前列表并加载新对话的消息
                viewModel.setConversationId(history.id)
            },
            onItemLongClick = { history ->
                // Handle long click - 显示重命名对话框
                showRenameDialog(history.id, history.title)
            }
        )
        binding.chatHistoryRecyclerview.layoutManager = LinearLayoutManager(this)
        binding.chatHistoryRecyclerview.adapter = historyAdapter
    }
    
    private fun showRenameDialog(conversationId: String, currentTitle: String) {
        val editText = EditText(this).apply {
            setText(currentTitle)
            hint = "输入新标题"
            setPadding(50, 30, 50, 30)
        }
        
        AlertDialog.Builder(this)
            .setTitle("重命名对话")
            .setView(editText)
            .setPositiveButton("确定") { _, _ ->
                val newTitle = editText.text.toString().trim()
                if (newTitle.isNotEmpty() && newTitle != currentTitle) {
                    dialogueViewModel.renameConversation(conversationId, newTitle)
                    Toast.makeText(this, "已重命名", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun sendMessage() {
        val message = binding.chatMessageInputEdittext.text.toString().trim()
        if (message.isNotEmpty()) {
            binding.chatMessageInputEdittext.text.clear()
            viewModel.sendMessage(message)
        } else {
            Toast.makeText(this, "消息不能为空", Toast.LENGTH_SHORT).show()
        }
    }
}
