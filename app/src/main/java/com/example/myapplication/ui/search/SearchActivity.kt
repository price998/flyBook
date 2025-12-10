package com.example.myapplication.ui.search

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.myapplication.ui.history.adapters.HistoryAdapter
import com.example.myapplication.databinding.ActivitySearchBinding
import com.example.myapplication.ui.chat.ChatActivity
import com.example.myapplication.ui.history.HistoryViewModel

/**
 * 搜索 Activity
 * 
 * 职责：
 * - 提供历史对话搜索功能
 * - 实时搜索并高亮关键字
 * - 点击搜索结果跳转到对应对话
 */
class SearchActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySearchBinding
    private lateinit var viewModel: HistoryViewModel
    private lateinit var adapter: HistoryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySearchBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // 适配状态栏颜色
        WindowCompat.setDecorFitsSystemWindows(window, true)

        // 初始化 ViewModel
        viewModel = ViewModelProvider(this)[HistoryViewModel::class.java]

        setupRecyclerView()
        setupListeners()
        observeViewModel()

        // 默认让输入框获取焦点
        binding.etSearch.requestFocus()
    }
    
    // ========== 初始化方法 ==========

    /** 设置搜索结果列表 */
    private fun setupRecyclerView() {
        adapter = HistoryAdapter(
            mutableListOf(),
            onItemClick = { history ->
                // 点击跳转到 ChatActivity
                val intent = Intent(this, ChatActivity::class.java)
                intent.putExtra(ChatActivity.EXTRA_CONVERSATION_ID, history.id)
                startActivity(intent)
                // 返回后回到搜索页
            },
            onItemLongClick = { 
                // 搜索页暂不支持长按操作
            }
        )
        
        binding.rvSearchResult.layoutManager = LinearLayoutManager(this)
        binding.rvSearchResult.adapter = adapter
    }

    /** 设置事件监听器 */
    private fun setupListeners() {
        // 取消按钮
        binding.tvCancel.setOnClickListener {
            finish()
        }

        // 输入框监听
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val keyword = s.toString().trim()
                performSearch(keyword)
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        // 键盘动作监听
        binding.etSearch.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val keyword = v.text.toString().trim()
                performSearch(keyword)
                hideKeyboard()
                v.clearFocus()
                true
            } else {
                false
            }
        }
    }
    
    // ========== ViewModel 观察 ==========

    /** 观察搜索结果 */
    private fun observeViewModel() {
        // 观察搜索结果
        viewModel.historyList.observe(this) { list ->
            adapter.updateData(list)
        }
    }
    
    // ========== 搜索逻辑 ==========

    /** 执行搜索 */
    private fun performSearch(keyword: String) {
        // 设置高亮关键字
        adapter.searchKeyword = keyword

        // 调用 ViewModel 进行搜索
        // 如果是空字符串，ViewModel 的 search 方法内部逻辑是加载所有历史
        // 如果只想在有输入时显示，可以在这里判断
        if (keyword.isEmpty()) {
             // 如果想要空的时候清空列表，可以手动传空列表或者修改 ViewModel
             // 这里复用 ViewModel 逻辑，空字符串会显示全部历史
             adapter.updateData(emptyList())
        } else {
             viewModel.search(keyword)
        }
    }

    /** 隐藏软键盘 */
    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.etSearch.windowToken, 0)
    }
}
