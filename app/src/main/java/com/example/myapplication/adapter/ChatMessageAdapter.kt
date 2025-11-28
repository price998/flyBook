package com.example.myapplication.adapter

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.databinding.ItemMessageAiBinding
import com.example.myapplication.databinding.ItemMessageUserBinding
import com.example.myapplication.model.ChatMessage
import io.noties.markwon.Markwon
import io.noties.prism4j.annotations.PrismBundle

@PrismBundle(
    includeAll = true,
    grammarLocatorClassName = ".Prism4jGrammarLocator"
)
class ChatMessageAdapter(
    private val messages: MutableList<ChatMessage>,
    private val markwon: Markwon // 通过构造函数注入，避免重复创建
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

  companion object {
    const val PAYLOAD_CONTENT_UPDATE = "content_update"
    private const val VIEW_TYPE_USER = 0
    private const val VIEW_TYPE_AI = 1
  }

  class UserMessageViewHolder(private val binding: ItemMessageUserBinding) :
          RecyclerView.ViewHolder(binding.root) {
    fun bind(message: ChatMessage) {
      binding.messageText.text = message.content
    }
  }

  class AiMessageViewHolder(private val binding: ItemMessageAiBinding) :
          RecyclerView.ViewHolder(binding.root) {
    fun bind(message: ChatMessage, isComplete: Boolean, markwon: Markwon) {
      // 使用 Markwon 渲染 Markdown 内容
      markwon.setMarkdown(binding.messageText, message.content)

      // 根据消息是否完成控制操作栏和隐私提示的可见性
      val visibility = if (isComplete) View.VISIBLE else View.GONE
      binding.privacyHintLayout.visibility = visibility
      binding.actionBarLayout.visibility = visibility
    }

    fun setupCopyButton(message: ChatMessage, context: Context) {
      binding.btnCopy.setOnClickListener {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("AI Response", message.content)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
      }
    }
  }

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
    return if (viewType == VIEW_TYPE_USER) {
      val binding =
              ItemMessageUserBinding.inflate(LayoutInflater.from(parent.context), parent, false)
      UserMessageViewHolder(binding)
    } else {
      val binding = ItemMessageAiBinding.inflate(LayoutInflater.from(parent.context), parent, false)
      AiMessageViewHolder(binding)
    }
  }

  override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
    onBindViewHolder(holder, position, mutableListOf())
  }

  override fun onBindViewHolder(
          holder: RecyclerView.ViewHolder,
          position: Int,
          payloads: MutableList<Any>
  ) {
    val message = messages[position]

    if (holder is UserMessageViewHolder) {
      holder.bind(message)
    } else if (holder is AiMessageViewHolder) {
      // 使用注入的 Markwon 实例
      
      // 如果有 payload，只更新内容
      if (payloads.isNotEmpty() && payloads.contains(PAYLOAD_CONTENT_UPDATE)) {
        holder.bind(message, message.isComplete, markwon)
        return
      }

      // 完整绑定
      holder.bind(message, message.isComplete, markwon)
      holder.setupCopyButton(message, holder.itemView.context)
    }
  }

  override fun getItemCount(): Int = messages.size

  override fun getItemViewType(position: Int): Int {
    return if (messages[position].isUser) VIEW_TYPE_USER else VIEW_TYPE_AI
  }
}
