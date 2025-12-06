package com.example.myapplication.network

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import android.util.Log
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 联网搜索服务接口
 */
interface WebSearchService {
    suspend fun search(query: String): String
}


/**
 * 真实的搜索服务实现 - 使用 Serper.dev (Google Search API)
 */
class SerperWebSearchService : WebSearchService {
    private val client = OkHttpClient()
    
    // 用户提供的 Serper API Key
    private val apiKey = "302494e56e67fc54c80ffb7e5b3c264d563b8c60" 

    override suspend fun search(query: String): String = suspendCancellableCoroutine { continuation ->
        try {
            Log.d("WebSearch", "Searching for: $query")
            val mediaType = "application/json".toMediaType()
            // 构建请求体，num=3 表示只获取前3条结果
            // gl=cn 表示搜索中国地区，hl=zh-cn 表示使用简体中文
            val body = "{\"q\":\"$query\",\"num\":3, \"gl\": \"cn\", \"hl\": \"zh-cn\"}".toRequestBody(mediaType)
            
            val request = Request.Builder()
                .url("https://google.serper.dev/search")
                .method("POST", body)
                .addHeader("X-API-KEY", apiKey)
                .addHeader("Content-Type", "application/json")
                .build()
            
            val call = client.newCall(request)
            
            // 注册取消回调
            continuation.invokeOnCancellation {
                Log.d("WebSearch", "Search cancelled")
                call.cancel()
            }

            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) {
                        Log.e("WebSearch", "Search request failed", e)
                        continuation.resumeWithException(e)
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        val responseBody = response.body?.string()
                        
                        if (!response.isSuccessful || responseBody == null) {
                            Log.e("WebSearch", "Search failed: ${response.code} $responseBody")
                            if (continuation.isActive) {
                                continuation.resume("搜索请求失败 (Code: ${response.code})。请检查网络或 API Key。")
                            }
                            return
                        }

                        Log.d("WebSearch", "Search response: $responseBody")

                        // 解析 JSON 结果
                        val json = JSONObject(responseBody)
                        val organic = json.optJSONArray("organic")
                        
                        if (organic == null || organic.length() == 0) {
                            if (continuation.isActive) {
                                continuation.resume("未找到关于 '$query' 的相关搜索结果。")
                            }
                            return
                        }

                        val sb = StringBuilder()
                        for (i in 0 until organic.length()) {
                            val item = organic.getJSONObject(i)
                            val title = item.optString("title")
                            val snippet = item.optString("snippet")
                            val link = item.optString("link")
                            
                            sb.append("【搜索结果 ${i + 1}】[$title]($link)\n")
                            if (snippet.isNotEmpty()) {
                                sb.append("摘要: $snippet\n")
                            }
                            sb.append("\n")
                        }
                        
                        if (continuation.isActive) {
                            continuation.resume(sb.toString().trim())
                        }
                    } catch (e: Exception) {
                        Log.e("WebSearch", "Search exception", e)
                        if (continuation.isActive) {
                            continuation.resume("搜索过程中发生错误: ${e.message}")
                        }
                    } finally {
                        response.close()
                    }
                }
            })
            
        } catch (e: Exception) {
            Log.e("WebSearch", "Search setup exception", e)
            if (continuation.isActive) {
                continuation.resumeWithException(e)
            }
        }
    }
}
