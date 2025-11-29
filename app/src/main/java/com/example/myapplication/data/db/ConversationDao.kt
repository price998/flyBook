package com.example.myapplication.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {
    @Insert
    suspend fun insertConversation(conversation: ConversationEntity): Long

    @Update
    suspend fun updateConversation(conversation: ConversationEntity)

    @Query("SELECT * FROM conversations WHERE isDeleted = 0 ORDER BY isPinned DESC, updatedAt DESC")
    fun getAllConversations(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :conversationId")
    suspend fun getConversationById(conversationId: String): ConversationEntity?

    // 物理删除 (彻底删除)
    @Query("DELETE FROM conversations WHERE id = :conversationId")
    suspend fun deleteConversation(conversationId: String)
    
    // 软删除 (移入回收站)
    @Query("UPDATE conversations SET isDeleted = 1 WHERE id = :conversationId")
    suspend fun softDeleteConversation(conversationId: String)

    @Query("SELECT * FROM conversations WHERE isDeleted = 0 ORDER BY updatedAt DESC LIMIT :limit")
    suspend fun getRecentConversations(limit: Int): List<ConversationEntity>

    @Query("SELECT * FROM conversations WHERE isDeleted = 0 AND title LIKE '%' || :keyword || '%' ORDER BY updatedAt DESC")
    suspend fun searchConversations(keyword: String): List<ConversationEntity>
}
