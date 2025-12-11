package com.example.myapplication.ui.search

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

class SearchViewModel(application: Application) : AndroidViewModel(application) {

    // Repository：负责数据获取
    private val repository = HistoryRepository(application)

    // 搜索结果列表（内部可修改）
    private val _searchResults = MutableLiveData<List<ChatHistory>>()
    // 搜索结果列表（外部只读）
    val searchResults: LiveData<List<ChatHistory>> = _searchResults

    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _searchResultsEmpty = MutableLiveData<Boolean>(false)
    val searchResultsEmpty: LiveData<Boolean> = _searchResultsEmpty

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    fun search(query: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) {
                    _isLoading.value = true
                    _errorMessage.value = null
                }

                val results =
                        if (query.isBlank()) {
                            // 如果查询为空，返回所有对话
                            repository.getHistoryList()
                        } else {
                            // 否则执行搜索
                            repository.searchHistory(query)
                        }

                withContext(Dispatchers.Main) {
                    _searchResults.value = results
                    _searchResultsEmpty.value = results.isEmpty()
                    _isLoading.value = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _errorMessage.value = "搜索失败: ${e.message}"
                    _searchResultsEmpty.value = true
                    _isLoading.value = false
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
    }
}
