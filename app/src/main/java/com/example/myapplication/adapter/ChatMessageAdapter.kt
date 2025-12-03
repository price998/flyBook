package com.example.myapplication.adapter

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.R
import com.example.myapplication.databinding.ItemMessageAiBinding
import com.example.myapplication.databinding.ItemMessageUserBinding
import com.example.myapplication.model.ChatMessage
import io.noties.markwon.Markwon
import io.noties.prism4j.annotations.PrismBundle
import kotlinx.coroutines.*

// 如果工程里有 Prism 依赖，可以保留这个注解；没有的话可以删掉
@PrismBundle(
    includeAll = true,
    grammarLocatorClassName = ".Prism4jGrammarLocator"
)
class ChatMessageAdapter(
    // 消息列表由外部维护（ViewModel），这里直接拿引用
    val messages: MutableList<ChatMessage>,
    private val markwon: Markwon,
    // 长按 AI 消息弹出操作对话框
    private val onAiMessageLongClick: (ChatMessage, View, Int, Int) -> Unit,
    // 底部操作栏回调：分享 / 点赞 / 点踩 / 重新生成
    private val onShareClick: (ChatMessage) -> Unit,
    private val onLikeClick: (ChatMessage) -> Unit,
    private val onDislikeClick: (ChatMessage) -> Unit,
    private val onReloadClick: (ChatMessage) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        const val PAYLOAD_CONTENT_UPDATE = "content_update"
        private const val VIEW_TYPE_USER = 0
        private const val VIEW_TYPE_AI = 1
    }

    // 打字机效果的协程作用域（主线程）
    private val typingScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /**
     * 记录已经播放过打字机动画的消息（用 timestamp 当 ID）
     *
     * ✅ 构造 Adapter 时，就把「当前已有的所有 AI 消息」全部加入这个集合，
     *    这样重新进入页面时，这些历史消息不会再播放打字机动画，
     *    只有之后新生成的 AI 回复才会播放。
     */
    private val animatedMessageIds: MutableSet<Long> = mutableSetOf<Long>().apply {
        addAll(messages.filter { !it.isUser }.map { it.timestamp })
    }

    /**
     * 记录哪些消息是“流式生成”的：
     * 只要某条消息在 isComplete = false 时出现过一次，就认为它是 streaming 消息，
     * 那么等它 isComplete = true 时，就不会再用本地模拟打字机。
     */
    private val streamingMessageIds: MutableSet<Long> = mutableSetOf()

    // ======= 用户消息 ViewHolder =======
    class UserMessageViewHolder(
        private val binding: ItemMessageUserBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(message: ChatMessage) {
            binding.messageText.text = message.content
            binding.messageText.setTextIsSelectable(true)
        }
    }

    // ======= AI 消息 ViewHolder =======
    class AiMessageViewHolder(
        val binding: ItemMessageAiBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private var typingJob: Job? = null

        fun bind(
            message: ChatMessage,
            markwon: Markwon,
            enableTyping: Boolean,
            onLongClick: (ChatMessage, View, Int, Int) -> Unit,
            onShareClick: (ChatMessage) -> Unit,
            onLikeClick: (ChatMessage) -> Unit,
            onDislikeClick: (ChatMessage) -> Unit,
            onReloadClick: (ChatMessage) -> Unit,
            typingScope: CoroutineScope
        ) {
            // 先把上一次的打字机动画停掉，避免复用 ViewHolder 时叠加
            typingJob?.cancel()
            typingJob = null

            val ctx = binding.root.context
            val activeColor = ContextCompat.getColor(ctx, R.color.text_primary)
            val inactiveColor = ContextCompat.getColor(ctx, R.color.text_secondary)

            // 只有完整输出的消息才展示隐私提示和底部操作栏
            val visibility = if (message.isComplete) View.VISIBLE else View.GONE
            binding.privacyHintLayout.visibility = visibility
            binding.actionBarLayout.visibility = visibility

            // 点赞 / 点踩 颜色（假设 ChatMessage 已经有 isLiked / isDisliked 字段）
            binding.btnLike.setColorFilter(
                if (message.isLiked) activeColor else inactiveColor
            )
            binding.btnDislike.setColorFilter(
                if (message.isDisliked) activeColor else inactiveColor
            )

            // ===== Markdown + 打字机效果 =====
            if (enableTyping) {
                // 本地模拟打字机：仅用于「非流式」的新 AI 消息
                val fullText = message.content
                binding.messageText.setTextIsSelectable(true)

                typingJob = typingScope.launch {
                    if (fullText.isEmpty()) {
                        markwon.setMarkdown(binding.messageText, "")
                        return@launch
                    }
                    val sb = StringBuilder()
                    fullText.forEach { ch ->
                        sb.append(ch)
                        // 逐字符渲染 Markdown
                        markwon.setMarkdown(binding.messageText, sb.toString())
                        delay(6L) // 打字机速度，可自行调整
                    }
                }
            } else {
                // 正常情况（包括流式）直接渲染当前 content
                markwon.setMarkdown(binding.messageText, message.content)
                binding.messageText.setTextIsSelectable(true)
            }

            // ===== 底部操作栏事件 =====

            // 分享
            binding.btnShare.setOnClickListener { onShareClick(message) }

            // 复制
            binding.btnCopy.setOnClickListener { copyToClipboard(ctx, message.content) }

            // 点赞 / 点踩交给 Activity 去更新状态（互斥逻辑在 Activity 里）
            binding.btnLike.setOnClickListener { onLikeClick(message) }
            binding.btnDislike.setOnClickListener { onDislikeClick(message) }

            // 重新加载
            binding.btnReload.setOnClickListener { onReloadClick(message) }

            // ===== 长按正文弹出更多操作 =====
            var lastTouchRawX = 0
            var lastTouchRawY = 0

            binding.messageText.setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_DOWN ||
                    event.action == MotionEvent.ACTION_MOVE
                ) {
                    lastTouchRawX = event.rawX.toInt()
                    lastTouchRawY = event.rawY.toInt()
                }
                false
            }

            binding.messageText.setOnLongClickListener { v ->
                onLongClick(message, v, lastTouchRawX, lastTouchRawY)
                true
            }
        }

        fun stopTyping() {
            typingJob?.cancel()
            typingJob = null
        }

        private fun copyToClipboard(context: Context, text: String) {
            val clipboard =
                context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("AI Response", text)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
        }
    }

    // ======= Adapter 基本实现 =======

    override fun getItemViewType(position: Int): Int {
        return if (messages[position].isUser) VIEW_TYPE_USER else VIEW_TYPE_AI
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_USER) {
            val binding = ItemMessageUserBinding.inflate(inflater, parent, false)
            UserMessageViewHolder(binding)
        } else {
            val binding = ItemMessageAiBinding.inflate(inflater, parent, false)
            AiMessageViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        bindInternal(holder, position)
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.isEmpty() || !payloads.contains(PAYLOAD_CONTENT_UPDATE)) {
            onBindViewHolder(holder, position)
            return
        }
        bindInternal(holder, position)
    }

    private fun bindInternal(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messages[position]

        if (holder is UserMessageViewHolder) {
            holder.bind(message)
            return
        }

        if (holder is AiMessageViewHolder) {
            val id = message.timestamp

            // 只记录 AI 消息的 streaming 状态
            if (!message.isUser && !message.isComplete) {
                streamingMessageIds.add(id)
            }

            // 是否是当前列表里的最后一条 AI 消息
            val isLastAi = position == itemCount - 1 && !message.isUser

            // 是否是“流式消息”（isComplete = false 时出现过）
            val isStreaming = streamingMessageIds.contains(id)

            /**
             * 启用本地打字机的条件：
             * 1. 是最后一条 AI 消息
             * 2. 后端没有流式更新（即没在 isComplete = false 状态下出现过）
             * 3. 这条消息之前没有播过打字机动画
             * 4. 当前已经是完整消息（isComplete = true）
             */
            val enableTyping =
                isLastAi &&
                        !isStreaming &&
                        !animatedMessageIds.contains(id) &&
                        message.isComplete

            if (enableTyping) {
                animatedMessageIds.add(id)
            }

            holder.bind(
                message = message,
                markwon = markwon,
                enableTyping = enableTyping,
                onLongClick = onAiMessageLongClick,
                onShareClick = onShareClick,
                onLikeClick = onLikeClick,
                onDislikeClick = onDislikeClick,
                onReloadClick = onReloadClick,
                typingScope = typingScope
            )
        }
    }

    override fun getItemCount(): Int = messages.size

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        if (holder is AiMessageViewHolder) {
            holder.stopTyping()
        }
        super.onViewRecycled(holder)
    }
}
