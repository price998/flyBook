package com.example.myapplication.ui.inputbar.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.myapplication.R
import com.example.myapplication.databinding.ItemMediaPreviewBinding
import com.example.myapplication.ui.inputbar.model.MediaType
import com.example.myapplication.ui.inputbar.model.SelectedMedia

/**
 * 附件预览适配器
 * 
 * 职责：
 * - 展示输入栏中选中的附件（图片/文件）
 * - 提供删除附件功能
 * - 区分图片和文件的显示方式
 * 
 * 使用场景：
 * - InputBarFragment 中的附件预览列表
 */
class AttachmentPreviewAdapter(
    val items: MutableList<SelectedMedia>,
    private val onDelete: (Int) -> Unit
) : RecyclerView.Adapter<AttachmentPreviewAdapter.ViewHolder>() {

    companion object {
        private const val TAG = "AttachmentPreviewAdapter"
    }

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

        android.util.Log.d(TAG, "绑定附件预览 - 位置: $position, 类型: ${item.type}, URI: ${item.uri}, 名称: ${item.name}")

        if (item.type == MediaType.IMAGE) {
            // 使用Glide加载图片
            android.util.Log.d(TAG, "加载图片预览: ${item.uri}")
            Glide.with(binding.imgThumb.context)
                .load(item.uri)
                .placeholder(R.drawable.ic_image_placeholder)
                .error(R.drawable.ic_image_error)
                .into(binding.imgThumb)
            binding.imgFileIcon.visibility = View.GONE
        } else {
            android.util.Log.d(TAG, "显示文件图标: ${item.name}")
            binding.imgThumb.setImageDrawable(null)
            binding.imgThumb.setBackgroundColor(Color.LTGRAY)
            binding.imgFileIcon.visibility = View.VISIBLE
        }

        binding.btnDelete.setOnClickListener {
            android.util.Log.d(TAG, "删除附件 - 位置: $position, 类型: ${item.type}")
            onDelete(position)
        }
    }

    override fun getItemCount() = items.size

    /**
     * 更新附件列表（使用 DiffUtil 计算差异）
     * @param newItems 新的附件列表
     */
    fun updateItems(newItems: MutableList<SelectedMedia>) {
        android.util.Log.d(TAG, "更新附件列表 - 旧数量: ${items.size}, 新数量: ${newItems.size}")
        val diffCallback = AttachmentDiffCallback(items, newItems)
        val diffResult = DiffUtil.calculateDiff(diffCallback)
        
        items.clear()
        items.addAll(newItems)
        diffResult.dispatchUpdatesTo(this)
        android.util.Log.d(TAG, "附件列表更新完成")
    }

    /**
     * DiffUtil 回调，用于计算列表差异
     */
    private class AttachmentDiffCallback(
        private val oldList: List<SelectedMedia>,
        private val newList: List<SelectedMedia>
    ) : DiffUtil.Callback() {
        
        override fun getOldListSize() = oldList.size
        
        override fun getNewListSize() = newList.size
        
        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition].uri == newList[newItemPosition].uri
        }
        
        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val oldItem = oldList[oldItemPosition]
            val newItem = newList[newItemPosition]
            return oldItem.uri == newItem.uri && oldItem.type == newItem.type
        }
    }
}