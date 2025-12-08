package com.example.myapplication.ui.widget

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.R
import com.example.myapplication.databinding.LayoutChatBottomBarBinding

class ChatInputView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

    val binding: LayoutChatBottomBarBinding =
        LayoutChatBottomBarBinding.inflate(LayoutInflater.from(context), this, true)

    // 回调接口
    var onSendClickListener: ((String) -> Unit)? = null
    var onStopClickListener: (() -> Unit)? = null
    var onVoiceModeChangeListener: ((Boolean) -> Unit)? = null
    var onMoreClickListener: (() -> Unit)? = null
    var onWebSearchToggleListener: ((Boolean) -> Unit)? = null
    var onModelSwitchClickListener: (() -> Unit)? = null
    var onInputTextChangedListener: ((String) -> Unit)? = null

    // 状态
    private var isVoiceMode = false
    private var isGenerating = false

    init {
        setupListeners()
    }

    private fun setupListeners() {
        // 发送按钮
        binding.ivSend.setOnClickListener {
            val content = binding.etInput.text.toString().trim()
            if (content.isNotEmpty()) {
                onSendClickListener?.invoke(content)
            }
        }

        // 停止按钮
        binding.ivStop.setOnClickListener {
            onStopClickListener?.invoke()
        }

        // 更多按钮
        binding.ivMore.setOnClickListener {
            onMoreClickListener?.invoke()
        }
        
        // 模型切换
        binding.ivMoreIcon.setOnClickListener {
            onModelSwitchClickListener?.invoke()
        }

        // 语音/键盘切换
        binding.ivMic.setOnClickListener {
            isVoiceMode = !isVoiceMode
            updateVoiceModeUI()
            onVoiceModeChangeListener?.invoke(isVoiceMode)
        }

        // 联网搜索
        binding.layoutWebSearch.setOnClickListener {
            val newState = !binding.layoutWebSearch.isSelected
            binding.layoutWebSearch.isSelected = newState
            onWebSearchToggleListener?.invoke(newState)
        }

        // 输入框文本变化监听
        binding.etInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateSendButtonState()
                onInputTextChangedListener?.invoke(s.toString())
            }

            override fun afterTextChanged(s: Editable?) {}
        })
    }

    /**
     * 更新语音/键盘模式 UI
     */
    fun setVoiceMode(voiceMode: Boolean) {
        if (isVoiceMode == voiceMode) return
        isVoiceMode = voiceMode
        updateVoiceModeUI()
    }

    private fun updateVoiceModeUI() {
        if (isVoiceMode) {
            // 语音模式：显示按住说话，隐藏输入框
            binding.ivMic.setImageResource(R.drawable.ic_keyboard)
            binding.etInput.visibility = View.GONE
            binding.ivSend.visibility = View.GONE
            binding.dividerSend.visibility = View.GONE
            binding.tvHoldToSpeak.visibility = View.VISIBLE
            
            // 隐藏键盘
            hideKeyboard()
        } else {
            // 键盘模式：显示输入框
            binding.ivMic.setImageResource(R.drawable.ic_mic)
            binding.etInput.visibility = View.VISIBLE
            binding.ivSend.visibility = View.VISIBLE
            binding.dividerSend.visibility = View.VISIBLE
            binding.tvHoldToSpeak.visibility = View.GONE
            
            // 聚焦并显示键盘
            binding.etInput.requestFocus()
            showKeyboard()
        }
    }

    /**
     * 设置生成状态（控制发送/停止按钮）
     */
    fun setGenerating(generating: Boolean) {
        isGenerating = generating
        binding.ivStop.visibility = if (generating) View.VISIBLE else View.GONE
        binding.ivSend.visibility = if (generating) View.GONE else View.VISIBLE
        updateSendButtonState()
    }

    /**
     * 更新发送按钮状态（颜色等）
     */
    private fun updateSendButtonState() {
        if (isGenerating) return // 如果正在生成，显示的是停止按钮，不用管发送按钮

        val hasContent = binding.etInput.text.toString().trim().isNotEmpty()
        if (hasContent) {
            binding.ivSend.setColorFilter(ContextCompat.getColor(context, R.color.black))
            binding.ivSend.isEnabled = true
        } else {
            binding.ivSend.setColorFilter(android.graphics.Color.parseColor("#8F959E"))
            // 暂时不禁用点击，让点击时没反应即可，或者根据需求禁用
            // binding.ivSend.isEnabled = false 
        }
    }

    /**
     * 获取输入框内容
     */
    fun getInputText(): String {
        return binding.etInput.text.toString().trim()
    }

    /**
     * 设置输入框内容
     */
    fun setInputText(text: String) {
        binding.etInput.setText(text)
        binding.etInput.setSelection(text.length)
    }

    /**
     * 清空输入框
     */
    fun clearInput() {
        binding.etInput.text.clear()
    }

    /**
     * 设置联网搜索状态
     */
    fun setWebSearchEnabled(enabled: Boolean) {
        binding.layoutWebSearch.isSelected = enabled
    }

    /**
     * 获取预览列表 RecyclerView（供 Adapter 使用）
     */
    fun getPreviewRecyclerView(): RecyclerView {
        return binding.rvPreview
    }

    /**
     * 显示键盘
     */
    private fun showKeyboard() {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(binding.etInput, InputMethodManager.SHOW_IMPLICIT)
    }

    /**
     * 隐藏键盘
     */
    private fun hideKeyboard() {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(windowToken, 0)
    }
}
