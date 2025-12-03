package com.example.myapplication.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.databinding.ItemHistoryBinding
import com.example.myapplication.model.ChatHistory

class HistoryAdapter(
        private var historyList: MutableList<ChatHistory>,
        private var currentConversationId: String? = null,
        private val onItemClick: (ChatHistory) -> Unit,
        private val onItemLongClick: ((ChatHistory) -> Unit)? = null
) : RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder>() {

  fun updateData(newList: List<ChatHistory>) {
    val diffCallback = HistoryDiffCallback(historyList, newList)
    val diffResult = DiffUtil.calculateDiff(diffCallback)
    historyList.clear()
    historyList.addAll(newList)
    diffResult.dispatchUpdatesTo(this)
  }

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
  inner class HistoryViewHolder(private val binding: ItemHistoryBinding) :
          RecyclerView.ViewHolder(binding.root) {

    init {
      binding.root.setOnClickListener {
        val position = adapterPosition
        if (position != RecyclerView.NO_POSITION) {
          onItemClick(historyList[position])
        }
      }

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

    fun bind(history: ChatHistory) {
      binding.historyTitle.text = history.title
      binding.historyPreview.text = history.lastMessage

      binding.iconPinned.visibility = if (history.isPinned) View.VISIBLE else View.GONE
      
      // 背景色
      val backgroundColor = when {
          history.id == currentConversationId -> Color.parseColor("#E3F2FD") 
          history.isPinned -> 0x0D000000.toInt() 
          else -> Color.TRANSPARENT
      }
      binding.root.setBackgroundColor(backgroundColor)
    }
  }

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
    val binding = ItemHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    return HistoryViewHolder(binding)
  }

  override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
    holder.bind(historyList[position])
  }

  override fun getItemCount(): Int = historyList.size
}
