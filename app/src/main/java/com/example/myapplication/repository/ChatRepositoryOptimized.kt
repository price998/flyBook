package com.example.myapplication.repository

import com.example.myapplication.data.db.ConversationDao
import com.example.myapplication.data.db.ConversationEntity
import com.example.myapplication.data.db.MessageDao
import com.example.myapplication.data.db.MessageEntity
import com.example.myapplication.model.*
import com.example.myapplication.network.ApiClient
import com.example.myapplication.network.ApiService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

/**
 * 优化后的 ChatRepository
 * 
 * 优化点：
 * 1. 职责单一：只负责协调数据源（数据库和网络）
 * 2. 依赖注入：ApiService 可以注入，便于测试
 * 3. 使用 Flow：自动响应数据库变化
 * 4. 代码简洁：从 199 行减少到 ~100 行
 */
class ChatRepositoryOptimized(
    private val messageDao: MessageDao,
    private val conversationDao: ConversationDao,
    private val apiService: ApiService = ApiClient // 默认使用单例，可注入 mock
) {
    // ==================== 对话管理 ====================
    
    /**
     * 创建新对话
     */
    suspend fun createConversation(title: String): String {
        val conversationId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        
        conversationDao.insertConversation(
            ConversationEntity(
                id = conversationId,
                title = title,
                createdAt = now,
                updatedAt = now,
                messageCount = 0
            )
        )
        return conversationId
    }
    
    /**
     * 更新对话标题
     */
    suspend fun updateConversationTitle(conversationId: String, title: String) {
        conversationDao.getConversationById(conversationId)?.let { conversation ->
            conversationDao.updateConversation(
                conversation.copy(
                    title = title,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }
    
    /**
     * 更新对话时间戳（新消息时调用）
     */
    private suspend fun updateConversationTimestamp(conversationId: String) {
        conversationDao.getConversationById(conversationId)?.let { conversation ->
            conversationDao.updateConversation(
                conversation.copy(
                    updatedAt = System.currentTimeMillis(),
                    messageCount = conversation.messageCount + 1
                )
            )
        }
    }
    
    // ==================== 消息管理 ====================
    
    /**
     * 获取消息列表（Flow 自动响应数据库变化）
     * 优势：UI 无需手动刷新，数据库变化自动推送
     */
    fun getMessagesFlow(conversationId: String): Flow<List<ChatMessage>> {
        return messageDao.getAllMessages() // 如果 DAO 改为返回 Flow
            .map { entities ->
                entities
                    .filter { it.conversationId == conversationId }
                    .map { it.toChatMessage() }
            }
    }
    
    /**
     * 保存消息到数据库
     */
    suspend fun saveMessage(conversationId: String, message: ChatMessage) {
        messageDao.insertMessage(
            MessageEntity(
                conversationId = conversationId,
                content = message.content,
                isUser = message.isUser,
                timestamp = message.timestamp,
                reasoningContent = message.reasoningContent,
                isComplete = message.isComplete
            )
        )
        updateConversationTimestamp(conversationId)
    }
    
    /**
     * 分页加载历史消息
     */
    suspend fun getMessagesBefore(
        conversationId: String,
        timestamp: Long,
        limit: Int = 20
    ): List<ChatMessage> {
        return messageDao.getMessagesBefore(conversationId, timestamp, limit)
            .map { it.toChatMessage() }
    }
    
    /**
     * 获取最新消息
     */
    suspend fun getLatestMessages(
        conversationId: String,
        limit: Int = 20
    ): List<ChatMessage> {
        return messageDao.getLatestMessages(conversationId, limit)
            .map { it.toChatMessage() }
    }
    
    // ==================== 网络请求 ====================
    
    /**
     * 流式对话（委托给 ApiService）
     */
    fun streamChat(
        messages: List<ApiMessage>,
        modelConfig: ModelConfig
    ): Flow<Delta> = apiService.streamChat(messages, modelConfig)
    
    // ==================== 辅助方法 ====================
    
    /**
     * Entity 转 Model
     */
    private fun MessageEntity.toChatMessage() = ChatMessage(
        content = content,
        isUser = isUser,
        reasoningContent = reasoningContent,
        isComplete = isComplete,
        timestamp = timestamp
    )
}
