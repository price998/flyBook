package com.example.myapplication.ui.history

import android.content.Context
import android.util.Log
import com.example.myapplication.data.db.AppDatabase
import com.example.myapplication.data.db.chat.ConversationEntity
import com.example.myapplication.domain.ChatHistory
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class HistoryRepository(context: Context) {
    private val database = AppDatabase.getDatabase(context)
    private val conversationDao = database.conversationDao()
    private val messageDao = database.messageDao()
    private val attachmentDao = database.attachmentDao()

    // 从数据库获取历史对话列表
    suspend fun getHistoryList(): List<ChatHistory> =
            withContext(Dispatchers.IO) {
                val conversations = conversationDao.getRecentConversations(50)
                val uniqueConversations = conversations.distinctBy { it.id }
                mapEntitiesToHistory(uniqueConversations)
            }

    // 搜索对话
    suspend fun searchHistory(keyword: String): List<ChatHistory> =
            withContext(Dispatchers.IO) {
                try {
                    val conversations = conversationDao.searchConversations(keyword)
                    conversations.distinctBy { it.id }.map { conversation ->
                        ChatHistory(
                                id = conversation.id,
                                title = conversation.title,
                                lastMessage = conversation.lastMessagePreview ?: "",
                                timestamp = Date(conversation.updatedAt),
                                messageCount = conversation.messageCount,
                                isPinned = conversation.isPinned
                        )
                    }
                } catch (e: Exception) {
                    Log.e("HistoryRepository", "搜索失败", e)
                    emptyList()
                }
            }

    suspend fun hasSearchResults(keyword: String): Boolean =
            withContext(Dispatchers.IO) {
                return@withContext conversationDao.searchConversations(keyword).isNotEmpty()
            }

    // 置顶/取消置顶
    suspend fun togglePin(conversationId: String, newPinnedState: Boolean) =
            withContext(Dispatchers.IO) {
                conversationDao.getConversationById(conversationId)?.let { conversation ->
                    conversationDao.updateConversation(conversation.copy(isPinned = newPinnedState))
                }
            }

    // 软删除
    suspend fun deleteConversation(conversationId: String) {
        withContext(Dispatchers.IO) {
            conversationDao.softDeleteConversation(conversationId, System.currentTimeMillis())
        }
    }

    // 清空回收站
    suspend fun emptyTrash() =
            withContext(Dispatchers.IO) {
                conversationDao.getTrashConversations(Int.MAX_VALUE).forEach { conversation ->
                    // 先删除消息，附件会因为外键级联自动删除
                    messageDao.deleteMessagesByConversation(conversation.id)
                    conversationDao.deleteConversation(conversation.id)
                }
            }

    // 清理过期的回收站内容
    suspend fun cleanupExpiredTrash(ttlDays: Int = 7) =
            withContext(Dispatchers.IO) {
                val cutoff = System.currentTimeMillis() - ttlDays * 24L * 60L * 60L * 1000L
                val ids = conversationDao.getTrashIdsBefore(cutoff)
                ids.forEach { id ->
                    // 先删除消息，附件会因为外键级联自动删除
                    messageDao.deleteMessagesByConversation(id)
                    conversationDao.deleteConversation(id)
                }
            }

    // 获取回收站列表
    suspend fun getTrashList(): List<ChatHistory> =
            withContext(Dispatchers.IO) {
                val conversations = conversationDao.getTrashConversations(Int.MAX_VALUE)
                mapEntitiesToHistory(conversations)
            }

    // 恢复对话
    suspend fun restoreConversation(conversationId: String) {
        withContext(Dispatchers.IO) {
            conversationDao.restoreConversation(conversationId, System.currentTimeMillis())
        }
    }

    // 彻底删除对话
    suspend fun hardDeleteConversation(conversationId: String) {
        withContext(Dispatchers.IO) {
            // 先删除消息，附件会因为外键级联自动删除
            messageDao.deleteMessagesByConversation(conversationId)
            conversationDao.deleteConversation(conversationId)
        }
    }

    // 实体转业务模型
    private fun mapEntitiesToHistory(conversations: List<ConversationEntity>): List<ChatHistory> {
        return conversations.map { entity ->
            ChatHistory(
                    id = entity.id,
                    title = entity.title,
                    lastMessage = entity.lastMessagePreview ?: "",
                    timestamp = Date(entity.updatedAt),
                    messageCount = entity.messageCount,
                    isPinned = entity.isPinned
            )
        }
    }
}
