package com.example.myapplication.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface AttachmentDao {
    @Insert
    suspend fun insertAttachment(attachment: AttachmentEntity): Long

    @Query("SELECT * FROM attachments WHERE messageId = :messageId")
    suspend fun getAttachmentsByMessageId(messageId: Long): List<AttachmentEntity>

    @Query("DELETE FROM attachments WHERE id = :id")
    suspend fun deleteAttachment(id: Long)
}
