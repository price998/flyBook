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
 * ML Kit 诊断工具
 * 
 * 用于诊断 ML Kit OCR 功能是否正常工作
 */
object MLKitDiagnostics {
    private const val TAG = "MLKitDiagnostics"

    /**
     * 检查 ML Kit 是否可用
     */
    fun checkMLKitAvailability(context: Context): DiagnosticResult {
        return try {
            Log.d(TAG, "========== ML Kit 可用性检查 ==========")
            
            // 尝试创建识别器
            val recognizer = TextRecognition.getClient(
                ChineseTextRecognizerOptions.Builder().build()
            )
            
            Log.d(TAG, "✓ ML Kit 识别器创建成功")
            recognizer.close()
            
            DiagnosticResult(
                success = true,
                message = "ML Kit 可用"
            )
        } catch (e: Exception) {
            Log.e(TAG, "✗ ML Kit 识别器创建失败", e)
            DiagnosticResult(
                success = false,
                message = "ML Kit 不可用: ${e.message}",
                error = e
            )
        }
    }

    /**
     * 检查图片 URI 是否可访问
     */
    fun checkImageUri(context: Context, uri: Uri): DiagnosticResult {
        return try {
            Log.d(TAG, "========== 图片 URI 检查 ==========")
            Log.d(TAG, "URI: $uri")
            Log.d(TAG, "Scheme: ${uri.scheme}")
            Log.d(TAG, "Path: ${uri.path}")
            
            val inputStream = context.contentResolver.openInputStream(uri)
            if (inputStream == null) {
                Log.e(TAG, "✗ 无法打开输入流")
                return DiagnosticResult(
                    success = false,
                    message = "无法访问图片 URI"
                )
            }
            
            val size = inputStream.available()
            inputStream.close()
            
            Log.d(TAG, "✓ URI 可访问")
            Log.d(TAG, "文件大小: $size bytes")
            
            DiagnosticResult(
                success = true,
                message = "URI 可访问，文件大小: $size bytes"
            )
        } catch (e: Exception) {
            Log.e(TAG, "✗ URI 访问失败", e)
            DiagnosticResult(
                success = false,
                message = "URI 访问失败: ${e.message}",
                error = e
            )
        }
    }

    /**
     * 检查图片是否可以加载
     */
    fun checkImageLoading(context: Context, uri: Uri): DiagnosticResult {
        return try {
            Log.d(TAG, "========== 图片加载检查 ==========")
            
            val image = InputImage.fromFilePath(context, uri)
            
            Log.d(TAG, "✓ 图片加载成功")
            Log.d(TAG, "宽度: ${image.width}")
            Log.d(TAG, "高度: ${image.height}")
            Log.d(TAG, "旋转角度: ${image.rotationDegrees}")
            Log.d(TAG, "格式: ${image.format}")
            
            DiagnosticResult(
                success = true,
                message = "图片加载成功 (${image.width}x${image.height})"
            )
        } catch (e: Exception) {
            Log.e(TAG, "✗ 图片加载失败", e)
            DiagnosticResult(
                success = false,
                message = "图片加载失败: ${e.message}",
                error = e
            )
        }
    }

    /**
     * 执行完整的 OCR 测试
     */
    suspend fun testOCR(context: Context, uri: Uri): DiagnosticResult = suspendCancellableCoroutine { continuation ->
        try {
            Log.d(TAG, "========== OCR 完整测试 ==========")
            
            // 1. 检查 URI
            val uriCheck = checkImageUri(context, uri)
            if (!uriCheck.success) {
                continuation.resume(uriCheck)
                return@suspendCancellableCoroutine
            }
            
            // 2. 检查图片加载
            val loadCheck = checkImageLoading(context, uri)
            if (!loadCheck.success) {
                continuation.resume(loadCheck)
                return@suspendCancellableCoroutine
            }
            
            // 3. 执行 OCR
            val recognizer = TextRecognition.getClient(
                ChineseTextRecognizerOptions.Builder().build()
            )
            val image = InputImage.fromFilePath(context, uri)
            
            Log.d(TAG, "开始 OCR 识别...")
            val startTime = System.currentTimeMillis()
            
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val duration = System.currentTimeMillis() - startTime
                    val text = visionText.text.trim()
                    
                    Log.d(TAG, "✓ OCR 识别成功")
                    Log.d(TAG, "耗时: ${duration}ms")
                    Log.d(TAG, "文字长度: ${text.length}")
                    Log.d(TAG, "文本块数量: ${visionText.textBlocks.size}")
                    
                    if (text.isEmpty()) {
                        Log.w(TAG, "⚠️ 未识别到任何文字")
                    } else {
                        Log.d(TAG, "识别内容预览: ${text.take(100)}")
                    }
                    
                    continuation.resume(
                        DiagnosticResult(
                            success = true,
                            message = "OCR 识别成功 (${duration}ms, ${text.length} 字符)"
                        )
                    )
                    recognizer.close()
                }
                .addOnFailureListener { e ->
                    val duration = System.currentTimeMillis() - startTime
                    
                    Log.e(TAG, "✗ OCR 识别失败 (${duration}ms)", e)
                    Log.e(TAG, "错误类型: ${e.javaClass.simpleName}")
                    Log.e(TAG, "错误信息: ${e.message}")
                    
                    continuation.resume(
                        DiagnosticResult(
                            success = false,
                            message = "OCR 识别失败: ${e.message}",
                            error = e
                        )
                    )
                    recognizer.close()
                }
        } catch (e: Exception) {
            Log.e(TAG, "✗ OCR 测试异常", e)
            continuation.resume(
                DiagnosticResult(
                    success = false,
                    message = "OCR 测试异常: ${e.message}",
                    error = e
                )
            )
        }
    }

    /**
     * 生成诊断报告
     */
    fun generateReport(context: Context, uri: Uri? = null): String {
        val report = StringBuilder()
        report.append("========== ML Kit 诊断报告 ==========\n\n")
        
        // 1. ML Kit 可用性
        val mlkitCheck = checkMLKitAvailability(context)
        report.append("1. ML Kit 可用性: ${if (mlkitCheck.success) "✓" else "✗"}\n")
        report.append("   ${mlkitCheck.message}\n\n")
        
        // 2. URI 检查（如果提供）
        if (uri != null) {
            val uriCheck = checkImageUri(context, uri)
            report.append("2. 图片 URI: ${if (uriCheck.success) "✓" else "✗"}\n")
            report.append("   ${uriCheck.message}\n\n")
            
            // 3. 图片加载检查
            if (uriCheck.success) {
                val loadCheck = checkImageLoading(context, uri)
                report.append("3. 图片加载: ${if (loadCheck.success) "✓" else "✗"}\n")
                report.append("   ${loadCheck.message}\n\n")
            }
        }
        
        report.append("========================================")
        
        val reportText = report.toString()
        Log.d(TAG, reportText)
        return reportText
    }

    /**
     * 诊断结果
     */
    data class DiagnosticResult(
        val success: Boolean,
        val message: String,
        val error: Throwable? = null
    )
}
