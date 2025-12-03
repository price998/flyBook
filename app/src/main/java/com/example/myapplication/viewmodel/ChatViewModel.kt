package com.example.myapplication.viewmodel

import android.app.Application
import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.db.AppDatabase
import com.example.myapplication.model.ApiMessage
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.MediaType
import com.example.myapplication.model.ModelConfig
import com.example.myapplication.model.ModelRegistry
import com.example.myapplication.model.SelectedMedia
import com.example.myapplication.repository.ChatRepository
import com.example.myapplication.utils.ModelPreferences
import com.example.myapplication.utils.OCRHelper
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.charset.Charset

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private const val TAG = "ChatViewModel"
        private const val TYPING_DELAY_MS = 80L // 打字机效果延迟（毫秒），增加延迟减少刷新频率，提升流畅度
        private const val BATCH_SIZE = 5 // 每收到5个字符才更新一次UI，减少渲染次数
        private const val MAX_FILE_PREVIEW_CHARS = 8000
    }

    private val database = AppDatabase.getDatabase(application)
    private val repository = ChatRepository(database.messageDao(), database.conversationDao())

    // 当前对话ID
    private var conversationId: String = ""

    private val _messages = MutableLiveData<MutableList<ChatMessage>>(mutableListOf())
    val messages: LiveData<MutableList<ChatMessage>> = _messages

    // 用于通知 UI 列表有更新（插入或修改）
    private val _messageUpdate = MutableLiveData<MessageUpdateEvent>()
    val messageUpdate: LiveData<MessageUpdateEvent> = _messageUpdate

    private var isLoadingMore = false
    private var hasMoreHistory = true
    
    // 停止生成标志
    private var stopGenerationFlag = false
    
    // 是否正在生成
    private val _isGenerating = MutableLiveData<Boolean>(false)
    val isGenerating: LiveData<Boolean> = _isGenerating


    // 图片OCR解析状态
    private val _ocrProgress = MutableLiveData<OCRProgress>()
    val ocrProgress: LiveData<OCRProgress> = _ocrProgress

    sealed class OCRProgress {
        object Idle : OCRProgress()
        data class Recognizing(val current: Int, val total: Int) : OCRProgress() // 当前进度
        data class Success(val text: String) : OCRProgress()
        data class Error(val message: String) : OCRProgress()
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
    private val _currentModel =
            MutableLiveData<ModelConfig>(ModelPreferences.getSelectedModel(application))
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
        // 检查conversationId，如果为空则创建新对话
        if (conversationId.isEmpty()) {
            Log.w(TAG, "conversationId为空，创建新对话")
            viewModelScope.launch {
                try {
                    val title = if (content.length > 20) content.substring(0, 20) + "..." else content
                    conversationId = repository.createConversation(title)
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
        val model = _currentModel.value ?: ModelRegistry.DEFAULT_MODEL
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
        _messageUpdate.value = MessageUpdateEvent.ItemInserted(currentList.size - 1)

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
                currentList.map { ApiMessage(if (it.isUser) "user" else "assistant", it.content) }

        // 3. 添加 AI 占位消息（标记为未完成）
        val aiMsg = ChatMessage("", false, isComplete = false)
        currentList.add(aiMsg)
        val aiMsgIndex = currentList.size - 1
        _messageUpdate.value = MessageUpdateEvent.ItemInserted(aiMsgIndex)

        viewModelScope.launch {
            _isGenerating.value = true
            val fullResponseBuilder = StringBuilder()
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
                    val content = delta.content
                    val reasoning = delta.reasoningContent

                    if (!reasoning.isNullOrEmpty()) {
                        reasoningBuilder.append(reasoning)
                    }

                    if (!content.isNullOrEmpty()) {
                        fullResponseBuilder.append(content)
                        charCount += content.length
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

    /**
     * 批量OCR识别所有选中的图片
     * @return 识别出的文字列表（按顺序）
     */
    private suspend fun batchOCRImages(images: List<Uri>): List<String> {
        val ocrHelper = OCRHelper(getApplication())
        val results = mutableListOf<String>()
        
        images.forEachIndexed { index, uri ->
            try {
                _ocrProgress.value = OCRProgress.Recognizing(index + 1, images.size)
                val recognizedText = ocrHelper.parseImage(uri)
                
                if (recognizedText.startsWith("错误") || 
                    recognizedText.startsWith("OCR识别失败") || 
                    recognizedText.startsWith("处理图片失败")) {
                    results.add("") // 识别失败，添加空字符串
                    Log.w(TAG, "图片OCR识别失败: $uri")
                } else {
                    results.add(recognizedText)
                }
            } catch (e: Exception) {
                Log.e(TAG, "图片OCR解析异常: $uri", e)
                results.add("") // 异常时添加空字符串
            }
        }
        
        ocrHelper.close() // 释放资源
        return results
    }

    /**
     * 发送消息（支持文本 + OCR识别的图片文字）
     * @param textContent 用户输入的文本内容
     * @param imageUris 图片URI列表
     */
    fun sendMessageWithOCR(textContent: String, imageUris: List<Uri>) {
        sendMessageWithAttachments(textContent, imageUris, emptyList())
    }

    fun sendMessageWithAttachments(
            textContent: String,
            imageUris: List<Uri>,
            fileUris: List<Uri>
    ) {
        if (imageUris.isEmpty() && fileUris.isEmpty()) {
            sendMessage(textContent)
            return
        }

        viewModelScope.launch {
            try {
                val sections = mutableListOf<String>()

                if (textContent.isNotBlank()) {
                    sections.add(textContent)
                }

                if (imageUris.isNotEmpty()) {
                    _ocrProgress.value = OCRProgress.Recognizing(0, imageUris.size)
                    val ocrResults = batchOCRImages(imageUris)
                    val imageSection = buildImageSection(ocrResults)
                    if (imageSection.isNotBlank()) {
                        sections.add(imageSection)
                    }
                }

                if (fileUris.isNotEmpty()) {
                    val fileResults = parseFiles(fileUris)
                    val fileSection = buildFileSection(fileResults)
                    if (fileSection.isNotBlank()) {
                        sections.add(fileSection)
                    }
                }

                val finalContent = sections.joinToString("\n\n").trim()

                if (finalContent.isBlank()) {
                    if (imageUris.isNotEmpty()) {
                        _ocrProgress.value = OCRProgress.Error("附件解析结果为空，请重试")
                    }
                    return@launch
                }

                withContext(Dispatchers.Main) {
                    sendMessage(finalContent)
                }

                if (imageUris.isNotEmpty()) {
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

    private fun buildImageSection(ocrResults: List<String>): String {
        val builder = StringBuilder()
        ocrResults.forEachIndexed { index, ocrText ->
            if (ocrText.isNotBlank()) {
                if (builder.isNotEmpty()) {
                    builder.append("\n\n")
                }
                builder.append("图片${index + 1}识别内容：\n")
                builder.append(ocrText)
            }
        }
        return builder.toString()
    }

    private suspend fun parseFiles(fileUris: List<Uri>): List<FileParseResult> {
        val resolver = getApplication<Application>().contentResolver
        val ocrHelper = OCRHelper(getApplication())
        
        return withContext(Dispatchers.IO) {
            val results = fileUris.map { uri ->
                val mimeType = resolver.getType(uri) ?: "application/octet-stream"
                val name = queryFileName(resolver, uri) ?: uri.lastPathSegment.orEmpty()
                val extension = name.substringAfterLast('.', "").lowercase()
                
                try {
                    val content = when {
                        mimeType == "application/pdf" || extension == "pdf" -> {
                            readPdfContent(resolver, uri, ocrHelper)
                        }
                        mimeType == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" || extension == "docx" -> {
                            readDocxContent(resolver, uri)
                        }
                        mimeType == "application/msword" || extension == "doc" -> {
                            "不支持 .doc 格式，请转换为 .docx 后重试"
                        }
                        mimeType.startsWith("text/") || 
                        mimeType.contains("json") || 
                        mimeType.contains("xml") ||
                        mimeType.contains("javascript") ||
                        mimeType.contains("gradle") ||
                        mimeType.contains("properties") ||
                        extension in setOf("txt", "md", "json", "xml", "html", "css", "js", "kt", "java", "py", "c", "cpp", "h", "gradle", "properties", "log") -> {
                            readTextContent(resolver, uri)
                        }
                        else -> {
                            // 尝试作为文本读取，如果检测到二进制则报错
                            try {
                                readTextContent(resolver, uri)
                            } catch (e: Exception) {
                                "不支持的文件格式: $mimeType ($extension)"
                            }
                        }
                    }
                    
                    // 截断过长的内容
                    val finalContent = if (content.length > MAX_FILE_PREVIEW_CHARS) {
                        content.substring(0, MAX_FILE_PREVIEW_CHARS) + "\n\n[内容因过长已截断]"
                    } else {
                        content
                    }

                    FileParseResult(
                        fileName = name.ifBlank { "未命名文件" },
                        content = finalContent,
                        mimeType = mimeType
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "解析文件失败: $uri", e)
                    FileParseResult(
                        fileName = name.ifBlank { "未命名文件" },
                        content = "解析失败: ${e.message}",
                        mimeType = mimeType
                    )
                }
            }
            ocrHelper.close()
            results
        }
    }

    private suspend fun readDocxContent(resolver: ContentResolver, uri: Uri): String {
        return withContext(Dispatchers.IO) {
            try {
                resolver.openInputStream(uri)?.use { inputStream ->
                    val zipInputStream = java.util.zip.ZipInputStream(inputStream)
                    var entry = zipInputStream.nextEntry
                    while (entry != null) {
                        if (entry.name == "word/document.xml") {
                            val content = zipInputStream.bufferedReader().readText()
                            return@withContext parseDocxXml(content)
                        }
                        zipInputStream.closeEntry()
                        entry = zipInputStream.nextEntry
                    }
                }
                "无法读取Docx内容：未找到文档主体"
            } catch (e: Exception) {
                Log.e(TAG, "读取Docx失败", e)
                "读取Docx失败: ${e.message}"
            }
        }
    }

    private fun parseDocxXml(xml: String): String {
        // 简单的正则提取，保留段落结构
        var text = xml
        // 替换段落标签为换行
        text = text.replace(Regex("<w:p.*?>"), "\n")
        text = text.replace(Regex("<w:br/>"), "\n")
        text = text.replace(Regex("<w:tab/>"), "\t")
        // 移除所有其他标签
        text = text.replace(Regex("<[^>]+>"), "")
        // 处理XML实体
        text = text.replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
        
        return text.trim()
    }

    private suspend fun readPdfContent(resolver: ContentResolver, uri: Uri, ocrHelper: OCRHelper): String {
        return resolver.openFileDescriptor(uri, "r")?.use { pfd ->
            val pdfRenderer = PdfRenderer(pfd)
            val builder = StringBuilder()
            val pageCount = pdfRenderer.pageCount
            val maxPages = 5 // 限制页数，避免处理时间过长
            
            for (i in 0 until minOf(pageCount, maxPages)) {
                val page = pdfRenderer.openPage(i)
                // 创建Bitmap，放大2倍以提高OCR识别率
                val bitmap = Bitmap.createBitmap(
                    page.width * 2,
                    page.height * 2, 
                    Bitmap.Config.ARGB_8888
                )
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                
                val text = ocrHelper.parseBitmap(bitmap)
                if (text.isNotBlank()) {
                    builder.append("第 ${i + 1} 页内容：\n$text\n\n")
                }
                
                page.close()
                bitmap.recycle()
            }
            
            if (pageCount > maxPages) {
                builder.append("\n[PDF过长，仅读取前 $maxPages 页]")
            }
            
            if (builder.isEmpty()) {
                "PDF内容识别为空"
            } else {
                builder.toString()
            }
        } ?: throw IllegalStateException("无法打开PDF文件")
    }

    private fun readTextContent(resolver: ContentResolver, uri: Uri): String {
        resolver.openInputStream(uri)?.use { stream ->
            // 限制读取大小 1MB
            val maxBytes = 1024 * 1024 
            val buffer = ByteArray(maxBytes)
            var totalRead = 0
            while (totalRead < maxBytes) {
                val read = stream.read(buffer, totalRead, maxBytes - totalRead)
                if (read == -1) break
                totalRead += read
            }
            
            val readBytes = if (totalRead == maxBytes) buffer else buffer.copyOf(totalRead)
            
            // 检查是否为二进制文件 (检查前1024字节中的空字节)
            val checkLength = minOf(readBytes.size, 1024)
            for (i in 0 until checkLength) {
                if (readBytes[i] == 0.toByte()) {
                    throw Exception("检测到二进制文件，无法作为文本读取")
                }
            }
            
            // 尝试检测编码
            // 优先尝试 UTF-8
            try {
                val text = String(readBytes, Charsets.UTF_8)
                // 简单的启发式检查：如果包含过多替换字符，可能不是UTF-8
                // \uFFFD 是 Unicode 替换字符
                if (text.contains("\uFFFD")) {
                     // 如果替换字符占比过高，尝试 GBK
                     val replacementCount = text.count { it == '\uFFFD' }
                     if (replacementCount > text.length * 0.05) {
                         throw Exception("Probably not UTF-8")
                     }
                }
                return text
            } catch (e: Exception) {
                // 尝试 GBK (常见的中文编码)
                try {
                    return String(readBytes, Charset.forName("GBK"))
                } catch (e2: Exception) {
                    // 最后的退路：ISO-8859-1
                    return String(readBytes, Charsets.ISO_8859_1)
                }
            }
        }
        throw IllegalStateException("无法读取文件流")
    }

    private fun queryFileName(resolver: ContentResolver, uri: Uri): String? {
        val projection = arrayOf(OpenableColumns.DISPLAY_NAME)
        resolver.query(uri, projection, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) {
                return cursor.getString(nameIndex)
            }
        }
        return null
    }

    private fun buildFileSection(fileResults: List<FileParseResult>): String {
        if (fileResults.isEmpty()) return ""
        val builder = StringBuilder()
        fileResults.forEachIndexed { index, result ->
            if (builder.isNotEmpty()) {
                builder.append("\n\n")
            }
            builder.append("文件${index + 1}（${result.fileName}）内容：\n")
            builder.append(result.content)
        }
        return builder.toString()
    }

    data class FileParseResult(
            val fileName: String,
            val content: String,
            val mimeType: String
    )
}

sealed class MessageUpdateEvent {
    data class ItemInserted(val position: Int) : MessageUpdateEvent()
    data class ItemChanged(val position: Int) : MessageUpdateEvent()
    data class HistoryLoaded(val count: Int) : MessageUpdateEvent()
    object NoMoreHistory : MessageUpdateEvent()
}
