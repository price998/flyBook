package com.example.myapplication.network

import android.util.Log
import com.example.myapplication.model.*
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
import java.util.concurrent.TimeUnit

/**
 * API 客户端 - 单例模式，复用 OkHttpClient
 */
object ApiClient : ApiService {
    private const val TAG = "ApiClient"
    private const val API_URL = "https://api.siliconflow.cn/v1/chat/completions"
    
    // 从配置文件或环境变量读取
    private val apiKey: String by lazy {
        // TODO: 从 BuildConfig 或安全存储中读取
        "sk-dvpgsxrmunuxjrhynxsusvkkoajnmbfiymodtdkczegmbuot"
    }
    
    // 单例 OkHttpClient - 复用连接池
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }
    
    private val gson = Gson()
    
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
