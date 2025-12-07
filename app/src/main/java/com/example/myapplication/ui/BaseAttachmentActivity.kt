package com.example.myapplication.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.view.MotionEvent
import android.view.View
import android.widget.PopupWindow
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.graphics.drawable.ColorDrawable
import com.example.myapplication.R
import com.example.myapplication.adapter.ModelAdapter
import com.example.myapplication.adapter.PreviewAdapter
import com.example.myapplication.databinding.DialogAttachmentOptionsBinding
import com.example.myapplication.databinding.DialogModelSelectorBinding
import com.example.myapplication.model.MediaType
import com.example.myapplication.model.ModelConfig
import com.example.myapplication.model.ModelRegistry
import com.example.myapplication.model.SelectedMedia
import com.example.myapplication.utils.ModelPreferences
import com.example.myapplication.utils.XunfeiSpeechRecognizer
import com.google.android.material.bottomsheet.BottomSheetDialog

/**
 * 附件、权限、语音识别相关的基类
 * ChatActivity 和 DialogueActivity 的公共父类
 */
abstract class BaseAttachmentActivity : AppCompatActivity() {

    // ========== 附件管理相关 ==========
    protected val selectedItems = mutableListOf<SelectedMedia>()
    protected lateinit var previewAdapter: PreviewAdapter
    protected lateinit var rvPreview: RecyclerView

    // ========== 语音识别相关 ==========
    private var xunfeiRecognizer: XunfeiSpeechRecognizer? = null
    private var initialY = 0f
    private var isCancelled = false

    // ========== 联网搜索相关 ==========
    private var isNetworkSearchEnabled = false

    // ========== 抽象方法 - 子类需实现 ==========
    /**
     * 获取"按住说话"的视图，用于设置触摸监听
     */
    protected abstract fun getHoldToSpeakView(): View?

    /**
     * 获取附件选择按钮（更多按钮），用于显示 PopupWindow
     */
    protected abstract fun getMoreButton(): View

    /**
     * 语音识别成功后的回调
     */
    protected abstract fun onVoiceRecognitionResult(text: String)

