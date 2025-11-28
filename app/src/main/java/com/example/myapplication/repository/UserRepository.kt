package com.example.myapplication.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class UserRepository {

    // 模拟登录请求
    suspend fun login(account: String, password: String): Result<Boolean> = withContext(Dispatchers.IO) {
        // 模拟网络延迟
        delay(1000)
        
        if (account == "admin" && password == "123456") {
            Result.success(true)
        } else {
            Result.failure(Exception("账号或密码错误"))
        }
    }
}
