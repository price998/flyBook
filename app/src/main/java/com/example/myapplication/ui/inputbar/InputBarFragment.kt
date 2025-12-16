package com.example.myapplication.ui.inputbar

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.myapplication.R
import com.example.myapplication.databinding.DialogAttachmentOptionsBinding
import com.example.myapplication.databinding.LayoutChatBottomBarBinding
import com.example.myapplication.ui.inputbar.model.MediaType
import com.example.myapplication.ui.inputbar.model.SelectedMedia
import com.example.myapplication.ui.inputbar.adapters.AttachmentPreviewAdapter
import com.example.myapplication.utils.XunfeiSpeechRecognizer

/**
 * 输入栏 Fragment
 * 
 * 职责：
 * - 管理输入框、发送按钮、语音按钮等 UI 组件
 * - 处理输入模式切换（键盘/语音）
 * - 管理附件预览列表和附件选择
 * - 管理语音识别
 * - 提供回调接口供宿主 Activity 处理业务逻辑
 */
class InputBarFragment : Fragment() {

    companion object {
        private const val TAG = "InputBarFragment"
    }

    private var _binding: LayoutChatBottomBarBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: InputBarViewModel by viewModels(
        ownerProducer = { requireActivity() },
        factoryProducer = { ViewModelProvider.AndroidViewModelFactory.getInstance(requireActivity().application) }
    )
    
    private lateinit var previewAdapter: AttachmentPreviewAdapter
    private var listener: InputBarListener? = null

    // ========== 附件选择相关 ==========
    private lateinit var pickMultipleMedia: ActivityResultLauncher<PickVisualMediaRequest>
    private lateinit var pickFileLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var requestImagePermissionLauncher: ActivityResultLauncher<String>

    // ========== 语音识别相关 ==========
    private var xunfeiRecognizer: XunfeiSpeechRecognizer? = null
    private var isVoiceCancelled = false
    private lateinit var requestAudioPermissionLauncher: ActivityResultLauncher<String>

    interface InputBarListener {
        fun onSendClick(text: String, imageUris: List<Uri>, fileUris: List<Uri>)
        fun onStopClick()
        fun onModelSelectorClick()
        fun onWebSearchToggle(isEnabled: Boolean)
        fun onVoiceResult(text: String)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        registerActivityResultLaunchers()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = LayoutChatBottomBarBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupPreviewAdapter()
        setupClickListeners()
        setupInputListener()
        setupVoiceInputListener()
        initVoiceRecognizer()
        observeViewModel()
        
        // 从持久化存储同步联网搜索状态
        val isWebSearchEnabled = InputBarViewModel.getWebSearchEnabled(requireContext())
        viewModel.setWebSearchEnabled(isWebSearchEnabled)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // 先销毁语音识别器，再清理 binding
        destroyVoiceRecognizer()
        _binding = null
    }
    
    override fun onDetach() {
        super.onDetach()
        // 双重保障：清理 listener 引用
        listener = null
    }

    fun setListener(listener: InputBarListener) {
        this.listener = listener
    }

    // ========== Activity Result Launchers ==========

