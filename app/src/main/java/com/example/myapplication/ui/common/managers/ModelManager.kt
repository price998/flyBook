package com.example.myapplication.ui.common.managers

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.MutableLiveData
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.myapplication.ui.common.adapter.ModelAdapter
import com.example.myapplication.config.ModelConfig
import com.example.myapplication.config.ModelRegistry
import com.example.myapplication.databinding.DialogModelSelectorBinding
import com.example.myapplication.utils.AppPreferences
import com.google.android.material.bottomsheet.BottomSheetDialog

/**
 * 模型管理器 - 单例模式
 * 
 * 统一管理 AI 模型的选择、切换和选择对话框
 * 
 * 职责：
 * - 管理当前选中的模型状态
 * - 持久化模型选择到 SharedPreferences
 * - 提供 LiveData 供 UI 观察模型变化
 * - 显示模型选择对话框
 */
object ModelManager {
    
    private const val TAG = "ModelManager"
    
    private var appContext: Context? = null
    
    private val _currentModel = MutableLiveData<ModelConfig>()
    

    
    /**
     * 确保已初始化（延迟初始化）
     */
    private fun ensureInitialized(context: Context) {
        if (appContext == null) {
            appContext = context.applicationContext
            // 从 SharedPreferences 加载上次选择的模型
            val savedModel = AppPreferences.getSelectedModel(appContext!!)
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
        AppPreferences.saveSelectedModel(appContext!!, modelConfig.id)
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
        
        val adapter = ModelAdapter(
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
