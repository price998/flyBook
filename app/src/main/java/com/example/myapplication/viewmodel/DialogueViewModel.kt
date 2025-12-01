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
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

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

    private val _isVoiceMode = MutableLiveData<Boolean>(false)
    val isVoiceMode: LiveData<Boolean> = _isVoiceMode

    private val _toastMessage = MutableLiveData<String?>()
    val toastMessage: LiveData<String?> = _toastMessage

    private val _shouldClearInput = MutableLiveData<Boolean>(false)
    val shouldClearInput: LiveData<Boolean> = _shouldClearInput

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
                        title = "🤝 怎样提升团队协作效率？",
                        prompt = "怎样提升团队协作效率？"
                    ),
                    RecommendedTopicEntity(
                        title = "📚 飞书知识问答是什么？",
                        prompt = "飞书知识问答是什么？"
                    ),
                    RecommendedTopicEntity(
                        title = "⚛️ 解释量子力学",
                        prompt = "请用通俗的语言给我解释量子力学的核心概念。"
                    ),
                    RecommendedTopicEntity(
                        title = "📊 OKR和KPI有什么区别？",
                        prompt = "请用通俗的语言给我解释OKR和KPI有什么区别。"
                    )
                )
                topicDao.insertAll(defaultTopics)
                loadTopics()
            }
        }
    }
    
    private fun loadTopics() {
        viewModelScope.launch {
            topicDao.getAllTopics().collect { topics ->
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
    
    fun createNewConversation(title: String, onResult: (String) -> Unit) {
        viewModelScope.launch {
            try {
                // 如果标题太长，截取前20个字符
                val displayTitle = if (title.length > 20) title.substring(0, 20) + "..." else title
                val id = withContext(Dispatchers.IO) {
                    chatRepository.createConversation(displayTitle)
                }
                onResult(id)
                loadHistory()
            } catch (e: Exception) {
                android.util.Log.e("DialogueViewModel", "创建对话失败", e)
                _errorMessage.value = "创建对话失败: ${e.message}"
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

    fun toggleVoiceMode() {
        _isVoiceMode.value = !(_isVoiceMode.value ?: false)
    }

    fun onToastShown() {
        _toastMessage.value = null
    }

    fun onInputCleared() {
        _shouldClearInput.value = false
    }
    
    fun showToast(message: String) {
        _toastMessage.value = message
    }
    
    fun clearInput() {
        _shouldClearInput.value = true
    }
}
