package com.cyron.manager

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.cyron.manager.background.DownloadForegroundService
import com.cyron.manager.background.DownloadQueueScheduler
import com.cyron.manager.domain.repository.DownloadRepository
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class CyronApplication : Application() {
    @Inject lateinit var repository: DownloadRepository
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val recoveryStarted = java.util.concurrent.atomic.AtomicBoolean(false)

    override fun onCreate() {
        super.onCreate()
        DownloadQueueScheduler.schedule(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START && recoveryStarted.compareAndSet(false, true)) {
                appScope.launch {
                    // Never mark a live foreground transfer PAUSED just because the
                    // Activity was reopened; only repair rows from a dead service.
                    if (!DownloadForegroundService.isRunning) repository.recoverInterrupted()
                }
            }
        })
    }
}
