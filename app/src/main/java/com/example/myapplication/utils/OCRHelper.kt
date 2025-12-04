package com.example.myapplication.utils

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * OCR图片识别助手
 * 使用ML Kit进行中文文字识别
 */
class OCRHelper(private val context: Context) {
    companion object {
        private const val TAG = "OCRHelper"
    }

    // 创建中文文字识别器
    private val recognizer = TextRecognition.getClient(
        ChineseTextRecognizerOptions.Builder().build()
    )

    /**
     * 从图片URI中识别文字
     * @param uri 图片的URI
     * @return 识别出的文字内容，如果识别失败返回错误信息
     */
    suspend fun parseImage(uri: Uri): String = suspendCancellableCoroutine { continuation ->
        try {
            // 从URI创建InputImage
            val image = InputImage.fromFilePath(context, uri)
            processImage(image, continuation)
        } catch (e: Exception) {
            Log.e(TAG, "处理图片时发生异常", e)
            continuation.resume("处理图片失败: ${e.message}")
        }
    }

    /**
     * 从Bitmap中识别文字
     * @param bitmap 图片Bitmap
     * @return 识别出的文字内容
     */
    suspend fun parseBitmap(bitmap: android.graphics.Bitmap): String = suspendCancellableCoroutine { continuation ->
        try {
            val image = InputImage.fromBitmap(bitmap, 0)
            processImage(image, continuation)
        } catch (e: Exception) {
            Log.e(TAG, "处理Bitmap时发生异常", e)
            continuation.resume("处理Bitmap失败: ${e.message}")
        }
    }

    private fun processImage(image: InputImage, continuation: kotlin.coroutines.Continuation<String>) {
        Log.d(TAG, "开始OCR识别")
        // 执行文字识别
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val extractedText = visionText.text
                Log.d(TAG, "OCR识别成功，识别文字长度: ${extractedText.length}")
                continuation.resume(extractedText)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "OCR识别失败", e)
                continuation.resume("OCR识别失败: ${e.message}")
            }
    }

    /**
     * 关闭识别器，释放资源
     * 注意：通常在Activity销毁时调用
     */
    fun close() {
        recognizer.close()
    }
}
