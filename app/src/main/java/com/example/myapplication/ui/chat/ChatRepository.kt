package com.example.myapplication.ui.chat

import android.util.Log
import com.example.myapplication.data.db.chat.ConversationDao
import com.example.myapplication.data.db.chat.ConversationEntity
import com.example.myapplication.data.db.chat.MessageDao
import com.example.myapplication.data.db.chat.MessageEntity
import com.example.myapplication.domain.ChatMessage
import com.example.myapplication.network.model.ApiMessage
import com.example.myapplication.network.model.Delta
import com.example.myapplication.config.ModelConfig
import com.example.myapplication.network.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * 聊天数据仓库（Repository 层）
 *
 * 职责：
 * 1. 封装数据库操作，ViewModel 不直接访问数据库
 * 2. 数据转换：将数据库实体转换为应用模型
 * 3. 异常处理：捕获数据库操作异常
 * 4. 线程安全：所有数据库操作都在 IO 线程执行
 */
class ChatRepository(
    private val messageDao: MessageDao? = null,
    private val conversationDao: ConversationDao? = null
) {
    companion object {
        private const val TAG = "ChatRepository"
    }

    // ==================== 对话管理 ====================
    
    /**
     * 创建新对话（自动截断标题）
     * 自动将过长的内容截断作为标题
     * 
     * @param content 原始内容（将作为标题来源）
     * @return 新创建的对话ID
     */
    suspend fun createConversationWithTruncatedTitle(content: String): String = withContext(Dispatchers.IO) {
        val displayTitle = if (content.length > 20) content.substring(0, 20) + "..." else content
        createConversation(displayTitle)
    }

    /**
     * 创建新对话
     * 
     * @param title 对话标题
     * @return 新创建的对话ID
     */
    suspend fun createConversation(title: String): String = withContext(Dispatchers.IO) {
        val conversationId = java.util.UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        conversationDao?.insertConversation(
            ConversationEntity(
                id = conversationId,
                title = title,
                createdAt = now,
                updatedAt = now,
                messageCount = 0
            )
        )
        conversationId
    }
    
    /**
     * 更新对话标题
     * 
     * @param conversationId 对话ID
     * @param title 新标题
     */
    suspend fun updateConversationTitle(conversationId: String, title: String) = withContext(Dispatchers.IO) {
        conversationDao?.getConversationById(conversationId)?.let { conversation ->
            conversationDao.updateConversation(
                conversation.copy(
                    title = title,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }
    
    /**
     * 自动更新对话状态（时间戳、消息数、预览）
     */
    private suspend fun updateConversationTimestamp(conversationId: String, lastMessage: String? = null) {
        // 注意：此方法是私有的，总是在 withContext(Dispatchers.IO) 内部被调用
        conversationDao?.getConversationById(conversationId)?.let { conversation ->
            conversationDao.updateConversation(
                conversation.copy(
                    updatedAt = System.currentTimeMillis(),
                    messageCount = conversation.messageCount + 1,
                    lastMessagePreview = lastMessage ?: conversation.lastMessagePreview
                )
            )
        }
    }

    /**
     * 生成假数据对话
     *
     * @param pairCount 对话轮数（默认为1000轮）
     * @return 新创建的对话ID
     */
    suspend fun generateFakeData(pairCount: Int = 1000): String = withContext(Dispatchers.IO) {
        val title = "假数据长对话（$pairCount 轮）"
        val conversationId = createConversation(title)
        
        val baseTime = System.currentTimeMillis() - pairCount * 4_000L

        for (i in 0 until pairCount) {
            val round = i + 1

            // 用户消息
            val userMsg = ChatMessage(
                content = "第 $round 轮提问：这是用于测试长列表和分页加载的假数据问题。",
                isUser = true,
                timestamp = baseTime + i * 4_000L
            )

            // AI 消息（稍微长一点）
            val botMsg = ChatMessage(
                content = buildString {
                    append("第 $round 轮回答：这是 AI 的假数据回复，用来测试 RecyclerView 渲染和分页加载性能。\n")
                    append("这一轮是总共 $pairCount 轮中的第 $round 轮，你可以上拉加载更多历史消息。")
                },
                isUser = false,
                timestamp = baseTime + i * 4_000L + 2_000L
            )

            // 顺带更新会话的 messageCount、lastMessagePreview 等
            saveMessage(conversationId, userMsg)
            saveMessage(conversationId, botMsg)
        }
        
        conversationId
    }

    // ==================== 消息管理 ====================
    
    /**
     * 保存消息到本地数据库
     * 
     * @param conversationId 对话ID
     * @param message 聊天消息（ChatMessage 模型）
     */
    suspend fun saveMessage(conversationId: String, message: ChatMessage) = withContext(Dispatchers.IO) {
        messageDao?.let { dao ->
            val entity = MessageEntity(
                conversationId = conversationId,
                content = message.content,
                isUser = message.isUser,
                timestamp = message.timestamp,
                reasoningContent = message.reasoningContent,
                isComplete = message.isComplete,
                isLiked = message.isLiked,
                isDisliked = message.isDisliked
            )
            dao.insertMessage(entity)
            
            // 截取预览内容（防止过长）
            val preview = if (message.content.length > 50) {
                message.content.substring(0, 50) + "..."
            } else {
                message.content
            }
            updateConversationTimestamp(conversationId, preview)
        }
    }
    
    /**
     * 查询最新消息（用于初始化聊天界面）
     * 
     * @param conversationId 对话ID
     * @param limit 返回消息数量限制
     * @return 消息列表
     */
    suspend fun getLatestMessages(conversationId: String, limit: Int = 20): List<ChatMessage> = withContext(Dispatchers.IO) {
        messageDao?.getLatestMessages(conversationId, limit)?.map { entity ->
            ChatMessage(
                content = entity.content,
                isUser = entity.isUser,
                reasoningContent = entity.reasoningContent,
                isComplete = entity.isComplete,
                timestamp = entity.timestamp,
                isLiked = entity.isLiked,
                isDisliked = entity.isDisliked
            )
        } ?: emptyList()
    }
    
    /**
     * 查询历史消息（分页加载）
     * 
     * @param conversationId 对话ID
     * @param timestamp 时间戳（返回此时间之前的消息）
     * @param limit 返回消息数量限制
     * @return 消息列表
     */
    suspend fun getMessagesBefore(conversationId: String, timestamp: Long, limit: Int = 20): List<ChatMessage> = withContext(Dispatchers.IO) {
        messageDao?.getMessagesBefore(conversationId, timestamp, limit)?.map { entity ->
            ChatMessage(
                content = entity.content,
                isUser = entity.isUser,
                reasoningContent = entity.reasoningContent,
                isComplete = entity.isComplete,
                timestamp = entity.timestamp,
                isLiked = entity.isLiked,
                isDisliked = entity.isDisliked
            )
        } ?: emptyList()
    }
    
    /**
     * 更新消息点赞/点踩状态
     * 
     * @param conversationId 对话ID
     * @param timestamp 消息时间戳
     * @param isLiked 是否点赞
     * @param isDisliked 是否点踩
     */
    suspend fun updateMessageLikeState(
        conversationId: String,
        timestamp: Long,
        isLiked: Boolean,
        isDisliked: Boolean
    ) = withContext(Dispatchers.IO) {
        messageDao?.updateMessageLikeState(conversationId, timestamp, isLiked, isDisliked)
    }
    
    /**
     * 删除一组消息（通常是用户问题 + AI 回答）
     * 
     * @param conversationId 对话ID
     * @param timestamps 要删除的消息时间戳列表
     */
    suspend fun deleteMessagesByTimestamps(
        conversationId: String,
        timestamps: List<Long>
    ) = withContext(Dispatchers.IO) {
        if (timestamps.isEmpty()) return@withContext
        messageDao?.deleteMessagesByTimestamps(conversationId, timestamps)

        // 如果想同步修正 conversation.messageCount 和 lastMessagePreview，
        // 可以这里再查一下剩余最后一条消息来更新，这里先简单略过。
    }

    // ==================== 网络请求（委托给 ApiClient） ====================
    
    /**
     * 流式对话 - 使用 ApiClient 单例
     * 优势：复用 OkHttpClient 连接池，减少资源占用
     */
    fun streamChat(messages: List<ApiMessage>, modelConfig: ModelConfig): Flow<Delta> {
        Log.d(TAG, "使用 ApiClient 进行流式请求")
        return ApiClient.streamChat(messages, modelConfig)
    }
}
