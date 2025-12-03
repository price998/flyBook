package com.example.myapplication.ui

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
import com.example.myapplication.adapter.HistoryAdapter
import com.example.myapplication.databinding.ActivitySearchBinding
import com.example.myapplication.viewmodel.HistoryViewModel

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

        // 默认让输入框获取焦点并弹出键盘
        binding.etSearch.requestFocus()
        // 注意：自动弹出键盘通常需要一点延迟或者在 onResume 中处理
    }

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

    private fun observeViewModel() {
        // 观察搜索结果
        viewModel.historyList.observe(this) { list ->
            adapter.updateData(list)
        }
    }

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

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.etSearch.windowToken, 0)
    }
}
