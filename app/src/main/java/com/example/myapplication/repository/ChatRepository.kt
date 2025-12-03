package com.example.myapplication.repository

import android.util.Log
import com.example.myapplication.data.db.ConversationDao
import com.example.myapplication.data.db.ConversationEntity
import com.example.myapplication.data.db.MessageDao
import com.example.myapplication.data.db.MessageEntity
import com.example.myapplication.model.*
import com.example.myapplication.network.ApiClient
import kotlinx.coroutines.flow.Flow

class ChatRepository(
    private val messageDao: MessageDao? = null,
    private val conversationDao: ConversationDao? = null
) {
    companion object {
        private const val TAG = "ChatRepository"
    }

    // ==================== 对话管理 ====================
    
    suspend fun createConversation(title: String): String {
        val conversationId = java.util.UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        conversationDao?.insertConversation(
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

    suspend fun updateConversationTitle(conversationId: String, title: String) {
        conversationDao?.getConversationById(conversationId)?.let { conversation ->
            conversationDao.updateConversation(
                conversation.copy(
                    title = title,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    private suspend fun updateConversationTimestamp(conversationId: String, lastMessage: String? = null) {
        conversationDao?.getConversationById(conversationId)?.let { conversation ->
            conversationDao.updateConversation(
                conversation.copy(
                    updatedAt = System.currentTimeMillis(),
                    messageCount = conversation.messageCount + 1,
                    lastMessagePreview = lastMessage ?: conversation.lastMessagePreview
                )
            )
        }
    }

    // ==================== 消息管理 ====================

    suspend fun saveMessage(conversationId: String, message: ChatMessage) {
        messageDao?.let { dao ->
            val entity = MessageEntity(
                conversationId = conversationId,
                content = message.content,
                isUser = message.isUser,
                timestamp = message.timestamp,
                reasoningContent = message.reasoningContent,
                isComplete = message.isComplete
            )
            dao.insertMessage(entity)
            
            // 截取预览内容（防止过长）
            val preview = if (message.content.length > 50) {
                message.content.substring(0, 50) + "..."
            } else {
                message.content
            }
            updateConversationTimestamp(conversationId, preview)
        }
    }

    suspend fun getLatestMessages(conversationId: String, limit: Int = 20): List<ChatMessage> {
        return messageDao?.getLatestMessages(conversationId, limit)?.map { entity ->
            ChatMessage(
                content = entity.content,
                isUser = entity.isUser,
                reasoningContent = entity.reasoningContent,
                isComplete = entity.isComplete,
                timestamp = entity.timestamp
            )
        } ?: emptyList()
    }

    suspend fun getMessagesBefore(conversationId: String, timestamp: Long, limit: Int = 20): List<ChatMessage> {
        return messageDao?.getMessagesBefore(conversationId, timestamp, limit)?.map { entity ->
            ChatMessage(
                content = entity.content,
                isUser = entity.isUser,
                reasoningContent = entity.reasoningContent,
                isComplete = entity.isComplete,
                timestamp = entity.timestamp
            )
        } ?: emptyList()
    }

    // ==================== 网络请求（委托给 ApiClient） ====================
    
    /**
     * 流式对话 - 使用 ApiClient 单例
     * 优势：复用 OkHttpClient 连接池，减少资源占用
     */
    fun streamChat(messages: List<ApiMessage>, modelConfig: ModelConfig): Flow<Delta> {
        Log.d(TAG, "使用 ApiClient 进行流式请求")
        return ApiClient.streamChat(messages, modelConfig)
    }
}
