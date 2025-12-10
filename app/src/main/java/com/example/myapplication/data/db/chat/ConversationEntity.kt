package com.example.myapplication.data.db.chat

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val messageCount: Int = 0,

    // 🆕 本地功能支持
    val isPinned: Boolean = false,          // 【置顶功能】
    val isDeleted: Boolean = false,         // 【删除功能】软删除标记 (true=回收站, false=正常)
    val lastMessagePreview: String? = null, // 【性能优化】列表页直接显示，无需查消息表
    
    // 【用户系统】即使是本地，也保留此字段以支持"注册登录"需求
    val userId: String = "local_user",
    //用于过期判断
    val deletedAt: Long? = null
)
