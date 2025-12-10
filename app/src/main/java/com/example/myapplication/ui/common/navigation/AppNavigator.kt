package com.example.myapplication.ui.common.navigation

import android.app.Activity
import android.content.Intent
import com.example.myapplication.ui.chat.ChatActivity
import com.example.myapplication.ui.main.MainActivity
import com.example.myapplication.ui.search.SearchActivity

/**
 * 应用导航器
 * 
 * 统一管理页面跳转逻辑，避免在多个 Activity 中重复代码
 * 
 * 职责：
 * - 封装页面跳转逻辑
 * - 管理 Intent 参数传递
 * - 提供统一的导航 API
 */
object AppNavigator {
    
    /**
     * 跳转到搜索页面
     */
    fun navigateToSearch(activity: Activity) {
        val intent = Intent(activity, SearchActivity::class.java)
        activity.startActivity(intent)
    }
    
    /**
     * 跳转到新对话（MainActivity）
     * 
     * @param activity 当前 Activity
     * @param resetInputMode 是否重置输入模式
     * @param finishCurrent 是否关闭当前 Activity
     */
    fun navigateToNewChat(
        activity: Activity,
        resetInputMode: Boolean = true,
        finishCurrent: Boolean = false
    ) {
        val intent = Intent(activity, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(MainActivity.EXTRA_RESET_INPUT_MODE, resetInputMode)
        }
        activity.startActivity(intent)
        if (finishCurrent) {
            activity.finish()
        }
    }
    
    /**
     * 跳转到聊天页面
     * 
     * @param activity 当前 Activity
     * @param conversationId 对话 ID（可选，为空则创建新对话）
     * @param initialQuestion 初始问题（可选）
     * @param isVoiceMode 是否为语音模式
     * @param imageUris 图片 URI 列表
     * @param fileUris 文件 URI 列表
     */
    fun navigateToChat(
        activity: Activity,
        conversationId: String? = null,
        initialQuestion: String? = null,
        isVoiceMode: Boolean = false,
        imageUris: List<String>? = null,
        fileUris: List<String>? = null
    ) {
        val intent = Intent(activity, ChatActivity::class.java).apply {
            conversationId?.let { putExtra(ChatActivity.EXTRA_CONVERSATION_ID, it) }
            initialQuestion?.let { putExtra(ChatActivity.EXTRA_INITIAL_QUESTION, it) }
            putExtra("is_voice_mode", isVoiceMode)
            imageUris?.let { putStringArrayListExtra("image_uris", ArrayList(it)) }
            fileUris?.let { putStringArrayListExtra("file_uris", ArrayList(it)) }
        }
        activity.startActivity(intent)
    }
    
    /**
     * 跳转到历史对话
     * 
     * @param activity 当前 Activity
     * @param conversationId 对话 ID
     */
    fun navigateToHistoryChat(activity: Activity, conversationId: String) {
        navigateToChat(activity, conversationId = conversationId)
    }
}
