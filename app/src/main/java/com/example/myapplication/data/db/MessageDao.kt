package com.example.myapplication.data.db

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

    @Query("DELETE FROM messages") suspend fun deleteAll()

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun deleteMessagesByConversation(conversationId: String)
}
