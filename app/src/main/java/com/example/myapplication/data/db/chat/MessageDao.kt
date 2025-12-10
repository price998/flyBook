package com.example.myapplication.data.db.chat

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Insert suspend fun insertMessage(message: MessageEntity): Long

    @Query("SELECT * FROM messages ORDER BY timestamp ASC")
    fun getAllMessages(): Flow<List<MessageEntity>>

    // 获取指定对话的指定时间戳之前的 N 条消息（用于下拉加载更多）
    @Query("SELECT * FROM (SELECT * FROM messages WHERE conversationId = :conversationId AND timestamp < :beforeTimestamp ORDER BY timestamp DESC LIMIT :limit) ORDER BY timestamp ASC")
    suspend fun getMessagesBefore(conversationId: String, beforeTimestamp: Long, limit: Int): List<MessageEntity>

    // 获取指定对话的最新 N 条消息
    @Query("SELECT * FROM (SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp DESC LIMIT :limit) ORDER BY timestamp ASC")
    suspend fun getLatestMessages(conversationId: String, limit: Int): List<MessageEntity>


    // 根据时间戳批量删除指定对话里的消息
    @Query(
        "DELETE FROM messages " +
                "WHERE conversationId = :conversationId AND timestamp IN (:timestamps)"
    )
    suspend fun deleteMessagesByTimestamps(
        conversationId: String,
        timestamps: List<Long>
    )

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun deleteMessagesByConversation(conversationId: String)
    @Query(
        """
        UPDATE messages 
        SET isLiked = :isLiked, isDisliked = :isDisliked
        WHERE conversationId = :conversationId AND timestamp = :timestamp
        """
    )
    suspend fun updateMessageLikeState(
        conversationId: String,
        timestamp: Long,
        isLiked: Boolean,
        isDisliked: Boolean
    )
}
