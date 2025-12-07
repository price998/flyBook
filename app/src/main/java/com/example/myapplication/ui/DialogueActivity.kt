package com.example.myapplication.ui

import android.content.Intent
import android.content.pm.PackageManager

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope

import com.example.myapplication.R
import com.example.myapplication.adapter.TopicAdapter
import com.example.myapplication.databinding.ActivityDialogueBinding
import com.example.myapplication.model.MediaType
import com.example.myapplication.model.ModelConfig
import com.example.myapplication.utils.ModelPreferences
import com.example.myapplication.viewmodel.DialogueViewModel
import com.example.myapplication.viewmodel.HistoryViewModel

import androidx.recyclerview.widget.RecyclerView
import android.view.MotionEvent
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch


class DialogueActivity : BaseAttachmentActivity(), HistoryFragment.Listener {

    companion object {
        const val EXTRA_RESET_INPUT_MODE = "extra_reset_input_mode"
        const val REQUEST_RECORD_AUDIO_PERMISSION = 2
    }

    private lateinit var viewModel: DialogueViewModel
    private lateinit var historyViewModel: HistoryViewModel
    private lateinit var binding: ActivityDialogueBinding

    private lateinit var topicAdapter: TopicAdapter

    private var autoScrollJob: Job? = null
    private var isUserInteracting = false
    // HistoryFragment.Listener
    override fun onHistorySelected(conversationId: String) {
        binding.drawerLayout.closeDrawer(GravityCompat.END)
        val intent = Intent(this, ChatActivity::class.java)
        intent.putExtra(ChatActivity.EXTRA_CONVERSATION_ID, conversationId)
        startActivity(intent)
    }
    override fun onConversationDeleted(conversationId: String) {
        // 无需特殊处理，列表会自动刷新
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityDialogueBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[DialogueViewModel::class.java]
        historyViewModel = ViewModelProvider(this)[HistoryViewModel::class.java]

        // 初始化预览适配器（继承自基类）
        rvPreview = binding.includeBottomBar.rvPreview
        setupPreviewAdapter()

        setupWindowInsets()
        setupClickListeners()
        setupNavigation()
        setupTopics()
        observeViewModel()

        // 初始化科大讯飞语音识别
        initXunfeiSpeechRecognizer()

        // 设置联网搜索监听器（继承自基类）
        setupWebSearchListener()
    }

    // 每次恢复到前台时更新历史列表
    override fun onResume() {
        super.onResume()
        historyViewModel.loadHistory()
        // 不再强制重置联网搜索状态，由 setupWebSearchListener() 从 SharedPreferences 读取
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
        ViewCompat.setOnApplyWindowInsetsListener(binding.includeBottomBar.root) { v, insets ->
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
            v.setPadding(v.paddingLeft, 0, v.paddingRight, v.paddingBottom)
            insets
        }
    }

