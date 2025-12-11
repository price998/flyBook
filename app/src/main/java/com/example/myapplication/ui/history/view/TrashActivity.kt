package com.example.myapplication.ui.history.view

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.myapplication.databinding.ActivityTrashBinding
import com.example.myapplication.ui.history.adapters.TrashAdapter
import com.example.myapplication.ui.history.viewmodel.TrashViewModel

class TrashActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTrashBinding
    private lateinit var viewModel: TrashViewModel
    private lateinit var adapter: TrashAdapter
    // 页面初始化
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTrashBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // 初始化 ViewModel、Adapter
        viewModel = ViewModelProvider(this)[TrashViewModel::class.java]

        adapter =
            TrashAdapter(
                mutableListOf(),
                onRestore = { item -> viewModel.restore(item.id) },
                onDelete = { item -> viewModel.hardDelete(item.id) }
            )
        // 绑定 RecyclerView
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        viewModel.trashList.observe(this) { list -> adapter.submit(list) }

        // 加载回收站列表
        viewModel.loadTrash()

        // 返回按钮点击事件
        binding.btnBack.setOnClickListener { finish() }
    }
    // 页面恢复
    override fun onResume() {
        super.onResume()
        viewModel.cleanupExpiredTrash()
        viewModel.loadTrash()
    }
}