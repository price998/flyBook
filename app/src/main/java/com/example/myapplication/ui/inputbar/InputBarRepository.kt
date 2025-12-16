package com.example.myapplication.ui.inputbar

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.nio.charset.Charset
import kotlin.coroutines.resume

/**
 * 输入栏数据仓库
 * 
 * 职责：
 * - 处理附件相关的业务逻辑
 * - 验证输入内容
 * - 处理文件和图片的元数据获取
 * - OCR 图片识别
 * - 文件解析（PDF、DOCX、TXT等）
 * - 附件内容处理和组合
 */
class InputBarRepository(private val context: Context) {

    companion object {
        private const val TAG = "InputBarRepository"
        private const val MAX_FILE_PREVIEW_CHARS = 8000
        private const val MAX_PDF_PAGES = 5
        private const val MAX_TEXT_BYTES = 1024 * 1024 // 1MB
    }

    private val contentResolver: ContentResolver = context.contentResolver
    
    init {
        Log.d(TAG, "InputBarRepository 初始化")
        Log.d(TAG, "Context: ${context.javaClass.simpleName}")
    }

    // ========== 附件内容处理 ==========

    /**
     * 处理附件并生成消息内容
     */
    suspend fun processAttachments(
        textContent: String,
        imageUris: List<Uri>,
        fileUris: List<Uri>,
        onProgress: ((current: Int, total: Int) -> Unit)? = null
    ): String = withContext(Dispatchers.IO) {
        Log.d(TAG, "========== 开始处理附件 ==========")
        Log.d(TAG, "文本内容: ${textContent.length}字符")
        Log.d(TAG, "图片数量: ${imageUris.size}")
        imageUris.forEachIndexed { index, uri ->
            Log.d(TAG, "  图片${index + 1}: $uri")
        }
        Log.d(TAG, "文件数量: ${fileUris.size}")
        fileUris.forEachIndexed { index, uri ->
            Log.d(TAG, "  文件${index + 1}: $uri")
        }
        
        val sections = mutableListOf<String>()

        if (textContent.isNotBlank()) {
            Log.d(TAG, "添加用户输入文本: ${textContent.length}字符")
            sections.add(textContent)
        }

        if (imageUris.isNotEmpty()) {
            Log.d(TAG, "开始处理图片...")
            val imageSection = processImages(imageUris, onProgress)
            if (imageSection.isNotBlank()) {
                Log.d(TAG, "✓ 图片处理成功 - 内容长度: ${imageSection.length}")
                sections.add(imageSection)
            } else {
                Log.w(TAG, "✗ 图片处理结果为空")
            }
        }

        if (fileUris.isNotEmpty()) {
            Log.d(TAG, "开始处理文件...")
            val fileSection = processFiles(fileUris)
            if (fileSection.isNotBlank()) {
                Log.d(TAG, "✓ 文件处理成功 - 内容长度: ${fileSection.length}")
                sections.add(fileSection)
            } else {
                Log.w(TAG, "✗ 文件处理结果为空")
            }
        }

        val result = sections.joinToString("\n\n").trim()
        Log.d(TAG, "========== 附件处理完成 ==========")
        Log.d(TAG, "最终内容长度: ${result.length}")
        Log.d(TAG, "段落数: ${sections.size}")
        Log.d(TAG, "内容预览: ${result.take(200)}")
        result
    }

