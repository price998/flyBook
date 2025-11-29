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
import kotlinx.coroutines.launch

import com.example.myapplication.data.db.RecommendedTopicEntity

class DialogueViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = HistoryRepository(application)
    private val chatRepository = ChatRepository(
        AppDatabase.getDatabase(application).messageDao(),
        AppDatabase.getDatabase(application).conversationDao()
    )
    private val topicDao = AppDatabase.getDatabase(application).recommendedTopicDao()

    private val _historyList = MutableLiveData<List<ChatHistory>>()
    val historyList: LiveData<List<ChatHistory>> = _historyList
    
    private val _topicList = MutableLiveData<List<RecommendedTopicEntity>>()
    val topicList: LiveData<List<RecommendedTopicEntity>> = _topicList

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

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
                val defaultTopics = listOf(
                    RecommendedTopicEntity(
                        title = "如何制定学习计划？",
                        category = "STUDY",
                        prompt = "请帮我制定一个高效的学习计划，目标是...",
                        sortOrder = 1,
                        createdAt = System.currentTimeMillis()
                    ),
                    RecommendedTopicEntity(
                        title = "写一首关于春天的诗",
                        category = "LIFE",
                        prompt = "请写一首关于春天的现代诗，风格要...",
                        sortOrder = 2,
                        createdAt = System.currentTimeMillis()
                    ),
                    RecommendedTopicEntity(
                        title = "解释量子力学",
                        category = "TECH",
                        prompt = "请用通俗易懂的语言解释一下量子力学。",
                        sortOrder = 3,
                        createdAt = System.currentTimeMillis()
                    ),
                    RecommendedTopicEntity(
                        title = "工作周报生成",
                        category = "WORK",
                        prompt = "请帮我生成一份工作周报，本周主要工作内容有...",
                        sortOrder = 4,
                        createdAt = System.currentTimeMillis()
                    )
                )
                topicDao.insertAll(defaultTopics)
                loadTopics()
            }
        }
    }
    
    private fun loadTopics() {
        viewModelScope.launch {
            topicDao.getAllActiveTopics().collect { topics ->
                _topicList.value = topics
            }
        }
    }

    fun loadHistory() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val list = repository.getHistoryList()
                _historyList.value = list
            } catch (e: Exception) {
                _errorMessage.value = "加载失败: ${e.message}"
            } finally {
                _isLoading.value = false
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

    fun togglePin(conversationId: String, isPinned: Boolean) {
        viewModelScope.launch {
            try {
                repository.togglePin(conversationId, !isPinned) // 取反状态
                loadHistory()
            } catch (e: Exception) {
                _errorMessage.value = "操作失败: ${e.message}"
            }
        }
    }

    fun search(query: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                if (query.isBlank()) {
                    loadHistory() // 如果搜索词为空，加载全部
                } else {
                    val list = repository.searchHistory(query)
                    _historyList.value = list
                }
            } catch (e: Exception) {
                _errorMessage.value = "搜索失败: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
}
