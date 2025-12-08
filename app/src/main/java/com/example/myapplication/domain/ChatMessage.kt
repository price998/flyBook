package com.example.myapplication.domain

data class ChatMessage(
    val content: String,
    val isUser: Boolean, // true 表示用户消息，false 表示 AI 回复
    val reasoningContent: String? = null, // 深度思考内容
    val isComplete: Boolean = true, // 消息是否完成输出（流式输出时为 false）
    val timestamp: Long = System.currentTimeMillis() ,// 消息时间戳
    val isLiked: Boolean = false,      // 是否点赞
    val isDisliked: Boolean = false,   // 是否点踩

)
