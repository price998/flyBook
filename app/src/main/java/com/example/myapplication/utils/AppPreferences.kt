package com.example.myapplication.utils

import android.content.Context
import android.content.SharedPreferences
import com.example.myapplication.config.ModelConfig
import com.example.myapplication.config.ModelRegistry

/**
 * 应用配置和状态管理
 * 
 * 统一管理应用的持久化配置和临时会话状态
 * 
 * 包含两个部分：
 * 1. 持久化配置（SharedPreferences）- 应用重启后保留
 * 2. 会话状态（内存）- 应用重启后重置
 */
object AppPreferences {
    
    // ========== SharedPreferences 配置 ==========
    private const val PREFS_NAME = "app_preferences"
    private const val KEY_SELECTED_MODEL_ID = "selected_model_id"
    
    private fun getPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    // ========== 模型配置（持久化） ==========
    
    /**
     * 保存选中的模型ID
     */
    fun saveSelectedModel(context: Context, modelId: String) {
        getPreferences(context).edit().putString(KEY_SELECTED_MODEL_ID, modelId).apply()
    }
    
    /**
     * 获取选中的模型配置
     */
    fun getSelectedModel(context: Context): ModelConfig {
        val prefs = getPreferences(context)
        val modelId = prefs.getString(KEY_SELECTED_MODEL_ID, null)
        
        return if (modelId != null) {
            ModelRegistry.getModelById(modelId) ?: ModelRegistry.DEFAULT_MODEL
        } else {
            ModelRegistry.DEFAULT_MODEL
        }
    }
    
    // ========== 会话状态（内存，不持久化） ==========
    
    /**
     * 联网搜索是否开启（会话状态，应用重启后重置）
     */
    private var isWebSearchEnabled: Boolean = false
    
    /**
     * 设置联网搜索状态
     */
    fun setWebSearchEnabled(enabled: Boolean) {
        isWebSearchEnabled = enabled
    }
    
    /**
     * 获取联网搜索状态
     */
    fun getWebSearchEnabled(): Boolean {
        return isWebSearchEnabled
    }
    
    /**
     * 重置所有会话状态（应用启动时调用）
     */
    fun resetSessionState() {
        isWebSearchEnabled = false
    }
}