    private suspend fun processImages(
        imageUris: List<Uri>,
        onProgress: ((current: Int, total: Int) -> Unit)?
    ): String {
        Log.d(TAG, "========== 开始处理图片列表 ==========")
        Log.d(TAG, "图片数量: ${imageUris.size}")
        
        // 首次处理图片时，执行诊断检查
        if (imageUris.isNotEmpty()) {
            try {
                val diagnostic = com.example.myapplication.utils.MLKitDiagnostics.checkMLKitAvailability(context)
                if (!diagnostic.success) {
                    Log.e(TAG, "✗ ML Kit 不可用: ${diagnostic.message}")
                    return "【图片识别失败】\nML Kit 不可用，请确保设备已安装 Google Play Services 并联网。\n错误: ${diagnostic.message}"
                }
                Log.d(TAG, "✓ ML Kit 可用性检查通过")
            } catch (e: Exception) {
                Log.e(TAG, "ML Kit 诊断检查失败", e)
            }
        }
        
        val results = mutableListOf<String>()

        imageUris.forEachIndexed { index, uri ->
            try {
                Log.d(TAG, "---------- 处理图片 ${index + 1}/${imageUris.size} ----------")
                Log.d(TAG, "URI: $uri")
                onProgress?.invoke(index + 1, imageUris.size)
                
                val recognizedText = parseImageOCR(uri)

                if (recognizedText.isEmpty()) {
                    Log.w(TAG, "⚠️ 图片OCR识别结果为空")
                    results.add("[图片${index + 1}：未识别到文字]")
                } else if (recognizedText.startsWith("错误") ||
                    recognizedText.startsWith("OCR识别失败") ||
                    recognizedText.startsWith("处理图片失败") ||
                    recognizedText.startsWith("无法访问")
                ) {
                    Log.w(TAG, "✗ 图片OCR识别失败: $recognizedText")
                    results.add("[图片${index + 1}：识别失败 - ${recognizedText}]")
                } else {
                    Log.d(TAG, "✓ 图片OCR识别成功 - 文本长度: ${recognizedText.length}")
                    results.add(recognizedText)
                }
            } catch (e: Exception) {
                Log.e(TAG, "✗ 图片OCR解析异常", e)
                results.add("[图片${index + 1}：解析异常 - ${e.message}]")
            }
        }

        val section = buildImageSection(results)
        Log.d(TAG, "========== 图片处理完成 ==========")
        Log.d(TAG, "有效结果: ${results.count { !it.startsWith("[图片") }}/${results.size}")
        Log.d(TAG, "失败结果: ${results.count { it.startsWith("[图片") }}/${results.size}")
        return section
    }

    private suspend fun processFiles(fileUris: List<Uri>): String {
        Log.d(TAG, "开始处理文件列表 - 数量: ${fileUris.size}")
        val results = fileUris.mapIndexed { index, uri ->
            Log.d(TAG, "处理文件 ${index + 1}/${fileUris.size}: $uri")
            val result = parseFile(uri)
            Log.d(TAG, "文件解析完成 - 名称: ${result.fileName}, 类型: ${result.mimeType}, 内容长度: ${result.content.length}")
            result
        }
        val section = buildFileSection(results)
        Log.d(TAG, "文件处理完成 - 成功: ${results.size}")
        return section
    }

    private fun buildImageSection(ocrResults: List<String>): String {
        if (ocrResults.isEmpty()) return ""
        
        val builder = StringBuilder()
        builder.append("【图片内容】\n")
        ocrResults.forEachIndexed { index, ocrText ->
            if (ocrText.isNotBlank()) {
                if (index > 0) builder.append("\n\n")
                builder.append("图片${index + 1}：\n$ocrText")
            }
        }
        return builder.toString()
    }

    private fun buildFileSection(fileResults: List<FileParseResult>): String {
        if (fileResults.isEmpty()) return ""
        
        val builder = StringBuilder()
        builder.append("【文件内容】\n")
        fileResults.forEachIndexed { index, result ->
            if (index > 0) builder.append("\n\n")
            builder.append("文件${index + 1}（${result.fileName}）：\n${result.content}")
        }
        return builder.toString()
    }

    // ========== OCR 图片识别 ==========

    /**
     * 从图片URI中识别文字
     */
    private suspend fun parseImageOCR(uri: Uri): String = suspendCancellableCoroutine { continuation ->
        try {
            Log.d(TAG, "========== 开始OCR识别 ==========")
            Log.d(TAG, "URI: $uri")
            Log.d(TAG, "URI Scheme: ${uri.scheme}")
            Log.d(TAG, "URI Path: ${uri.path}")
            
            // 检查 URI 是否可访问
            try {
                val inputStream = contentResolver.openInputStream(uri)
                if (inputStream == null) {
                    Log.e(TAG, "无法打开图片输入流 - URI 可能无效")
                    continuation.resume("无法访问图片文件")
                    return@suspendCancellableCoroutine
                }
                inputStream.close()
                Log.d(TAG, "✓ URI 可访问")
            } catch (e: Exception) {
                Log.e(TAG, "✗ 无法访问 URI", e)
                continuation.resume("无法访问图片: ${e.message}")
                return@suspendCancellableCoroutine
            }
            
            // 创建 ML Kit 识别器
            Log.d(TAG, "创建 ML Kit 中文识别器...")
            val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
            
            // 从 URI 加载图片
            Log.d(TAG, "从 URI 加载图片...")
            val image = try {
                InputImage.fromFilePath(context, uri)
            } catch (e: Exception) {
                Log.e(TAG, "✗ 加载图片失败", e)
                continuation.resume("加载图片失败: ${e.message}")
                recognizer.close()
                return@suspendCancellableCoroutine
            }
            
            Log.d(TAG, "✓ 图片加载成功")
            Log.d(TAG, "图片尺寸: ${image.width} x ${image.height}")
            Log.d(TAG, "图片旋转角度: ${image.rotationDegrees}")
            
            // 开始 OCR 识别
            Log.d(TAG, "开始 OCR 识别...")
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val text = visionText.text.trim()
                    Log.d(TAG, "========== OCR识别完成 ==========")
                    Log.d(TAG, "识别文字长度: ${text.length}")
                    Log.d(TAG, "识别块数量: ${visionText.textBlocks.size}")
                    
                    if (text.isEmpty()) {
                        Log.w(TAG, "⚠️ OCR识别成功但未识别到任何文字")
                        Log.w(TAG, "可能原因：图片中没有文字、文字太小、图片模糊等")
                    } else {
                        Log.d(TAG, "✓ 识别成功")
                        Log.d(TAG, "识别内容预览: ${text.take(100)}")
                    }
                    
                    continuation.resume(text)
                    recognizer.close()
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "========== OCR识别失败 ==========")
                    Log.e(TAG, "错误类型: ${e.javaClass.simpleName}")
                    Log.e(TAG, "错误信息: ${e.message}", e)
                    continuation.resume("OCR识别失败: ${e.message}")
                    recognizer.close()
                }
        } catch (e: Exception) {
            Log.e(TAG, "========== 处理图片时发生异常 ==========")
            Log.e(TAG, "异常类型: ${e.javaClass.simpleName}")
            Log.e(TAG, "异常信息: ${e.message}", e)
            continuation.resume("处理图片失败: ${e.message}")
        }
    }

