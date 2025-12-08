package com.example.myapplication.ui.history

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.myapplication.R
import com.example.myapplication.ui.history.adapters.HistoryAdapter
import com.example.myapplication.databinding.DialogCustomInputBinding
import com.example.myapplication.databinding.FragmentHistoryBinding
import com.example.myapplication.databinding.ItemDialogMenuBinding

/**
 * 历史对话列表 Fragment
 * 
 * 职责：
 * - 显示历史对话列表
 * - 处理对话的置顶、重命名、删除操作
 * - 通过回调接口通知宿主 Activity 对话选择事件
 * 
 * 使用方式：
 * ```kotlin
 * val fragment = HistoryFragment.newInstance(currentConversationId)
 * supportFragmentManager.beginTransaction()
 *     .replace(R.id.history_fragment_container, fragment)
 *     .commit()
 * ```
 */
class HistoryFragment : Fragment() {

    interface Listener {
        fun onHistorySelected(conversationId: String)
        fun onConversationDeleted(conversationId: String)
    }

    private var listener: Listener? = null
    private var currentConversationId: String? = null
    private lateinit var viewModel: HistoryViewModel
    private lateinit var historyAdapter: HistoryAdapter
    
    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!

    override fun onAttach(context: Context) {
        super.onAttach(context)
        listener = activity as? Listener
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentConversationId = arguments?.getString(ARG_CURRENT_CONVERSATION_ID)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(requireActivity())[HistoryViewModel::class.java]

        historyAdapter = HistoryAdapter(
            mutableListOf(),
            currentConversationId = currentConversationId,
            onItemClick = { history ->
                listener?.onHistorySelected(history.id)
            },
            onItemLongClick = { history ->
                val items = listOf(
                    mapOf("text" to if (history.isPinned) "取消置顶" else "置顶会话", "icon" to R.drawable.icon_pin),
                    mapOf("text" to "重命名会话标题", "icon" to R.drawable.icon_edit),
                    mapOf("text" to "删除会话", "icon" to R.drawable.icon_delete)
                )

                val adapter = object : android.widget.ArrayAdapter<Map<String, Any>>(
                    requireContext(),
                    R.layout.item_dialog_menu,
                    items
                ) {
                    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                        val binding: ItemDialogMenuBinding
                        val v: View
                        if (convertView == null) {
                            binding = ItemDialogMenuBinding.inflate(layoutInflater, parent, false)
                            v = binding.root
                            v.tag = binding
                        } else {
                            v = convertView
                            binding = v.tag as ItemDialogMenuBinding
                        }
                        val item = getItem(position) ?: return v
                        val iconRes = item["icon"] as Int
                        val text = item["text"] as String
                        binding.ivMenuIcon.setImageResource(iconRes)
                        binding.tvMenuText.text = text
                        if (text == "删除会话") {
                            binding.tvMenuText.setTextColor(android.graphics.Color.RED)
                            binding.ivMenuIcon.setColorFilter(android.graphics.Color.RED)
                        } else {
                            binding.tvMenuText.setTextColor(android.graphics.Color.BLACK)
                            binding.ivMenuIcon.setColorFilter(android.graphics.Color.BLACK)
                        }
                        return v
                    }
                }

                AlertDialog.Builder(requireContext(), R.style.RoundedDialogTheme)
                    .setAdapter(adapter) { _, which ->
                        when (which) {
                            0 -> viewModel.togglePin(history.id, history.isPinned)
                            1 -> showRenameDialog(history.title) { newTitle ->
                                viewModel.renameConversation(history.id, newTitle)
                            }
                            2 -> showDeleteConfirmDialog {
                                viewModel.deleteConversation(history.id)
                                listener?.onConversationDeleted(history.id)
                            }
                        }
                    }
                    .show()
            }
        )

        binding.historyRecyclerview.layoutManager = LinearLayoutManager(requireContext())
        binding.historyRecyclerview.adapter = historyAdapter
        val divider = DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL)
        divider.setDrawable(ColorDrawable(android.graphics.Color.parseColor("#EEEEEE")))
        binding.historyRecyclerview.addItemDecoration(divider)

        viewModel.historyList.observe(viewLifecycleOwner) { history ->
            historyAdapter.updateData(history)
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onDetach() {
        super.onDetach()
        listener = null
    }

    fun updateCurrentConversation(conversationId: String?) {
        currentConversationId = conversationId
        if (this::historyAdapter.isInitialized) {
            historyAdapter.setSelectedId(conversationId)
        }
    }

    /**
     * 显示重命名对话框
     */
    private fun showRenameDialog(
        currentTitle: String,
        onRename: (String) -> Unit
    ) {
        val binding = DialogCustomInputBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(requireContext(), R.style.RoundedDialogTheme)
            .setView(binding.root)
            .create()

        binding.tvDialogTitle.text = "修改标题"
        binding.etDialogInput.setText(currentTitle)
        binding.etDialogInput.visibility = View.VISIBLE
        binding.tvDialogMessage.visibility = View.GONE
        
        // 移动光标到末尾
        binding.etDialogInput.setSelection(currentTitle.length)

        binding.btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        binding.btnConfirm.setOnClickListener {
            val newTitle = binding.etDialogInput.text.toString().trim()
            if (newTitle.isNotEmpty() && newTitle != currentTitle) {
                onRename(newTitle)
                Toast.makeText(requireContext(), "已重命名", Toast.LENGTH_SHORT).show()
            }
            dialog.dismiss()
        }

        dialog.show()
    }

    /**
     * 显示删除确认对话框
     */
    private fun showDeleteConfirmDialog(
        onConfirm: () -> Unit
    ) {
        val binding = DialogCustomInputBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(requireContext(), R.style.RoundedDialogTheme)
            .setView(binding.root)
            .create()

        binding.tvDialogTitle.text = "确认删除"
        binding.etDialogInput.visibility = View.GONE
        binding.tvDialogMessage.visibility = View.VISIBLE
        binding.tvDialogMessage.text = "确定要删除这个对话吗？"
        
        // 设置删除按钮文本
        (binding.btnConfirm as? android.widget.TextView)?.text = "删除"
        
        binding.btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        binding.btnConfirm.setOnClickListener {
            onConfirm()
            Toast.makeText(requireContext(), "已删除", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        dialog.show()
    }

    companion object {
        private const val ARG_CURRENT_CONVERSATION_ID = "arg_current_conversation_id"
        fun newInstance(currentConversationId: String?): HistoryFragment {
            val f = HistoryFragment()
            val args = Bundle()
            if (currentConversationId != null) {
                args.putString(ARG_CURRENT_CONVERSATION_ID, currentConversationId)
            }
            f.arguments = args
            return f
        }
    }
}
