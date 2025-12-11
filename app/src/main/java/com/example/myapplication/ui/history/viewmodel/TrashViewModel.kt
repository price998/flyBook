package com.example.myapplication.ui.history.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.myapplication.domain.ChatHistory
import com.example.myapplication.ui.history.HistoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TrashViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = HistoryRepository(application)

    private val _trashList = MutableLiveData<List<ChatHistory>>()
    val trashList: LiveData<List<ChatHistory>> = _trashList

    fun loadTrash() {
        viewModelScope.launch(Dispatchers.IO) {
            cleanupExpiredTrash() // 确保进入页面时清理过期
            val trashList = repository.getTrashList()
            withContext(Dispatchers.Main) { _trashList.value = trashList }
        }
    }
    // 恢复指定对话
    fun restore(conversationId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.restoreConversation(conversationId)
            val trashList = repository.getTrashList()
            withContext(Dispatchers.Main) { _trashList.value = trashList }
        }
    }

    fun hardDelete(conversationId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.hardDeleteConversation(conversationId)
            val trashList = repository.getTrashList()
            withContext(Dispatchers.Main) { _trashList.value = trashList }
        }
    }

    fun cleanupExpiredTrash(ttlDays: Int = 7) {
        viewModelScope.launch { repository.cleanupExpiredTrash(ttlDays) }
    }
}
