package com.example.myapplication.network

import android.util.Log
import com.example.myapplication.BuildConfig
import com.example.myapplication.network.model.*
import com.example.myapplication.config.ModelConfig
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedReader
import java.io.IOException

/**
 * API 客户端（网络层核心类）
 * 
 * 职责：
 * 1. 封装 AI API 的网络请求逻辑
 * 2. 处理 SSE（Server-Sent Events）流式响应
 * 3. 将流式数据转换为 Kotlin Flow
 * 4. 管理请求生命周期（支持取消）
 * 
 * 设计模式：
 * - 单例模式（object）：全局唯一实例，复用 OkHttpClient 连接池
 * - 接口实现：实现 ApiService 接口，便于测试和替换
 * 
 * 技术特点：
 * - 使用 OkHttp 处理 HTTP 请求
 * - 使用 Kotlin Flow 处理流式数据
 * - 使用 callbackFlow 将回调转换为 Flow
 * - 支持请求取消（Flow 取消时自动取消 OkHttp 请求）
 * 
 * 安全性：
 * - API Key 从 BuildConfig 读取（不硬编码）
 * - 使用 lazy 延迟初始化，避免启动时加载
 */
object ApiClient : ApiService {
    private const val TAG = "ApiClient"
    
    /** AI API 端点 URL */
    private const val API_URL = "https://api.siliconflow.cn/v1/chat/completions"
    
    /**
     * API Key（从 BuildConfig 读取，安全存储）
     * 
     * 配置方式：
     * 1. 在项目根目录创建 local.properties 文件
     * 2. 添加：AI_API_KEY=your_api_key_here
     * 3. BuildConfig 会自动生成 AI_API_KEY 常量
     */
    private val apiKey: String by lazy {
        BuildConfig.AI_API_KEY.also {
            if (it.isEmpty()) {
                Log.w(TAG, "AI_API_KEY is empty! Please set it in local.properties")
            }
        }
    }
    
    /**
     * 复用共享的 OkHttpClient
     * 
     * 优势：
     * - 复用连接池，减少 TCP 握手开销
     * - 统一配置超时、拦截器等
     * - 减少内存占用
     */
    private val client: OkHttpClient by lazy { HttpClientProvider.client }
    
    /** JSON 序列化/反序列化工具 */
    private val gson = Gson()
    
    /**
     * 流式对话请求（核心方法）
     * 
     * 功能：
     * 1. 发送聊天请求到 AI API
     * 2. 接收 SSE 流式响应
     * 3. 解析每个数据块（chunk）
     * 4. 通过 Flow 发送给调用者
     * 
     * 参数：
     * @param messages 对话历史消息列表
     * @param modelConfig 模型配置（包含模型名称、参数等）
     * 
     * 返回：
     * @return Flow<Delta> 流式数据流，每个 Delta 包含一小段生成的文本
     * 
     * 流程：
     * 1. 构造请求体（包含消息、模型配置）
     * 2. 发送 POST 请求到 API
     * 3. 逐行读取响应（SSE 格式）
     * 4. 解析 "data: " 开头的行
     * 5. 将解析后的 Delta 发送到 Flow
     * 6. 遇到 "[DONE]" 或连接关闭时结束
     * 
     * 异常处理：
     * - 网络错误：通过 Flow.close(exception) 传递
     * - 解析错误：记录日志但继续处理后续数据
     * - 请求取消：Flow 取消时自动取消 OkHttp 请求
     * 
     * 线程安全：
     * - 使用 flowOn(Dispatchers.IO) 在 IO 线程执行
     * - 避免阻塞主线程
     */
    override fun streamChat(
        messages: List<ApiMessage>,
        modelConfig: ModelConfig
    ): Flow<Delta> = callbackFlow {
        val requestBody = EnhancedChatRequest(
            model = modelConfig.apiModel,
            messages = messages,
            stream = true,
            maxTokens = modelConfig.maxTokens,
            enableThinking = modelConfig.enableThinking,
            thinkingBudget = modelConfig.thinkingBudget,
            temperature = modelConfig.temperature,
            topP = modelConfig.topP,
            topK = modelConfig.topK,
            frequencyPenalty = modelConfig.frequencyPenalty
        )
        
        val json = gson.toJson(requestBody)
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = json.toRequestBody(mediaType)
        
        val request = Request.Builder()
            .url(API_URL)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()
        
        val call = client.newCall(request)
        
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "请求失败", e)
                close(e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    val errorMsg = "请求失败: ${response.code} ${response.message}"
                    Log.e(TAG, errorMsg)
                    close(IOException(errorMsg))
                    response.close()
                    return
                }
                
                val responseBody = response.body
                if (responseBody == null) {
                    close(IOException("响应体为空"))
                    response.close()
                    return
                }
                
                try {
                    val reader = BufferedReader(responseBody.byteStream().reader())
                    var line = reader.readLine()
                    while (line != null) {
                        if (line.startsWith("data: ")) {
                            val data = line.removePrefix("data: ").trim()
                            if (data == "[DONE]") break
                            
                            try {
                                val chunk = gson.fromJson(data, ChatCompletionChunk::class.java)
                                chunk.choices?.firstOrNull()?.delta?.let { 
                                    trySend(it) 
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "解析chunk失败: ${e.message}", e)
                            }
                        }
                        line = reader.readLine()
                    }
                    reader.close()
                    close()
                } catch (e: Exception) {
                    Log.e(TAG, "流式响应处理异常: ${e.message}", e)
                    close(e)
                } finally {
                    response.close()
                }
            }
        })
        
        // 当Flow被取消时，取消OkHttp请求
        awaitClose {
            Log.d(TAG, "Flow collection cancelled, canceling OkHttp call")
            call.cancel()
        }
    }.flowOn(Dispatchers.IO)
}
