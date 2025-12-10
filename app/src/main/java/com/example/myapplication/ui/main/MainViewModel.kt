package com.example.myapplication.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.myapplication.data.source.DefaultTopics
import com.example.myapplication.data.model.RecommendedTopic
import com.example.myapplication.ui.common.managers.ModelManager

/**
 * Main view model.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val _topicList = MutableLiveData<List<RecommendedTopic>>()
    val topicList: LiveData<List<RecommendedTopic>> = _topicList

    private val _toastMessage = MutableLiveData<String?>()
    val toastMessage: LiveData<String?> = _toastMessage



    init {
        // 确保 ModelManager 初始化
        ModelManager.getCurrentModel(application)
        loadTopics()
    }

    private fun loadTopics() {
        _topicList.value = DefaultTopics.getDefaultTopics()
    }


    /**
     * Toast 消息已显示
     */
    fun onToastShown() {
        _toastMessage.value = null
    }

    /**
     * 显示 Toast 消息
     */
    @Suppress("unused")
    fun showToast(message: String) {
        _toastMessage.value = message
    }

}
