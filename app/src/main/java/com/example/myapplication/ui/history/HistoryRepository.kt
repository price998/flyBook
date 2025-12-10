package com.example.myapplication.ui.history

import android.content.Context
import com.example.myapplication.data.db.AppDatabase
import com.example.myapplication.data.db.chat.ConversationEntity
import com.example.myapplication.domain.ChatHistory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class HistoryRepository(context: Context) {
    
    private val database = AppDatabase.getDatabase(context)
    private val conversationDao = database.conversationDao()

    // 从数据库获取历史对话列表
    suspend fun getHistoryList(): List<ChatHistory> = withContext(Dispatchers.IO) {
        val conversations = conversationDao.getRecentConversations(50)
        // 避免列表出现重复会话：按 id 去重，保留第一个（最新的）
        val uniqueConversations = conversations.distinctBy { it.id }
        mapEntitiesToHistory(uniqueConversations)
    }

    // 搜索对话
    suspend fun searchHistory(keyword: String): List<ChatHistory> = withContext(Dispatchers.IO) {
        val conversations = conversationDao.searchConversations(keyword)
        val uniqueConversations = conversations.distinctBy { it.id }
        mapEntitiesToHistory(uniqueConversations)
    }

    // 置顶/取消置顶
    suspend fun togglePin(conversationId: String, newPinnedState: Boolean) = withContext(Dispatchers.IO) {
        conversationDao.getConversationById(conversationId)?.let { conversation ->
            conversationDao.updateConversation(conversation.copy(isPinned = newPinnedState))
        }
    }

    // 软删除
    suspend fun deleteConversation(conversationId: String) {
        conversationDao.softDeleteConversation(conversationId, System.currentTimeMillis())
    }

    //实体转业务模型
    private fun mapEntitiesToHistory(conversations: List<ConversationEntity>): List<ChatHistory> {
        return conversations.map { entity ->
            ChatHistory(
                id = entity.id,
                title = entity.title,
                lastMessage = entity.lastMessagePreview ?: "暂无消息",
                timestamp = java.util.Date(entity.updatedAt),
                messageCount = entity.messageCount,
                isPinned = entity.isPinned
            )
        }
    }
}
