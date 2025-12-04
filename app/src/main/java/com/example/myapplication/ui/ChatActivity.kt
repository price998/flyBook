package com.example.myapplication.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import com.example.myapplication.model.SelectedMedia
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.DividerItemDecoration
import android.graphics.drawable.ColorDrawable
import androidx.recyclerview.widget.RecyclerView
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
import com.example.myapplication.viewmodel.ChatViewModel.OCRProgress
import com.example.myapplication.viewmodel.HistoryViewModel
import com.example.myapplication.viewmodel.MessageUpdateEvent
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.bottomsheet.BottomSheetDialog
import io.noties.markwon.Markwon
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.image.ImagesPlugin
import io.noties.markwon.linkify.LinkifyPlugin
import kotlin.math.abs
import com.example.myapplication.model.MediaType
import com.example.myapplication.adapter.PreviewAdapter
import android.text.Editable
import android.text.TextWatcher

class ChatActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_CONVERSATION_ID = "extra_conversation_id"
        const val EXTRA_INITIAL_QUESTION = "extra_initial_question"
        const val EXTRA_ENABLE_SEARCH = "extra_enable_search"
    }

    private var isKeyboardMode = false
    private var isGeneratingResponse = false
    private var ocrProgressDialog: AlertDialog? = null
    private var isNetworkSearchEnabled = false
    private var previousMessageCount = 0


    private val selectedItems = mutableListOf<SelectedMedia>()
    private lateinit var adapter: PreviewAdapter
    private lateinit var rvPreview: RecyclerView

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
        updateSendButtonVisibility()

        // 初始化 RecyclerView 用于预览
        rvPreview = binding.rvPreview
        setupPreviewAdapter()

        viewModel = ViewModelProvider(this)[ChatViewModel::class.java]
        historyViewModel = ViewModelProvider(this)[HistoryViewModel::class.java]

        setupWindowInsets()
        dialogueViewModel = ViewModelProvider(this)[DialogueViewModel::class.java]
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
            isGeneratingResponse = isGenerating
            binding.ivStop.visibility = if (isGenerating) View.VISIBLE else View.GONE
            updateSendButtonVisibility()
        }

        // 观察OCR解析进度
        viewModel.ocrProgress.observe(this) { progress ->
            handleOCRProgress(progress)
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
        // 新建对话按钮点击事件 - 跳转到主界面
        binding.icNewChat.setOnClickListener {
            val intent = Intent(this, DialogueActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            intent.putExtra(DialogueActivity.EXTRA_RESET_INPUT_MODE, true)
            startActivity(intent)
            finish()
        }

        // 查看历史对话按钮点击事件
        binding.ivMenu.setOnClickListener {
            binding.chatDrawerLayout.openDrawer(GravityCompat.END)
            updateSidebarSelection(isNewChat = true)
        }

        // 菜单-知识库按钮
        binding.btnKnowledgeBase.setOnClickListener {
            binding.chatDrawerLayout.closeDrawers()
            val intent = Intent(this, DialogueActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            intent.putExtra(DialogueActivity.EXTRA_RESET_INPUT_MODE, true)
            startActivity(intent)
            finish()
        }

        // 历史抽屉中的知识库按钮
        binding.btnKnowledgeBase.setOnClickListener {
            Toast.makeText(this, "知识库", Toast.LENGTH_SHORT).show()
            updateSidebarSelection(isKnowledgeBase = true)
        }


        binding.chatAddButton.setOnClickListener { showAttachmentOptions() }
        binding.ivMic.setOnClickListener { toggleInputMode() }

        binding.ivMoreIcon.setOnClickListener { showModelSelectorDialog() }

        binding.layoutWebSearch.setOnClickListener { toggleNetworkSearchBackground() }

        binding.ivSend.setOnClickListener { sendMessage() }

        binding.ivStop.setOnClickListener {
            viewModel.stopGeneration()
            Toast.makeText(this, "正在停止生成...", Toast.LENGTH_SHORT).show()
        }

        setupInputListener()
    }

    private fun setupWindowInsets() {
        // 确保DrawerLayout不会被状态栏遮挡
        ViewCompat.setOnApplyWindowInsetsListener(binding.chatDrawerLayout) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            insets
        }

        // 确保输入布局在键盘显示时保持可见
        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomChatInputArea) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
            val bottomPadding = java.lang.Math.max(imeInsets.bottom, systemBars.bottom)
            val params = v.layoutParams as android.view.ViewGroup.MarginLayoutParams
            params.bottomMargin = bottomPadding
            v.layoutParams = params

            insets
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

    private fun updateSendButtonVisibility() {
        binding.chatSendButton.visibility =
            if (!isGeneratingResponse && isKeyboardMode) View.VISIBLE else View.GONE
    }

    private fun clearSelectedMedia() {
        if (selectedItems.isEmpty()) return
        selectedItems.clear()
        adapter.notifyDataSetChanged()
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
                            2 -> {
                                // 确认删除
                                DialogHelper.showDeleteConfirmDialog(this) {
                                    historyViewModel.deleteConversation(history.id)
                                    // 如果删除的是当前会话，退出或清空
                                    if (history.id == viewModel.currentConversationId) {
                                        finish()
                                    }
                                }

        AlertDialog.Builder(this)
            .setTitle("重命名对话")
            .setView(editText)
            .setPositiveButton("确定") { _, _ ->
                val newTitle = editText.text.toString().trim()
                if (newTitle.isNotEmpty() && newTitle != currentTitle) {

                    historyViewModel.renameConversation(conversationId, newTitle)
                    Toast.makeText(this, "已重命名", Toast.LENGTH_SHORT).show()
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

            val imageUris = selectedItems
                .filter { it.type == MediaType.IMAGE }
                .map { it.uri }
            val fileUris = selectedItems
                .filter { it.type == MediaType.FILE }
                .map { it.uri }

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

    /**
     * 打开图片选择器
     */
    private fun openImagePicker() {
        // 检查权限
        if (checkImagePermission()) {
            launchImagePicker()
        } else {
            requestImagePermission()
        }
    }

    /**
     * 检查图片读取权限
     */
    private fun checkImagePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ 使用新的权限
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_MEDIA_IMAGES
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            // Android 12 及以下
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * 请求图片读取权限
     */
    private fun requestImagePermission() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        requestPermissionLauncher.launch(permission)
    }

    /**
     * 权限请求结果处理
     */
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            launchImagePicker()
        } else {
            Toast.makeText(
                this,
                "需要读取图片权限才能选择图片",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    /**
     * 启动图片选择器
     */
    private fun launchImagePicker() {
        pickMultipleMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
    }

    private val pickMultipleMedia = registerForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(9)
    ) { uris ->
        if (uris.isNotEmpty()) {
            val items = uris.map { SelectedMedia(it, MediaType.IMAGE) }
            addMediaItems(items)
        }
    }

    private val pickFileLauncher = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            val items = uris.map { SelectedMedia(it, MediaType.FILE) }
            addMediaItems(items)
        }
    }

    private fun launchFilePicker() {
        pickFileLauncher.launch(arrayOf("*/*"))
    }

    private fun showAttachmentOptions() {
        val view = layoutInflater.inflate(R.layout.dialog_attachment_options, null)

        val width = (120 * resources.displayMetrics.density).toInt()

        val popupWindow = android.widget.PopupWindow(
            view,
            width,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        )

        popupWindow.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        popupWindow.elevation = 10f

        view.findViewById<View>(R.id.tv_option_photo).setOnClickListener {
            popupWindow.dismiss()
            openImagePicker()
        }

        view.findViewById<View>(R.id.tv_option_file).setOnClickListener {
            popupWindow.dismiss()
            launchFilePicker()
        }

        // Measure view to calculate position
        view.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )

        val popupHeight = view.measuredHeight
        val anchor = binding.chatAddButton

        // Show above the button
        // y offset: negative of (anchor height + popup height + margin)
        val yOffset = -(anchor.height + popupHeight + 20)

        popupWindow.showAsDropDown(anchor, 0, yOffset)
    }

    private fun setupPreviewAdapter() {
        adapter = PreviewAdapter(selectedItems) { position ->
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



    /**
     * 处理OCR解析进度
     */
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
                Toast.makeText(
                    this,
                    "识别成功，已发送",
                    Toast.LENGTH_SHORT
                ).show()
            }
            is OCRProgress.Error -> {
                ocrProgressDialog?.dismiss()
                ocrProgressDialog = null
                Toast.makeText(
                    this,
                    progress.message,
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    /**
     * 显示OCR解析进度对话框
     */
    private fun showOCRProgressDialog() {
        ocrProgressDialog = MaterialAlertDialogBuilder(this)
            .setTitle("正在识别图片文字...")
            .setMessage("请稍候，正在使用OCR识别图片中的文字")
            .setCancelable(false)
            .create()
        ocrProgressDialog?.show()
    }

    private fun setupInputListener() {
        binding.chatMessageInputEdittext.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                val content = s.toString().trim()
                if (content.isNotEmpty()) {
                    binding.chatSendButton.setBackgroundResource(R.drawable.rounded_corner_blue_background)
                    binding.chatSendButton.setColorFilter(ContextCompat.getColor(this@ChatActivity, R.color.white))
                } else {
                    binding.chatSendButton.background = null
                    binding.chatSendButton.clearColorFilter()
                }
            }
        })
    }
}

