package com.example.myapplication.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.db.AppDatabase
import com.example.myapplication.model.ChatHistory
import com.example.myapplication.repository.ChatRepository
import com.example.myapplication.repository.HistoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 历史对话列表的 ViewModel
 * 在 DialogueActivity 和 ChatActivity 之间共享
 */
class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = HistoryRepository(application)
    private val chatRepository = ChatRepository(
        AppDatabase.getDatabase(application).messageDao(),
        AppDatabase.getDatabase(application).conversationDao()
    )

    private val _historyList = MutableLiveData<List<ChatHistory>>()
    val historyList: LiveData<List<ChatHistory>> = _historyList

    @Suppress("unused")
    private val _errorMessage = MutableLiveData<String?>()
    @Suppress("unused")
    val errorMessage: LiveData<String?> = _errorMessage

    init {
        loadHistory()
    }

    /**
     * 加载历史对话列表
     */
    fun loadHistory() {
        viewModelScope.launch {
            _errorMessage.value = null
            try {
                val list = repository.getHistoryList()
                _historyList.value = list
            } catch (e: Exception) {
                _errorMessage.value = "加载失败: ${e.message}"
            }
        }
    }

    /**
     * 创建新对话
     */
    fun createNewConversation(title: String, onResult: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val displayTitle = if (title.length > 20) title.substring(0, 20) + "..." else title
                val id = withContext(Dispatchers.IO) {
                    chatRepository.createConversation(displayTitle)
                }
                onResult(id)
                loadHistory()
            } catch (e: Exception) {
                android.util.Log.e("HistoryViewModel", "创建对话失败", e)
                _errorMessage.value = "创建对话失败: ${e.message}"
            }
        }
    }

    /**
     * 重命名对话
     */
    fun renameConversation(conversationId: String, newTitle: String) {
        viewModelScope.launch {
            try {
                chatRepository.updateConversationTitle(conversationId, newTitle)
                loadHistory()
            } catch (e: Exception) {
                _errorMessage.value = "重命名失败: ${e.message}"
            }
        }
    }

    /**
     * 删除对话（预留功能）
     */
    @Suppress("unused")
    fun deleteConversation(conversationId: String) {
        viewModelScope.launch {
            try {
                repository.deleteConversation(conversationId)
                loadHistory()
            } catch (e: Exception) {
                _errorMessage.value = "删除失败: ${e.message}"
            }
        }
    }

    /**
     * 切换置顶状态（预留功能）
     */
    @Suppress("unused")
    fun togglePin(conversationId: String, currentIsPinned: Boolean) {
        viewModelScope.launch {
            try {
                repository.togglePin(conversationId, !currentIsPinned)
                loadHistory()
            } catch (e: Exception) {
                _errorMessage.value = "操作失败: ${e.message}"
            }
        }
    }

    /**
     * 搜索历史对话（预留功能）
     */
    @Suppress("unused")
    fun search(query: String) {
        viewModelScope.launch {
            try {
                if (query.isBlank()) {
                    loadHistory()
                } else {
                    val list = repository.searchHistory(query)
                    _historyList.value = list
                }
            } catch (e: Exception) {
                _errorMessage.value = "搜索失败: ${e.message}"
            }
        }
    }
}