    private fun setupClickListeners() {
        // 点击菜单按钮：显示侧边栏
        binding.ivMenu.setOnClickListener { binding.drawerLayout.openDrawer(GravityCompat.END) }

        // 侧边栏搜索按钮
                    binding.includeSidebar.btnSidebarSearch.setOnClickListener {
            binding.drawerLayout.closeDrawer(GravityCompat.END)
            startActivity(Intent(this, SearchActivity::class.java))
        }

        // 侧边栏 - 新建对话按钮
        binding.includeSidebar.btnNewChat.setOnClickListener {
            binding.drawerLayout.closeDrawer(GravityCompat.END)
            // 清空输入框和附件
            binding.includeBottomBar.etInput.text.clear()
            clearSelectedMedia()
            // 重置侧边栏选中状态
            updateSidebarSelection(isNewChat = true)
            Toast.makeText(this, "新建对话", Toast.LENGTH_SHORT).show()
        }

        // 侧边栏 - 知识库按钮（当前就在 DialogueActivity，无需跳转）
        binding.includeSidebar.btnKnowledgeBase.setOnClickListener {
            binding.drawerLayout.closeDrawer(GravityCompat.END)
            updateSidebarSelection(isKnowledgeBase = true)
            Toast.makeText(this, "知识库", Toast.LENGTH_SHORT).show()
        }

        // 联网搜索已通过 setupWebSearchListener() 设置，从 SharedPreferences 读取状态
        
        // 侧边栏 - 生成假数据（调试用）
                    binding.includeSidebar.btnGenerateFakeData.setOnClickListener {
            binding.drawerLayout.closeDrawer(GravityCompat.END)
            Toast.makeText(this, "正在生成假数据对话…", Toast.LENGTH_SHORT).show()

            historyViewModel.generateFakeConversation { conversationId ->
                // 生成完成后直接跳到 ChatActivity 展示这条长对话
                val intent = Intent(this@DialogueActivity, ChatActivity::class.java)
                intent.putExtra(ChatActivity.EXTRA_CONVERSATION_ID, conversationId)
                // 默认不开启联网搜索
                intent.putExtra("is_web_search_enabled", false)
                startActivity(intent)
            }
        }

        // 点击更多模型按钮
        binding.includeBottomBar.ivMoreIcon.setOnClickListener { showModelSelectorDialog() }

        // 联网搜索已通过 setupWebSearchListener() 设置

        // 点击上传图片按钮
        binding.includeBottomBar.ivMore.setOnClickListener { showAttachmentOptions() }

        // 点击麦克风/键盘按钮：切换到语音模式或键盘模式
        binding.includeBottomBar.ivMic.setOnClickListener { viewModel.toggleVoiceMode() }

        // 点击发送按钮：发送消息
        binding.includeBottomBar.ivSend.setOnClickListener {
            val content = binding.includeBottomBar.etInput.text.toString().trim()

            // 如果没有输入文本也没有选中附件，提示用户
            if (content.isEmpty() && selectedItems.isEmpty()) {
                Toast.makeText(this, "请输入消息或选择附件", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 创建新对话
            val displayTitle =
                    if (content.isNotEmpty()) {
                        if (content.length > 20) content.substring(0, 20) + "..." else content
                    } else {
                        "图片对话"
                    }

            historyViewModel.createNewConversation(displayTitle) { conversationId ->
                // 传递初始消息、对话ID和是否联网搜索
                val intent = Intent(this@DialogueActivity, ChatActivity::class.java)
                if (content.isNotEmpty()) {
                    intent.putExtra(ChatActivity.EXTRA_INITIAL_QUESTION, content)
                }
                intent.putExtra(ChatActivity.EXTRA_CONVERSATION_ID, conversationId)
                intent.putExtra("is_web_search_enabled", binding.includeBottomBar.layoutWebSearch.isSelected)

                // 如果开启了联网搜索，给用户提示
                if (binding.includeBottomBar.layoutWebSearch.isSelected && content.isNotEmpty()) {
                    Toast.makeText(this@DialogueActivity, "🔍 将使用联网搜索回答您的问题", Toast.LENGTH_SHORT)
                            .show()
                }
                // 传递当前的语音模式状态
                intent.putExtra("is_voice_mode", viewModel.isVoiceMode.value ?: false)

                // 传递选中的附件（如果有的话）
                if (selectedItems.isNotEmpty()) {
                    val imageUris =
                            selectedItems.filter { it.type == MediaType.IMAGE }.map {
                                it.uri.toString()
                            }
                    val fileUris =
                            selectedItems.filter { it.type == MediaType.FILE }.map {
                                it.uri.toString()
                            }
                    intent.putStringArrayListExtra("image_uris", ArrayList(imageUris))
                    intent.putStringArrayListExtra("file_uris", ArrayList(fileUris))
                }

                startActivity(intent)

                // 清空输入框和附件
                runOnUiThread {
                    binding.includeBottomBar.etInput.text.clear()
                    val itemCount = selectedItems.size
                    selectedItems.clear()
                    if (itemCount > 0) {
                        previewAdapter.notifyItemRangeRemoved(0, itemCount)
                    }
                    updatePreviewVisibility()
                }
            }
        }
        
        // 设置语音输入的触摸监听
        setupVoiceInputListener()
    }

    private fun setupNavigation() {
        // 历史列表改为 Fragment 托管
        val fragment = HistoryFragment.newInstance(null)
        supportFragmentManager.beginTransaction()
            .replace(R.id.history_fragment_container, fragment)
            .commit()
    }

    private fun setupTopics() {
        topicAdapter = TopicAdapter { topic ->
            val content = topic.prompt
            if (content.isNotEmpty()) {
                val isWebSearchEnabled = binding.includeBottomBar.layoutWebSearch.isSelected
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
                            
                            binding.includeBottomBar.etInput.text.clear()
                        } catch (e: Exception) {
                            e.printStackTrace()
                            Toast.makeText(
                                            this@DialogueActivity,
                                            "启动对话失败: ${e.message}",
                                            Toast.LENGTH_SHORT
                                    )
                                    .show()
                        }
                    }
                }
            }
        }

