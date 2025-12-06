package com.example.myapplication.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.db.AppDatabase
import com.example.myapplication.model.ChatHistory
import com.example.myapplication.repository.ChatRepository
import com.example.myapplication.repository.HistoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.myapplication.model.ChatMessage

/**
 * 历史对话列表的 ViewModel
 * 在 DialogueActivity 和 ChatActivity 之间共享
 */
class HistoryViewModel(application: Application) : AndroidViewModel(application) {
    // 1. 依赖注入：初始化数据层仓库（Repository）
    private val repository = HistoryRepository(application)
    private val chatRepository = ChatRepository(
        AppDatabase.getDatabase(application).messageDao(),
        AppDatabase.getDatabase(application).conversationDao()
    )
    // 2. 数据容器：MutableLiveData（内部可修改）+ LiveData（外部仅可观察），保证数据单向流动
    private val _historyList = MutableLiveData<List<ChatHistory>>()
    val historyList: LiveData<List<ChatHistory>> = _historyList

    @Suppress("unused")
    private val _errorMessage = MutableLiveData<String?>()
    @Suppress("unused")
    val errorMessage: LiveData<String?> = _errorMessage
    // 3. 初始化自动加载历史列表
    init {
        loadHistory()
    }

    /**
     * 加载历史对话列表
     */
    fun loadHistory() {
        viewModelScope.launch {
            _errorMessage.value = null
            try {
                val list = repository.getHistoryList()
                _historyList.value = list
            } catch (e: Exception) {
                _errorMessage.value = "加载失败: ${e.message}"
            }
        }
    }

    /**
     * 创建新对话
     */
    fun createNewConversation(title: String, onResult: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val displayTitle = if (title.length > 20) title.substring(0, 20) + "..." else title
                val id = withContext(Dispatchers.IO) {
                    chatRepository.createConversation(displayTitle)
                }
                onResult(id)
                loadHistory()
            } catch (e: Exception) {
                android.util.Log.e("HistoryViewModel", "创建对话失败", e)
                _errorMessage.value = "创建对话失败: ${e.message}"
            }
        }
    }

    /**
     * 重命名对话
     */
    fun renameConversation(conversationId: String, newTitle: String) {
        viewModelScope.launch {
            try {
                chatRepository.updateConversationTitle(conversationId, newTitle)
                loadHistory()
            } catch (e: Exception) {
                _errorMessage.value = "重命名失败: ${e.message}"
            }
        }
    }

    /**
     * 删除对话（预留功能）
     */
    @Suppress("unused")
    fun deleteConversation(conversationId: String) {
        viewModelScope.launch {
            try {
                repository.deleteConversation(conversationId)
                loadHistory()
            } catch (e: Exception) {
                _errorMessage.value = "删除失败: ${e.message}"
            }
        }
    }

    /**
     * 切换置顶状态（预留功能）
     */
    @Suppress("unused")
    fun togglePin(conversationId: String, currentIsPinned: Boolean) {
        viewModelScope.launch {
            try {
                repository.togglePin(conversationId, !currentIsPinned)
                loadHistory()
            } catch (e: Exception) {
                _errorMessage.value = "操作失败: ${e.message}"
            }
        }
    }

    /**
     * 搜索历史对话
     */
    @Suppress("unused")
    fun search(query: String) {
        viewModelScope.launch {
            try {
                if (query.isBlank()) {
                    loadHistory()
                } else {
                    val list = repository.searchHistory(query)
                    _historyList.value = list
                }
            } catch (e: Exception) {
                _errorMessage.value = "搜索失败: ${e.message}"
            }
        }
    }
    /**
     * 生成一条长对话假数据（默认 120 轮 = 240 条消息）
     * 在 IO 线程里通过 ChatRepository 一条条写入，自动维护 messageCount 等字段
     */
    fun generateFakeConversation(
        pairCount: Int = 1000,
        onResult: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val conversationId = withContext(Dispatchers.IO) {
                    // 会话标题
                    val title = "假数据长对话（$pairCount 轮）"
                    val id = chatRepository.createConversation(title)

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

                        // 利用已有的 saveMessage，顺带更新会话的 messageCount、lastMessagePreview 等
                        chatRepository.saveMessage(id, userMsg)
                        chatRepository.saveMessage(id, botMsg)
                    }

                    id
                }

                // 刷新历史列表
                loadHistory()
                // 回调给 UI
                onResult(conversationId)
            } catch (e: Exception) {
                _errorMessage.value = "生成假数据失败: ${e.message}"
            }
        }
    }
}
