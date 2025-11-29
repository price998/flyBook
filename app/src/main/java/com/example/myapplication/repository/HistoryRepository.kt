package com.example.myapplication.repository

import android.content.Context
import com.example.myapplication.data.db.AppDatabase
import com.example.myapplication.model.ChatHistory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Date

class HistoryRepository(context: Context) {
    
    private val database = AppDatabase.getDatabase(context)
    private val conversationDao = database.conversationDao()

    // 从数据库获取历史对话列表
    suspend fun getHistoryList(): List<ChatHistory> = withContext(Dispatchers.IO) {
        val conversations = conversationDao.getRecentConversations(50)
        mapEntitiesToHistory(conversations)
    }

    // 搜索对话
    suspend fun searchHistory(keyword: String): List<ChatHistory> = withContext(Dispatchers.IO) {
        val conversations = conversationDao.searchConversations(keyword)
        mapEntitiesToHistory(conversations)
    }

    // 置顶/取消置顶
    suspend fun togglePin(conversationId: String, isPinned: Boolean) {
        conversationDao.getConversationById(conversationId)?.let { conversation ->
            conversationDao.updateConversation(conversation.copy(isPinned = isPinned))
        }
    }

    // 软删除
    suspend fun deleteConversation(conversationId: String) {
        conversationDao.softDeleteConversation(conversationId)
    }

    private fun mapEntitiesToHistory(conversations: List<com.example.myapplication.data.db.ConversationEntity>): List<ChatHistory> {
        return conversations.map { entity ->
            ChatHistory(
                id = entity.id,
                title = entity.title,
                lastMessage = entity.lastMessagePreview ?: "暂无消息",
                timestamp = Date(entity.updatedAt),
                messageCount = entity.messageCount,
                isPinned = entity.isPinned
            )
        }
    }
}
