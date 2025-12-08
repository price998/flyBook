package com.example.myapplication.network

import com.example.myapplication.network.model.*
import com.example.myapplication.config.ModelConfig
import kotlinx.coroutines.flow.Flow

/**
 * 网络服务接口 - 职责单一，只负责网络请求
 */
interface ApiService {
    fun streamChat(messages: List<ApiMessage>, modelConfig: ModelConfig): Flow<Delta>
}