    /**
     * 从Bitmap中识别文字（用于PDF页面OCR）
     */
    private suspend fun parseBitmapOCR(bitmap: Bitmap): String = suspendCancellableCoroutine { continuation ->
        try {
            val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
            val image = InputImage.fromBitmap(bitmap, 0)
            
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    continuation.resume(visionText.text)
                    recognizer.close()
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "OCR识别失败", e)
                    continuation.resume("OCR识别失败: ${e.message}")
                    recognizer.close()
                }
        } catch (e: Exception) {
            Log.e(TAG, "处理Bitmap时发生异常", e)
            continuation.resume("处理Bitmap失败: ${e.message}")
        }
    }


    // ========== 文件解析 ==========

    data class FileParseResult(
        val fileName: String,
        val content: String,
        val mimeType: String
    )

    /**
     * 解析单个文件
     */
    private suspend fun parseFile(uri: Uri): FileParseResult = withContext(Dispatchers.IO) {
        val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"
        val name = queryFileName(uri) ?: uri.lastPathSegment.orEmpty()
        val extension = name.substringAfterLast('.', "").lowercase()

        Log.d(TAG, "解析文件 - 名称: $name, 类型: $mimeType, 扩展名: $extension")

        try {
            val content = when {
                mimeType == "application/pdf" || extension == "pdf" -> {
                    Log.d(TAG, "识别为PDF文件，开始解析")
                    readPdfContent(uri)
                }
                mimeType == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" ||
                        extension == "docx" -> {
                    Log.d(TAG, "识别为DOCX文件，开始解析")
                    readDocxContent(uri)
                }
                mimeType == "application/msword" || extension == "doc" -> {
                    Log.w(TAG, "不支持的DOC格式")
                    "不支持 .doc 格式，请转换为 .docx 后重试"
                }
                isTextFile(mimeType, extension) -> {
                    Log.d(TAG, "识别为文本文件，开始读取")
                    readTextContent(uri)
                }
                else -> {
                    Log.d(TAG, "未知文件类型，尝试作为文本读取")
                    try {
                        readTextContent(uri)
                    } catch (e: Exception) {
                        Log.w(TAG, "无法作为文本读取", e)
                        "不支持的文件格式: $mimeType ($extension)"
                    }
                }
            }

            val finalContent = if (content.length > MAX_FILE_PREVIEW_CHARS) {
                Log.d(TAG, "文件内容过长，截断 - 原长度: ${content.length}, 截断后: $MAX_FILE_PREVIEW_CHARS")
                content.substring(0, MAX_FILE_PREVIEW_CHARS) + "\n\n[内容因过长已截断]"
            } else {
                content
            }

            Log.d(TAG, "文件解析成功 - 内容长度: ${finalContent.length}")
            FileParseResult(name.ifBlank { "未命名文件" }, finalContent, mimeType)
        } catch (e: Exception) {
            Log.e(TAG, "解析文件失败: $uri", e)
            FileParseResult(name.ifBlank { "未命名文件" }, "解析失败: ${e.message}", mimeType)
        }
    }

    private fun isTextFile(mimeType: String, extension: String): Boolean {
        return mimeType.startsWith("text/") ||
                mimeType.contains("json") ||
                mimeType.contains("xml") ||
                mimeType.contains("javascript") ||
                extension in setOf(
                    "txt", "md", "json", "xml", "html", "css", "js",
                    "kt", "java", "py", "c", "cpp", "h",
                    "gradle", "properties", "log"
                )
    }

    private suspend fun readPdfContent(uri: Uri): String {
        return contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
            val pdfRenderer = PdfRenderer(pfd)
            val builder = StringBuilder()
            val pageCount = pdfRenderer.pageCount

            for (i in 0 until minOf(pageCount, MAX_PDF_PAGES)) {
                val page = pdfRenderer.openPage(i)
                val bitmap = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                val text = parseBitmapOCR(bitmap)
                if (text.isNotBlank()) {
                    builder.append("第 ${i + 1} 页内容：\n$text\n\n")
                }

                page.close()
                bitmap.recycle()
            }

            if (pageCount > MAX_PDF_PAGES) {
                builder.append("\n[PDF过长，仅读取前 $MAX_PDF_PAGES 页]")
            }

            if (builder.isEmpty()) "PDF内容识别为空" else builder.toString()
        } ?: throw IllegalStateException("无法打开PDF文件")
    }

    private suspend fun readDocxContent(uri: Uri): String = withContext(Dispatchers.IO) {
        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val zipInputStream = java.util.zip.ZipInputStream(inputStream)
                var entry = zipInputStream.nextEntry
                while (entry != null) {
                    if (entry.name == "word/document.xml") {
                        val content = zipInputStream.bufferedReader().readText()
                        return@withContext parseDocxXml(content)
                    }
                    zipInputStream.closeEntry()
                    entry = zipInputStream.nextEntry
                }
            }
            "无法读取Docx内容：未找到文档主体"
        } catch (e: Exception) {
            Log.e(TAG, "读取Docx失败", e)
            "读取Docx失败: ${e.message}"
        }
    }

    private fun parseDocxXml(xml: String): String {
        var text = xml
        text = text.replace(Regex("<w:p.*?>"), "\n")
        text = text.replace(Regex("<w:br/>"), "\n")
        text = text.replace(Regex("<w:tab/>"), "\t")
        text = text.replace(Regex("<[^>]+>"), "")
        text = text.replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
        return text.trim()
    }

    private fun readTextContent(uri: Uri): String {
        contentResolver.openInputStream(uri)?.use { stream ->
            val buffer = ByteArray(MAX_TEXT_BYTES)
            var totalRead = 0
            while (totalRead < MAX_TEXT_BYTES) {
                val read = stream.read(buffer, totalRead, MAX_TEXT_BYTES - totalRead)
                if (read == -1) break
                totalRead += read
            }

            val readBytes = if (totalRead == MAX_TEXT_BYTES) buffer else buffer.copyOf(totalRead)

            // 检查是否为二进制文件
            val checkLength = minOf(readBytes.size, 1024)
            for (i in 0 until checkLength) {
                if (readBytes[i] == 0.toByte()) {
                    throw Exception("检测到二进制文件，无法作为文本读取")
                }
            }

            // 尝试检测编码
            return try {
                val text = String(readBytes, Charsets.UTF_8)
                if (text.count { it == '\uFFFD' } > text.length * 0.05) {
                    throw Exception("Probably not UTF-8")
                }
                text
            } catch (e: Exception) {
                try {
                    String(readBytes, Charset.forName("GBK"))
                } catch (e2: Exception) {
                    String(readBytes, Charsets.ISO_8859_1)
                }
            }
        }
        throw IllegalStateException("无法读取文件流")
    }

    private fun queryFileName(uri: Uri): String? {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) {
                return cursor.getString(nameIndex)
            }
        }
        return null
    }
}
