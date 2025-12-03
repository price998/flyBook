package com.example.myapplication.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface UserDao {
    // 根据用户名查找用户
    @Query("SELECT * FROM users WHERE username = :username LIMIT 1")
    suspend fun getUserByUsername(username: String): UserEntity?

    // 注册新用户
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertUser(user: UserEntity)

    // 更新最后登录时间
    @Query("UPDATE users SET lastLoginTime = :time WHERE uid = :uid")
    suspend fun updateLastLogin(uid: String, time: Long)
    
    // 检查是否有用户存在
    @Query("SELECT COUNT(*) FROM users")
    suspend fun getUserCount(): Int
}
