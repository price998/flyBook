package com.example.myapplication.ui.common.managers

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.myapplication.R
import com.example.myapplication.databinding.DialogAttachmentOptionsBinding
import com.example.myapplication.ui.common.model.MediaType
import com.example.myapplication.ui.common.model.SelectedMedia

/**
 * 附件选择管理器
 * 
 * 职责：
 * - 管理图片和文件选择（UI交互）
 * - 处理附件权限请求
 * - 提供附件选择菜单
 * 
 * 注意：此类只负责UI层的附件选择，不处理附件内容。
 * 附件内容处理（OCR、文件解析）由 AttachmentContentProcessor 负责。
 * 预览列表由 InputBarFragment 管理。
 */
class AttachmentSelectionManager(private val activity: AppCompatActivity) {
    
    // ========== 回调接口 ==========
    private var onMediaSelectedListener: ((List<SelectedMedia>) -> Unit)? = null

    // ========== 文件选择器 ==========
    private val pickMultipleMedia: ActivityResultLauncher<PickVisualMediaRequest> =
        activity.registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(9)) { uris ->
            if (uris.isNotEmpty()) {
                val items = uris.map { SelectedMedia(it, MediaType.IMAGE) }
                // 直接通知监听器，不使用内部的 previewAdapter
                onMediaSelectedListener?.invoke(items)
            }
        }

    private val pickFileLauncher: ActivityResultLauncher<Array<String>> =
        activity.registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            if (uris.isNotEmpty()) {
                val items = uris.map { SelectedMedia(it, MediaType.FILE) }
                // 直接通知监听器，不使用内部的 previewAdapter
                onMediaSelectedListener?.invoke(items)
            }
        }

    // ========== 权限请求 ==========
    private val requestImagePermissionLauncher: ActivityResultLauncher<String> =
        activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                launchImagePicker()
            } else {
                Toast.makeText(activity, "需要读取图片权限才能选择图片", Toast.LENGTH_SHORT).show()
            }
        }

    // ========== 公共方法 ==========

    /**
     * 显示附件选择菜单（图片/文件）
     * @param anchor 附件按钮作为锚点
     */
    fun showAttachmentOptions(anchor: View) {
        val view = activity.layoutInflater.inflate(R.layout.dialog_attachment_options, null, false)
        val dialogBinding = DialogAttachmentOptionsBinding.bind(view)

        val width = (120 * activity.resources.displayMetrics.density).toInt()

        val popupWindow = PopupWindow(
            view,
            width,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        )

        popupWindow.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
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

        // Show above the button
        val yOffset = -(anchor.height + popupHeight + 20)
        popupWindow.showAsDropDown(anchor, 0, yOffset)
    }

    /**
     * 设置媒体选择监听器
     */
    fun setOnMediaSelectedListener(listener: (List<SelectedMedia>) -> Unit) {
        this.onMediaSelectedListener = listener
    }

    // ========== 私有方法 ==========

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
            ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_MEDIA_IMAGES) ==
                    PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_EXTERNAL_STORAGE) ==
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
}
