package com.example.myapplication.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.databinding.ItemModelOptionBinding
import com.example.myapplication.model.ModelConfig

class ModelAdapter(
        private val models: List<ModelConfig>,
        private var selectedModelId: String,
        private val onItemClick: (ModelConfig) -> Unit
) : RecyclerView.Adapter<ModelAdapter.ModelViewHolder>() {

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
