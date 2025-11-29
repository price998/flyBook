package com.example.myapplication.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.databinding.ItemHistoryBinding
import com.example.myapplication.model.ChatHistory

class HistoryAdapter(
        private var historyList: MutableList<ChatHistory>,
        private val onItemClick: (ChatHistory) -> Unit,
        private val onItemLongClick: ((ChatHistory) -> Unit)? = null
) : RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder>() {

  fun updateData(newList: List<ChatHistory>) {
    historyList.clear()
    historyList.addAll(newList)
    notifyDataSetChanged()
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
      binding.iconPinned.visibility = if (history.isPinned) android.view.View.VISIBLE else android.view.View.GONE
      // 置顶项背景色微调
      binding.root.setBackgroundColor(
          if (history.isPinned) 0x0D000000.toInt() // 浅灰色背景 (5% black)
          else 0x00000000 // 透明
      )
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
