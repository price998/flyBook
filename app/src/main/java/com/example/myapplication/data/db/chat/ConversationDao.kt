package com.example.myapplication.data.db.chat

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {
    @Insert suspend fun insertConversation(conversation: ConversationEntity): Long

    @Update suspend fun updateConversation(conversation: ConversationEntity)
    //俺没有用到过
    @Query("SELECT * FROM conversations WHERE isDeleted = 0 ORDER BY isPinned DESC, updatedAt DESC")
    fun getAllConversations(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :conversationId")
    suspend fun getConversationById(conversationId: String): ConversationEntity?

    // 物理删除 (彻底删除)
    @Query("DELETE FROM conversations WHERE id = :conversationId")
    suspend fun deleteConversation(conversationId: String)

    // 软删除 (移入回收站) 同时记录删除时间（毫秒）
    @Query(
            "UPDATE conversations SET isDeleted = 1, deletedAt = :timestamp, updatedAt = :timestamp WHERE id = :id"
    )
    suspend fun softDeleteConversation(id: String, timestamp: Long)

    // 从回收站恢复
    @Query(
            "UPDATE conversations SET isDeleted = 0, deletedAt = NULL, updatedAt = :ts WHERE id = :conversationId"
    )
    suspend fun restoreConversation(conversationId: String, ts: Long)

    @Query(
            "SELECT * FROM conversations WHERE isDeleted = 0 ORDER BY isPinned DESC, updatedAt DESC LIMIT :limit"
    )
    suspend fun getRecentConversations(limit: Int): List<ConversationEntity>

    // 索关键字是否出现在标题和会话内容消息里
    @Query(
            """
    SELECT DISTINCT c.* FROM conversations c
    LEFT JOIN messages m ON c.id = m.conversationId
    WHERE c.isDeleted = 0 
    AND (c.title LIKE '%' || :keyword || '%' 
         OR m.content LIKE '%' || :keyword || '%')
    ORDER BY c.isPinned DESC, c.updatedAt DESC
    """
    )
    suspend fun searchConversations(keyword: String): List<ConversationEntity>

    // ===== 回收站相关 =====
    @Query("SELECT * FROM conversations WHERE isDeleted = 1 ORDER BY deletedAt DESC LIMIT :limit")
    suspend fun getTrashConversations(limit: Int): List<ConversationEntity>

    @Query(
            "SELECT * FROM conversations WHERE isDeleted = 1 AND title LIKE '%' || :keyword || '%' ORDER BY deletedAt DESC"
    )
    suspend fun searchTrashConversations(keyword: String): List<ConversationEntity>

    @Query(
            "SELECT id FROM conversations WHERE isDeleted = 1 AND deletedAt IS NOT NULL AND deletedAt < :cutoff"
    )
    suspend fun getTrashIdsBefore(cutoff: Long): List<String>
}
