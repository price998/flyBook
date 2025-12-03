package com.example.myapplication.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recommended_topics")
data class RecommendedTopicEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,               // 话题标题
    val prompt: String               // 预设提示词
)
