package com.example.myapplication.ui.history.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.chauthai.swipereveallayout.ViewBinderHelper
import com.example.myapplication.databinding.ItemTrashRevealBinding
import com.example.myapplication.model.ChatHistory

class TrashAdapter(
    val items: MutableList<ChatHistory>,
    private val onRestore: (ChatHistory) -> Unit,
    private val onDelete: (ChatHistory) -> Unit
) : RecyclerView.Adapter<TrashAdapter.VH>() {

    private val binderHelper = ViewBinderHelper().apply { setOpenOnlyOne(true) }

    inner class VH(val binding: ItemTrashRevealBinding) : RecyclerView.ViewHolder(binding.root)
    //创建 ViewHolder，绑定布局
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemTrashRevealBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }
    //绑定数据到 ViewHolder，设置恢复/删除按钮点击事件
    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.binding.tvTitle.text = item.title
        holder.binding.tvSubtitle.text = item.lastMessage

        // 绑定 SwipeRevealLayout
        binderHelper.bind(holder.binding.swipeLayout, item.id)

        holder.binding.btnRestore.setOnClickListener {
            onRestore(item)
            binderHelper.closeLayout(item.id)
        }
        holder.binding.btnDelete.setOnClickListener {
            onDelete(item)
            binderHelper.closeLayout(item.id)
        }
    }

    override fun getItemCount(): Int = items.size

    fun submit(list: List<ChatHistory>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }
}
