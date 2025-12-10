package com.example.myapplication.ui.common.dialogs

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.*
import androidx.fragment.app.DialogFragment
import com.example.myapplication.databinding.BottomSheetMessageActionsBinding
import com.example.myapplication.domain.ChatMessage

/**
 * 消息操作底部弹窗
 * 
 * 职责：
 * - 显示消息操作菜单（复制、选择文本、翻译、点赞、点踩、重新生成、删除）
 * - 根据长按位置动态定位弹窗
 * - 通过回调接口通知宿主 Activity 处理操作
 */
class MessageActionsBottomSheet : DialogFragment() {

    interface Listener {
        fun onCopy(message: ChatMessage)
        fun onSelectText(message: ChatMessage)
        fun onSpeak(message: ChatMessage)
        fun onTranslate(message: ChatMessage)
        fun onLike(message: ChatMessage)
        fun onDislike(message: ChatMessage)
        fun onReload(message: ChatMessage)
        fun onDelete(message: ChatMessage)
    }

    private var _binding: BottomSheetMessageActionsBinding? = null
    private val binding get() = _binding!!

    private lateinit var message: ChatMessage
    private lateinit var actionsListener: Listener

    private var anchorX: Int = 0
    private var anchorY: Int = 0

    companion object {
        fun newInstance(
            message: ChatMessage,
            anchorX: Int,
            anchorY: Int,
            listener: Listener
        ): MessageActionsBottomSheet {
            return MessageActionsBottomSheet().apply {
                this.message = message
                this.anchorX = anchorX
                this.anchorY = anchorY
                this.actionsListener = listener
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isCancelable = true
        setStyle(STYLE_NO_TITLE, 0)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetMessageActionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.let { window ->
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            window.setLayout(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window.setDimAmount(0.25f)

            // 根据长按位置放在附近
            val decor = window.decorView
            decor.post {
                val params = window.attributes
                params.gravity = Gravity.TOP or Gravity.START

                val dm = resources.displayMetrics
                val screenW = dm.widthPixels
                val screenH = dm.heightPixels
                val popupW = decor.measuredWidth
                val popupH = decor.measuredHeight

                val safeX = anchorX.coerceIn(0, screenW - popupW)
                val safeY = anchorY.coerceIn(0, screenH - popupH)

                params.x = safeX
                params.y = safeY
                window.attributes = params
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.itemCopy.setOnClickListener {
            actionsListener.onCopy(message); dismiss()
        }
        binding.itemSelect.setOnClickListener {
            actionsListener.onSelectText(message); dismiss()
        }
        binding.itemRead.setOnClickListener {
            actionsListener.onSpeak(message); dismiss()
        }
        binding.itemTranslate.setOnClickListener {
            actionsListener.onTranslate(message); dismiss()
        }
        binding.itemLike.setOnClickListener {
            actionsListener.onLike(message); dismiss()
        }
        binding.itemDislike.setOnClickListener {
            actionsListener.onDislike(message); dismiss()
        }
        binding.itemReload.setOnClickListener {
            actionsListener.onReload(message); dismiss()
        }
        binding.itemDelete.setOnClickListener {
            actionsListener.onDelete(message); dismiss()
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

}
