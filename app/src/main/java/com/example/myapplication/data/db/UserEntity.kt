package com.example.myapplication.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey
    val uid: String,                // 用户ID (UUID)
    val username: String,           // 用户名 (唯一标识)
    val password: String,           // 密码 (本地存储)
    val createdAt: Long = System.currentTimeMillis(),
    val lastLoginTime: Long = 0
)
