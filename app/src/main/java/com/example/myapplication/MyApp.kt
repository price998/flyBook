package com.example.myapplication

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.myapplication.ui.history.TrashCleanupWorker
import java.util.concurrent.TimeUnit

/**
 * 应用全局Application类
 *
 * 职责：
 * - 应用启动时的全局初始化
 * - 定时任务调度（如回收站清理）
 */
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        scheduleTrashCleanup()
    }

    /** 调度回收站定时清理任务 每天执行一次，清理过期的回收站内容 */
    private fun scheduleTrashCleanup() {
        val request = PeriodicWorkRequestBuilder<TrashCleanupWorker>(1, TimeUnit.DAYS).build()
        WorkManager.getInstance(this)
                .enqueueUniquePeriodicWork(
                        "trash_cleanup_daily",
                        ExistingPeriodicWorkPolicy.KEEP,
                        request
                )
    }
}