    // ========== 文件选择器 ==========
    private val pickMultipleMedia: ActivityResultLauncher<PickVisualMediaRequest> =
        registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(9)) { uris ->
            if (uris.isNotEmpty()) {
                val items = uris.map { SelectedMedia(it, MediaType.IMAGE) }
                addMediaItems(items)
            }
        }

    private val pickFileLauncher: ActivityResultLauncher<Array<String>> =
        registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            if (uris.isNotEmpty()) {
                val items = uris.map { SelectedMedia(it, MediaType.FILE) }
                addMediaItems(items)
            }
        }

    // ========== 权限请求 ==========
    private val requestImagePermissionLauncher: ActivityResultLauncher<String> =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                launchImagePicker()
            } else {
                Toast.makeText(this, "需要读取图片权限才能选择图片", Toast.LENGTH_SHORT).show()
            }
        }

    // ========== 附件管理方法 ==========

    /**
     * 初始化附件预览适配器
     */
    protected fun setupPreviewAdapter() {
        previewAdapter = PreviewAdapter(selectedItems) { position ->
            // 删除逻辑
            selectedItems.removeAt(position)
            previewAdapter.notifyItemRemoved(position)
            updatePreviewVisibility()
        }

        rvPreview.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        rvPreview.adapter = previewAdapter
    }

    /**
     * 添加媒体文件到预览列表
     */
    protected fun addMediaItems(newItems: List<SelectedMedia>) {
        val startPos = selectedItems.size
        selectedItems.addAll(newItems)
        previewAdapter.notifyItemRangeInserted(startPos, newItems.size)
        rvPreview.scrollToPosition(selectedItems.size - 1)
        updatePreviewVisibility()
    }

    /**
     * 控制预览 RecyclerView 的显示与隐藏
     */
    protected fun updatePreviewVisibility() {
        rvPreview.visibility = if (selectedItems.isEmpty()) View.GONE else View.VISIBLE
    }

    /**
     * 清空选中的附件
     */
    protected fun clearSelectedMedia() {
        if (selectedItems.isEmpty()) return
        val itemCount = selectedItems.size
        selectedItems.clear()
        previewAdapter.notifyItemRangeRemoved(0, itemCount)
        updatePreviewVisibility()
    }

    /**
     * 显示附件选择菜单（图片/文件）
     */
    protected fun showAttachmentOptions() {
        val view = layoutInflater.inflate(R.layout.dialog_attachment_options, null, false)
        val dialogBinding = DialogAttachmentOptionsBinding.bind(view)

        val width = (120 * resources.displayMetrics.density).toInt()

        val popupWindow = PopupWindow(
            view,
            width,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        )

        popupWindow.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
        popupWindow.elevation = 10f

        dialogBinding.tvOptionPhoto.setOnClickListener {
            popupWindow.dismiss()
            openImagePicker()
        }

        dialogBinding.tvOptionFile.setOnClickListener {
            popupWindow.dismiss()
            launchFilePicker()
        }

        // Measure view to calculate position
        view.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )

        val popupHeight = view.measuredHeight
        val anchor = getMoreButton()

        // Show above the button
        val yOffset = -(anchor.height + popupHeight + 20)
        popupWindow.showAsDropDown(anchor, 0, yOffset)
    }

    // ========== 图片选择相关 ==========

    /**
     * 打开图片选择器
     */
    private fun openImagePicker() {
        if (checkImagePermission()) {
            launchImagePicker()
        } else {
            requestImagePermission()
        }
    }

    /**
     * 检查图片读取权限
     */
    private fun checkImagePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) ==
                    PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) ==
                    PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * 请求图片读取权限
     */
    private fun requestImagePermission() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        requestImagePermissionLauncher.launch(permission)
    }

    /**
     * 启动图片选择器
     */
    private fun launchImagePicker() {
        pickMultipleMedia.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
        )
    }

    /**
     * 启动文件选择器
     */
    private fun launchFilePicker() {
        pickFileLauncher.launch(arrayOf("*/*"))
    }

    // ========== 录音权限相关 ==========

    /**
     * 检查录音权限
     */
    private fun checkRecordAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
    }

    /**
     * 请求录音权限
     */
    protected fun requestRecordAudioPermission(requestCode: Int) {
        requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), requestCode)
    }

    // ========== 语音识别相关 ==========

    /**
     * 初始化科大讯飞语音识别
     */
    protected fun initXunfeiSpeechRecognizer() {
        try {
            xunfeiRecognizer = XunfeiSpeechRecognizer(this).apply {
                init()

                // 设置识别结果监听
                setOnResultListener { text ->
                    if (text.isNotEmpty() && !isCancelled) {
                        runOnUiThread {
                            android.util.Log.d(this@BaseAttachmentActivity::class.simpleName, "识别结果：$text")
                            onVoiceRecognitionResult(text)
                            Toast.makeText(this@BaseAttachmentActivity, "识别成功", Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                // 设置错误监听
                setOnErrorListener { error ->
                    runOnUiThread {
                        Toast.makeText(this@BaseAttachmentActivity, "识别失败: $error", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            android.util.Log.d(this::class.simpleName, "科大讯飞语音识别初始化成功")
        } catch (e: Exception) {
            android.util.Log.e(this::class.simpleName, "科大讯飞语音识别初始化失败", e)
            Toast.makeText(this, "语音识别初始化失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 设置语音输入的触摸监听器
     */
    @android.annotation.SuppressLint("ClickableViewAccessibility")
    protected fun setupVoiceInputListener() {
        val holdToSpeakView = getHoldToSpeakView() ?: return

        holdToSpeakView.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialY = event.rawY
                    isCancelled = false

                    if (checkRecordAudioPermission()) {
                        try {
                            xunfeiRecognizer?.startListening()
                            (view as? android.widget.TextView)?.text = "松开发送，上滑取消"
                            view.setBackgroundColor(android.graphics.Color.parseColor("#E3F2FD"))
                        } catch (e: Exception) {
                            android.util.Log.e(this::class.simpleName, "科大讯飞启动失败", e)
                            Toast.makeText(this, "语音识别启动失败", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        onRecordAudioPermissionNeeded()
                    }
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaY = initialY - event.rawY
                    if (deltaY > 150f) {
                        isCancelled = true
                        (view as? android.widget.TextView)?.text = "松开手指，取消发送"
                        view.setBackgroundColor(android.graphics.Color.parseColor("#FFCDD2"))
                    } else {
                        isCancelled = false
                        (view as? android.widget.TextView)?.text = "松开发送，上滑取消"
                        view.setBackgroundColor(android.graphics.Color.parseColor("#E3F2FD"))
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isCancelled) {
                        xunfeiRecognizer?.cancel()
                        Toast.makeText(this, "已取消录音", Toast.LENGTH_SHORT).show()
                    } else {
                        xunfeiRecognizer?.stopListening()
                    }
                    (view as? android.widget.TextView)?.text = "按住说话"
                    view.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    isCancelled = false
                    true
                }
                else -> false
            }
        }
    }

    /**
     * 当需要录音权限时的回调，子类需实现具体的请求逻辑
     */
    protected abstract fun onRecordAudioPermissionNeeded()

    // ========== 模型切换相关 ==========

    /**
     * 获取当前选中的模型ID，子类需实现
     */
    protected abstract fun getCurrentModelId(): String

    /**
     * 切换模型时的回调，子类需实现具体的切换逻辑
     */
    protected abstract fun onModelSwitch(modelConfig: ModelConfig)

    /**
     * 获取联网搜索布局视图，用于设置状态和点击监听
     */
    protected abstract fun getWebSearchLayout(): View?

    /**
     * 联网搜索状态切换时的回调，子类需实现具体的同步逻辑
     */
    protected abstract fun onWebSearchToggle(isEnabled: Boolean)

    /**
     * 显示模型选择器对话框（公共方法）
     */
    protected fun showModelSelectorDialog() {
        val dialog = BottomSheetDialog(this)
        val dialogBinding = DialogModelSelectorBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)

        dialogBinding.modelListRecyclerview.layoutManager = LinearLayoutManager(this)

        val currentModelId = getCurrentModelId()
        val adapter = ModelAdapter(ModelRegistry.ALL_MODELS, currentModelId) { modelConfig ->
            onModelSwitch(modelConfig)
            Toast.makeText(this, "已切换到: ${modelConfig.displayName}", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        dialogBinding.modelListRecyclerview.adapter = adapter
        dialog.show()
    }

    // ========== 联网搜索相关 ==========

    /**
     * 设置联网搜索的点击监听器（公共方法）
     */
    protected fun setupWebSearchListener() {
        val webSearchLayout = getWebSearchLayout() ?: return
        
        // 从 SharedPreferences 读取上次的状态
        isNetworkSearchEnabled = ModelPreferences.getWebSearchEnabled(this)
        webSearchLayout.isSelected = isNetworkSearchEnabled
        
        webSearchLayout.setOnClickListener {
            toggleNetworkSearch()
        }
    }

    /**
     * 切换联网搜索状态（公共方法）
     */
    private fun toggleNetworkSearch() {
        isNetworkSearchEnabled = !isNetworkSearchEnabled
        val webSearchLayout = getWebSearchLayout()
        webSearchLayout?.isSelected = isNetworkSearchEnabled

        // 保存状态到 SharedPreferences
        ModelPreferences.saveWebSearchEnabled(this, isNetworkSearchEnabled)

        // 通知子类同步状态（如更新 ViewModel）
        onWebSearchToggle(isNetworkSearchEnabled)

        // 显示提示
        val message = if (isNetworkSearchEnabled) "联网搜索已开启" else "联网搜索已关闭"
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    /**
     * 设置联网搜索的初始状态（从 Intent 读取时使用）
     */
    protected fun setNetworkSearchEnabled(enabled: Boolean) {
        isNetworkSearchEnabled = enabled
        val webSearchLayout = getWebSearchLayout()
        webSearchLayout?.isSelected = enabled
        
        // 保存状态到 SharedPreferences
        ModelPreferences.saveWebSearchEnabled(this, enabled)
        
        // 同步到子类
        onWebSearchToggle(enabled)
    }

    // ========== 生命周期 ==========

    override fun onDestroy() {
        super.onDestroy()
        xunfeiRecognizer?.destroy()
        xunfeiRecognizer = null
    }
}
