package com.example.myapplication.ui.inputbar.model

import android.net.Uri

/**
 * 附件数据模型
 * 
 * 用于表示用户选择的附件（图片或文件）
 * 
 * @property uri 附件的URI
 * @property type 附件类型（图片或文件）
 * @property name 附件名称（可选）
 */
data class SelectedMedia(
    val uri: Uri,
    val type: MediaType,
    val name: String = ""
)

/**
 * 附件类型枚举
 */
enum class MediaType { 
    /** 图片类型 */
    IMAGE, 
    /** 文件类型 */
    FILE 
}
