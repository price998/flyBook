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
import com.example.myapplication.adapter.HistoryAdapter
import com.example.myapplication.adapter.ModelAdapter
import com.example.myapplication.adapter.TopicAdapter
import com.example.myapplication.databinding.ActivityDialogueBinding
import com.example.myapplication.databinding.DialogModelSelectorBinding
import com.example.myapplication.model.ModelRegistry
import com.example.myapplication.utils.ModelPreferences
import com.example.myapplication.viewmodel.DialogueViewModel
import com.example.myapplication.viewmodel.HistoryViewModel
import com.google.android.material.bottomsheet.BottomSheetDialog

import androidx.recyclerview.widget.RecyclerView
import android.view.MotionEvent
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope

class DialogueActivity : AppCompatActivity() {

    private lateinit var viewModel: DialogueViewModel
    private lateinit var historyViewModel: HistoryViewModel
    private lateinit var binding: ActivityDialogueBinding
    private lateinit var historyAdapter: HistoryAdapter
    private lateinit var topicAdapter: TopicAdapter
    
    private var autoScrollJob: Job? = null
    private var isUserInteracting = false

    private val models = ModelRegistry.ALL_MODELS

    companion object {
        const val EXTRA_RESET_INPUT_MODE = "extra_reset_input_mode"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        binding = ActivityDialogueBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[DialogueViewModel::class.java]
        historyViewModel = ViewModelProvider(this)[HistoryViewModel::class.java]

        setupWindowInsets()
        setupClickListeners()
        setupNavigation()
        setupTopics()
        observeViewModel()
    }

    // 每次恢复到前台时更新历史列表
    override fun onResume() {
        super.onResume()
        historyViewModel.loadHistory()
        binding.layoutWebSearch.isSelected = false
        updateSidebarSelection(isNewChat = true)
        startAutoScroll()
    }

    override fun onPause() {
        super.onPause()
        stopAutoScroll()
    }

