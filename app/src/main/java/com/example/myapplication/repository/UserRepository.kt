package com.example.myapplication.repository

import com.example.myapplication.data.db.UserDao
import com.example.myapplication.data.db.UserEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

class UserRepository(private val userDao: UserDao) {

    suspend fun login(username: String, password: String): Result<UserEntity> = withContext(Dispatchers.IO) {
        try {
            val user = userDao.getUserByUsername(username)
            if (user == null) {
                Result.failure(Exception("用户不存在"))
            } else if (user.password != password) {
                Result.failure(Exception("密码错误"))
            } else {
                userDao.updateLastLogin(user.uid, System.currentTimeMillis())
                Result.success(user)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun register(username: String, password: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val existingUser = userDao.getUserByUsername(username)
            if (existingUser != null) {
                Result.failure(Exception("用户名已存在"))
            } else {
                val newUser = UserEntity(
                    uid = UUID.randomUUID().toString(),
                    username = username,
                    password = password,
                    createdAt = System.currentTimeMillis(), // 假设UserEntity有这个字段，没有就去掉
                    lastLoginTime = System.currentTimeMillis()
                )
                userDao.insertUser(newUser)
                Result.success(true)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
