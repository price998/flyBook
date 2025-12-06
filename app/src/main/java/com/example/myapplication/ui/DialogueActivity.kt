package com.example.myapplication.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognizerIntent
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
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.DividerItemDecoration
import android.graphics.drawable.ColorDrawable
import com.example.myapplication.R
import com.example.myapplication.adapter.HistoryAdapter
import com.example.myapplication.adapter.ModelAdapter
import com.example.myapplication.adapter.PreviewAdapter
import com.example.myapplication.adapter.TopicAdapter
import com.example.myapplication.databinding.ActivityDialogueBinding
import com.example.myapplication.databinding.DialogAttachmentOptionsBinding
import com.example.myapplication.databinding.DialogModelSelectorBinding
import com.example.myapplication.model.MediaType
import com.example.myapplication.model.ModelRegistry
import com.example.myapplication.model.SelectedMedia
import com.example.myapplication.utils.DialogHelper
import com.example.myapplication.utils.ModelPreferences
import com.example.myapplication.utils.XunfeiSpeechRecognizer
import com.example.myapplication.viewmodel.DialogueViewModel
import com.example.myapplication.viewmodel.HistoryViewModel
import com.google.android.material.bottomsheet.BottomSheetDialog

import androidx.recyclerview.widget.RecyclerView
import android.view.MotionEvent
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.example.myapplication.databinding.ItemDialogMenuBinding
import androidx.lifecycle.lifecycleScope

class DialogueActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_RESET_INPUT_MODE = "extra_reset_input_mode"
        const val REQUEST_RECORD_AUDIO_PERMISSION = 2
    }

    private lateinit var viewModel: DialogueViewModel
    private lateinit var historyViewModel: HistoryViewModel
    private lateinit var binding: ActivityDialogueBinding
    private lateinit var historyAdapter: HistoryAdapter
    private lateinit var topicAdapter: TopicAdapter

    // 文件上传相关
    private val selectedItems = mutableListOf<SelectedMedia>()
    private lateinit var previewAdapter: PreviewAdapter
    private lateinit var rvPreview: androidx.recyclerview.widget.RecyclerView

    private var autoScrollJob: Job? = null
    private var isUserInteracting = false

    // 科大讯飞语音识别
    private var xunfeiRecognizer: XunfeiSpeechRecognizer? = null
    private var useXunfeiRecognizer = true // 优先使用科大讯飞，失败时回退到Google

    // 语音输入相关
    private var initialY = 0f // 记录按下时的Y坐标
    private var isCancelled = false // 是否取消录音

    private val models = ModelRegistry.ALL_MODELS

    // 文件选择器
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

    // 语音识别启动器
    private val voiceRecognitionLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK) {
                    val matches =
                            result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                    if (!matches.isNullOrEmpty()) {
                        val recognizedText = matches[0]
                        // 将识别的文字填入输入框
                        binding.etInput.setText(recognizedText)
                        binding.etInput.setSelection(recognizedText.length)
                    }
                }
            }

    // 权限请求
    private val requestPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
                if (isGranted) {
                    launchImagePicker()
                } else {
                    Toast.makeText(this, "需要读取图片权限才能选择图片", Toast.LENGTH_SHORT).show()
                }
            }
    // 页面基础配置与系统适配
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityDialogueBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[DialogueViewModel::class.java]
        historyViewModel = ViewModelProvider(this)[HistoryViewModel::class.java]

        // 初始化预览适配器
        rvPreview = binding.rvPreview
        setupPreviewAdapter()

        setupWindowInsets()
        setupClickListeners()
        setupNavigation()
        setupTopics()
        observeViewModel()

        // 初始化科大讯飞语音识别
        initXunfeiSpeechRecognizer()
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
            v.setPadding(v.paddingLeft, 0, v.paddingRight, v.paddingBottom)
            insets
        }
    }

    private fun setupClickListeners() {
        // 点击菜单按钮：显示侧边栏
        binding.ivMenu.setOnClickListener { binding.drawerLayout.openDrawer(GravityCompat.END) }

        // 侧边栏搜索按钮
        binding.btnSidebarSearch.setOnClickListener {
            binding.drawerLayout.closeDrawer(GravityCompat.END)
            startActivity(Intent(this, SearchActivity::class.java))
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
        binding.ivMoreIcon.setOnClickListener { showModelSelectorDialog() }

        // 点击上传图片按钮
        binding.ivMore.setOnClickListener { showAttachmentOptions() }

        // 点击麦克风/键盘按钮：切换到语音模式或键盘模式
        binding.ivMic.setOnClickListener { viewModel.toggleVoiceMode() }

        // 点击发送按钮：发送消息
        binding.ivSend.setOnClickListener {
            val content = binding.etInput.text.toString().trim()

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
                intent.putExtra("is_web_search_enabled", binding.layoutWebSearch.isSelected)

                // 如果开启了联网搜索，给用户提示
                if (binding.layoutWebSearch.isSelected && content.isNotEmpty()) {
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
                    binding.etInput.text.clear()
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
        // 设置历史记录列表
        historyAdapter =
                HistoryAdapter(
                        mutableListOf(),
                        currentConversationId = null,
                        onItemClick = { history ->
                            binding.drawerLayout.closeDrawer(GravityCompat.END)
                            val intent = Intent(this, ChatActivity::class.java)
                            intent.putExtra(ChatActivity.EXTRA_CONVERSATION_ID, history.id)
                            startActivity(intent)
                        },
                        onItemLongClick = { history ->
                            // 长按显示菜单：置顶/取消置顶、重命名、删除
                            val items =
                                    listOf(
                                            mapOf(
                                                    "text" to
                                                            if (history.isPinned) "取消置顶"
                                                            else "置顶会话",
                                                    "icon" to R.drawable.icon_pin
                                            ),
                                            mapOf(
                                                    "text" to "重命名会话标题",
                                                    "icon" to R.drawable.icon_edit
                                            ),
                                            mapOf(
                                                    "text" to "删除会话",
                                                    "icon" to R.drawable.icon_delete
                                            )
                                    )

                            val adapter =
                                    object :
                                            android.widget.ArrayAdapter<Map<String, Any>>(
                                                    this,
                                                    R.layout.item_dialog_menu,
                                                    items
                                            ) {
                                        override fun getView(
                                                position: Int,
                                                convertView: View?,
                                                parent: android.view.ViewGroup
                                        ): View {
                                            val binding: ItemDialogMenuBinding
                                            val view: View

                                            if (convertView == null) {
                                                binding =
                                                        ItemDialogMenuBinding.inflate(
                                                                layoutInflater,
                                                                parent,
                                                                false
                                                        )
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
                                                binding.tvMenuText.setTextColor(
                                                        android.graphics.Color.RED
                                                )
                                                binding.ivMenuIcon.setColorFilter(
                                                        android.graphics.Color.RED
                                                )
                                            } else {
                                                binding.tvMenuText.setTextColor(
                                                        android.graphics.Color.BLACK
                                                )
                                                binding.ivMenuIcon.setColorFilter(
                                                        android.graphics.Color.BLACK
                                                )
                                            }

                                            return view
                                        }
                                    }

                            AlertDialog.Builder(
                                            this,
                                            R.style.RoundedDialogTheme
                                    )
                                    .setAdapter(adapter) { _, which ->
                                        when (which) {
                                            0 -> {
                                                // 切换置顶状态
                                                historyViewModel.togglePin(
                                                        history.id,
                                                        history.isPinned
                                                )
                                            }
                                            1 -> {
                                                // 重命名
                                                DialogHelper.showRenameDialog(
                                                        this,
                                                        history.title
                                                ) { newTitle ->
                                                    historyViewModel.renameConversation(
                                                            history.id,
                                                            newTitle
                                                    )
                                                }
                                            }
                                            2 -> {
                                                // 确认删除
                                                DialogHelper.showDeleteConfirmDialog(this) {
                                                    historyViewModel.deleteConversation(history.id)
                                                }
                                            }
                                        }
                                    }
                                    .show()
                        }
                )

        binding.historyRecyclerview.apply {
            layoutManager = LinearLayoutManager(this@DialogueActivity)
            adapter = historyAdapter
            // 添加分割线
            val divider =
                    DividerItemDecoration(this@DialogueActivity, DividerItemDecoration.VERTICAL)
            divider.setDrawable(ColorDrawable(android.graphics.Color.parseColor("#EEEEEE")))
            addItemDecoration(divider)
        }

        // 新对话按钮：只是重置界面状态，不实际创建对话
        binding.btnNewChat.setOnClickListener {
            binding.drawerLayout.closeDrawer(GravityCompat.END)

            binding.etInput.text.clear()
            updateSidebarSelection(isNewChat = true)
            Toast.makeText(this, "已切换到新对话", Toast.LENGTH_SHORT).show()
        }
        // 知识库按钮：仅Toast提示（待实现）
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
        binding.btnNewChat.isSelected = isNewChat
        binding.btnKnowledgeBase.isSelected = isKnowledgeBase

        // 清除历史列表选中状态
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
    // 观察 ViewModel 的 LiveData，实现 “数据变化自动更新 UI”
    private fun observeViewModel() {
        // 历史会话列表变化：更新HistoryAdapter
        historyViewModel.historyList.observe(this) { history -> historyAdapter.updateData(history) }

        viewModel.topicList.observe(this) { topics -> topicAdapter.updateData(topics) }

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
                    val imm =
                            getSystemService(INPUT_METHOD_SERVICE) as
                                    android.view.inputmethod.InputMethodManager
                    imm.showSoftInput(
                            binding.etInput,
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
                binding.etInput.text.clear()
                viewModel.onInputCleared()
            }
        }
    }
    
    // ========== 文件上传相关方法 ==========

    private fun setupPreviewAdapter() {
        previewAdapter =
                PreviewAdapter(selectedItems) { position ->
                    // 删除逻辑
                    selectedItems.removeAt(position)
                    previewAdapter.notifyItemRemoved(position)
                    // 如果删光了，更新可见性
                    updatePreviewVisibility()
                }

        rvPreview.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        rvPreview.adapter = previewAdapter
    }

    private fun addMediaItems(newItems: List<SelectedMedia>) {
        val startPos = selectedItems.size
        selectedItems.addAll(newItems)
        previewAdapter.notifyItemRangeInserted(startPos, newItems.size)
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

    private fun showAttachmentOptions() {
        val view = layoutInflater.inflate(R.layout.dialog_attachment_options, binding.root, false)
        val dialogBinding = DialogAttachmentOptionsBinding.bind(view)

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

    /** 启动图片选择器 */
    private fun launchImagePicker() {
        pickMultipleMedia.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
        )
    }

    private fun launchFilePicker() {
        pickFileLauncher.launch(arrayOf("*/*"))
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
                                android.util.Log.e("DialogueActivity", "科大讯飞启动失败", e)
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
                                    android.util.Log.d("DialogueActivity", "识别结果：$text")
                                    // 自动切换到键盘模式，这样可以在输入框看到识别内容
                                    viewModel.setVoiceMode(false)
                                    // 将识别结果填入输入框
                                    binding.etInput.setText(text)
                                    binding.etInput.setSelection(text.length)
                                    Toast.makeText(this@DialogueActivity, "识别成功", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }

                        // 设置错误监听
                        setOnErrorListener { error ->
                            Toast.makeText(this@DialogueActivity, error, Toast.LENGTH_SHORT).show()
                            // 如果科大讯飞失败，回退到Google语音识别
                            useXunfeiRecognizer = false
                        }
                    }
            android.util.Log.d("DialogueActivity", "科大讯飞语音识别初始化成功")
        } catch (e: Exception) {
            android.util.Log.e("DialogueActivity", "科大讯飞语音识别初始化失败", e)
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
                android.util.Log.e("DialogueActivity", "科大讯飞语音识别启动失败", e)
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
}
