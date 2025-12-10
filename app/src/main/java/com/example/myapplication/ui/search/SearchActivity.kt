package com.example.myapplication.ui.search

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.myapplication.R
import com.example.myapplication.databinding.ActivitySearchBinding
import com.example.myapplication.ui.chat.ChatActivity
import com.example.myapplication.ui.history.HistoryViewModel
import com.example.myapplication.ui.history.adapters.HistoryAdapter

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
        adapter =
                HistoryAdapter(
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
        binding.tvCancel.setOnClickListener { finish() }

        // 输入框文本变化监听
        binding.etSearch.addTextChangedListener(
                object : TextWatcher {
                    override fun beforeTextChanged(
                            s: CharSequence?,
                            start: Int,
                            count: Int,
                            after: Int
                    ) {}

                    override fun onTextChanged(
                            s: CharSequence?,
                            start: Int,
                            before: Int,
                            count: Int
                    ) {
                        val query = s?.toString()?.trim() ?: ""
                        performSearch(query)
                    }

                    override fun afterTextChanged(s: Editable?) {}
                }
        )

        // 键盘搜索按钮监听
        binding.etSearch.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val query = v.text.toString().trim()
                performSearch(query)
                hideKeyboard()
                v.clearFocus()
                true
            } else {
                false
            }
        }
    }

    // 观察 ViewModel 数据变化
    private fun observeViewModel() {
        // 观察搜索结果
        viewModel.historyList.observe(this) { list ->
            adapter.updateData(list)
            updateEmptyView(list.isEmpty() && binding.etSearch.text.toString().isNotBlank())
        }

        // 观察错误信息
        viewModel.errorMessage.observe(this) { error ->
            error?.let { Toast.makeText(this, it, Toast.LENGTH_SHORT).show() }
        }

        // 观察加载状态
        viewModel.isLoading.observe(this) { isLoading ->
            // 这里可以添加加载状态UI更新
            if (isLoading) {
                // 显示加载中
            } else {
                // 隐藏加载中
            }
        }
    }

    // 搜索不出来
    private fun updateEmptyView(show: Boolean) {
        if (show) {
            binding.rvSearchResult.visibility = View.GONE
            binding.emptySearch.root.visibility = View.VISIBLE
            // 更新无结果提示文本
            val noResultText =
                    getString(R.string.no_search_results, binding.etSearch.text.toString())
            binding.emptySearch.tvNoResult.text = noResultText
        } else {
            binding.rvSearchResult.visibility = View.VISIBLE
            binding.emptySearch.root.visibility = View.GONE
        }
    }

    private fun performSearch(query: String) {
        // 设置搜索关键字到 adapter 以启用高亮显示
        adapter.searchKeyword = query
        viewModel.search(query)
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.etSearch.windowToken, 0)
    }
}
