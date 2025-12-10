package com.example.myapplication.ui.inputbar

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.myapplication.ui.inputbar.model.SelectedMedia

/**
 * 输入栏 ViewModel
 * 
 * 职责：
 * - 管理输入框状态（键盘模式/语音模式）
 * - 管理附件列表
 * - 管理联网搜索开关状态（持久化存储）
 * - 管理生成状态
 * - 在 MainActivity 和 ChatActivity 之间共享输入栏状态
 * - 通过 Repository 处理业务逻辑
 * 
 * 设计理念：
 * - 类似 HistoryViewModel，作为共享的状态管理器
 * - 输入栏的所有状态都由 ViewModel 管理
 * - Fragment 只负责 UI 展示和用户交互
 * - Activity 通过 ViewModel 获取和更新状态
 * - 使用 Repository 处理数据操作
 * - 联网搜索状态使用 SharedPreferences 持久化
 */
class InputBarViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val PREFS_NAME = "inputbar_prefs"
        private const val KEY_WEB_SEARCH_ENABLED = "web_search_enabled"
        
        /**
         * 获取联网搜索状态（从 SharedPreferences 读取）
         * 静态方法供其他模块（如 ChatViewModel）调用
         */
        fun getWebSearchEnabled(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getBoolean(KEY_WEB_SEARCH_ENABLED, false)
        }
        
        /**
         * 设置联网搜索状态（写入 SharedPreferences）
         * 静态方法供其他模块调用
         */
        fun setWebSearchEnabled(context: Context, enabled: Boolean) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putBoolean(KEY_WEB_SEARCH_ENABLED, enabled).apply()
        }
        
        /**
         * 重置会话状态
         */
        fun resetSessionState(context: Context) {
            setWebSearchEnabled(context, false)
        }
        
        // 兼容旧代码的无参方法（已废弃，建议使用带 Context 的版本）
        @Deprecated("Use getWebSearchEnabled(context) instead", ReplaceWith("getWebSearchEnabled(context)"))
        fun getWebSearchEnabled(): Boolean = false
    }

    // 注意：prefs 必须在其他使用它的属性之前初始化
    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ========== 输入模式状态 ==========
    
    private val _isKeyboardMode = MutableLiveData(true)
    val isKeyboardMode: LiveData<Boolean> = _isKeyboardMode
    
    /**
     * 设置输入模式
     * @param isKeyboardMode true=键盘模式，false=语音模式
     */
    fun setInputMode(isKeyboardMode: Boolean) {
        if (_isKeyboardMode.value != isKeyboardMode) {
            _isKeyboardMode.value = isKeyboardMode
        }
    }
    
    /**
     * 切换输入模式
     */
    fun toggleInputMode() {
        _isKeyboardMode.value = !(_isKeyboardMode.value ?: true)
    }

    // ========== 附件列表状态 ==========
    
    private val _attachments = MutableLiveData<List<SelectedMedia>>(emptyList())
    val attachments: LiveData<List<SelectedMedia>> = _attachments
    
    /**
     * 添加附件
     */
    fun addAttachments(items: List<SelectedMedia>) {
        val currentList = _attachments.value.orEmpty().toMutableList()
        android.util.Log.d("InputBarViewModel", "添加附件 - 当前数量: ${currentList.size}, 新增数量: ${items.size}")
        currentList.addAll(items)
        _attachments.value = currentList
        android.util.Log.d("InputBarViewModel", "附件添加完成 - 总数量: ${currentList.size}")
    }
    

    /**
     * 移除附件
     */
    fun removeAttachment(position: Int) {
        val currentList = _attachments.value.orEmpty().toMutableList()
        android.util.Log.d("InputBarViewModel", "移除附件 - 位置: $position, 当前数量: ${currentList.size}")
        if (position in currentList.indices) {
            val removed = currentList.removeAt(position)
            _attachments.value = currentList
            android.util.Log.d("InputBarViewModel", "附件已移除 - 类型: ${removed.type}, 剩余数量: ${currentList.size}")
        } else {
            android.util.Log.w("InputBarViewModel", "移除附件失败 - 位置越界: $position")
        }
    }
    
    /**
     * 清空附件
     */
    fun clearAttachments() {
        val count = _attachments.value?.size ?: 0
        if (_attachments.value?.isNotEmpty() == true) {
            android.util.Log.d("InputBarViewModel", "清空附件 - 数量: $count")
            _attachments.value = emptyList()
            android.util.Log.d("InputBarViewModel", "附件已清空")
        }
    }

    // ========== 输入文本状态 ==========
    
    private val _inputText = MutableLiveData("")
    val inputText: LiveData<String> = _inputText
    
    /**
     * 设置输入文本
     */
    fun setInputText(text: String) {
        _inputText.value = text
    }
    
    /**
     * 清空输入文本
     */
    fun clearInputText() {
        if (_inputText.value?.isNotEmpty() == true) {
            _inputText.value = ""
        }
    }

    // ========== 联网搜索状态（持久化存储） ==========
    
    private val _isWebSearchEnabled = MutableLiveData(loadWebSearchEnabled())
    val isWebSearchEnabled: LiveData<Boolean> = _isWebSearchEnabled
    
    /**
     * 从 SharedPreferences 加载联网搜索状态
     */
    private fun loadWebSearchEnabled(): Boolean {
        return prefs.getBoolean(KEY_WEB_SEARCH_ENABLED, false)
    }
    
    /**
     * 设置联网搜索状态（同时持久化）
     */
    fun setWebSearchEnabled(isEnabled: Boolean) {
        if (_isWebSearchEnabled.value != isEnabled) {
            _isWebSearchEnabled.value = isEnabled
            // 持久化到 SharedPreferences
            prefs.edit().putBoolean(KEY_WEB_SEARCH_ENABLED, isEnabled).apply()
        }
    }
    
    /**
     * 切换联网搜索状态
     */
    fun toggleWebSearch() {
        val newValue = !(_isWebSearchEnabled.value ?: false)
        _isWebSearchEnabled.value = newValue
        // 持久化到 SharedPreferences
        prefs.edit().putBoolean(KEY_WEB_SEARCH_ENABLED, newValue).apply()
    }

    // ========== 生成状态 ==========
    
    private val _isGenerating = MutableLiveData(false)
    val isGenerating: LiveData<Boolean> = _isGenerating
    
    /**
     * 设置生成状态
     */
    fun setGenerating(isGenerating: Boolean) {
        if (_isGenerating.value != isGenerating) {
            _isGenerating.value = isGenerating
        }
    }


}
