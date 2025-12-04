package com.example.myapplication.utils

import android.content.Context
import android.content.SharedPreferences
import com.example.myapplication.model.ModelConfig
import com.example.myapplication.model.ModelRegistry

/** 管理模型选择的持久化存储 */
object ModelPreferences {
    private const val PREFS_NAME = "model_preferences"
    private const val KEY_SELECTED_MODEL_ID = "selected_model_id"

    private fun getPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /** 保存选中的模型ID */
    fun saveSelectedModel(context: Context, modelId: String) {
        getPreferences(context).edit().putString(KEY_SELECTED_MODEL_ID, modelId).apply()
    }

    /** 获取选中的模型配置 */
    fun getSelectedModel(context: Context): ModelConfig {
        val prefs = getPreferences(context)
        val modelId = prefs.getString(KEY_SELECTED_MODEL_ID, null)

        return if (modelId != null) {
            ModelRegistry.getModelById(modelId) ?: ModelRegistry.DEFAULT_MODEL
        } else {
            ModelRegistry.DEFAULT_MODEL
        }
    }
}
