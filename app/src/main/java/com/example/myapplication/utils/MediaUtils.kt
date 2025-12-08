package com.example.myapplication.utils

import android.app.Application
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
 * 媒体处理工具类
 * 
 * 统一管理图片OCR识别、文件解析和附件内容处理
 * 
 * 包含三个主要组件：
 * 1. OCRHelper - OCR图片识别
 * 2. FileParserHelper - 文件解析
 * 3. AttachmentContentProcessor - 附件内容处理
 */

// ============================================================
// OCRHelper - OCR图片识别
// ============================================================

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
     */
    suspend fun parseImage(uri: Uri): String = suspendCancellableCoroutine { continuation ->
        try {
            val image = InputImage.fromFilePath(context, uri)
            processImage(image, continuation)
        } catch (e: Exception) {
            Log.e(TAG, "处理图片时发生异常", e)
            continuation.resume("处理图片失败: ${e.message}")
        }
    }

    /**
     * 从Bitmap中识别文字
     */
    suspend fun parseBitmap(bitmap: Bitmap): String = suspendCancellableCoroutine { continuation ->
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
     */
    fun close() {
        recognizer.close()
    }
}

// ============================================================
// FileParserHelper - 文件解析
// ============================================================

/**
 * 文件解析工具类
 * 负责解析各种类型的文件（PDF、DOCX、TXT等）
 */
