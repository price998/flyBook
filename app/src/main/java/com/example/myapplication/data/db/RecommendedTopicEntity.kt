package com.example.myapplication.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recommended_topics")
data class RecommendedTopicEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,               // 话题标题
    val subtitle: String? = null,    // 副标题/描述
    val icon: String? = null,        // 图标资源名
    val category: String,            // 分类：TECH / LIFE / WORK / STUDY
    val prompt: String,              // 预设提示词 (点击后填入输入框)
    val sortOrder: Int = 0,          // 排序
    val isActive: Boolean = true,    // 是否启用
    val clickCount: Int = 0,         // 点击次数
    val createdAt: Long              // 创建时间
)
