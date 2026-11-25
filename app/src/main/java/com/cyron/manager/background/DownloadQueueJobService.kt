package com.cyron.manager.background

import android.app.job.JobParameters
import android.app.job.JobService
import android.content.Intent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class DownloadQueueJobService : JobService() {
    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartJob(params: JobParameters): Boolean {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                val intent = Intent(this@DownloadQueueJobService, DownloadForegroundService::class.java)
                    .setAction(DownloadForegroundService.ACTION_RECOVER)
                ContextCompat.startForegroundService(this@DownloadQueueJobService, intent)
            } finally {
                jobFinished(params, false)
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        scope.cancel()
        return false
    }
}
