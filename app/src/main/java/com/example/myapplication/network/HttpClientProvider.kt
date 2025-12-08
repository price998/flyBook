package com.example.myapplication.network

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * HTTP 客户端提供者 - 单例模式
 * 
 * 统一管理 OkHttpClient 实例，避免多处创建导致资源浪费
 * 
 * 优势：
 * - 复用连接池，减少 TCP 握手开销
 * - 统一超时配置
 * - 减少内存占用
 * 
 * 使用方式：
 * ```kotlin
 * val client = HttpClientProvider.client
 * val request = Request.Builder().url("...").build()
 * client.newCall(request).enqueue(...)
 * ```
 */
object HttpClientProvider {
    
    /**
     * 默认超时时间（秒）
     */
    private const val DEFAULT_CONNECT_TIMEOUT = 30L
    private const val DEFAULT_READ_TIMEOUT = 30L
    private const val DEFAULT_WRITE_TIMEOUT = 30L
    
    /**
     * 共享的 OkHttpClient 实例
     * 
     * 配置说明：
     * - connectTimeout: 建立连接的超时时间
     * - readTimeout: 读取数据的超时时间
     * - writeTimeout: 写入数据的超时时间
     */
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(DEFAULT_CONNECT_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(DEFAULT_READ_TIMEOUT, TimeUnit.SECONDS)
            .writeTimeout(DEFAULT_WRITE_TIMEOUT, TimeUnit.SECONDS)
            // 可以在这里添加通用的拦截器
            .build()
    }
    
}
