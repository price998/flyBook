package com.example.myapplication.ui.common.managers

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.MutableLiveData
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.myapplication.ui.inputbar.adapters.ModelSelectorAdapter
import com.example.myapplication.config.ModelConfig
import com.example.myapplication.config.ModelRegistry
import com.example.myapplication.databinding.DialogModelSelectorBinding
import com.google.android.material.bottomsheet.BottomSheetDialog

/**
 * 模型管理器（单例模式）
 * 
 * 职责：
 * 1. 统一管理 AI 模型的选择和切换
 * 2. 持久化模型选择（SharedPreferences）
 * 3. 提供模型选择对话框
 * 4. 提供 LiveData 供 UI 观察模型变化
 * 
 * 核心功能：
 * - 获取当前选中的模型
 * - 切换模型
 * - 显示模型选择对话框
 * - 自动保存用户选择
 * 
 * 设计模式：
 * - 单例模式（object）：全局唯一实例
 * - 延迟初始化：首次使用时才初始化
 * - 观察者模式：通过 LiveData 通知 UI 更新
 * 
 * 使用方式：
 * ```kotlin
 * // 获取当前模型
 * val model = ModelManager.getCurrentModel(context)
 * 
 * // 显示模型选择对话框
 * ModelManager.showSelector(activity) { selectedModel ->
 *     // 处理模型选择
 * }
 * ```
 * 
 * 数据持久化：
 * - 使用 SharedPreferences 保存用户选择
 * - 应用重启后自动恢复上次选择的模型
 * 
 * 模型配置：
 * - 所有可用模型定义在 ModelRegistry 中
 * - 默认模型：ModelRegistry.DEFAULT_MODEL
 * 
 * 优势：
 * - 统一管理：避免重复代码
 * - 状态同步：所有界面使用同一个模型实例
 * - 易于扩展：新增模型只需修改 ModelRegistry
 */
object ModelManager {
    
    private const val TAG = "ModelManager"
    private const val PREFS_NAME = "app_preferences"
    private const val KEY_SELECTED_MODEL_ID = "selected_model_id"
    
    private var appContext: Context? = null
    
    private val _currentModel = MutableLiveData<ModelConfig>()
    
    private fun getPreferences(): SharedPreferences? {
        return appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    /**
     * 确保已初始化（延迟初始化）
     */
    private fun ensureInitialized(context: Context) {
        if (appContext == null) {
            appContext = context.applicationContext
            // 从 SharedPreferences 加载上次选择的模型
            val modelId = getPreferences()?.getString(KEY_SELECTED_MODEL_ID, null)
            val savedModel = if (modelId != null) {
                ModelRegistry.getModelById(modelId) ?: ModelRegistry.DEFAULT_MODEL
            } else {
                ModelRegistry.DEFAULT_MODEL
            }
            _currentModel.value = savedModel
            android.util.Log.d(TAG, "ModelManager 初始化完成，当前模型: ${savedModel.displayName}")
        }
    }
    
    /**
     * 切换模型
     */
    private fun switchModel(context: Context, modelConfig: ModelConfig) {
        ensureInitialized(context)
        
        android.util.Log.d(TAG, "=== 切换模型 ===")
        android.util.Log.d(TAG, "模型名称: ${modelConfig.displayName}")
        android.util.Log.d(TAG, "API模型: ${modelConfig.apiModel}")
        
        _currentModel.value = modelConfig
        
        // 持久化到 SharedPreferences
        getPreferences()?.edit()?.putString(KEY_SELECTED_MODEL_ID, modelConfig.id)?.apply()
    }
    
    /**
     * 获取当前模型（非 LiveData 方式）
     */
    fun getCurrentModel(context: Context): ModelConfig {
        ensureInitialized(context)
        return _currentModel.value ?: ModelRegistry.DEFAULT_MODEL
    }
    
    /**
     * 显示模型选择对话框
     * 
     * @param activity 当前 Activity
     * @param onModelSelected 模型选择回调（可选，默认会自动调用 switchModel）
     */
    fun showSelector(
        activity: AppCompatActivity,
        onModelSelected: ((ModelConfig) -> Unit)? = null
    ) {
        ensureInitialized(activity)
        
        val dialog = BottomSheetDialog(activity)
        val binding = DialogModelSelectorBinding.inflate(activity.layoutInflater)
        
        binding.modelListRecyclerview.layoutManager = LinearLayoutManager(activity)
        
        val adapter = ModelSelectorAdapter(
            models = ModelRegistry.ALL_MODELS,
            selectedModelId = getCurrentModel(activity).id,
            onItemClick = { modelConfig ->
                switchModel(activity, modelConfig)
                onModelSelected?.invoke(modelConfig)
                dialog.dismiss()
            }
        )
        
        binding.modelListRecyclerview.adapter = adapter
        dialog.setContentView(binding.root)
        dialog.show()
    }
}
