package com.example.myapplication.ui.common.model

import android.net.Uri

// 数据模型
data class SelectedMedia(
    val uri: Uri,
    val type: MediaType,
    val name: String = ""
)

// 枚举类型
enum class MediaType { IMAGE, FILE }
