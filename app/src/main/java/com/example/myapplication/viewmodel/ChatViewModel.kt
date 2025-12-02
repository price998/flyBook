package com.example.myapplication.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.db.AppDatabase
import com.example.myapplication.model.ApiMessage
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.ModelConfig
import com.example.myapplication.model.ModelRegistry
import com.example.myapplication.network.SerperWebSearchService
import com.example.myapplication.network.WebSearchService
import com.example.myapplication.repository.ChatRepository
import com.example.myapplication.utils.ModelPreferences
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private const val TAG = "ChatViewModel"
        private const val TYPING_DELAY_MS = 80L // 打字机效果延迟（毫秒），增加延迟减少刷新频率，提升流畅度
        private const val BATCH_SIZE = 5 // 每收到5个字符才更新一次UI，减少渲染次数
    }

    private val database = AppDatabase.getDatabase(application)
    private val repository = ChatRepository(database.messageDao(), database.conversationDao())
    private val webSearchService: WebSearchService = SerperWebSearchService()

    // 当前对话ID
    private var conversationId: String = ""

    private val _messages = MutableLiveData(mutableListOf<ChatMessage>())
    val messages: LiveData<MutableList<ChatMessage>> = _messages

    // 用于通知 UI 列表有更新（插入或修改）
    private val _messageUpdate = MutableLiveData<MessageUpdateEvent>()
    val messageUpdate: LiveData<MessageUpdateEvent> = _messageUpdate

    private var isLoadingMore = false
    private var hasMoreHistory = true

    // 停止生成标志
    private var stopGenerationFlag = false

    // 是否正在生成
    private val _isGenerating = MutableLiveData(false)
    val isGenerating: LiveData<Boolean> = _isGenerating

    // 联网搜索开关状态
    private val _isSearchEnabled = MutableLiveData(false)
    @Suppress("unused")
    val isSearchEnabled: LiveData<Boolean> = _isSearchEnabled

    fun toggleSearch(enabled: Boolean) {
        _isSearchEnabled.value = enabled
    }

    // 设置对话ID（从Activity传入）
    fun setConversationId(id: String) {
        if (conversationId != id) {
            conversationId = id
            // 清空当前消息列表
            val currentList = _messages.value ?: mutableListOf()
            currentList.clear()
            _messages.value = currentList // 触发更新
            // 加载新对话的消息
            loadInitialMessages()
        }
    }

    private fun loadInitialMessages() {
        if (conversationId.isEmpty()) return

        viewModelScope.launch {
            try {
                val historyMessages = repository.getLatestMessages(conversationId, 20)
                val currentList = _messages.value ?: mutableListOf()

                if (historyMessages.isNotEmpty()) {
                    if (currentList.isEmpty()) {
                        currentList.addAll(historyMessages)
                    } else {
                        val existingTimestamps = currentList.map { it.timestamp }.toSet()
                        val newHistory =
                                historyMessages.filter {
                                    !existingTimestamps.contains(it.timestamp)
                                }
                        currentList.addAll(0, newHistory)
                    }
                }

                // 始终触发LiveData更新，即使列表为空
                _messages.value = currentList
            } catch (e: Exception) {
                Log.e(TAG, "Error loading initial messages", e)
            }
        }
    }

    fun loadMoreHistory() {
        if (isLoadingMore || !hasMoreHistory) {
            // 如果没有更多历史记录，通知UI停止刷新
            if (!hasMoreHistory) {
                _messageUpdate.value = MessageUpdateEvent.NoMoreHistory
            }
            return
        }

        val currentList = _messages.value ?: return
        if (currentList.isEmpty()) {
            _messageUpdate.value = MessageUpdateEvent.NoMoreHistory
            return
        }

        val oldestTimestamp = currentList.first().timestamp

        isLoadingMore = true
        viewModelScope.launch {
            try {
                val oldMessages = repository.getMessagesBefore(conversationId, oldestTimestamp, 20)
                if (oldMessages.isNotEmpty()) {
                    currentList.addAll(0, oldMessages)
                    _messageUpdate.value = MessageUpdateEvent.HistoryLoaded(oldMessages.size)
                } else {
                    hasMoreHistory = false
                    _messageUpdate.value = MessageUpdateEvent.NoMoreHistory
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading more history", e)
                _messageUpdate.value = MessageUpdateEvent.NoMoreHistory
            } finally {
                isLoadingMore = false
            }
        }
    }

    // 当前选中的模型（从 SharedPreferences 加载）
    private val _currentModel = MutableLiveData(ModelPreferences.getSelectedModel(application))
    val currentModel: LiveData<ModelConfig> = _currentModel

    /** 切换模型 */
    fun switchModel(modelConfig: ModelConfig) {
        Log.d(TAG, "=== 切换模型 ===")
        Log.d(TAG, "模型名称: ${modelConfig.displayName}")
        Log.d(TAG, "API模型: ${modelConfig.apiModel}")
        Log.d(TAG, "maxTokens: ${modelConfig.maxTokens}")
        Log.d(TAG, "temperature: ${modelConfig.temperature}")
        Log.d(TAG, "enableThinking: ${modelConfig.enableThinking}")
        _currentModel.value = modelConfig
        // 保存到 SharedPreferences
        ModelPreferences.saveSelectedModel(getApplication(), modelConfig.id)
    }

    /** 停止生成 */
    fun stopGeneration() {
        Log.d(TAG, "停止生成请求")
        stopGenerationFlag = true
    }

    fun sendMessage(content: String) {
        val currentList = _messages.value ?: mutableListOf()
        val model = _currentModel.value ?: ModelRegistry.DEFAULT_MODEL
        stopGenerationFlag = false // 重置停止标志
        Log.d(TAG, "=== 发送消息 ===")
        Log.d(TAG, "使用模型: ${model.displayName}")
        Log.d(TAG, "API模型参数: ${model.apiModel}")
        Log.d(TAG, "maxTokens: ${model.maxTokens}")
        Log.d(TAG, "temperature: ${model.temperature}")
        Log.d(TAG, "enableThinking: ${model.enableThinking}")

        // 1. 添加用户消息
        val userMsg = ChatMessage(content, true)
        currentList.add(userMsg)
        _messageUpdate.value = MessageUpdateEvent.ItemInserted(currentList.size - 1)

        // 保存用户消息到数据库
        viewModelScope.launch { repository.saveMessage(conversationId, userMsg) }

        // 2. 准备 API 消息上下文
        // 将现有的 ChatMessage 转换为 ApiMessage
        // 注意：这里把刚刚添加的用户消息也包含进去了
        val apiMessages =
                currentList
                        .map { ApiMessage(if (it.isUser) "user" else "assistant", it.content) }
                        .toMutableList()

        // 3. 添加 AI 占位消息（标记为未完成）
        val aiMsg = ChatMessage("", false, isComplete = false)
        currentList.add(aiMsg)
        val aiMsgIndex = currentList.size - 1
        _messageUpdate.value = MessageUpdateEvent.ItemInserted(aiMsgIndex)

        viewModelScope.launch {
            _isGenerating.value = true

            var searchResultForDisplay = ""

            // --- 联网搜索逻辑 ---
            if (_isSearchEnabled.value == true) {
                try {
                    // 更新UI显示正在搜索
                    currentList[aiMsgIndex] =
                            ChatMessage("🔍 正在联网搜索相关信息...", false, isComplete = false)
                    _messageUpdate.value = MessageUpdateEvent.ItemChanged(aiMsgIndex)

                    // 执行搜索
                    val searchResult = webSearchService.search(content)
                    searchResultForDisplay = searchResult // 保存以用于显示

                    // 构造 Prompt
                    if (apiMessages.isNotEmpty()) {
                        val lastIndex = apiMessages.lastIndex
                        val lastMsg = apiMessages[lastIndex]
                        if (lastMsg.role == "user") {
                            val newContent =
                                    """
                                基于以下互联网搜索结果回答用户问题。如果搜索结果没有帮助，请使用你自己的知识。
                                
                                【搜索结果】：
                                $searchResult
                                
                                【用户问题】：${lastMsg.content}
                            """.trimIndent()
                            apiMessages[lastIndex] = lastMsg.copy(content = newContent)
                        }
                    }

                    // 清空提示文字，准备开始流式输出
                    currentList[aiMsgIndex] = ChatMessage("", false, isComplete = false)
                    _messageUpdate.value = MessageUpdateEvent.ItemChanged(aiMsgIndex)
                } catch (e: Exception) {
                    Log.e(TAG, "搜索失败", e)
                    // 搜索失败不影响继续对话，只是没有搜索结果
                }
            }
            // --- 联网搜索结束 ---

            val fullResponseBuilder = StringBuilder()
            if (searchResultForDisplay.isNotEmpty()) {
                fullResponseBuilder.append("### 🔍 搜索结果\n\n")
                fullResponseBuilder.append(searchResultForDisplay)
                // 确保 --- 前面有空行，避免上一行被解析为标题
                fullResponseBuilder.append("\n\n---\n\n")
            }

            val reasoningBuilder = StringBuilder()
            var charCount = 0 // 字符计数器

            try {
                Log.d(TAG, "开始流式请求，模型: ${model.apiModel}")
                repository.streamChat(apiMessages, model).collect { delta ->
                    // 检查是否需要停止
                    if (stopGenerationFlag) {
                        Log.d(TAG, "检测到停止标志，终止生成")
                        return@collect
                    }
                    val deltaContent = delta.content
                    val reasoning = delta.reasoningContent

                    if (!reasoning.isNullOrEmpty()) {
                        reasoningBuilder.append(reasoning)
                    }

                    if (!deltaContent.isNullOrEmpty()) {
                        fullResponseBuilder.append(deltaContent)
                        charCount += deltaContent.length
                    }

                    // 批量更新：每收到 BATCH_SIZE 个字符才更新一次UI
                    if (charCount >= BATCH_SIZE) {
                        charCount = 0

                        // 更新列表中的消息对象（流式输出中，标记为未完成）
                        currentList[aiMsgIndex] =
                                ChatMessage(
                                        content = fullResponseBuilder.toString(),
                                        isUser = false,
                                        reasoningContent =
                                                reasoningBuilder.toString().takeIf {
                                                    it.isNotEmpty()
                                                },
                                        isComplete = false
                                )
                        // 通知 Adapter 更新特定位置
                        _messageUpdate.value = MessageUpdateEvent.ItemChanged(aiMsgIndex)

                        // 添加延迟以控制打字机速度
                        delay(TYPING_DELAY_MS)
                    }
                }

                // 流式输出完成，标记消息为完成状态
                Log.d(TAG, "流式输出完成")
                val completeMsg =
                        ChatMessage(
                                content = fullResponseBuilder.toString(),
                                isUser = false,
                                reasoningContent =
                                        reasoningBuilder.toString().takeIf { it.isNotEmpty() },
                                isComplete = true
                        )
                currentList[aiMsgIndex] = completeMsg
                _messageUpdate.value = MessageUpdateEvent.ItemChanged(aiMsgIndex)

                // 保存 AI 消息到数据库
                repository.saveMessage(conversationId, completeMsg)
            } catch (e: Exception) {
                // 处理错误，标记为完成状态
                if (!stopGenerationFlag) {
                    fullResponseBuilder.append("\n[Error: ${e.message}]")
                }
                currentList[aiMsgIndex] =
                        ChatMessage(
                                content = fullResponseBuilder.toString(),
                                isUser = false,
                                isComplete = true
                        )
                _messageUpdate.value = MessageUpdateEvent.ItemChanged(aiMsgIndex)
                Log.e(TAG, "流式输出错误", e)
            } finally {
                _isGenerating.value = false
                stopGenerationFlag = false
            }
        }
    }
}

sealed class MessageUpdateEvent {
    data class ItemInserted(val position: Int) : MessageUpdateEvent()
    data class ItemChanged(val position: Int) : MessageUpdateEvent()
    data class HistoryLoaded(val count: Int) : MessageUpdateEvent()
    data object NoMoreHistory : MessageUpdateEvent()
}
