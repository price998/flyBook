package com.example.myapplication.ui.common.managers

import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication.utils.XunfeiSpeechSynthesizer

/**
 * TTS（语音合成）管理器
 * 
 * 负责管理语音播放的生命周期
 * 
 * 使用方式：
 * ```kotlin
 * // 初始化
 * ttsManager = TTSManager(activity)
 * ttsManager.init()
 * 
 * // 播放文本
 * ttsManager.speak("你好，这是一段测试文本")
 * 
 * // 停止播放
 * ttsManager.stop()
 * 
 * // 销毁
 * ttsManager.destroy()
 * ```
 */
class TTSManager(private val activity: AppCompatActivity) {

    private var synthesizer: XunfeiSpeechSynthesizer? = null
    
    // 回调监听器
    private var onStartListener: (() -> Unit)? = null
    private var onCompleteListener: (() -> Unit)? = null
    private var onErrorListener: ((String) -> Unit)? = null

    /**
     * 初始化 TTS
     */
    fun init() {
        try {
            synthesizer = XunfeiSpeechSynthesizer(activity).apply {
                init()

                setOnStartListener {
                    activity.runOnUiThread {
                        android.util.Log.d(TAG, "开始播放")
                        onStartListener?.invoke()
                    }
                }

                setOnCompleteListener {
                    activity.runOnUiThread {
                        android.util.Log.d(TAG, "播放完成")
                        onCompleteListener?.invoke()
                    }
                }

                setOnErrorListener { error ->
                    activity.runOnUiThread {
                        android.util.Log.e(TAG, "播放失败: $error")
                        onErrorListener?.invoke(error)
                        Toast.makeText(activity, error, Toast.LENGTH_SHORT).show()
                    }
                }
            }
            android.util.Log.d(TAG, "TTS 初始化成功")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "TTS 初始化失败", e)
            Toast.makeText(activity, "语音播放初始化失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 播放文本
     */
    fun speak(text: String) {
        if (text.isBlank()) {
            Toast.makeText(activity, "没有可播放的内容", Toast.LENGTH_SHORT).show()
            return
        }
        
        // 清理 Markdown 格式，只保留纯文本
        val cleanText = cleanMarkdown(text)
        
        try {
            synthesizer?.speak(cleanText)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "播放失败", e)
            Toast.makeText(activity, "语音播放失败", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 停止播放
     */
    fun stop() {
        synthesizer?.stop()
    }

    /**
     * 暂停播放
     */
    fun pause() {
        synthesizer?.pause()
    }

    /**
     * 继续播放
     */
    fun resume() {
        synthesizer?.resume()
    }

    /**
     * 是否正在播放
     */
    fun isSpeaking(): Boolean = synthesizer?.isSpeaking() ?: false

    /**
     * 清理 Markdown 格式，提取纯文本
     */
    private fun cleanMarkdown(text: String): String {
        var result = text
        
        // 移除代码块
        result = result.replace(Regex("```[\\s\\S]*?```"), "代码块已省略。")
        // 移除行内代码
        result = result.replace(Regex("`[^`]+`"), "")
        // 移除链接，保留文本
        result = result.replace(Regex("\\[([^]]+)]\\([^)]+\\)"), "$1")
        // 移除图片
        result = result.replace(Regex("!\\[([^]]*)]\\([^)]+\\)"), "")
        // 移除标题符号
        result = result.replace(Regex("^#{1,6}\\s*", RegexOption.MULTILINE), "")
        // 移除加粗和斜体
        result = result.replace(Regex("\\*{1,2}([^*]+)\\*{1,2}"), "$1")
        result = result.replace(Regex("_{1,2}([^_]+)_{1,2}"), "$1")
        // 移除分隔线
        result = result.replace(Regex("^[-*_]{3,}$", RegexOption.MULTILINE), "")
        // 移除列表符号
        result = result.replace(Regex("^[\\s]*[-*+]\\s+", RegexOption.MULTILINE), "")
        result = result.replace(Regex("^[\\s]*\\d+\\.\\s+", RegexOption.MULTILINE), "")
        // 移除多余空行
        result = result.replace(Regex("\n{3,}"), "\n\n")
        
        return result.trim()
    }

    // ========== 监听器设置 ==========

    fun setOnStartListener(listener: () -> Unit) {
        onStartListener = listener
    }

    fun setOnCompleteListener(listener: () -> Unit) {
        onCompleteListener = listener
    }

    fun setOnErrorListener(listener: (String) -> Unit) {
        onErrorListener = listener
    }

    /**
     * 销毁资源
     */
    fun destroy() {
        synthesizer?.destroy()
        synthesizer = null
    }

    companion object {
        private const val TAG = "TTSManager"
    }
}
