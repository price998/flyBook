package com.example.myapplication.adapter

import com.example.myapplication.model.MediaType
import com.example.myapplication.model.SelectedMedia
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.myapplication.databinding.ItemMediaPreviewBinding
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.myapplication.R

class PreviewAdapter(
    private var items: MutableList<SelectedMedia>,
    private val onDelete: (Int) -> Unit
) : RecyclerView.Adapter<PreviewAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemMediaPreviewBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemMediaPreviewBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val binding = holder.binding

        if (item.type == MediaType.IMAGE) {
            // 使用Glide加载图片
            Glide.with(binding.imgThumb.context)
                .load(item.uri)
                .placeholder(R.drawable.ic_image_placeholder)
                .error(R.drawable.ic_image_error)
                .into(binding.imgThumb)
            binding.imgFileIcon.visibility = View.GONE
        } else {
            binding.imgThumb.setImageDrawable(null)
            binding.imgThumb.setBackgroundColor(Color.LTGRAY)
            binding.imgFileIcon.visibility = View.VISIBLE
        }

        binding.btnDelete.setOnClickListener {
            onDelete(position)
        }
    }

    override fun getItemCount() = items.size

    @Suppress("unused")
    fun updateItems(newItems: MutableList<SelectedMedia>) {
        items = newItems
        notifyItemRangeChanged(0, items.size)
    }

    @Suppress("unused")
    fun removeItem(position: Int) {
        if (position in 0 until items.size) {
            items.removeAt(position)
            notifyItemRemoved(position)
        }
    }
}