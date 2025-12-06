package com.example.myapplication.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(
    tableName = "messages",
    indices = [androidx.room.Index("conversationId"), androidx.room.Index("timestamp")]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationId: String,
    val content: String,
    val isUser: Boolean,
    val timestamp: Long,
    val reasoningContent: String? = null,
    val isComplete: Boolean = true,
    
    // 🆕 支持图片、文件、语音
    val msgType: String = "text",   // text, image, voice, file
    val audioDuration: Int = 0  ,    // 语音时长(秒)
    val isLiked: Boolean = false,
    val isDisliked: Boolean = false
)
