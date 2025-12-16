package com.example.myapplication.ui.history.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.db.AppDatabase
import com.example.myapplication.domain.ChatHistory
import com.example.myapplication.ui.chat.ChatRepository
import com.example.myapplication.ui.history.HistoryRepository
import kotlinx.coroutines.launch

/** 历史对话列表的 ViewModel 在 MainActivity 和 ChatActivity 之间共享 */
class HistoryViewModel(application: Application) : AndroidViewModel(application) {
    // 1. 依赖注入：初始化数据层仓库（Repository）
    private val repository = HistoryRepository(application)
    private val chatRepository =
            ChatRepository(
                    AppDatabase.Companion.getDatabase(application).messageDao(),
                    AppDatabase.Companion.getDatabase(application).conversationDao()
            )
    // 2. 数据容器：MutableLiveData（内部可修改）+ LiveData（外部仅可观察），保证数据单向流动
    private val _historyList = MutableLiveData<List<ChatHistory>>()
    val historyList: LiveData<List<ChatHistory>> = _historyList

    @Suppress("unused") private val _errorMessage = MutableLiveData<String?>()
    @Suppress("unused") val errorMessage: LiveData<String?> = _errorMessage
    // 3. 初始化自动加载历史列表
    init {
        loadHistory()
    }

    /** 加载历史对话列表 */
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

    /** 重命名对话 */
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

    /** 删除对话 在 HistoryFragment 长按菜单中调用 */
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

    /** 切换置顶状态 在 HistoryFragment 长按菜单中调用 */
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

    /** 搜索历史对话 */
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
