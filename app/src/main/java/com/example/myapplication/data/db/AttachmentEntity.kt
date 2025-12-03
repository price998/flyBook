package com.example.myapplication.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "attachments",
    foreignKeys = [ForeignKey(
        entity = MessageEntity::class,
        parentColumns = ["id"],
        childColumns = ["messageId"],
        onDelete = ForeignKey.CASCADE // 消息删除时自动删附件记录
    )],
    indices = [Index("messageId")] // 为外键添加索引，提升查询性能
)
data class AttachmentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val messageId: Long,
    val type: String,               // image, pdf, word, audio
    val localPath: String,          // 本地文件路径
    val remoteUrl: String? = null,  // 服务端URL (本地模式下可能为空)
    val fileName: String,           // 文件名
    val fileSize: Long,             // 文件大小
    val mimeType: String            // 例如 application/pdf
)
