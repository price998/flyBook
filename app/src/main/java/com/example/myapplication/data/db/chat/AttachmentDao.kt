package com.example.myapplication.data.db.chat

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface AttachmentDao {
    @Insert
    suspend fun insertAttachment(attachment: AttachmentEntity): Long

    @Query("DELETE FROM attachments WHERE id = :id")
    suspend fun deleteAttachment(id: Long)
    
    // 根据消息ID列表删除附件
    @Query("DELETE FROM attachments WHERE messageId IN (:messageIds)")
    suspend fun deleteAttachmentsByMessageIds(messageIds: List<Long>)
}