    private fun setupWindowInsets() {
        // 确保DrawerLayout不会被状态栏遮挡
        ViewCompat.setOnApplyWindowInsetsListener(binding.drawerLayout) { v, insets ->
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

    private fun setupClickListeners() {
        // 点击菜单按钮：显示侧边栏
        binding.ivMenu.setOnClickListener {
            binding.drawerLayout.openDrawer(GravityCompat.END)
        }

        // 点击联网搜索按钮：切换选中状态
        binding.layoutWebSearch.setOnClickListener {
            it.isSelected = !it.isSelected
            if (it.isSelected) {
                Toast.makeText(this, "联网搜索已开启", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "联网搜索已关闭", Toast.LENGTH_SHORT).show()
            }
        }
        
        // 点击更多模型按钮
        binding.ivMoreIcon.setOnClickListener {
            showModelSelectorDialog()
        }

        // 点击上传图片按钮
        binding.ivMore.setOnClickListener {
            Toast.makeText(this, "上传图片功能待实现", Toast.LENGTH_SHORT).show()
        }

        // 点击麦克风/键盘按钮：切换到语音模式或键盘模式
        binding.ivMic.setOnClickListener {
            viewModel.toggleVoiceMode()
        }

        // 点击发送按钮：发送消息
        binding.ivSend.setOnClickListener {
            val content = binding.etInput.text.toString()
            // 如果消息不为空，跳转到ChatActivity并清除输入
            if (content.isNotEmpty()) {
                // 创建新对话
                historyViewModel.createNewConversation(content) { conversationId ->
                    // 传递初始消息、对话ID和是否联网搜索
                    val intent = Intent(this@DialogueActivity, ChatActivity::class.java)
                    intent.putExtra(ChatActivity.EXTRA_INITIAL_QUESTION, content)
                    intent.putExtra(ChatActivity.EXTRA_CONVERSATION_ID, conversationId)
                    intent.putExtra("is_web_search_enabled", binding.layoutWebSearch.isSelected)
                    // 传递当前的语音模式状态
                    intent.putExtra("is_voice_mode", viewModel.isVoiceMode.value ?: false)
                    startActivity(intent)
                    
                    // 清空输入框
                    runOnUiThread {
                        binding.etInput.text.clear()
                    }
                }
            }
        }
        
        // 点击“按住说话”：开始录音
        binding.tvHoldToSpeak.setOnClickListener {
             Toast.makeText(this, "正在录音...", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupNavigation() {
        // 设置历史记录列表
        historyAdapter = HistoryAdapter(
            mutableListOf(),
            onItemClick = { history ->
                binding.drawerLayout.closeDrawer(GravityCompat.END)
                val intent = Intent(this, ChatActivity::class.java)
                intent.putExtra(ChatActivity.EXTRA_CONVERSATION_ID, history.id)
                startActivity(intent)
            },
            onItemLongClick = { _ ->
                 // 长按处理逻辑，比如删除或置顶
                 // TODO: 实现长按功能（删除、置顶等）
            }
        )
        
        binding.historyRecyclerview.apply {
            layoutManager = LinearLayoutManager(this@DialogueActivity)
            adapter = historyAdapter
        }

        // 绑定侧边栏按钮点击事件
        binding.btnNewChat.setOnClickListener {
            binding.drawerLayout.closeDrawer(GravityCompat.END)
            // 重置界面
            Toast.makeText(this, "已创建新对话", Toast.LENGTH_SHORT).show()
            binding.etInput.text.clear()
            updateSidebarSelection(isNewChat = true)
        }
        
        binding.btnKnowledgeBase.setOnClickListener {
            binding.drawerLayout.closeDrawer(GravityCompat.END)
            Toast.makeText(this, "我的知识库", Toast.LENGTH_SHORT).show()
            updateSidebarSelection(isKnowledgeBase = true)
        }
    }

    private fun setupTopics() {
        topicAdapter = TopicAdapter { topic ->
            val content = topic.prompt
            if (content.isNotEmpty()) {
                val isWebSearchEnabled = binding.layoutWebSearch.isSelected
                val isVoiceMode = viewModel.isVoiceMode.value ?: false
                
                historyViewModel.createNewConversation(content) { conversationId ->
                    runOnUiThread {
                        try {
                            val intent = Intent(this@DialogueActivity, ChatActivity::class.java)
                            intent.putExtra(ChatActivity.EXTRA_INITIAL_QUESTION, content)
                            intent.putExtra(ChatActivity.EXTRA_CONVERSATION_ID, conversationId)
                            intent.putExtra("is_web_search_enabled", isWebSearchEnabled)
                            intent.putExtra("is_voice_mode", isVoiceMode)
                            startActivity(intent)
                            
                            binding.etInput.text.clear()
                        } catch (e: Exception) {
                            e.printStackTrace()
                            Toast.makeText(this@DialogueActivity, "启动对话失败: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }

        binding.topicRecyclerview.apply {
            layoutManager = androidx.recyclerview.widget.StaggeredGridLayoutManager(
                2,
                androidx.recyclerview.widget.StaggeredGridLayoutManager.HORIZONTAL
            )
            adapter = topicAdapter
            
            addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {
                override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                    when (e.action) {
                        MotionEvent.ACTION_DOWN -> isUserInteracting = true
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> isUserInteracting = false
                    }
                    return false
                }
            })
        }
    }

    private fun startAutoScroll() {
        stopAutoScroll()
        autoScrollJob = lifecycleScope.launch {
            while (isActive) {
                delay(30) 
                if (!isUserInteracting && binding.topicRecyclerview.canScrollHorizontally(1)) {
                    binding.topicRecyclerview.scrollBy(2, 0)
                }
            }
        }
    }

    private fun stopAutoScroll() {
        autoScrollJob?.cancel()
        autoScrollJob = null
    }
    
    private fun updateSidebarSelection(isNewChat: Boolean = false, isKnowledgeBase: Boolean = false) {
        val highlightColor = android.graphics.Color.parseColor("#E3F2FD")
        binding.btnNewChat.setBackgroundColor(if (isNewChat) highlightColor else android.graphics.Color.TRANSPARENT)
        binding.btnKnowledgeBase.setBackgroundColor(if (isKnowledgeBase) highlightColor else android.graphics.Color.TRANSPARENT)
        
        if (isNewChat || isKnowledgeBase) {
             historyAdapter.setSelectedId(null)
        }
    }
    
    private fun showModelSelectorDialog() {
        val dialog = BottomSheetDialog(this)
        val dialogBinding = DialogModelSelectorBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)

        dialogBinding.modelListRecyclerview.layoutManager = LinearLayoutManager(this)

        // 获取当前选中的模型ID
        val currentModelConfig = ModelPreferences.getSelectedModel(this)
        val currentModelId = currentModelConfig.id

        val adapter =
                ModelAdapter(models, currentModelId) { modelConfig ->
                    // 保存到SharedPreferences
                    ModelPreferences.saveSelectedModel(this, modelConfig.id)
                    Toast.makeText(this, "已切换到: ${modelConfig.displayName}", Toast.LENGTH_SHORT)
                            .show()
                    dialog.dismiss()
                }

        dialogBinding.modelListRecyclerview.adapter = adapter
        dialog.show()
    }

    private fun observeViewModel() {
        // 观察历史记录变化并更新RecyclerView
        historyViewModel.historyList.observe(this) { history ->
            historyAdapter.updateData(history)
        }

        viewModel.topicList.observe(this) { topics ->
            topicAdapter.updateData(topics)
        }

        // 观察是否为语音模式
        viewModel.isVoiceMode.observe(this) { isVoiceMode ->
            if (isVoiceMode == true) {
                // 如果是，隐藏输入框、发送按钮、分隔符，并显示按住说话
                binding.ivMic.setImageResource(R.drawable.ic_keyboard)
                binding.etInput.visibility = View.GONE
                binding.ivSend.visibility = View.GONE
                binding.dividerSend.visibility = View.GONE
                binding.tvHoldToSpeak.visibility = View.VISIBLE
            } else {
                // 如果否，显示输入框、发送按钮、分隔符，并隐藏按住说话
                binding.ivMic.setImageResource(R.drawable.ic_mic)
                binding.etInput.visibility = View.VISIBLE
                binding.ivSend.visibility = View.VISIBLE
                binding.dividerSend.visibility = View.VISIBLE
                binding.tvHoldToSpeak.visibility = View.GONE

                // 仅当处于活动状态时才显示键盘
                if (hasWindowFocus()) {
                    binding.etInput.requestFocus()
                    val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                    imm.showSoftInput(binding.etInput, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
                }
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
                binding.etInput.text.clear()
                viewModel.onInputCleared()
            }
        }
    }
    
}
