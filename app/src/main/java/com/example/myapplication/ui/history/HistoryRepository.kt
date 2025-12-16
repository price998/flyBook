package com.example.myapplication.ui.history

import android.content.Context
import android.util.Log
import com.example.myapplication.data.db.AppDatabase
import com.example.myapplication.data.db.chat.ConversationEntity
import com.example.myapplication.domain.ChatHistory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Date

/**
 * 历史对话数据仓库（Repository 层）
 * 
 * 职责：
 * 1. 封装对话历史的数据库操作
 * 2. 提供对话的增删改查功能
 * 3. 实现软删除和回收站机制
 * 4. 数据转换：将数据库实体转换为业务模型
 * 
 * 核心功能：
 * - 获取历史对话列表
 * - 搜索对话
 * - 置顶/取消置顶
 * - 软删除（移到回收站）
 * - 恢复对话
 * - 彻底删除
 * - 清空回收站
 * 
 * 线程安全：
 * - 所有数据库操作都在 IO 线程执行（withContext(Dispatchers.IO)）
 * - 避免阻塞主线程
 */
class HistoryRepository(context: Context) {
    private val database = AppDatabase.getDatabase(context)
    private val conversationDao = database.conversationDao()
    private val messageDao = database.messageDao()
    private val attachmentDao = database.attachmentDao()

    /**
     * 获取历史对话列表
     * 
     * @return 最近的50条对话（按更新时间倒序）
     */
    suspend fun getHistoryList(): List<ChatHistory> =
        withContext(Dispatchers.IO) {
            val conversations = conversationDao.getRecentConversations(50)
            val uniqueConversations = conversations.distinctBy { it.id }
            mapEntitiesToHistory(uniqueConversations)
        }

    /**
     * 搜索对话
     * 
     * @param keyword 搜索关键词（匹配标题）
     * @return 匹配的对话列表
     */
    suspend fun searchHistory(keyword: String): List<ChatHistory> =
        withContext(Dispatchers.IO) {
            val conversations = conversationDao.searchConversations(keyword)
            val uniqueConversations = conversations.distinctBy { it.id }
            mapEntitiesToHistory(uniqueConversations)
        }

    /**
     * 检查是否有搜索结果
     * 
     * @param keyword 搜索关键词
     * @return true 如果有结果，false 如果没有
     */
    suspend fun hasSearchResults(keyword: String): Boolean =
        withContext(Dispatchers.IO) {
            return@withContext conversationDao.searchConversations(keyword).isNotEmpty()
        }

    /**
     * 置顶/取消置顶对话
     * 
     * @param conversationId 对话ID
     * @param newPinnedState 新的置顶状态
     */
    suspend fun togglePin(conversationId: String, newPinnedState: Boolean) =
        withContext(Dispatchers.IO) {
            conversationDao.getConversationById(conversationId)?.let { conversation ->
                conversationDao.updateConversation(conversation.copy(isPinned = newPinnedState))
            }
        }

    /**
     * 软删除对话（移到回收站）
     * 
     * 注意：
     * - 不会立即删除数据
     * - 只是标记为已删除，并记录删除时间
     * - 可以通过 restoreConversation 恢复
     * 
     * @param conversationId 对话ID
     */
    suspend fun deleteConversation(conversationId: String) {
        withContext(Dispatchers.IO) {
            conversationDao.softDeleteConversation(conversationId, System.currentTimeMillis())
        }
    }

    /**
     * 清空回收站
     * 
     * 功能：
     * 1. 获取所有回收站中的对话
     * 2. 删除每个对话的所有消息
     * 3. 删除对话本身
     * 4. 附件会因为外键级联自动删除
     * 
     * 注意：此操作不可恢复！
     */
    suspend fun emptyTrash() =
        withContext(Dispatchers.IO) {
            conversationDao.getTrashConversations(Int.MAX_VALUE).forEach { conversation ->
                // 先删除消息，附件会因为外键级联自动删除
                messageDao.deleteMessagesByConversation(conversation.id)
                conversationDao.deleteConversation(conversation.id)
            }
        }

    /**
     * 清理过期的回收站内容
     * 
     * 功能：
     * - 自动删除超过指定天数的回收站对话
     * - 默认保留7天
     * 
     * @param ttlDays 保留天数（默认7天）
     */
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

    /**
     * 获取回收站列表
     * 
     * @return 回收站中的所有对话
     */
    suspend fun getTrashList(): List<ChatHistory> =
        withContext(Dispatchers.IO) {
            val conversations = conversationDao.getTrashConversations(Int.MAX_VALUE)
            mapEntitiesToHistory(conversations)
        }

    /**
     * 恢复对话（从回收站恢复）
     * 
     * @param conversationId 对话ID
     */
    suspend fun restoreConversation(conversationId: String) {
        withContext(Dispatchers.IO) {
            conversationDao.restoreConversation(conversationId, System.currentTimeMillis())
        }
    }

    /**
     * 彻底删除对话
     * 
     * 功能：
     * 1. 删除对话的所有消息
     * 2. 删除对话本身
     * 3. 附件会因为外键级联自动删除
     * 
     * 注意：此操作不可恢复！
     * 
     * @param conversationId 对话ID
     */
    suspend fun hardDeleteConversation(conversationId: String) {
        withContext(Dispatchers.IO) {
            // 先删除消息，附件会因为外键级联自动删除
            messageDao.deleteMessagesByConversation(conversationId)
            conversationDao.deleteConversation(conversationId)
        }
    }

    /**
     * 数据转换：实体 → 业务模型
     * 
     * 将数据库实体（ConversationEntity）转换为业务模型（ChatHistory）
     * 
     * @param conversations 数据库实体列表
     * @return 业务模型列表
     */
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

