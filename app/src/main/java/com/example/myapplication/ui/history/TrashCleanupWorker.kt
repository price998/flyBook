package com.example.myapplication.ui.history

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.myapplication.repository.HistoryRepository

class TrashCleanupWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    //后台定时清理过期回收站数据
    override suspend fun doWork(): Result {
        return try {
            val repo = HistoryRepository(applicationContext)
            repo.cleanupExpiredTrash(7)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
