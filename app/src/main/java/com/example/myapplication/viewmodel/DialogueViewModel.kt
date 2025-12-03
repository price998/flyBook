package com.example.myapplication.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.DefaultTopics
import com.example.myapplication.data.db.AppDatabase
import com.example.myapplication.data.db.RecommendedTopicEntity
import kotlinx.coroutines.launch


class DialogueViewModel(application: Application) : AndroidViewModel(application) {

    private val topicDao = AppDatabase.getDatabase(application).recommendedTopicDao()

    private val _topicList = MutableLiveData<List<RecommendedTopicEntity>>()
    val topicList: LiveData<List<RecommendedTopicEntity>> = _topicList

    private val _isVoiceMode = MutableLiveData(false)
    val isVoiceMode: LiveData<Boolean> = _isVoiceMode

    private val _toastMessage = MutableLiveData<String?>()
    val toastMessage: LiveData<String?> = _toastMessage

    private val _shouldClearInput = MutableLiveData(false)
    val shouldClearInput: LiveData<Boolean> = _shouldClearInput

    init {
        initializeTopics()
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
            topicDao.getAllTopics().collect { topics ->
                _topicList.value = topics
            }
        }
    }

    /**
     * 切换语音模式
     */
    fun toggleVoiceMode() {
        _isVoiceMode.value = !(_isVoiceMode.value ?: false)
    }

    /**
     * Toast 消息已显示
     */
    fun onToastShown() {
        _toastMessage.value = null
    }

    /**
     * 输入框已清空
     */
    fun onInputCleared() {
        _shouldClearInput.value = false
    }

    /**
     * 显示 Toast 消息
     */
    @Suppress("unused")
    fun showToast(message: String) {
        _toastMessage.value = message
    }

    /**
     * 清空输入框
     */
    @Suppress("unused")
    fun clearInput() {
        _shouldClearInput.value = true
    }
}