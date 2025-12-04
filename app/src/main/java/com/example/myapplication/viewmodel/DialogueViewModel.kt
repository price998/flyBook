package com.example.myapplication.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.myapplication.data.DefaultTopics
import com.example.myapplication.data.RecommendedTopic

/**
 * Dialogue view model.
 */
class DialogueViewModel(application: Application) : AndroidViewModel(application) {

    private val _topicList = MutableLiveData<List<RecommendedTopic>>()
    val topicList: LiveData<List<RecommendedTopic>> = _topicList

    private val _isVoiceMode = MutableLiveData(false)
    val isVoiceMode: LiveData<Boolean> = _isVoiceMode

    private val _toastMessage = MutableLiveData<String?>()
    val toastMessage: LiveData<String?> = _toastMessage

    private val _shouldClearInput = MutableLiveData(false)
    val shouldClearInput: LiveData<Boolean> = _shouldClearInput

    init {
        loadTopics()
    }

    private fun loadTopics() {
        _topicList.value = DefaultTopics.getDefaultTopics()
    }

    /**
     * 切换语音模式
     */
    fun toggleVoiceMode() {
        _isVoiceMode.value = !(_isVoiceMode.value ?: false)
    }

    /**
     * 设置语音模式
     */
    fun setVoiceMode(isVoiceMode: Boolean) {
        _isVoiceMode.value = isVoiceMode
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