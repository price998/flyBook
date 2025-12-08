package com.example.myapplication.ui.common.managers

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.myapplication.utils.XunfeiSpeechRecognizer

/**
 * 语音识别管理器
 * 负责管理语音识别的生命周期和权限处理
 */
class VoiceRecognitionManager(private val activity: AppCompatActivity) {

    private var xunfeiRecognizer: XunfeiSpeechRecognizer? = null
    private var isCancelled = false
    private var onResultListener: ((String) -> Unit)? = null
    private var onErrorListener: ((String) -> Unit)? = null
    private var onPermissionNeededListener: (() -> Unit)? = null

    /**
     * 初始化语音识别器
     */
    fun init() {
        try {
            xunfeiRecognizer = XunfeiSpeechRecognizer(activity).apply {
                init()

                // 设置识别结果监听
                setOnResultListener { text ->
                    if (text.isNotEmpty() && !isCancelled) {
                        activity.runOnUiThread {
                            android.util.Log.d(TAG, "识别结果：$text")
                            onResultListener?.invoke(text)
                            Toast.makeText(activity, "识别成功", Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                // 设置错误监听
                setOnErrorListener { error ->
                    activity.runOnUiThread {
                        android.util.Log.e(TAG, "识别失败: $error")
                        onErrorListener?.invoke(error)
                        Toast.makeText(activity, "识别失败: $error", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            android.util.Log.d(TAG, "科大讯飞语音识别初始化成功")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "科大讯飞语音识别初始化失败", e)
            Toast.makeText(activity, "语音识别初始化失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 开始语音识别
     */
    fun startRecognition() {
        isCancelled = false
        if (checkRecordAudioPermission()) {
            try {
                xunfeiRecognizer?.startListening()
                android.util.Log.d(TAG, "语音识别已启动")
            } catch (e: Exception) {
                android.util.Log.e(TAG, "科大讯飞启动失败", e)
                Toast.makeText(activity, "语音识别启动失败", Toast.LENGTH_SHORT).show()
            }
        } else {
            android.util.Log.d(TAG, "需要录音权限")
            onPermissionNeededListener?.invoke()
        }
    }

    /**
     * 停止语音识别
     */
    fun stopRecognition() {
        android.util.Log.d(TAG, "停止语音识别，isCancelled: $isCancelled")
        if (!isCancelled) {
            xunfeiRecognizer?.stopListening()
        }
        isCancelled = false
    }

    /**
     * 取消语音识别
     */
    fun cancelRecognition() {
        android.util.Log.d(TAG, "取消语音识别")
        isCancelled = true
        xunfeiRecognizer?.cancel()
    }

    /**
     * 检查录音权限
     */
    private fun checkRecordAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
    }

    /**
     * 设置识别结果监听器
     */
    fun setOnResultListener(listener: (String) -> Unit) {
        onResultListener = listener
    }


    /**
     * 设置权限需要监听器
     */
    fun setOnPermissionNeededListener(listener: () -> Unit) {
        onPermissionNeededListener = listener
    }

    /**
     * 销毁资源
     */
    fun destroy() {
        xunfeiRecognizer?.destroy()
        xunfeiRecognizer = null
    }

    companion object {
        private const val TAG = "VoiceRecognitionManager"
    }
}
