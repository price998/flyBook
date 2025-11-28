package com.example.myapplication.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class MessageEntity(
        @PrimaryKey(autoGenerate = true) val id: Long = 0,
        val conversationId: String, // 关联到对话会话
        val content: String,
        val isUser: Boolean,
        val timestamp: Long,
        val reasoningContent: String? = null,
        val isComplete: Boolean = true
)
