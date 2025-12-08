package com.example.myapplication.network.model

import com.google.gson.annotations.SerializedName


// 增强的请求体，支持更多参数
data class EnhancedChatRequest(
    val model: String,
    val messages: List<ApiMessage>,
    val stream: Boolean = false,
    @SerializedName("max_tokens") val maxTokens: Int = 4096,
    @SerializedName("enable_thinking") val enableThinking: Boolean = false,
    @SerializedName("thinking_budget") val thinkingBudget: Int = 4096,
    @SerializedName("min_p") val minP: Double = 0.05,
    val stop: String? = null,
    val temperature: Double = 0.7,
    @SerializedName("top_p") val topP: Double = 0.7,
    @SerializedName("top_k") val topK: Int = 50,
    @SerializedName("frequency_penalty") val frequencyPenalty: Double = 0.5,
    val n: Int = 1,
    @SerializedName("response_format") val responseFormat: ResponseFormat? = null
)

data class ResponseFormat(
    val type: String = "text"
)

// 消息结构
data class ApiMessage(
    val role: String, // "user" or "assistant"
    val content: String
)

// SSE 响应行结构
data class ChatCompletionChunk(
    val id: String?,
    val model: String?,
    val choices: List<ChunkChoice>?
)

data class ChunkChoice(
    val index: Int,
    val delta: Delta,
    @SerializedName("finish_reason") val finishReason: String?
)

data class Delta(
    val content: String?,
    @SerializedName("reasoning_content") val reasoningContent: String?,
    val role: String?
)