    private fun registerActivityResultLaunchers() {
        // 图片选择器
        pickMultipleMedia = registerForActivityResult(
            ActivityResultContracts.PickMultipleVisualMedia(9)
        ) { uris ->
            Log.d(TAG, "图片选择结果 - 数量: ${uris.size}")
            if (uris.isNotEmpty()) {
                uris.forEachIndexed { index, uri ->
                    Log.d(TAG, "选中图片 $index: $uri")
                }
                val items = uris.map { SelectedMedia(it, MediaType.IMAGE) }
                viewModel.addAttachments(items)
                Log.d(TAG, "已添加 ${items.size} 张图片到附件列表")
            } else {
                Log.d(TAG, "未选择任何图片")
            }
        }

        // 文件选择器
        pickFileLauncher = registerForActivityResult(
            ActivityResultContracts.OpenMultipleDocuments()
        ) { uris ->
            Log.d(TAG, "文件选择结果 - 数量: ${uris.size}")
            if (uris.isNotEmpty()) {
                uris.forEachIndexed { index, uri ->
                    Log.d(TAG, "选中文件 $index: $uri")
                }
                val items = uris.map { SelectedMedia(it, MediaType.FILE) }
                viewModel.addAttachments(items)
                Log.d(TAG, "已添加 ${items.size} 个文件到附件列表")
            } else {
                Log.d(TAG, "未选择任何文件")
            }
        }

        // 图片权限请求
        requestImagePermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                launchImagePicker()
            } else {
                Toast.makeText(requireContext(), "需要读取图片权限才能选择图片", Toast.LENGTH_SHORT).show()
            }
        }

        // 录音权限请求
        requestAudioPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                startVoiceRecognition()
            } else {
                Toast.makeText(requireContext(), "需要录音权限才能使用语音输入", Toast.LENGTH_SHORT).show()
            }
        }
    }


    // ========== 附件选择功能 ==========

    private fun showAttachmentOptions(anchor: View) {
        val view = layoutInflater.inflate(R.layout.dialog_attachment_options, null, false)
        val dialogBinding = DialogAttachmentOptionsBinding.bind(view)

        val width = (120 * resources.displayMetrics.density).toInt()
        val popupWindow = PopupWindow(view, width, ViewGroup.LayoutParams.WRAP_CONTENT, true)

        popupWindow.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        popupWindow.elevation = 10f

        dialogBinding.tvOptionPhoto.setOnClickListener {
            popupWindow.dismiss()
            openImagePicker()
        }

        dialogBinding.tvOptionFile.setOnClickListener {
            popupWindow.dismiss()
            launchFilePicker()
        }

        view.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )

        val yOffset = -(anchor.height + view.measuredHeight + 20)
        popupWindow.showAsDropDown(anchor, 0, yOffset)
    }

    private fun openImagePicker() {
        Log.d(TAG, "打开图片选择器")
        if (checkImagePermission()) {
            Log.d(TAG, "图片权限已授予")
            launchImagePicker()
        } else {
            Log.d(TAG, "图片权限未授予，请求权限")
            requestImagePermission()
        }
    }

    private fun checkImagePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_MEDIA_IMAGES) ==
                    PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_EXTERNAL_STORAGE) ==
                    PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestImagePermission() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        requestImagePermissionLauncher.launch(permission)
    }

    private fun launchImagePicker() {
        // 只选择图片，不包括视频（视频不支持 OCR）
        pickMultipleMedia.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    private fun launchFilePicker() {
        Log.d(TAG, "启动文件选择器")
        pickFileLauncher.launch(arrayOf("*/*"))
    }

    // ========== 语音识别功能 ==========

    private fun initVoiceRecognizer() {
        try {
            val recognizer = XunfeiSpeechRecognizer(requireContext())
            recognizer.init()
            recognizer.setOnResultListener { text ->
                if (text.isNotEmpty() && !isVoiceCancelled) {
                    requireActivity().runOnUiThread {
                        Log.d(TAG, "识别结果：$text")
                        listener?.onVoiceResult(text)
                        Toast.makeText(requireContext(), "识别成功", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            recognizer.setOnErrorListener { error ->
                requireActivity().runOnUiThread {
                    Log.e(TAG, "识别失败: $error")
                    Toast.makeText(requireContext(), "识别失败: $error", Toast.LENGTH_SHORT).show()
                }
            }
            xunfeiRecognizer = recognizer
            Log.d(TAG, "语音识别初始化成功")
        } catch (e: Exception) {
            Log.e(TAG, "语音识别初始化失败", e)
            xunfeiRecognizer = null
        }
    }

    private fun destroyVoiceRecognizer() {
        xunfeiRecognizer?.destroy()
        xunfeiRecognizer = null
    }

    private fun checkAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
    }

    private fun startVoiceRecognition() {
        isVoiceCancelled = false
        try {
            xunfeiRecognizer?.startListening()
            Log.d(TAG, "语音识别已启动")
        } catch (e: Exception) {
            Log.e(TAG, "语音识别启动失败", e)
            Toast.makeText(requireContext(), "语音识别启动失败", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopVoiceRecognition() {
        Log.d(TAG, "停止语音识别")
        if (!isVoiceCancelled) {
            xunfeiRecognizer?.stopListening()
        }
        isVoiceCancelled = false
    }

    private fun cancelVoiceRecognition() {
        Log.d(TAG, "取消语音识别")
        isVoiceCancelled = true
        xunfeiRecognizer?.cancel()
    }


    // ========== UI 设置 ==========

    private fun observeViewModel() {
        viewModel.isKeyboardMode.observe(viewLifecycleOwner) { updateInputModeUI(it) }
        viewModel.attachments.observe(viewLifecycleOwner) { updateAttachmentsList(it) }
        viewModel.inputText.observe(viewLifecycleOwner) { text ->
            if (binding.etInput.text.toString() != text) {
                binding.etInput.setText(text)
                binding.etInput.setSelection(text.length)
            }
        }
        viewModel.isWebSearchEnabled.observe(viewLifecycleOwner) { binding.layoutWebSearch.isSelected = it }
        viewModel.isGenerating.observe(viewLifecycleOwner) { updateGeneratingUI(it) }
    }

    private fun setupPreviewAdapter() {
        previewAdapter = AttachmentPreviewAdapter(
            items = mutableListOf(),
            onDelete = { viewModel.removeAttachment(it) }
        )
        binding.rvPreview.apply {
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            adapter = previewAdapter
        }
    }
    
    private fun updateAttachmentsList(attachments: List<SelectedMedia>) {
        Log.d(TAG, "更新附件预览列表 - 数量: ${attachments.size}")
        attachments.forEachIndexed { index, media ->
            Log.d(TAG, "附件 $index: 类型=${media.type}, URI=${media.uri}, 名称=${media.name}")
        }
        previewAdapter.updateItems(attachments.toMutableList())
        val visibility = if (attachments.isEmpty()) View.GONE else View.VISIBLE
        binding.rvPreview.visibility = visibility
        Log.d(TAG, "附件预览区域可见性: ${if (visibility == View.VISIBLE) "显示" else "隐藏"}")
    }

    private fun setupClickListeners() {
        binding.ivSend.setOnClickListener {
            val text = binding.etInput.text.toString().trim()
            val attachments = viewModel.attachments.value.orEmpty()
            val imageUris = attachments.filter { it.type == MediaType.IMAGE }.map { it.uri }
            val fileUris = attachments.filter { it.type == MediaType.FILE }.map { it.uri }
            listener?.onSendClick(text, imageUris, fileUris)
        }
        
        binding.ivStop.setOnClickListener { listener?.onStopClick() }
        binding.ivMore.setOnClickListener { showAttachmentOptions(it) }
        
        // 通知viewModel切换模式
        binding.ivMic.setOnClickListener {
            viewModel.toggleInputMode()
        }
        
        binding.ivMoreIcon.setOnClickListener { listener?.onModelSelectorClick() }
        
        binding.layoutWebSearch.setOnClickListener {
            viewModel.toggleWebSearch()
            val isEnabled = viewModel.isWebSearchEnabled.value ?: false
            // 状态已在 ViewModel 中同步到全局
            listener?.onWebSearchToggle(isEnabled)
        }
    }

    private fun setupInputListener() {
        binding.etInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                binding.ivSend.background = null
                binding.ivSend.clearColorFilter()
                viewModel.setInputText(s?.toString() ?: "")
            }
        })
    }

    private var initialY = 0f
    private var isCancelled = false
    
    @SuppressLint("ClickableViewAccessibility")
    private fun setupVoiceInputListener() {
        binding.tvHoldToSpeak.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialY = event.rawY
                    isCancelled = false
                    (view as? TextView)?.text = "松开发送，上滑取消"
                    view.setBackgroundColor(Color.parseColor("#E3F2FD"))
                    
                    if (checkAudioPermission()) {
                        startVoiceRecognition()
                    } else {
                        requestAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaY = initialY - event.rawY
                    if (deltaY > 150f) {
                        isCancelled = true
                        (view as? TextView)?.text = "松开手指，取消发送"
                        view.setBackgroundColor(Color.parseColor("#FFCDD2"))
                    } else {
                        isCancelled = false
                        (view as? TextView)?.text = "松开发送，上滑取消"
                        view.setBackgroundColor(Color.parseColor("#E3F2FD"))
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isCancelled) {
                        cancelVoiceRecognition()
                    } else {
                        stopVoiceRecognition()
                    }
                    (view as? TextView)?.text = getString(R.string.mic_hint)
                    view.setBackgroundColor(Color.TRANSPARENT)
                    isCancelled = false
                    view.performClick()
                    true
                }
                else -> false
            }
        }
    }

    private fun updateInputModeUI(isKeyboardMode: Boolean) {
        if (isKeyboardMode) {
            binding.tvHoldToSpeak.visibility = View.GONE
            binding.etInput.visibility = View.VISIBLE
            binding.ivMic.setImageResource(R.drawable.ic_mic)
        } else {
            binding.tvHoldToSpeak.visibility = View.VISIBLE
            binding.etInput.visibility = View.GONE
            binding.ivMic.setImageResource(R.drawable.ic_keyboard)
        }
        updateSendButtonVisibility()
    }

    private fun updateGeneratingUI(isGenerating: Boolean) {
        binding.ivStop.visibility = if (isGenerating) View.VISIBLE else View.GONE
        binding.ivSend.visibility = if (isGenerating) View.GONE else View.VISIBLE
        updateSendButtonVisibility()
    }

    private fun updateSendButtonVisibility() {
        val isKeyboardMode = viewModel.isKeyboardMode.value ?: true
        val isGenerating = viewModel.isGenerating.value ?: false
        binding.ivSend.visibility = if (!isGenerating && isKeyboardMode) View.VISIBLE else View.GONE
    }

    // ========== 公共方法 ==========

    fun addMediaItems(items: List<SelectedMedia>) = viewModel.addAttachments(items)
    fun clearMediaItems() = viewModel.clearAttachments()
    fun clearInput() = viewModel.clearInputText()
    fun setInputText(text: String) = viewModel.setInputText(text)
    fun setGenerating(isGenerating: Boolean) = viewModel.setGenerating(isGenerating)
    fun setInputMode(isKeyboardMode: Boolean) = viewModel.setInputMode(isKeyboardMode)
    fun setWebSearchEnabled(isEnabled: Boolean) = viewModel.setWebSearchEnabled(isEnabled)
    
    fun requestInputFocus() {
        if (_binding == null) return
        binding.etInput.requestFocus()
    }
}
