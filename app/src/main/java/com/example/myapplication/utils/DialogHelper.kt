package com.example.myapplication.utils

import android.content.Context
import android.view.LayoutInflater
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.example.myapplication.databinding.DialogCustomInputBinding



/** 对话框工具类 提供通用的对话框创建方法，避免代码重复 */
object DialogHelper {

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
        val binding = DialogCustomInputBinding.inflate(LayoutInflater.from(context))
        val dialog = AlertDialog.Builder(context, com.example.myapplication.R.style.RoundedDialogTheme)
            .setView(binding.root)
            .create()

        binding.tvDialogTitle.text = "修改标题"
        binding.etDialogInput.setText(currentTitle)
        binding.etDialogInput.visibility = android.view.View.VISIBLE
        binding.tvDialogMessage.visibility = android.view.View.GONE
        
        // 移动光标到末尾
        binding.etDialogInput.setSelection(currentTitle.length)

        binding.btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        binding.btnConfirm.setOnClickListener {
            val newTitle = binding.etDialogInput.text.toString().trim()
            if (newTitle.isNotEmpty() && newTitle != currentTitle) {
                onRename(newTitle)
                Toast.makeText(context, "已重命名", Toast.LENGTH_SHORT).show()
            }
            dialog.dismiss()
        }

        dialog.show()
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
        val binding = DialogCustomInputBinding.inflate(LayoutInflater.from(context))
        val dialog = AlertDialog.Builder(context, com.example.myapplication.R.style.RoundedDialogTheme)
            .setView(binding.root)
            .create()

        binding.tvDialogTitle.text = title
        binding.etDialogInput.visibility = android.view.View.GONE
        binding.tvDialogMessage.visibility = android.view.View.VISIBLE
        binding.tvDialogMessage.text = message
        
        // 强制转换为 TextView 以便设置文本，因为布局中 btnConfirm 可能是 View 类型（如果 xml 定义不一致）
        // 实际上在 DataBinding 中会根据 xml 类型生成。假设 btnConfirm 是 TextView 或 Button
        (binding.btnConfirm as? android.widget.TextView)?.text = "删除"
        
        binding.btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        binding.btnConfirm.setOnClickListener {
            onConfirm()
            Toast.makeText(context, "已删除", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        dialog.show()
    }
}
