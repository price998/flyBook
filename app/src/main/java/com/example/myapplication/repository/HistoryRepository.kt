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
        
        conversations.map { entity ->
            ChatHistory(
                id = entity.id,
                title = entity.title,
                lastMessage = "", // 可以后续优化，从消息表获取最后一条消息
                timestamp = Date(entity.updatedAt),
                messageCount = entity.messageCount
            )
        }
    }
}