class FileParserHelper(
    private val contentResolver: ContentResolver,
    private val ocrHelper: OCRHelper
) {
    companion object {
        private const val TAG = "FileParserHelper"
        private const val MAX_FILE_PREVIEW_CHARS = 8000
        private const val MAX_PDF_PAGES = 5
        private const val MAX_TEXT_BYTES = 1024 * 1024 // 1MB
    }

    data class FileParseResult(
        val fileName: String,
        val content: String,
        val mimeType: String
    )

    /**
     * 批量解析文件
     */
    suspend fun parseFiles(fileUris: List<Uri>): List<FileParseResult> {
        return withContext(Dispatchers.IO) {
            fileUris.map { uri -> parseFile(uri) }
        }
    }

    /**
     * 解析单个文件
     */
    private suspend fun parseFile(uri: Uri): FileParseResult {
        val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"
        val name = queryFileName(uri) ?: uri.lastPathSegment.orEmpty()
        val extension = name.substringAfterLast('.', "").lowercase()

        return try {
            val content = when {
                mimeType == "application/pdf" || extension == "pdf" -> readPdfContent(uri)
                mimeType == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" ||
                        extension == "docx" -> readDocxContent(uri)
                mimeType == "application/msword" || extension == "doc" -> 
                    "不支持 .doc 格式，请转换为 .docx 后重试"
                isTextFile(mimeType, extension) -> readTextContent(uri)
                else -> {
                    try {
                        readTextContent(uri)
                    } catch (e: Exception) {
                        "不支持的文件格式: $mimeType ($extension)"
                    }
                }
            }

            val finalContent = if (content.length > MAX_FILE_PREVIEW_CHARS) {
                content.substring(0, MAX_FILE_PREVIEW_CHARS) + "\n\n[内容因过长已截断]"
            } else {
                content
            }

            FileParseResult(
                fileName = name.ifBlank { "未命名文件" },
                content = finalContent,
                mimeType = mimeType
            )
        } catch (e: Exception) {
            Log.e(TAG, "解析文件失败: $uri", e)
            FileParseResult(
                fileName = name.ifBlank { "未命名文件" },
                content = "解析失败: ${e.message}",
                mimeType = mimeType
            )
        }
    }

    private fun isTextFile(mimeType: String, extension: String): Boolean {
        return mimeType.startsWith("text/") ||
                mimeType.contains("json") ||
                mimeType.contains("xml") ||
                mimeType.contains("javascript") ||
                mimeType.contains("gradle") ||
                mimeType.contains("properties") ||
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
                val bitmap = Bitmap.createBitmap(
                    page.width * 2,
                    page.height * 2,
                    Bitmap.Config.ARGB_8888
                )
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                val text = ocrHelper.parseBitmap(bitmap)
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

    private suspend fun readDocxContent(uri: Uri): String {
        return withContext(Dispatchers.IO) {
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
                if (text.contains("\uFFFD")) {
                    val replacementCount = text.count { it == '\uFFFD' }
                    if (replacementCount > text.length * 0.05) {
                        throw Exception("Probably not UTF-8")
                    }
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
        val projection = arrayOf(OpenableColumns.DISPLAY_NAME)
        contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) {
                return cursor.getString(nameIndex)
            }
        }
        return null
    }

    /**
     * 构建文件内容展示文本
     */
    fun buildFileSection(fileResults: List<FileParseResult>): String {
        if (fileResults.isEmpty()) return ""
        val builder = StringBuilder()
        fileResults.forEachIndexed { index, result ->
            if (builder.isNotEmpty()) {
                builder.append("\n\n")
            }
            builder.append("文件${index + 1}（${result.fileName}）内容：\n")
            builder.append(result.content)
        }
        return builder.toString()
    }
}

// ============================================================
// AttachmentContentProcessor - 附件内容处理
// ============================================================

/**
 * 附件内容处理器
 * 
 * 职责：
 * - 处理图片OCR识别
 * - 解析各种文件格式
 * - 将附件内容组合成最终的消息文本
 */
class AttachmentContentProcessor(private val application: Application) {
    
    companion object {
        private const val TAG = "AttachmentProcessor"
    }

    /**
     * 处理附件并生成消息内容
     */
    suspend fun processAttachments(
        textContent: String,
        imageUris: List<Uri>,
        fileUris: List<Uri>,
        onProgress: ((current: Int, total: Int) -> Unit)? = null
    ): String {
        return withContext(Dispatchers.IO) {
            val sections = mutableListOf<String>()

            if (textContent.isNotBlank()) {
                sections.add(textContent)
            }

            if (imageUris.isNotEmpty()) {
                val imageSection = processImages(imageUris, onProgress)
                if (imageSection.isNotBlank()) {
                    sections.add(imageSection)
                }
            }

            if (fileUris.isNotEmpty()) {
                val fileSection = processFiles(fileUris)
                if (fileSection.isNotBlank()) {
                    sections.add(fileSection)
                }
            }

            sections.joinToString("\n\n").trim()
        }
    }

    private suspend fun processImages(
        imageUris: List<Uri>,
        onProgress: ((current: Int, total: Int) -> Unit)?
    ): String {
        val ocrHelper = OCRHelper(application)
        val results = mutableListOf<String>()

        try {
            imageUris.forEachIndexed { index, uri ->
                try {
                    onProgress?.invoke(index + 1, imageUris.size)
                    val recognizedText = ocrHelper.parseImage(uri)

                    if (recognizedText.startsWith("错误") ||
                        recognizedText.startsWith("OCR识别失败") ||
                        recognizedText.startsWith("处理图片失败")
                    ) {
                        results.add("")
                        Log.w(TAG, "图片OCR识别失败: $uri")
                    } else {
                        results.add(recognizedText)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "图片OCR解析异常: $uri", e)
                    results.add("")
                }
            }
        } finally {
            ocrHelper.close()
        }

        return buildImageSection(results)
    }

    private suspend fun processFiles(fileUris: List<Uri>): String {
        val ocrHelper = OCRHelper(application)
        val fileParser = FileParserHelper(application.contentResolver, ocrHelper)

        return try {
            val results = fileParser.parseFiles(fileUris)
            fileParser.buildFileSection(results)
        } finally {
            ocrHelper.close()
        }
    }

    private fun buildImageSection(ocrResults: List<String>): String {
        val builder = StringBuilder()
        ocrResults.forEachIndexed { index, ocrText ->
            if (ocrText.isNotBlank()) {
                if (builder.isNotEmpty()) {
                    builder.append("\n\n")
                }
                builder.append("图片${index + 1}识别内容：\n")
                builder.append(ocrText)
            }
        }
        return builder.toString()
    }
}
