package com.example.myapplication.model

/**
 * AI 模型配置
 */
data class ModelConfig(
    val id: String,              // 模型唯一标识
    val displayName: String,     // 显示名称
    val apiModel: String,        // API 中使用的模型名称
    val maxTokens: Int = 4096,   // 最大 token 数
    val temperature: Double = 0.7,
    val topP: Double = 0.7,
    val topK: Int = 50,
    val frequencyPenalty: Double = 0.5,
    val enableThinking: Boolean = false,  // 是否启用深度思考
    val thinkingBudget: Int = 4096
)

/**
 * 预定义的模型列表
 */
object ModelRegistry {
    private val QWEN_3_32B = ModelConfig(
        id = "qwen-3-32b",
        displayName = "Qwen3-32B",
        apiModel = "Qwen/Qwen3-32B"
    )
    
    private val DEEPSEEK_V2_5 = ModelConfig(
        id = "deepseek-v2.5",
        displayName = "DeepSeek-V2.5",
        apiModel = "deepseek-ai/DeepSeek-V2.5"
    )
    
    private val GLM_4_6 = ModelConfig(
        id = "glm-4.6",
        displayName = "GLM-4.6",
        apiModel = "zai-org/GLM-4.6"
    )
    
    /**
     * 所有可用模型列表（仅支持这三个模型）
     */
    val ALL_MODELS = listOf(
        QWEN_3_32B,
        DEEPSEEK_V2_5,
        GLM_4_6
    )
    
    /**
     * 根据 ID 获取模型配置
     */
    fun getModelById(id: String): ModelConfig? {
        return ALL_MODELS.find { it.id == id }
    }
    
    /**
     * 默认模型
     */
    val DEFAULT_MODEL = QWEN_3_32B
}
