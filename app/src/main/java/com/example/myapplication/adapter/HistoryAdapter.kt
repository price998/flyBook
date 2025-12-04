package com.example.myapplication.adapter

import android.graphics.Color
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.databinding.ItemHistoryBinding
import com.example.myapplication.model.ChatHistory
import androidx.core.content.ContextCompat
import com.example.myapplication.R

class HistoryAdapter(
        private var historyList: MutableList<ChatHistory>,
        private var currentConversationId: String? = null,
        private val onItemClick: (ChatHistory) -> Unit,
        private val onItemLongClick: ((ChatHistory) -> Unit)? = null
) : RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder>() {

    // 搜索关键字，用于高亮显示
    var searchKeyword: String = ""
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    //高性能数据更新
  fun updateData(newList: List<ChatHistory>) {
    val diffCallback = HistoryDiffCallback(historyList, newList)
    val diffResult = DiffUtil.calculateDiff(diffCallback)
    historyList.clear()
    historyList.addAll(newList)
    //DiffUtil只刷新有变化的条目,替代notifyDataSetChanged全量刷新
    diffResult.dispatchUpdatesTo(this)
  }
    //定义“如何对比新旧列表”
  private class HistoryDiffCallback(
    private val oldList: List<ChatHistory>,
    private val newList: List<ChatHistory>
  ) : DiffUtil.Callback() {
    override fun getOldListSize(): Int = oldList.size
    override fun getNewListSize(): Int = newList.size
    
    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
      return oldList[oldItemPosition].id == newList[newItemPosition].id
    }
    
    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
      return oldList[oldItemPosition] == newList[newItemPosition]
    }
  }
    //选中状态管理：局部刷新选中项
  fun setSelectedId(id: String?) {
    val previousId = currentConversationId
    currentConversationId = id
    
    // 刷新变化的部分
    if (previousId != null) {
        val prevIndex = historyList.indexOfFirst { it.id == previousId }
        if (prevIndex != -1) notifyItemChanged(prevIndex)
    }
    if (id != null) {
        val newIndex = historyList.indexOfFirst { it.id == id }
        if (newIndex != -1) notifyItemChanged(newIndex)
    }
  }
    //ViewHolder 初始化
  inner class HistoryViewHolder(private val binding: ItemHistoryBinding) :
          RecyclerView.ViewHolder(binding.root) {

    init {
        //条目点击事件
      binding.root.setOnClickListener {
        val position = adapterPosition
        if (position != RecyclerView.NO_POSITION) {
          onItemClick(historyList[position])
        }
      }
        // 条目长按事件
      binding.root.setOnLongClickListener {
        val position = adapterPosition
        if (position != RecyclerView.NO_POSITION) {
          onItemLongClick?.invoke(historyList[position])
          true
        } else {
          false
        }
      }
    }
    //数据绑定到 UI
    fun bind(history: ChatHistory) {
        //显示文本，处理高亮
        if (searchKeyword.isNotEmpty()) {
            val titleSpannable = SpannableString(history.title)
            val startIndex = history.title.indexOf(searchKeyword, ignoreCase = true)
            if (startIndex >= 0) {
                titleSpannable.setSpan(
                    ForegroundColorSpan(Color.RED),
                    startIndex,
                    startIndex + searchKeyword.length,
                    android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            binding.historyTitle.text = titleSpannable
        } else {
            binding.historyTitle.text = history.title
        }

      binding.historyPreview.text = history.lastMessage
        //置顶图标显隐
      binding.iconPinned.visibility = if (history.isPinned) View.VISIBLE else View.GONE

      // 背景色（优先级：选中 > 置顶 > 普通）
      val context = binding.root.context
      val isSelected = history.id == currentConversationId
      
      val backgroundRes = when {
          history.id == currentConversationId -> R.drawable.bg_history_item_selected
          history.isPinned -> R.drawable.bg_history_item_pinned
          else -> R.drawable.bg_history_item_normal
      }
      binding.root.setBackgroundResource(backgroundRes)
      
      // 选中时字体颜色变化
      if (isSelected) {
           binding.historyTitle.setTextColor(ContextCompat.getColor(context, R.color.sidebar_item_selected_text))
      } else {
           binding.historyTitle.setTextColor(Color.BLACK)
      }
    }
  }
    //适配器生命周期方法
  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
    val binding = ItemHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    return HistoryViewHolder(binding)
  }

  override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
    holder.bind(historyList[position])
  }

  override fun getItemCount(): Int = historyList.size
}
