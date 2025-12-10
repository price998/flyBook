package com.example.myapplication.ui.inputbar.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.config.ModelConfig
import com.example.myapplication.databinding.ItemModelOptionBinding

/**
 * 模型选择适配器
 * 
 * 职责：
 * - 展示可选的 AI 模型列表
 * - 高亮当前选中的模型
 * - 处理模型选择事件
 * 
 * 使用场景：
 * - InputBarFragment 中的模型选择对话框
 * - 通过 ModelManager 调用
 */
class ModelSelectorAdapter(
        private val models: List<ModelConfig>,
        private var selectedModelId: String,
        private val onItemClick: (ModelConfig) -> Unit
) : RecyclerView.Adapter<ModelSelectorAdapter.ModelViewHolder>() {

  class ModelViewHolder(private val binding: ItemModelOptionBinding) :
          RecyclerView.ViewHolder(binding.root) {

    fun bind(model: ModelConfig, isSelected: Boolean, onClick: () -> Unit) {
      binding.modelNameText.text = model.displayName
      binding.currentModelText.visibility = if (isSelected) View.VISIBLE else View.GONE
      binding.root.setOnClickListener { onClick() }
    }
  }

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ModelViewHolder {
    val binding = ItemModelOptionBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
    )
    return ModelViewHolder(binding)
  }

  override fun onBindViewHolder(holder: ModelViewHolder, position: Int) {
    val model = models[position]
    holder.bind(
            model = model,
            isSelected = model.id == selectedModelId,
            onClick = {
              val previousSelectedId = selectedModelId
              selectedModelId = model.id
              // 刷新之前选中的和新选中的项
              notifyItemChanged(models.indexOfFirst { it.id == previousSelectedId })
              notifyItemChanged(position)
              onItemClick(model)
            }
    )
  }

  override fun getItemCount(): Int = models.size
}
