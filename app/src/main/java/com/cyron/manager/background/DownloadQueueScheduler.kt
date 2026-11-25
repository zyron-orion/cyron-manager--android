package com.cyron.manager.background

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context

object DownloadQueueScheduler {
    private const val RECOVERY_JOB_ID = 4701
    private const val NUDGE_JOB_ID = 4702
    private const val RECOVERY_INTERVAL_MS = 15L * 60L * 1_000L

    fun schedule(context: Context) {
        val component = ComponentName(context, DownloadQueueJobService::class.java)
        val job = JobInfo.Builder(RECOVERY_JOB_ID, component)
            .setPeriodic(RECOVERY_INTERVAL_MS)
            .setPersisted(true)
            .build()
        context.getSystemService(JobScheduler::class.java).schedule(job)
    }

    fun nudge(context: Context) {
        val component = ComponentName(context, DownloadQueueJobService::class.java)
        val job = JobInfo.Builder(NUDGE_JOB_ID, component)
            .setMinimumLatency(5_000L)
            .setOverrideDeadline(30_000L)
            .build()
        context.getSystemService(JobScheduler::class.java).schedule(job)
    }
}
