package com.example.myapplication.utils

import android.content.Context
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.myapplication.adapter.ModelAdapter
import com.example.myapplication.databinding.DialogModelSelectorBinding
import com.example.myapplication.model.ModelConfig
import com.example.myapplication.model.ModelRegistry
import com.google.android.material.bottomsheet.BottomSheetDialog

/**
 * 对话框工具类
 * 提供通用的对话框创建方法，避免代码重复
 */
object DialogHelper {

    /**
     * 显示模型选择对话框
     * @param context 上下文
     * @param currentModelId 当前选中的模型ID
     * @param onModelSelected 模型选择回调
     */
    fun showModelSelectorDialog(
        context: Context,
        currentModelId: String,
        onModelSelected: (ModelConfig) -> Unit
    ) {
        val dialog = BottomSheetDialog(context)
        val dialogBinding = DialogModelSelectorBinding.inflate(LayoutInflater.from(context))
        dialog.setContentView(dialogBinding.root)

        dialogBinding.modelListRecyclerview.layoutManager = LinearLayoutManager(context)

        val adapter = ModelAdapter(ModelRegistry.ALL_MODELS, currentModelId) { modelConfig ->
            onModelSelected(modelConfig)
            Toast.makeText(context, "已切换到: ${modelConfig.displayName}", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        dialogBinding.modelListRecyclerview.adapter = adapter
        dialog.show()
    }

    /**
     * 显示重命名对话框
     * @param context 上下文
     * @param currentTitle 当前标题
     * @param onRename 重命名回调，参数为新标题
     */
    fun showRenameDialog(
        context: Context,
        currentTitle: String,
        onRename: (String) -> Unit
    ) {
        val editText = EditText(context).apply {
            setText(currentTitle)
            hint = "输入新标题"
            setPadding(50, 30, 50, 30)
        }

        AlertDialog.Builder(context)
            .setTitle("重命名对话")
            .setView(editText)
            .setPositiveButton("确定") { _, _ ->
                val newTitle = editText.text.toString().trim()
                if (newTitle.isNotEmpty() && newTitle != currentTitle) {
                    onRename(newTitle)
                    Toast.makeText(context, "已重命名", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /**
     * 显示删除确认对话框
     * @param context 上下文
     * @param title 对话标题
     * @param message 提示消息
     * @param onConfirm 确认删除回调
     */
    fun showDeleteConfirmDialog(
        context: Context,
        title: String = "确认删除",
        message: String = "确定要删除这个对话吗？",
        onConfirm: () -> Unit
    ) {
        AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("删除") { _, _ ->
                onConfirm()
                Toast.makeText(context, "已删除", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }
}
