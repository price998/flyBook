package com.example.myapplication.ui.history


import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.myapplication.model.ChatHistory
import com.example.myapplication.repository.HistoryRepository
import kotlinx.coroutines.launch

class TrashViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = HistoryRepository(application)

    private val _trashList = MutableLiveData<List<ChatHistory>>()
    val trashList: LiveData<List<ChatHistory>> = _trashList

    fun loadTrash() {
        viewModelScope.launch {
            cleanupExpiredTrash() // 确保进入页面时清理过期
            _trashList.value = repository.getTrashList()
        }
    }
    //恢复指定对话
    fun restore(conversationId: String) {
        viewModelScope.launch {
            repository.restoreConversation(conversationId)
            _trashList.value = repository.getTrashList()
        }
    }

    fun hardDelete(conversationId: String) {
        viewModelScope.launch {
            repository.hardDeleteConversation(conversationId)
            _trashList.value = repository.getTrashList()
        }
    }

    fun cleanupExpiredTrash(ttlDays: Int = 7) {
        viewModelScope.launch {
            repository.cleanupExpiredTrash(ttlDays)
        }
    }
}
