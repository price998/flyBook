package com.example.myapplication.ui.common.fragments

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.myapplication.R
import com.example.myapplication.ui.common.adapter.PreviewAdapter
import com.example.myapplication.databinding.LayoutChatBottomBarBinding
import com.example.myapplication.ui.common.model.MediaType
import com.example.myapplication.ui.common.model.SelectedMedia

/**
 * 输入栏 Fragment
 * 
 * 职责：
 * - 管理输入框、发送按钮、语音按钮等 UI 组件
 * - 处理输入模式切换（键盘/语音）
 * - 管理附件预览列表
 * - 提供回调接口供宿主 Activity 处理业务逻辑
 * 
 * 使用方式：
 * 1. 在 Activity 的布局中添加 FragmentContainerView
 * 2. 实现 InputBarListener 接口
 * 3. 通过 setListener() 设置监听器
 */
class InputBarFragment : Fragment() {

    private var _binding: LayoutChatBottomBarBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var previewAdapter: PreviewAdapter
    private val selectedItems = mutableListOf<SelectedMedia>()
    
    private var listener: InputBarListener? = null
    private var isKeyboardMode = true
    private var isGenerating = false

    interface InputBarListener {
        /** 发送按钮点击 */
        fun onSendClick(text: String, imageUris: List<android.net.Uri>, fileUris: List<android.net.Uri>)
        
        /** 停止生成按钮点击 */
        fun onStopClick()
        
        /** 附件按钮点击 */
        fun onAttachmentClick(anchor: View)
        
        /** 输入模式切换（键盘/语音） */
        fun onInputModeToggle(isKeyboardMode: Boolean)
        
        /** 模型选择按钮点击 */
        fun onModelSelectorClick()
        
        /** 联网搜索开关切换 */
        fun onWebSearchToggle(isEnabled: Boolean)
        
        /** 语音输入按钮按下 */
        fun onVoiceInputStart()
        
        /** 语音输入按钮松开 */
        fun onVoiceInputEnd()
        
        /** 语音输入取消 */
        fun onVoiceInputCancel()
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
        
        // 从会话状态管理器读取联网搜索状态
        val isWebSearchEnabled = com.example.myapplication.utils.AppPreferences.getWebSearchEnabled()
        binding.layoutWebSearch.isSelected = isWebSearchEnabled
        android.util.Log.d("InputBarFragment", "联网搜索状态初始化为: $isWebSearchEnabled")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /** 设置监听器 */
    fun setListener(listener: InputBarListener) {
        this.listener = listener
    }

    /** 设置预览适配器 */
    private fun setupPreviewAdapter() {
        previewAdapter = PreviewAdapter(
            items = selectedItems,
            onDelete = { position ->
                selectedItems.removeAt(position)
                previewAdapter.notifyItemRemoved(position)
                updatePreviewVisibility()
            }
        )
        
        binding.rvPreview.apply {
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            adapter = previewAdapter
        }
    }

    /** 设置点击监听器 */
    private fun setupClickListeners() {
        // 发送按钮
        binding.ivSend.setOnClickListener {
            val text = binding.etInput.text.toString().trim()
            val imageUris = selectedItems.filter { it.type == MediaType.IMAGE }.map { it.uri }
            val fileUris = selectedItems.filter { it.type == MediaType.FILE }.map { it.uri }
            listener?.onSendClick(text, imageUris, fileUris)
        }
        
        // 停止生成按钮
        binding.ivStop.setOnClickListener {
            listener?.onStopClick()
        }
        
        // 附件按钮
        binding.ivMore.setOnClickListener { view ->
            listener?.onAttachmentClick(view)
        }
        
        // 输入模式切换按钮
        binding.ivMic.setOnClickListener {
            toggleInputMode()
        }
        
        // 模型选择按钮
        binding.ivMoreIcon.setOnClickListener {
            listener?.onModelSelectorClick()
        }
        
        // 联网搜索开关
        binding.layoutWebSearch.setOnClickListener {
            android.util.Log.d("InputBarFragment", "联网搜索按钮被点击")
            val isEnabled = !binding.layoutWebSearch.isSelected
            binding.layoutWebSearch.isSelected = isEnabled
            // 保存到会话状态管理器
            com.example.myapplication.utils.AppPreferences.setWebSearchEnabled(isEnabled)
            android.util.Log.d("InputBarFragment", "联网搜索状态切换为: $isEnabled")
            listener?.onWebSearchToggle(isEnabled)
        }
    }

