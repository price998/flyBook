package com.example.myapplication.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.DefaultTopics
import com.example.myapplication.data.db.AppDatabase
import com.example.myapplication.data.db.RecommendedTopicEntity
import com.example.myapplication.model.ChatHistory
import com.example.myapplication.repository.ChatRepository
import com.example.myapplication.repository.HistoryRepository
import kotlinx.coroutines.launch

class DialogueViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = HistoryRepository(application)
    private val chatRepository =
            ChatRepository(
                    AppDatabase.getDatabase(application).messageDao(),
                    AppDatabase.getDatabase(application).conversationDao()
            )
    private val topicDao = AppDatabase.getDatabase(application).recommendedTopicDao()

    private val _historyList = MutableLiveData<List<ChatHistory>>()
    val historyList: LiveData<List<ChatHistory>> = _historyList

    private val _topicList = MutableLiveData<List<RecommendedTopicEntity>>()
    val topicList: LiveData<List<RecommendedTopicEntity>> = _topicList


    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    init {
        initializeTopics()
        loadHistory()
        loadTopics()
    }

    private fun initializeTopics() {
        viewModelScope.launch {
            if (topicDao.getCount() == 0) {
                topicDao.insertAll(DefaultTopics.getDefaultTopics())
                loadTopics()
            }
        }
    }

    private fun loadTopics() {
        viewModelScope.launch {
            topicDao.getAllActiveTopics().collect { topics -> _topicList.value = topics }
        }
    }

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

    fun deleteConversation(conversationId: String) {
        viewModelScope.launch {
            try {
                repository.deleteConversation(conversationId)
                loadHistory() // 刷新列表
            } catch (e: Exception) {
                _errorMessage.value = "删除失败: ${e.message}"
            }
        }
    }

    fun togglePin(conversationId: String, currentIsPinned: Boolean) {
        viewModelScope.launch {
            try {
                // 切换置顶状态：当前是置顶则取消，当前未置顶则置顶
                repository.togglePin(conversationId, !currentIsPinned)
                loadHistory()
            } catch (e: Exception) {
                _errorMessage.value = "操作失败: ${e.message}"
            }
        }
    }

    fun search(query: String) {
        viewModelScope.launch {
            try {
                if (query.isBlank()) {
                    loadHistory() // 如果搜索词为空，加载全部
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
