package com.example.myapplication.ui.chat

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.db.AppDatabase
import com.example.myapplication.domain.ChatMessage
import com.example.myapplication.network.SerperWebSearchService
import com.example.myapplication.network.WebSearchService
import com.example.myapplication.network.model.ApiMessage
import com.example.myapplication.ui.inputbar.InputBarRepository
import com.example.myapplication.ui.common.managers.ModelManager

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private const val TAG = "ChatViewModel"
        private const val TYPING_DELAY_MS = 30L // 30ms延迟，平衡流畅度与性能（约30fps）
        private const val BATCH_SIZE = 4 // 每4个字符更新一次，减少UI渲染压力
    }

    private val database = AppDatabase.getDatabase(application)
    private val repository = ChatRepository(database.messageDao(), database.conversationDao())
    private val webSearchService: WebSearchService = SerperWebSearchService()
    private val inputBarRepository = InputBarRepository(application)

    // 当前对话ID
    private var conversationId: String = ""
    val currentConversationId: String
        get() = conversationId

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

    // 聊天状态事件（用于 View 层显示状态文案）
    private val _chatStatus = MutableLiveData<ChatStatusEvent>(ChatStatusEvent.Idle)
    val chatStatus: LiveData<ChatStatusEvent> = _chatStatus

    // 图片OCR解析状态
    private val _ocrProgress = MutableLiveData<OCRProgress>()
    val ocrProgress: LiveData<OCRProgress> = _ocrProgress

    sealed class OCRProgress {
        data object Idle : OCRProgress()
        data class Recognizing(val current: Int, val total: Int) : OCRProgress() // 当前进度
        data class Success(val text: String) : OCRProgress()
        data class Error(val message: String) : OCRProgress()
    }

    // 设置对话ID（从Activity传入）
    fun setConversationId(id: String) {
        if (conversationId != id) {
            // 先取消正在进行的生成任务
            stopGeneration()
            
            // 等待协程完全取消后再切换对话
            generationJob?.cancel()
            generationJob = null
            
            conversationId = id
            // 清空当前消息列表
            val currentList = _messages.value ?: mutableListOf()
            currentList.clear()
            _messages.value = currentList // 触发更新
            // 重置状态
            hasMoreHistory = true
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


    private var generationJob: kotlinx.coroutines.Job? = null

    /** 停止生成 */
    fun stopGeneration() {
        Log.d(TAG, "停止生成请求")
        stopGenerationFlag = true
        generationJob?.cancel() // 取消协程，立即停止
        Log.d(TAG, "生成任务已取消，stopGenerationFlag = $stopGenerationFlag")
    }

    fun sendMessage(content: String) {
        // 检查conversationId，如果为空则创建新对话
        if (conversationId.isEmpty()) {
            Log.w(TAG, "conversationId为空，创建新对话")
            viewModelScope.launch {
                try {
                    conversationId = repository.createConversationWithTruncatedTitle(content)
                    Log.d(TAG, "创建新对话: $conversationId")
                    // 递归调用，这次conversationId已经有值了
                    sendMessage(content)
                } catch (e: Exception) {
                    Log.e(TAG, "创建对话失败", e)
                }
            }
            return
        }

        val currentList = _messages.value ?: mutableListOf()
        val model = ModelManager.getCurrentModel(getApplication())
        stopGenerationFlag = false // 重置停止标志
        Log.d(TAG, "=== 发送消息 ===")
        Log.d(TAG, "conversationId: $conversationId")
        Log.d(TAG, "使用模型: ${model.displayName}")
        Log.d(TAG, "API模型参数: ${model.apiModel}")
        Log.d(TAG, "maxTokens: ${model.maxTokens}")
        Log.d(TAG, "temperature: ${model.temperature}")
        Log.d(TAG, "enableThinking: ${model.enableThinking}")

        // 1. 添加用户消息
        val userMsg = ChatMessage(content, true)
        currentList.add(userMsg)
        _messageUpdate.value = MessageUpdateEvent.ItemInserted(currentList.size - 1, isUserMessage = true)

        // 保存用户消息到数据库
        viewModelScope.launch {
            try {
                repository.saveMessage(conversationId, userMsg)
            } catch (e: Exception) {
                Log.e(TAG, "保存消息到数据库失败", e)
            }
        }

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
        val aiTimestamp = aiMsg.timestamp
        _messageUpdate.value = MessageUpdateEvent.ItemInserted(aiMsgIndex, isUserMessage = false)

        generationJob =
                viewModelScope.launch {
                    _isGenerating.value = true
                    var searchResultForDisplay = ""

                    // --- 联网搜索逻辑 ---
                    // 从 InputBarViewModel 读取联网搜索状态（使用持久化存储）
                    val isSearchEnabled = com.example.myapplication.ui.inputbar.InputBarViewModel.getWebSearchEnabled(getApplication())
                    Log.d(TAG, "Search enabled status: $isSearchEnabled")
                    if (isSearchEnabled) {
                        Log.d(TAG, "Starting web search for: $content")
                        try {
                            // 检查索引是否有效
                            if (aiMsgIndex >= currentList.size) {
                                Log.w(TAG, "搜索开始时aiMsgIndex超出范围，对话可能已切换")
                                return@launch
                            }
                            
                            // 通知 View 层显示搜索状态（UI 文案由 View 层控制）
                            _chatStatus.value = ChatStatusEvent.WebSearching(aiMsgIndex, aiTimestamp)

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

                            // 检查索引是否有效
                            if (aiMsgIndex >= currentList.size) {
                                Log.w(TAG, "搜索完成时aiMsgIndex超出范围，对话可能已切换")
                                return@launch
                            }
                            
                            // 通知 View 层搜索完成
                            _chatStatus.value = ChatStatusEvent.SearchComplete(aiMsgIndex, aiTimestamp)
                        } catch (e: Exception) {
                            Log.e(TAG, "搜索失败", e)
                            // 搜索失败不影响继续对话，只是没有搜索结果
                            _chatStatus.value = ChatStatusEvent.Idle
                        }
                    }
                    // --- 联网搜索结束 ---

                    val fullResponseBuilder = StringBuilder()
                    if (searchResultForDisplay.isNotEmpty()) {
                        // 搜索结果头部使用资源字符串
                        val searchHeader = getApplication<Application>().getString(
                            com.example.myapplication.R.string.chat_search_result_header
                        )
                        fullResponseBuilder.append(searchHeader)
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
                                throw java.util.concurrent.CancellationException("用户停止生成")
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
                                
                                // 再次检查停止标志，避免在更新UI前被中断
                                if (stopGenerationFlag) {
                                    Log.d(TAG, "批量更新前检测到停止标志，终止生成")
                                    throw java.util.concurrent.CancellationException("用户停止生成")
                                }

                                // 检查索引是否有效（防止对话切换导致列表被清空）
                                if (aiMsgIndex >= currentList.size) {
                                    Log.w(TAG, "aiMsgIndex超出范围，对话可能已切换，停止更新")
                                    throw java.util.concurrent.CancellationException("对话已切换")
                                }

                                // 更新列表中的消息对象（流式输出中，标记为未完成）
                                currentList[aiMsgIndex] =
                                        ChatMessage(
                                                content = fullResponseBuilder.toString(),
                                                isUser = false,
                                                reasoningContent =
                                                        reasoningBuilder.toString().takeIf {
                                                            it.isNotEmpty()
                                                        },
                                                isComplete = false,
                                                timestamp = aiTimestamp
                                        )
                                // 通知 Adapter 更新特定位置
                                _messageUpdate.value = MessageUpdateEvent.ItemChanged(aiMsgIndex)

                                // 添加延迟以控制打字机速度
                                delay(TYPING_DELAY_MS)
                                
                                // 延迟后再次检查停止标志
                                if (stopGenerationFlag) {
                                    Log.d(TAG, "延迟后检测到停止标志，终止生成")
                                    throw java.util.concurrent.CancellationException("用户停止生成")
                                }
                            }
                        }

                        // 流式输出完成，标记消息为完成状态
                        Log.d(TAG, "流式输出完成")
                        
                        // 检查索引是否有效
                        if (aiMsgIndex >= currentList.size) {
                            Log.w(TAG, "流式输出完成时aiMsgIndex超出范围，对话可能已切换")
                            return@launch
                        }
                        
                        val completeMsg =
                                ChatMessage(
                                        content = fullResponseBuilder.toString(),
                                        isUser = false,
                                        reasoningContent =
                                                reasoningBuilder.toString().takeIf {
                                                    it.isNotEmpty()
                                                },
                                        isComplete = true,
                                        timestamp = aiTimestamp
                                )
                        currentList[aiMsgIndex] = completeMsg
                        _messageUpdate.value = MessageUpdateEvent.ItemChanged(aiMsgIndex)

                        // 保存 AI 消息到数据库
                        repository.saveMessage(conversationId, completeMsg)
                    } catch (e: Exception) {
                        // 处理错误或取消，标记为完成状态
                        if (e is java.util.concurrent.CancellationException || stopGenerationFlag) {
                            Log.d(TAG, "生成已取消")
                        } else {
                            fullResponseBuilder.append("\n[Error: ${e.message}]")
                            Log.e(TAG, "流式输出错误", e)
                        }

                        // 检查索引是否有效
                        if (aiMsgIndex >= currentList.size) {
                            Log.w(TAG, "异常处理时aiMsgIndex超出范围，对话可能已切换")
                            return@launch
                        }

                        currentList[aiMsgIndex] =
                                ChatMessage(
                                        content = fullResponseBuilder.toString(),
                                        isUser = false,
                                        isComplete = true,
                                        reasoningContent =
                                                reasoningBuilder.toString().takeIf {
                                                    it.isNotEmpty()
                                                },
                                        timestamp = aiTimestamp
                                )
                        _messageUpdate.value = MessageUpdateEvent.ItemChanged(aiMsgIndex)

                        // 即使取消或出错，也保存已生成的内容
                        val partialMsg =
                                ChatMessage(
                                        content = fullResponseBuilder.toString(),
                                        isUser = false,
                                        isComplete = true,
                                        reasoningContent =
                                                reasoningBuilder.toString().takeIf {
                                                    it.isNotEmpty()
                                                },
                                        timestamp = aiTimestamp
                                )
                        // 启动新协程异步保存，使能够立即响应停止状态
                        viewModelScope.launch {
                            try {
                                repository.saveMessage(conversationId, partialMsg)
                            } catch (e: Exception) {
                                Log.e(TAG, "Error saving partial message", e)
                            }
                        }
                    } finally {
                        _isGenerating.value = false
                        _chatStatus.value = ChatStatusEvent.Idle
                        stopGenerationFlag = false
                        generationJob = null
                    }
                }
    }

    /**
     * 重新生成回复（不添加新的用户消息）
     * 用于"重新生成"功能，基于现有的对话历史重新生成 AI 回复
     */
    fun regenerateAnswer() {
        if (conversationId.isEmpty()) {
            Log.w(TAG, "conversationId为空，无法重新生成")
            return
        }

        val currentList = _messages.value ?: mutableListOf()
        if (currentList.isEmpty()) {
            Log.w(TAG, "消息列表为空，无法重新生成")
            return
        }

        val model = ModelManager.getCurrentModel(getApplication())
        stopGenerationFlag = false
        
        Log.d(TAG, "=== 重新生成回复 ===")
        Log.d(TAG, "conversationId: $conversationId")
        Log.d(TAG, "使用模型: ${model.displayName}")

        // 准备 API 消息上下文（使用现有的消息列表，不添加新的用户消息）
        val apiMessages = currentList
            .map { ApiMessage(if (it.isUser) "user" else "assistant", it.content) }
            .toMutableList()

        // 添加 AI 占位消息
        val aiMsg = ChatMessage("", false, isComplete = false)
        currentList.add(aiMsg)
        val aiMsgIndex = currentList.size - 1
        val aiTimestamp = aiMsg.timestamp
        _messageUpdate.value = MessageUpdateEvent.ItemInserted(aiMsgIndex, isUserMessage = false)

        // 获取最后一条用户消息的内容（用于搜索）
        val lastUserMessage = currentList.lastOrNull { it.isUser }?.content ?: ""

        generationJob = viewModelScope.launch {
            _isGenerating.value = true
            var searchResultForDisplay = ""

            // 联网搜索逻辑
            val isSearchEnabled = com.example.myapplication.ui.inputbar.InputBarViewModel.getWebSearchEnabled(getApplication())
            if (isSearchEnabled && lastUserMessage.isNotEmpty()) {
                Log.d(TAG, "Starting web search for: $lastUserMessage")
                try {
                    if (aiMsgIndex >= currentList.size) {
                        Log.w(TAG, "搜索开始时aiMsgIndex超出范围")
                        return@launch
                    }
                    
                    _chatStatus.value = ChatStatusEvent.WebSearching(aiMsgIndex, aiTimestamp)
                    val searchResult = webSearchService.search(lastUserMessage)
                    searchResultForDisplay = searchResult

                    if (apiMessages.isNotEmpty()) {
                        val lastIndex = apiMessages.lastIndex
                        val lastMsg = apiMessages[lastIndex]
                        if (lastMsg.role == "user") {
                            val newContent = """
                                基于以下互联网搜索结果回答用户问题。如果搜索结果没有帮助，请使用你自己的知识。
                                
                                【搜索结果】：
                                $searchResult
                                
                                【用户问题】：${lastMsg.content}
                            """.trimIndent()
                            apiMessages[lastIndex] = lastMsg.copy(content = newContent)
                        }
                    }

                    if (aiMsgIndex >= currentList.size) {
                        Log.w(TAG, "搜索完成时aiMsgIndex超出范围")
                        return@launch
                    }
                    
                    _chatStatus.value = ChatStatusEvent.SearchComplete(aiMsgIndex, aiTimestamp)
                } catch (e: Exception) {
                    Log.e(TAG, "搜索失败", e)
                    _chatStatus.value = ChatStatusEvent.Idle
                }
            }

            val fullResponseBuilder = StringBuilder()
            if (searchResultForDisplay.isNotEmpty()) {
                val searchHeader = getApplication<Application>().getString(
                    com.example.myapplication.R.string.chat_search_result_header
                )
                fullResponseBuilder.append(searchHeader)
                fullResponseBuilder.append(searchResultForDisplay)
                fullResponseBuilder.append("\n\n---\n\n")
            }

            val reasoningBuilder = StringBuilder()
            var charCount = 0

            try {
                Log.d(TAG, "开始流式请求")
                repository.streamChat(apiMessages, model).collect { delta ->
                    if (stopGenerationFlag) {
                        throw java.util.concurrent.CancellationException("用户停止生成")
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

                    if (charCount >= BATCH_SIZE) {
                        charCount = 0
                        
                        if (stopGenerationFlag) {
                            throw java.util.concurrent.CancellationException("用户停止生成")
                        }

                        if (aiMsgIndex >= currentList.size) {
                            Log.w(TAG, "aiMsgIndex超出范围")
                            throw java.util.concurrent.CancellationException("对话已切换")
                        }

                        currentList[aiMsgIndex] = ChatMessage(
                            content = fullResponseBuilder.toString(),
                            isUser = false,
                            reasoningContent = reasoningBuilder.toString().takeIf { it.isNotEmpty() },
                            isComplete = false,
                            timestamp = aiTimestamp
                        )
                        _messageUpdate.value = MessageUpdateEvent.ItemChanged(aiMsgIndex)
                        delay(TYPING_DELAY_MS)
                        
                        if (stopGenerationFlag) {
                            throw java.util.concurrent.CancellationException("用户停止生成")
                        }
                    }
                }

                Log.d(TAG, "流式输出完成")
                
                if (aiMsgIndex >= currentList.size) {
                    Log.w(TAG, "流式输出完成时aiMsgIndex超出范围")
                    return@launch
                }
                
                val completeMsg = ChatMessage(
                    content = fullResponseBuilder.toString(),
                    isUser = false,
                    reasoningContent = reasoningBuilder.toString().takeIf { it.isNotEmpty() },
                    isComplete = true,
                    timestamp = aiTimestamp
                )
                currentList[aiMsgIndex] = completeMsg
                _messageUpdate.value = MessageUpdateEvent.ItemChanged(aiMsgIndex)
                repository.saveMessage(conversationId, completeMsg)
            } catch (e: Exception) {
                if (e is java.util.concurrent.CancellationException || stopGenerationFlag) {
                    Log.d(TAG, "生成已取消")
                } else {
                    fullResponseBuilder.append("\n[Error: ${e.message}]")
                    Log.e(TAG, "流式输出错误", e)
                }

                if (aiMsgIndex >= currentList.size) {
                    Log.w(TAG, "异常处理时aiMsgIndex超出范围")
                    return@launch
                }

                currentList[aiMsgIndex] = ChatMessage(
                    content = fullResponseBuilder.toString(),
                    isUser = false,
                    isComplete = true,
                    reasoningContent = reasoningBuilder.toString().takeIf { it.isNotEmpty() },
                    timestamp = aiTimestamp
                )
                _messageUpdate.value = MessageUpdateEvent.ItemChanged(aiMsgIndex)

                val partialMsg = ChatMessage(
                    content = fullResponseBuilder.toString(),
                    isUser = false,
                    isComplete = true,
                    reasoningContent = reasoningBuilder.toString().takeIf { it.isNotEmpty() },
                    timestamp = aiTimestamp
                )
                viewModelScope.launch {
                    try {
                        repository.saveMessage(conversationId, partialMsg)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error saving partial message", e)
                    }
                }
            } finally {
                _isGenerating.value = false
                _chatStatus.value = ChatStatusEvent.Idle
                stopGenerationFlag = false
                generationJob = null
            }
        }
    }

    /**
     * 发送消息（支持文本 + OCR识别的图片文字）
     * @param textContent 用户输入的文本内容
     * @param imageUris 图片URI列表
     */
    @Suppress("unused")
    fun sendMessageWithOCR(textContent: String, imageUris: List<Uri>) {
        sendMessageWithAttachments(textContent, imageUris, emptyList())
    }

    /**
     * 发送带附件的消息
     */
    fun sendMessageWithAttachments(textContent: String, imageUris: List<Uri>, fileUris: List<Uri>) {
        Log.d(TAG, "开始处理带附件的消息 - 文本长度: ${textContent.length}, 图片: ${imageUris.size}, 文件: ${fileUris.size}")
        
        if (imageUris.isEmpty() && fileUris.isEmpty()) {
            Log.d(TAG, "无附件，直接发送文本消息")
            sendMessage(textContent)
            return
        }

        viewModelScope.launch {
            try {
                // 显示处理进度
                if (imageUris.isNotEmpty()) {
                    Log.d(TAG, "开始OCR识别 - 图片数量: ${imageUris.size}")
                    _ocrProgress.value = OCRProgress.Recognizing(0, imageUris.size)
                }

                // 使用 InputBarRepository 处理所有附件
                Log.d(TAG, "调用 InputBarRepository 处理附件")
                val finalContent = inputBarRepository.processAttachments(
                    textContent = textContent,
                    imageUris = imageUris,
                    fileUris = fileUris,
                    onProgress = { current, total ->
                        Log.d(TAG, "OCR进度更新: $current/$total")
                        _ocrProgress.value = OCRProgress.Recognizing(current, total)
                    }
                )

                Log.d(TAG, "附件处理完成 - 最终内容长度: ${finalContent.length}")

                if (finalContent.isBlank()) {
                    Log.w(TAG, "附件解析结果为空")
                    if (imageUris.isNotEmpty()) {
                        _ocrProgress.value = OCRProgress.Error("附件解析结果为空，请重试")
                    }
                    return@launch
                }

                // 发送消息
                Log.d(TAG, "发送包含附件内容的消息")
                sendMessage(finalContent)

                // 更新进度状态
                if (imageUris.isNotEmpty()) {
                    Log.d(TAG, "OCR识别成功")
                    _ocrProgress.value = OCRProgress.Success(finalContent)
                    delay(500)
                    _ocrProgress.value = OCRProgress.Idle
                }
            } catch (e: Exception) {
                Log.e(TAG, "处理附件失败", e)
                if (imageUris.isNotEmpty()) {
                    _ocrProgress.value = OCRProgress.Error("解析失败: ${e.message}")
                }
            }
        }
    }
    fun updateMessageLikeState(message: ChatMessage) {
        if (conversationId.isEmpty()) return
        viewModelScope.launch {
            try {
                repository.updateMessageLikeState(
                    conversationId = conversationId,
                    timestamp = message.timestamp,
                    isLiked = message.isLiked,
                    isDisliked = message.isDisliked
                )
            } catch (e: Exception) {
                Log.e(TAG, "updateMessageLikeState failed", e)
            }
        }
    }

    // 🆕 删除一组消息（问题+回答）
    fun deleteMessagePair(timestamps: List<Long>) {
        if (conversationId.isEmpty() || timestamps.isEmpty()) return
        viewModelScope.launch {
            try {
                repository.deleteMessagesByTimestamps(conversationId, timestamps)
            } catch (e: Exception) {
                Log.e(TAG, "deleteMessagePair failed", e)
            }
        }
    }

    /** 生成一条长对话假数据 */
    fun generateFakeConversation(onResult: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val conversationId = repository.generateFakeData()
                onResult(conversationId)
            } catch (e: Exception) {
                Log.e(TAG, "生成假数据失败", e)
            }
        }
    }
}

sealed class MessageUpdateEvent {
    data class ItemInserted(val position: Int, val isUserMessage: Boolean = false) : MessageUpdateEvent()
    data class ItemChanged(val position: Int) : MessageUpdateEvent()
    data class HistoryLoaded(val count: Int) : MessageUpdateEvent()
    data object NoMoreHistory : MessageUpdateEvent()
}

/**
 * 聊天状态事件（用于 View 层显示状态文案）
 * 将 UI 文案的控制权交给 View 层，ViewModel 只负责通知状态变化
 */
sealed class ChatStatusEvent {
    /** 正在联网搜索 */
    data class WebSearching(val position: Int, val timestamp: Long) : ChatStatusEvent()
    /** 搜索完成，准备生成回复 */
    data class SearchComplete(val position: Int, val timestamp: Long) : ChatStatusEvent()
    /** 空闲状态 */
    data object Idle : ChatStatusEvent()
}
