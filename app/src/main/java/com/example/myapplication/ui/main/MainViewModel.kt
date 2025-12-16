package com.example.myapplication.ui.main

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.db.AppDatabase
import com.example.myapplication.data.model.RecommendedTopic
import com.example.myapplication.data.source.DefaultTopics
import com.example.myapplication.ui.chat.ChatRepository
import com.example.myapplication.ui.common.managers.ModelManager
import kotlinx.coroutines.launch

/**
 * Main view model.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val _topicList = MutableLiveData<List<RecommendedTopic>>()
    val topicList: LiveData<List<RecommendedTopic>> = _topicList

    private val _toastMessage = MutableLiveData<String?>()
    val toastMessage: LiveData<String?> = _toastMessage

    // 初始化repository，传入Dao
    private val chatRepository =
        ChatRepository(
            AppDatabase.Companion.getDatabase(application).messageDao(),
            AppDatabase.Companion.getDatabase(application).conversationDao()
        )

    init {
        // 确保 ModelManager 初始化
        ModelManager.getCurrentModel(application)
        loadTopics()
    }

    private fun loadTopics() {
        _topicList.value = DefaultTopics.getDefaultTopics()
    }


    /**
     * Toast 消息已显示
     */
    fun onToastShown() {
        _toastMessage.value = null
    }

    /**
     * 显示 Toast 消息
     */
    @Suppress("unused")
    fun showToast(message: String) {
        _toastMessage.value = message
    }

    /** 创建新对话 */
    fun createNewConversation(title: String, onResult: (String) -> Unit) {
        Log.d("MainViewModel", "开始创建对话: $title")
        viewModelScope.launch {
            try {
                // 调用Repository的对话创建方法（内部处理截断）
                val id = chatRepository.createConversationWithTruncatedTitle(title)
                // 回调
                onResult(id)
            } catch (e: Exception) {
                _toastMessage.value = "创建对话失败: ${e.message}"
            }
        }
    }

    /** 生成一条长对话假数据 */
    fun generateFakeConversation(pairCount: Int = 1000, onResult: (String) -> Unit) {
        viewModelScope.launch {
            try {
                // 调用Repository的生成假数据方法
                val conversationId = chatRepository.generateFakeData(pairCount)
                onResult(conversationId)
            } catch (e: Exception) {
                _toastMessage.value = "生成假数据失败: ${e.message}"
            }
        }
    }
}
