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

class DialogueViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = HistoryRepository(application)
    private val chatRepository = ChatRepository(
        AppDatabase.getDatabase(application).messageDao(),
        AppDatabase.getDatabase(application).conversationDao()
    )

    private val _historyList = MutableLiveData<List<ChatHistory>>()
    val historyList: LiveData<List<ChatHistory>> = _historyList

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    init {
        loadHistory()
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
                // 重新加载历史列表以显示更新后的标题
                loadHistory()
            } catch (e: Exception) {
                _errorMessage.value = "重命名失败: ${e.message}"
            }
        }
    }
}
