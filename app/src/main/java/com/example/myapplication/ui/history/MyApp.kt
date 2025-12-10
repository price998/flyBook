package com.example.myapplication

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.myapplication.ui.history.TrashCleanupWorker
import java.util.concurrent.TimeUnit

class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        scheduleTrashCleanup()
    }

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