        binding.topicRecyclerview.apply {
            layoutManager =
                    androidx.recyclerview.widget.StaggeredGridLayoutManager(
                            2,
                            androidx.recyclerview.widget.StaggeredGridLayoutManager.HORIZONTAL
                    )
            adapter = topicAdapter
            
            addOnItemTouchListener(
                    object : RecyclerView.SimpleOnItemTouchListener() {
                        override fun onInterceptTouchEvent(
                                rv: RecyclerView,
                                e: MotionEvent
                        ): Boolean {
                            when (e.action) {
                                MotionEvent.ACTION_DOWN -> isUserInteracting = true
                                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                                        isUserInteracting = false
                            }
                            return false
                        }
                    }
            )
        }
    }

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

    private fun stopAutoScroll() {
        autoScrollJob?.cancel()
        autoScrollJob = null
    }
    
    private fun updateSidebarSelection(isNewChat: Boolean = false, isKnowledgeBase: Boolean = false) {
                binding.includeSidebar.btnNewChat.isSelected = isNewChat
                binding.includeSidebar.btnKnowledgeBase.isSelected = isKnowledgeBase
        // 历史列表由 Fragment 管理，不在此重置选中状态
    }
    
    // 模型选择对话框已在基类实现，直接使用 binding.ivMoreIcon.setOnClickListener { showModelSelectorDialog() }
    // 观察 ViewModel 的 LiveData，实现 “数据变化自动更新 UI”
    private fun observeViewModel() {
        // 历史列表由 Fragment 管理，此处不再直接更新 Adapter

        viewModel.topicList.observe(this) { topics -> topicAdapter.updateData(topics) }

        // 观察是否为语音模式
        viewModel.isVoiceMode.observe(this) { isVoiceMode ->
            if (isVoiceMode == true) {
                // 如果是，隐藏输入框、发送按钮、分隔符，并显示按住说话
                binding.includeBottomBar.ivMic.setImageResource(R.drawable.ic_keyboard)
                binding.includeBottomBar.etInput.visibility = View.GONE
                binding.includeBottomBar.ivSend.visibility = View.GONE
                binding.includeBottomBar.dividerSend.visibility = View.GONE
                binding.includeBottomBar.tvHoldToSpeak.visibility = View.VISIBLE
            } else {
                // 如果否，显示输入框、发送按钮、分隔符，并隐藏按住说话
                binding.includeBottomBar.ivMic.setImageResource(R.drawable.ic_mic)
                binding.includeBottomBar.etInput.visibility = View.VISIBLE
                binding.includeBottomBar.ivSend.visibility = View.VISIBLE
                binding.includeBottomBar.dividerSend.visibility = View.VISIBLE
                binding.includeBottomBar.tvHoldToSpeak.visibility = View.GONE

                // 仅当处于活动状态时才显示键盘
                if (hasWindowFocus()) {
                    binding.includeBottomBar.etInput.requestFocus()
                    val imm =
                            getSystemService(INPUT_METHOD_SERVICE) as
                                    android.view.inputmethod.InputMethodManager
                    imm.showSoftInput(
                            binding.includeBottomBar.etInput,
                            android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT
                    )
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
                binding.includeBottomBar.etInput.text.clear()
                viewModel.onInputCleared()
            }
        }
    }
    
    // 附件管理、权限请求、语音识别等方法已移至基类 BaseAttachmentActivity

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
    
    override fun getHoldToSpeakView(): View = binding.includeBottomBar.tvHoldToSpeak
    
    override fun getMoreButton(): View = binding.includeBottomBar.ivMore
    
    override fun onVoiceRecognitionResult(text: String) {
        // 自动切换到键盘模式，这样可以在输入框看到识别内容
        viewModel.setVoiceMode(false)
        // 将识别结果填入输入框
        binding.includeBottomBar.etInput.setText(text)
        binding.includeBottomBar.etInput.setSelection(text.length)
    }
    
    override fun onRecordAudioPermissionNeeded() {
        requestRecordAudioPermission(REQUEST_RECORD_AUDIO_PERMISSION)
    }

    override fun getCurrentModelId(): String {
        return ModelPreferences.getSelectedModel(this).id
    }

    override fun onModelSwitch(modelConfig: ModelConfig) {
        ModelPreferences.saveSelectedModel(this, modelConfig.id)
    }

    override fun getWebSearchLayout(): View = binding.includeBottomBar.layoutWebSearch

    override fun onWebSearchToggle(isEnabled: Boolean) {
        // DialogueActivity 不需要同步到 ViewModel，只需要保持状态用于传递给 ChatActivity
    }
}
