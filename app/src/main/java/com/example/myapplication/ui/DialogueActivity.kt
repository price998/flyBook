package com.example.myapplication.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.myapplication.R
import com.example.myapplication.adapter.HistoryAdapter
import com.example.myapplication.adapter.TopicAdapter
import com.example.myapplication.data.db.AppDatabase
import com.example.myapplication.databinding.ActivityDialogueBinding
import com.example.myapplication.model.ModelConfig
import com.example.myapplication.repository.ChatRepository
import com.example.myapplication.utils.DialogHelper
import com.example.myapplication.utils.ModelPreferences
import com.example.myapplication.viewmodel.DialogueViewModel
import kotlinx.coroutines.launch

class DialogueActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_RESET_INPUT_MODE = "extra_reset_input_mode"
    }

    private lateinit var viewModel: DialogueViewModel
    private lateinit var binding: ActivityDialogueBinding
    
    private var isKeyboardMode = false
    private lateinit var selectedModel: ModelConfig
    private var isNetworkSearchEnabled = false
    
    private lateinit var historyAdapter: HistoryAdapter
    private lateinit var topicAdapter: TopicAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityDialogueBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Initialize ViewModel
        viewModel = ViewModelProvider(this)[DialogueViewModel::class.java]
        
        // 从 SharedPreferences 加载选中的模型
        selectedModel = ModelPreferences.getSelectedModel(this)
        
        setupHistoryRecyclerView()
        setupTopicRecyclerView()
        setupSearch()
        observeViewModel()

        binding.documentListIcon.setOnClickListener { toggleInputMode() }
        
        binding.moreOptionsIcon.setOnClickListener { showModelSelectorDialog() }

        binding.networkSearchLayout.setOnClickListener {
            toggleNetworkSearchBackground(binding.networkSearchLayout)
        }

        binding.sendButton.setOnClickListener {
            val question = binding.messageInputEdittext.text.toString().trim()
            if (question.isEmpty()) {
                Toast.makeText(this, "请先输入问题", Toast.LENGTH_SHORT).show()
            } else {
                // 创建新对话
                lifecycleScope.launch {
                    val database = AppDatabase.getDatabase(this@DialogueActivity)
                    val repository = ChatRepository(database.messageDao(), database.conversationDao())
                    
                    // 使用问题的前20个字符作为对话标题
                    val title = if (question.length > 20) question.substring(0, 20) + "..." else question
                    val conversationId = repository.createConversation(title)
                    
                    // 启动ChatActivity并传递对话ID和初始问题
                    val intent = Intent(this@DialogueActivity, ChatActivity::class.java)
                    intent.putExtra(ChatActivity.EXTRA_CONVERSATION_ID, conversationId)
                    intent.putExtra(ChatActivity.EXTRA_INITIAL_QUESTION, question)
                    // 传递联网搜索状态
                    intent.putExtra(ChatActivity.EXTRA_ENABLE_SEARCH, isNetworkSearchEnabled)
                    startActivity(intent)
                    
                    binding.messageInputEdittext.text.clear()
                    resetInputMode()
                }
            }
        }

        if (intent.getBooleanExtra(EXTRA_RESET_INPUT_MODE, false)) {
            resetInputMode()
        }
        
        // 如果从ChatActivity跳转过来并需要打开历史抽屉
        if (intent.getBooleanExtra("open_history_drawer", false)) {
            // 延迟打开抽屉，确保布局已完成
            binding.drawerLayout.post {
                binding.drawerLayout.openDrawer(GravityCompat.END)
            }
        }
        
        // Cloud icon click listener (上传文件)
        binding.cloudIcon.setOnClickListener {
            android.util.Log.d("DialogueActivity", "Cloud icon clicked - Upload file")
            Toast.makeText(this, "上传文件功能开发中", Toast.LENGTH_SHORT).show()
            // TODO: 实现文件上传功能
        }
        
        // Menu icon click listener (打开历史记录抽屉)
        binding.menuIcon.setOnClickListener {
            android.util.Log.d("DialogueActivity", "Menu icon clicked")
            binding.drawerLayout.openDrawer(GravityCompat.END)
        }
        
        // New dialogue button click listener
        binding.newDialogueButton.setOnClickListener {
            binding.drawerLayout.closeDrawers()
            startNewConversation()
        }
        
        // Knowledge base button click listener
        binding.knowledgeBaseButton.setOnClickListener {
            Toast.makeText(this, "知识库", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent ?: return
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_RESET_INPUT_MODE, false)) {
            resetInputMode()
        }
        
        // 如果从ChatActivity跳转过来并需要打开历史抽屉
        if (intent.getBooleanExtra("open_history_drawer", false)) {
            binding.drawerLayout.post {
                binding.drawerLayout.openDrawer(GravityCompat.END)
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        // 每次返回时刷新历史列表
        viewModel.loadHistory()
    }

    private fun toggleInputMode() {
        isKeyboardMode = !isKeyboardMode

        if (isKeyboardMode) {
            binding.holdToSpeakButton.visibility = View.GONE
            binding.messageInputEdittext.visibility = View.VISIBLE
            binding.messageInputEdittext.requestFocus()
            binding.documentListIcon.setImageResource(R.drawable.microphone)
        } else {
            binding.holdToSpeakButton.visibility = View.VISIBLE
            binding.messageInputEdittext.visibility = View.GONE
            binding.documentListIcon.setImageResource(R.drawable.ic_keyword)
        }
    }

    private fun resetInputMode() {
        isKeyboardMode = false
        binding.holdToSpeakButton.visibility = View.VISIBLE
        binding.messageInputEdittext.visibility = View.GONE
        binding.documentListIcon.setImageResource(R.drawable.ic_keyword)
    }

    /**
     * 开始新对话
     * 清空输入框、重置输入模式、重置联网搜索状态
     */
    private fun startNewConversation() {
        // 清空输入框
        binding.messageInputEdittext.text.clear()
        
        // 重置输入模式为语音模式
        resetInputMode()
        
        // 重置联网搜索状态
        if (isNetworkSearchEnabled) {
            isNetworkSearchEnabled = false
            binding.networkSearchLayout.setBackgroundResource(R.drawable.rounded_corner_gray_background)
        }
        
        // 关闭抽屉（如果打开的话）
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.END)) {
            binding.drawerLayout.closeDrawers()
        }
        
        // 显示提示
        Toast.makeText(this, "开始新对话", Toast.LENGTH_SHORT).show()
        
        android.util.Log.d("DialogueActivity", "Started new conversation")
    }

    private fun showModelSelectorDialog() {
        DialogHelper.showModelSelectorDialog(this, selectedModel.id) { modelConfig ->
            selectedModel = modelConfig
            ModelPreferences.saveSelectedModel(this, modelConfig.id)
        }
    }
    
    private fun toggleNetworkSearchBackground(networkSearchLayout: View) {
        isNetworkSearchEnabled = !isNetworkSearchEnabled
        
        if (isNetworkSearchEnabled) {
            // 切换为蓝色背景
            networkSearchLayout.setBackgroundResource(R.drawable.rounded_corner_blue_background)
        } else {
            // 切换回灰色背景
            networkSearchLayout.setBackgroundResource(R.drawable.rounded_corner_gray_background)
        }
    }
    
    private fun setupTopicRecyclerView() {
        topicAdapter = TopicAdapter { topic ->
            // Handle topic click - fill input or start chat
            if (topic.prompt.isNotEmpty()) {
                // 如果是键盘模式，填入输入框
                if (!isKeyboardMode) {
                    toggleInputMode()
                }
                binding.messageInputEdittext.setText(topic.prompt)
                binding.messageInputEdittext.setSelection(topic.prompt.length)
            }
        }
        binding.topicRecyclerview.layoutManager = LinearLayoutManager(this)
        binding.topicRecyclerview.adapter = topicAdapter
    }
    
    private fun observeViewModel() {
        viewModel.historyList.observe(this) { history -> historyAdapter.updateData(history) }
        
        viewModel.topicList.observe(this) { topics ->
            topicAdapter.updateData(topics)
        }

        viewModel.errorMessage.observe(this) { error ->
            error?.let { Toast.makeText(this, it, Toast.LENGTH_SHORT).show() }
        }
    }
    
    private fun setupSearch() {
        binding.searchEditText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                viewModel.search(s?.toString() ?: "")
            }
        })
    }

    private fun setupHistoryRecyclerView() {
        historyAdapter =
                HistoryAdapter(
                    mutableListOf(),
                    onItemClick = { history ->
                        // Handle history item click - 打开选中的对话
                        binding.drawerLayout.closeDrawers()
                        
                        // 跳转到ChatActivity并传递对话ID
                        val intent = Intent(this, ChatActivity::class.java)
                        intent.putExtra(ChatActivity.EXTRA_CONVERSATION_ID, history.id)
                        startActivity(intent)
                    },
                    onItemLongClick = { history ->
                        // Handle long click - 显示操作菜单
                        showLongClickMenu(history)
                    }
                )
        binding.historyRecyclerview.layoutManager = LinearLayoutManager(this)
        binding.historyRecyclerview.adapter = historyAdapter
    }

    private fun showLongClickMenu(history: com.example.myapplication.model.ChatHistory) {
        val options = arrayOf(
            if (history.isPinned) "取消置顶" else "置顶对话",
            "重命名",
            "删除对话"
        )

        AlertDialog.Builder(this)
            .setTitle(history.title)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> viewModel.togglePin(history.id, history.isPinned)
                    1 -> showRenameDialog(history.id, history.title)
                    2 -> showDeleteConfirmDialog(history.id)
                }
            }
            .show()
    }

    private fun showDeleteConfirmDialog(conversationId: String) {
        DialogHelper.showDeleteConfirmDialog(this) {
            viewModel.deleteConversation(conversationId)
        }
    }
    
    private fun showRenameDialog(conversationId: String, currentTitle: String) {
        DialogHelper.showRenameDialog(this, currentTitle) { newTitle ->
            viewModel.renameConversation(conversationId, newTitle)
        }
    }
}
