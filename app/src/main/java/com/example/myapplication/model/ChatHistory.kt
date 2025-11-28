package com.example.myapplication.model

import java.util.Date

data class ChatHistory(
    val id: String,
    val title: String,
    val lastMessage: String,
    val timestamp: Date,
    val messageCount: Int = 0
)