    /** 设置输入监听器 */
    private fun setupInputListener() {
        binding.etInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                binding.ivSend.background = null
                binding.ivSend.clearColorFilter()
            }
        })
    }

    /** 设置语音输入监听器 */
    private var initialY = 0f
    private var isCancelled = false
    
    @android.annotation.SuppressLint("ClickableViewAccessibility")
    private fun setupVoiceInputListener() {
        binding.tvHoldToSpeak.setOnTouchListener { view, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    initialY = event.rawY
                    isCancelled = false
                    
                    // 更新 UI
                    (view as? android.widget.TextView)?.text = "松开发送，上滑取消"
                    view.setBackgroundColor(android.graphics.Color.parseColor("#E3F2FD"))
                    
                    listener?.onVoiceInputStart()
                    true
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    val deltaY = initialY - event.rawY
                    if (deltaY > 150f) {
                        isCancelled = true
                        (view as? android.widget.TextView)?.text = "松开手指，取消发送"
                        view.setBackgroundColor(android.graphics.Color.parseColor("#FFCDD2"))
                    } else {
                        isCancelled = false
                        (view as? android.widget.TextView)?.text = "松开发送，上滑取消"
                        view.setBackgroundColor(android.graphics.Color.parseColor("#E3F2FD"))
                    }
                    true
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    if (isCancelled) {
                        listener?.onVoiceInputCancel()
                    } else {
                        listener?.onVoiceInputEnd()
                    }
                    
                    // 恢复 UI
                    (view as? android.widget.TextView)?.text = getString(R.string.mic_hint)
                    view.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    isCancelled = false
                    view.performClick()
                    true
                }
                else -> false
            }
        }
    }

    /** 切换输入模式 */
    private fun toggleInputMode() {
        isKeyboardMode = !isKeyboardMode
        updateInputModeUI()
        listener?.onInputModeToggle(isKeyboardMode)
    }

    /** 更新输入模式 UI */
    private fun updateInputModeUI() {
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

    /** 更新发送按钮可见性 */
    private fun updateSendButtonVisibility() {
        binding.ivSend.visibility = if (!isGenerating && isKeyboardMode) View.VISIBLE else View.GONE
    }

    /** 更新预览列表可见性 */
    private fun updatePreviewVisibility() {
        binding.rvPreview.visibility = if (selectedItems.isEmpty()) View.GONE else View.VISIBLE
    }

    // ========== 公共方法：供宿主 Activity 调用 ==========

    /** 添加附件 */
    fun addMediaItems(items: List<SelectedMedia>) {
        if (_binding == null) return
        val startPosition = selectedItems.size
        selectedItems.addAll(items)
        previewAdapter.notifyItemRangeInserted(startPosition, items.size)
        updatePreviewVisibility()
    }

    /** 清空附件 */
    fun clearMediaItems() {
        if (_binding == null) return
        val itemCount = selectedItems.size
        selectedItems.clear()
        previewAdapter.notifyItemRangeRemoved(0, itemCount)
        updatePreviewVisibility()
    }

    /** 清空输入框 */
    fun clearInput() {
        if (_binding == null) return
        binding.etInput.text.clear()
    }

    /** 设置输入文本 */
    fun setInputText(text: String) {
        if (_binding == null) return
        binding.etInput.setText(text)
        binding.etInput.setSelection(text.length)
    }

    /** 设置生成状态 */
    fun setGenerating(isGenerating: Boolean) {
        if (_binding == null) return
        this.isGenerating = isGenerating
        binding.ivStop.visibility = if (isGenerating) View.VISIBLE else View.GONE
        binding.ivSend.visibility = if (isGenerating) View.GONE else View.VISIBLE
        updateSendButtonVisibility()
    }

    /** 设置输入模式（键盘/语音） */
    fun setInputMode(isKeyboardMode: Boolean) {
        if (_binding == null) return
        if (this.isKeyboardMode != isKeyboardMode) {
            this.isKeyboardMode = isKeyboardMode
            updateInputModeUI()
        }
    }

    /** 设置联网搜索状态 */
    fun setWebSearchEnabled(isEnabled: Boolean) {
        if (_binding == null) return
        binding.layoutWebSearch.isSelected = isEnabled
    }


    /** 请求输入框焦点 */
    fun requestInputFocus() {
        if (_binding == null) return
        binding.etInput.requestFocus()
    }
}
