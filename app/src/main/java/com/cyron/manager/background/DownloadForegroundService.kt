package com.cyron.manager.background

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.cyron.manager.MainActivity
import com.cyron.manager.R
import com.cyron.manager.domain.model.DownloadStatus
import com.cyron.manager.domain.model.DownloadTask
import com.cyron.manager.domain.repository.DownloadRepository
import com.cyron.manager.domain.repository.SettingsRepository
import com.cyron.manager.download.DownloadEngine
import com.cyron.manager.download.NetworkMonitor
import com.cyron.manager.download.SourceTransferSnapshots
import com.cyron.manager.di.FolderAccessCoordinator
import com.cyron.manager.domain.model.DownloadSource
import com.cyron.manager.domain.model.TransferSnapshot
import com.cyron.manager.domain.model.resetForRetry
import com.cyron.manager.media.MediaDownloadManager
import com.cyron.manager.storage.SafFileStore
import com.cyron.manager.torrent.TorrentDownloadManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

@AndroidEntryPoint
class DownloadForegroundService : Service() {
    @Inject lateinit var engine: DownloadEngine
    @Inject lateinit var repository: DownloadRepository
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var sourceSnapshots: SourceTransferSnapshots
    @Inject lateinit var network: NetworkMonitor
    @Inject lateinit var mediaManager: MediaDownloadManager
    @Inject lateinit var torrentManager: TorrentDownloadManager
    @Inject lateinit var fileStore: SafFileStore
    @Inject lateinit var folderAccess: FolderAccessCoordinator

    private val serviceScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, error -> Log.e(TAG, "Uncaught download service failure", error) }
    )
    private val activeJobs = java.util.concurrent.ConcurrentHashMap<String, Job>()
    private val activeIds = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val pausingIds = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val cancellingIds = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val deletingIds = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val resumeRequested = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val publishingIds = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private var stopJob: Job? = null
    private val lastPersistAt = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private var wakeLock: PowerManager.WakeLock? = null
    private var wakeRenewal: Job? = null
    private var isForeground = false
    private val liveNotificationIds = mutableSetOf<Int>()
    private val notifications get() = getSystemService(NotificationManager::class.java)
    @Volatile private var stopping = false
    @Volatile private var uiLanguage = "en"

    private fun tr(english: String, persian: String): String = if (uiLanguage == "fa") persian else english

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        createChannel()
        startForeground(NOTIFICATION_ID, notification(tr("Ready for downloads", "آماده برای دانلود"), 0, true))
        isForeground = true
        observeTransfers()
        observeNetwork()
        // Keep service notification copy in sync when the user switches language,
        // even if no new download is started.
        launchSafely {
            settings.settings.collect { preferences ->
                uiLanguage = preferences.language
                // Channel names/descriptions are user-visible too; recreate them
                // when the interface language changes.
                createChannel()
                val cutoff = System.currentTimeMillis() - preferences.historyRetentionDays.coerceIn(1, 365) * 24L * 60L * 60L * 1_000L
                repository.pruneHistory(cutoff)
                if (activeJobs.isNotEmpty()) renderCurrentNotification()
            }
        }
    }

    /**
     * Every service action runs here so a storage/database failure can never crash the process;
     * the failing action is logged and the download row keeps its last persisted state.
     */
    private fun launchSafely(block: suspend CoroutineScope.() -> Unit): Job = serviceScope.launch {
        try {
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.e(TAG, "Download service action failed", error)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // An idle service may still be alive after stopForeground(); every new
        // startForegroundService() contract requires promoting it again.
        if (!isForeground) {
            startForeground(NOTIFICATION_ID, notification(tr("Preparing download…", "در حال آماده‌سازی دانلود…"), 0, true))
            isForeground = true
        } else {
            notifications.notify(NOTIFICATION_ID, notification(tr("Preparing download…", "در حال آماده‌سازی دانلود…"), 0, true))
        }
        if (intent == null) {
            // START_STICKY restarts the service after process death. Resume the rows
            // that were interrupted instead of waiting for the user to reopen the app.
            launchSafely { recoverQueue() }
            return START_STICKY
        }
        when (intent.action) {
            ACTION_PAUSE -> intent.stringId()?.let { pause(it) }
            ACTION_RESUME -> intent.stringId()?.let(::resume)
            ACTION_CANCEL -> intent.stringId()?.let(::cancelDownload)
            ACTION_CANCEL_PROMPT -> intent.stringId()?.let(::askCancelConfirmation)
            ACTION_CANCEL_KEPT -> notifications.cancel(CANCEL_PROMPT_NOTIFICATION_ID)
            ACTION_PAUSE_ALL -> pauseAll()
            ACTION_RESUME_ALL -> resumeAll()
            ACTION_DELETE -> intent.stringId()?.let { id -> deleteDownload(id, intent.getBooleanExtra(EXTRA_DELETE_FILE, true)) }
            ACTION_START -> launchSafely { enqueue(intent.stringId()) }
            ACTION_RETRY -> launchSafely { retry(intent.stringId()) }
            ACTION_RECOVER -> launchSafely { recoverQueue() }
        }
        return START_STICKY
    }

    private fun Intent?.stringId(): String? = this?.getStringExtra(EXTRA_DOWNLOAD_ID)

    private suspend fun recoverQueue() {
        val interrupted = folderAccess.withFolderLock {
            val tasks = repository.observeDownloads().first()
            val retainedIds = tasks.map(DownloadTask::id).toSet() + activeIds
            mediaManager.cleanupOrphans(retainedIds)
            torrentManager.cleanupOrphans(retainedIds)
            tasks.filter { it.status == DownloadStatus.COMPLETED && it.id !in activeIds }.forEach { task ->
                fileStore.finalizePublication(task.id)
                if (task.source == DownloadSource.MEDIA) mediaManager.cleanup(task.id)
                if (task.source == DownloadSource.TORRENT) torrentManager.releaseAfterExport(task.id)
            }
            repository.interruptedDownloads().filter { it.id !in activeIds }.also { pending ->
                pending.forEach { task ->
                    if (task.status != DownloadStatus.PUBLISHING) {
                        repository.saveDownload(task.copy(status = DownloadStatus.QUEUED, error = null, updatedAt = System.currentTimeMillis()))
                    }
                }
            }
        }
        interrupted.forEach { enqueue(it.id) }
        startQueued()
        if (interrupted.isEmpty() && activeJobs.isEmpty() && repository.queuedDownloads(1).isEmpty()) {
            if (isForeground) {
                notifications.cancel(NOTIFICATION_ID)
                stopForeground(STOP_FOREGROUND_REMOVE)
                isForeground = false
            }
            stopSelf()
        }
    }

    private fun observeNetwork() {
        launchSafely {
            network.observe().collect { state ->
                val preferences = settings.settings.first()
                val allowed = state.online && (!preferences.wifiOnly || state.unmetered)
                if (!allowed) {
                    val message = when {
                        !state.online -> "Waiting for an internet connection."
                        else -> "Wi-Fi only is enabled. Waiting for an unmetered network."
                    }
                    val ids = activeJobs.keys + repository.queuedDownloads(64).map(DownloadTask::id)
                    ids.toSet().forEach { pause(it, message) }
                } else {
                    startQueued()
                    renderCurrentNotification()
                }
            }
        }
    }

    private fun observeTransfers() {
        launchSafely {
            // HTTP transfers publish to the engine while media and torrents publish to sourceSnapshots.
            // Both streams must drive progress persistence and the notification; listening to the engine
            // alone left a torrent-only session with a frozen notification and an empty progress column.
            combine(engine.snapshots(), sourceSnapshots.snapshots()) { http, source ->
                http.associateBy(TransferSnapshot::downloadId) + source
            }.collect { combined ->
                val tick = progressTick(combined.values, lastPersistAt, System.currentTimeMillis())
                tick.toPersist.forEach { snapshot -> repository.updateProgress(snapshot.downloadId, snapshot.downloadedBytes, snapshot.totalBytes) }
                combined.values.filter { it.status == DownloadStatus.PUBLISHING }.forEach { snapshot ->
                    if (publishingIds.add(snapshot.downloadId)) {
                        val current = repository.getDownload(snapshot.downloadId)
                        if (current?.status == DownloadStatus.DOWNLOADING) {
                            repository.updateStatus(snapshot.downloadId, DownloadStatus.PUBLISHING)
                        }
                    }
                }
                if (tick.shouldRender) renderCurrentNotification()
                if (activeJobs.isEmpty()) releaseWakeLock()
            }
        }
    }

    private suspend fun enqueue(id: String?) {
        if (stopping || id == null) return
        folderAccess.withFolderLock {
            if (pausingIds.contains(id) || cancellingIds.contains(id) || deletingIds.contains(id)) return@withFolderLock
            if (!activeIds.add(id)) return@withFolderLock
            resumeRequested.remove(id)
            var task = repository.getDownload(id) ?: run { activeIds.remove(id); return@withFolderLock }
            if (task.source != DownloadSource.TORRENT) {
                val recovered = fileStore.recoverPublication(id)
                if (recovered != null) {
                    val completed = task.copy(
                        status = DownloadStatus.COMPLETED,
                        savedUri = recovered.uri.toString(),
                        publishedUris = listOf(recovered.uri.toString()),
                        actualSha256 = recovered.sha256,
                        totalBytes = recovered.bytes,
                        downloadedBytes = recovered.bytes,
                        error = null,
                        updatedAt = System.currentTimeMillis()
                    )
                    repository.saveDownload(completed)
                    fileStore.finalizePublication(id)
                    if (task.source == DownloadSource.MEDIA) mediaManager.cleanup(id)
                    if (settings.settings.first().notificationsEnabled) showFinished(completed, DownloadStatus.COMPLETED)
                    activeIds.remove(id)
                    return@withFolderLock
                }
            }
            if (task.status == DownloadStatus.PUBLISHING) {
                task = task.copy(status = DownloadStatus.QUEUED, error = null, updatedAt = System.currentTimeMillis())
                repository.saveDownload(task)
            }
            // A DOWNLOADING/FAILED/CANCELLED row without an active job is stale (the
            // process or service died mid-transfer); resuming it is safe and expected.
            if (task.status !in setOf(DownloadStatus.QUEUED, DownloadStatus.PAUSED, DownloadStatus.DOWNLOADING, DownloadStatus.FAILED, DownloadStatus.CANCELLED)) {
                activeIds.remove(id)
                return@withFolderLock
            }
            val sourceDestination = if (task.source == DownloadSource.HTTP) task.savedUri else task.outputFolderUri ?: task.savedUri
            if (sourceDestination.isNullOrBlank()) {
                repository.saveDownload(task.copy(status = DownloadStatus.FAILED, error = "Choose an output folder before starting.", updatedAt = System.currentTimeMillis()))
                activeIds.remove(id)
                return@withFolderLock
            }
            val preferences = settings.settings.first()
            uiLanguage = preferences.language
            if (!network.isOnline() || (preferences.wifiOnly && !network.isUnmetered())) {
                val message = if (!network.isOnline()) "Waiting for an internet connection." else "Wi-Fi only is enabled. Waiting for an unmetered network."
                repository.saveDownload(task.copy(status = DownloadStatus.QUEUED, error = message, updatedAt = System.currentTimeMillis()))
                activeIds.remove(id)
                return@withFolderLock
            }
            if (activeJobs.size >= preferences.maxConcurrentDownloads) {
                // Keep a Resume request queued so it starts as soon as a slot frees up.
                if (task.status != DownloadStatus.QUEUED) repository.saveDownload(task.copy(status = DownloadStatus.QUEUED, error = null, updatedAt = System.currentTimeMillis()))
                activeIds.remove(id)
                return@withFolderLock
            }
            val finalTask = task.copy(savedUri = sourceDestination, connectionCount = task.connectionCount.coerceIn(1, 16), status = DownloadStatus.DOWNLOADING, error = null, updatedAt = System.currentTimeMillis())
            repository.saveDownload(finalTask)
            acquireWakeLock()
            val job = serviceScope.launch(start = CoroutineStart.LAZY) {
                try {
                    when (finalTask.source) {
                        DownloadSource.HTTP -> when (val result = engine.download(finalTask)) {
                            is com.cyron.manager.domain.model.DownloadResult.Success -> {
                                // A retried task re-probes its size, so the real total is only known now.
                                val totalBytes = (result.totalBytes.takeIf { it > 0 } ?: finalTask.totalBytes).coerceAtLeast(0L)
                                val completed = finalTask.copy(status = DownloadStatus.COMPLETED, savedUri = result.savedUri, publishedUris = listOf(result.savedUri), totalBytes = totalBytes, downloadedBytes = totalBytes, actualSha256 = result.sha256, error = null, updatedAt = System.currentTimeMillis())
                                repository.saveDownload(completed)
                                fileStore.finalizePublication(id)
                                if (settings.settings.first().notificationsEnabled) showFinished(completed, DownloadStatus.COMPLETED)
                            }
                            is com.cyron.manager.domain.model.DownloadResult.Failure -> {
                                val status = when (result.reason) {
                                    com.cyron.manager.domain.model.DownloadFailure.CANCELLED -> if (result.retryable) DownloadStatus.PAUSED else DownloadStatus.CANCELLED
                                    else -> DownloadStatus.FAILED
                                }
                                val updated = finalTask.copy(status = status, error = result.message, downloadedBytes = engine.snapshots().first().find { it.downloadId == id }?.downloadedBytes ?: finalTask.downloadedBytes, updatedAt = System.currentTimeMillis())
                                repository.saveDownload(updated)
                                if (status == DownloadStatus.FAILED && settings.settings.first().notificationsEnabled) showFinished(updated, status)
                            }
                        }
                        DownloadSource.MEDIA -> {
                            val result = mediaManager.download(
                                finalTask.id,
                                finalTask.url,
                                finalTask.displayName ?: finalTask.fileName.substringBeforeLast('.'),
                                mediaManager.choiceFromSelector(finalTask.formatSelector),
                                sourceDestination,
                                connections = finalTask.connectionCount,
                                onPublishing = {
                                    publishingIds.add(finalTask.id)
                                    repository.updateStatus(finalTask.id, DownloadStatus.PUBLISHING)
                                }
                            ) { media ->
                                // Media streams do report real bytes and speed while they run. Only the
                                // percentage used to be published, which left the details screen with a
                                // moving ring and nothing else.
                                sourceSnapshots.publish(TransferSnapshot(finalTask.id, media.downloadedBytes, media.totalBytes, media.bytesPerSecond, DownloadStatus.DOWNLOADING, fraction = media.fraction))
                            }
                            val completed = finalTask.copy(fileName = result.fileName, mimeType = result.mimeType, totalBytes = result.bytes, downloadedBytes = result.bytes, savedUri = result.savedUri, publishedUris = listOf(result.savedUri), actualSha256 = result.sha256, status = DownloadStatus.COMPLETED, error = null, updatedAt = System.currentTimeMillis())
                            repository.saveDownload(completed)
                            fileStore.finalizePublication(finalTask.id)
                            sourceSnapshots.remove(finalTask.id)
                            mediaManager.cleanup(finalTask.id)
                            if (settings.settings.first().notificationsEnabled) showFinished(completed, DownloadStatus.COMPLETED)
                        }
                        DownloadSource.TORRENT -> {
                            torrentManager.start(finalTask.id, finalTask.url, sourceDestination, finalTask.displayName ?: finalTask.fileName)
                            val result = torrentManager.observe(finalTask.id).onEach { progress ->
                                sourceSnapshots.publish(TransferSnapshot(finalTask.id, progress.downloadedBytes, progress.totalBytes, progress.bytesPerSecond, DownloadStatus.DOWNLOADING, progress.error))
                            }.first { it.complete || it.error != null }
                            if (result.error != null) error(result.error)
                            sourceSnapshots.publish(TransferSnapshot(finalTask.id, result.downloadedBytes, result.totalBytes, result.bytesPerSecond, DownloadStatus.DOWNLOADING))
                            repository.updateStatus(finalTask.id, DownloadStatus.PUBLISHING)
                            val exported = torrentManager.exportCompleted(finalTask.id)
                            val completed = finalTask.copy(fileName = exported.fileName, mimeType = exported.mimeType, totalBytes = exported.bytes, downloadedBytes = exported.bytes, savedUri = exported.savedUri, publishedUris = exported.publishedUris, status = DownloadStatus.COMPLETED, error = null, updatedAt = System.currentTimeMillis())
                            repository.saveDownload(completed)
                            sourceSnapshots.remove(finalTask.id)
                            torrentManager.releaseAfterExport(finalTask.id)
                            if (settings.settings.first().notificationsEnabled) showFinished(completed, DownloadStatus.COMPLETED)
                        }
                    }
                } catch (cancel: CancellationException) {
                    throw cancel
                } catch (error: Exception) {
                    val failed = finalTask.copy(status = DownloadStatus.FAILED, error = error.message?.takeIf(String::isNotBlank) ?: "Download failed. Check your connection and retry.", updatedAt = System.currentTimeMillis())
                    repository.saveDownload(failed)
                    if (settings.settings.first().notificationsEnabled) showFinished(failed, DownloadStatus.FAILED)
                } finally {
                    activeJobs.remove(id)
                    activeIds.remove(id)
                    lastPersistAt.remove(id)
                    publishingIds.remove(id)
                    if (!pausingIds.contains(id)) resumeRequested.remove(id)
                    if (repository.getDownload(id)?.status != DownloadStatus.DOWNLOADING) sourceSnapshots.remove(id)
                    if (activeJobs.isEmpty()) releaseWakeLock()
                    if (!stopping) launchSafely { startQueued(); renderCurrentNotification() }
                }
            }
            activeJobs[id] = job
            job.invokeOnCompletion { activeIds.remove(id) }
            job.start()
        }
        renderCurrentNotification()
    }

    private fun pause(id: String, networkMessage: String? = null) {
        launchSafely {
            var job: Job? = null
            var source: DownloadSource? = null
            var mediaLive: TransferSnapshot? = null
            val accepted = folderAccess.withFolderLock {
                val current = repository.getDownload(id) ?: return@withFolderLock false
                if (current.status == DownloadStatus.PUBLISHING || publishingIds.contains(id) ||
                    cancellingIds.contains(id) || deletingIds.contains(id)
                ) return@withFolderLock false
                val activeJob = activeJobs[id]
                if (activeJob == null) {
                    if (current.status in setOf(DownloadStatus.QUEUED, DownloadStatus.PROBING, DownloadStatus.DOWNLOADING)) {
                        val status = if (networkMessage != null) DownloadStatus.QUEUED else DownloadStatus.PAUSED
                        repository.saveDownload(current.copy(status = status, error = networkMessage, updatedAt = System.currentTimeMillis()))
                    }
                    sourceSnapshots.remove(id)
                    resumeRequested.remove(id)
                    return@withFolderLock false
                }
                if (!pausingIds.add(id)) return@withFolderLock false
                job = activeJob
                source = current.source
                mediaLive = if (current.source == DownloadSource.MEDIA) sourceSnapshots.snapshots().value[id] else null
                val status = if (networkMessage != null) DownloadStatus.QUEUED else DownloadStatus.PAUSED
                repository.pauseIfActive(id, status, networkMessage)
                true
            }
            if (!accepted) return@launchSafely
            val activeJob = job!!
            val downloadSource = source!!
            try {
                when (downloadSource) {
                    DownloadSource.HTTP -> engine.pause(id)
                    DownloadSource.MEDIA -> mediaManager.cancel(id)
                    DownloadSource.TORRENT -> torrentManager.pause(id)
                }
                if (downloadSource != DownloadSource.HTTP) activeJob.cancel(CancellationException("Paused by the user."))
                activeJob.join()
                var restart = false
                folderAccess.withFolderLock {
                    activeJobs.remove(id, activeJob)
                    val task = repository.getDownload(id) ?: return@withFolderLock
                    if (task.status in setOf(DownloadStatus.COMPLETED, DownloadStatus.FAILED, DownloadStatus.CANCELLED)) return@withFolderLock
                    if (recoverPublishedAfterInterruption(task)) return@withFolderLock
                    val pausedBytes = mediaLive?.downloadedBytes?.takeIf { it > 0 } ?: task.downloadedBytes
                    val pausedTotal = mediaLive?.totalBytes?.takeIf { it > 0 } ?: task.totalBytes
                    if (resumeRequested.remove(id)) {
                        repository.saveDownload(task.copy(status = DownloadStatus.QUEUED, error = null, downloadedBytes = pausedBytes, totalBytes = pausedTotal, updatedAt = System.currentTimeMillis()))
                        restart = true
                    } else {
                        val status = if (networkMessage != null) DownloadStatus.QUEUED else DownloadStatus.PAUSED
                        repository.saveDownload(task.copy(status = status, error = networkMessage, downloadedBytes = pausedBytes, totalBytes = pausedTotal, updatedAt = System.currentTimeMillis()))
                    }
                    sourceSnapshots.remove(id)
                    lastPersistAt.remove(id)
                }
                if (restart) enqueue(id)
            } finally {
                pausingIds.remove(id)
            }
            startQueued()
            renderCurrentNotification()
        }
    }

    private suspend fun recoverPublishedAfterInterruption(task: DownloadTask): Boolean {
        if (task.source !in setOf(DownloadSource.HTTP, DownloadSource.MEDIA)) return false
        if (task.status != DownloadStatus.PUBLISHING && !publishingIds.contains(task.id)) return false
        val recovered = fileStore.recoverPublication(task.id) ?: return false
        val completed = task.copy(
            status = DownloadStatus.COMPLETED,
            savedUri = recovered.uri.toString(),
            publishedUris = listOf(recovered.uri.toString()),
            actualSha256 = recovered.sha256,
            totalBytes = recovered.bytes,
            downloadedBytes = recovered.bytes,
            error = null,
            updatedAt = System.currentTimeMillis()
        )
        repository.saveDownload(completed)
        fileStore.finalizePublication(task.id)
        sourceSnapshots.remove(task.id)
        if (task.source == DownloadSource.MEDIA) mediaManager.cleanup(task.id)
        if (settings.settings.first().notificationsEnabled) showFinished(completed, DownloadStatus.COMPLETED)
        return true
    }

    private fun pauseAll() {
        launchSafely {
            val active = mutableListOf<String>()
            folderAccess.withFolderLock {
                repository.resumableDownloads().filter { it.status != DownloadStatus.PUBLISHING }.forEach { task ->
                    if (activeJobs.containsKey(task.id) || activeIds.contains(task.id)) {
                        active += task.id
                    } else {
                        repository.pauseIfActive(task.id, DownloadStatus.PAUSED)
                    }
                }
            }
            active.forEach { pause(it) }
        }
    }

    private fun resumeAll() {
        launchSafely {
            repository.resumableDownloads().forEach { task -> enqueue(task.id) }
            startQueued()
            renderCurrentNotification()
        }
    }

    private fun resume(id: String) {
        launchSafely {
            if (cancellingIds.contains(id) || deletingIds.contains(id)) return@launchSafely
            val task = repository.getDownload(id) ?: return@launchSafely
            if (activeJobs.containsKey(id)) {
                // If a pause is still winding down (or the row was already marked
                // paused), let that action restart the transfer when it finishes.
                if (pausingIds.contains(id) || task.status != DownloadStatus.DOWNLOADING) resumeRequested += id
                return@launchSafely
            }
            enqueue(id)
        }
    }

    private suspend fun retry(id: String?) {
        if (id == null || cancellingIds.contains(id) || deletingIds.contains(id)) return
        var reset = false
        folderAccess.withFolderLock {
            val task = repository.getDownload(id) ?: return@withFolderLock
            if (task.status != DownloadStatus.FAILED && task.status != DownloadStatus.CANCELLED) return@withFolderLock
            // The stored validators and size describe the representation seen when the task was created.
            // Keeping them made "Retry" fail forever after the remote file changed.
            repository.saveDownload(task.resetForRetry())
            reset = true
        }
        if (reset) enqueue(id)
    }

    private fun cancelDownload(id: String) {
        launchSafely {
            var job: Job? = null
            var source: DownloadSource? = null
            val accepted = folderAccess.withFolderLock {
                val task = repository.getDownload(id) ?: return@withFolderLock false
                if (task.status == DownloadStatus.PUBLISHING || publishingIds.contains(id) || deletingIds.contains(id)) return@withFolderLock false
                if (!cancellingIds.add(id)) return@withFolderLock false
                resumeRequested.remove(id)
                job = activeJobs[id]
                source = task.source
                true
            }
            if (!accepted) return@launchSafely
            try {
                when (source) {
                    DownloadSource.HTTP -> engine.cancel(id)
                    DownloadSource.MEDIA -> mediaManager.cancel(id)
                    DownloadSource.TORRENT -> torrentManager.cancel(id, removeData = true)
                    null -> Unit
                }
                val activeJob = job
                activeJob?.cancel(CancellationException("Cancelled by the user."))
                activeJob?.join()
                folderAccess.withFolderLock {
                    val latest = repository.getDownload(id) ?: return@withFolderLock
                    if (latest.status != DownloadStatus.COMPLETED && recoverPublishedAfterInterruption(latest)) {
                        if (latest.source == DownloadSource.HTTP) engine.release(id)
                        if (latest.source == DownloadSource.MEDIA) mediaManager.cleanup(id)
                        return@withFolderLock
                    }
                    if (latest.source == DownloadSource.HTTP) engine.release(id)
                    if (latest.source == DownloadSource.MEDIA) mediaManager.cleanup(id)
                    activeJob?.let { activeJobs.remove(id, it) } ?: activeJobs.remove(id)
                    sourceSnapshots.remove(id)
                    if (latest.status != DownloadStatus.COMPLETED) {
                        repository.saveDownload(latest.copy(status = DownloadStatus.CANCELLED, downloadedBytes = 0, error = null, updatedAt = System.currentTimeMillis()))
                    }
                }
            } finally {
                cancellingIds.remove(id)
            }
            startQueued()
            renderCurrentNotification()
        }
    }

    private fun deleteDownload(id: String, deleteFile: Boolean = true) {
        launchSafely {
            var job: Job? = null
            var source: DownloadSource? = null
            val accepted = folderAccess.withFolderLock {
                val task = repository.getDownload(id) ?: return@withFolderLock false
                if (task.status == DownloadStatus.PUBLISHING || publishingIds.contains(id)) return@withFolderLock false
                if (!deletingIds.add(id)) return@withFolderLock false
                resumeRequested.remove(id)
                job = activeJobs[id]
                source = task.source
                true
            }
            if (!accepted) return@launchSafely
            try {
                when (source) {
                    DownloadSource.HTTP -> engine.cancel(id)
                    DownloadSource.MEDIA -> mediaManager.cancel(id)
                    DownloadSource.TORRENT -> torrentManager.cancel(id, removeData = true)
                    null -> Unit
                }
                val activeJob = job
                activeJob?.cancel(CancellationException("Deleted by the user."))
                activeJob?.join()
                folderAccess.withFolderLock {
                    val latest = repository.getDownload(id) ?: return@withFolderLock
                    if (latest.status != DownloadStatus.COMPLETED) recoverPublishedAfterInterruption(latest)
                    if (latest.source == DownloadSource.HTTP) engine.release(id)
                    if (latest.source == DownloadSource.MEDIA) mediaManager.cleanup(id)
                    activeJob?.let { activeJobs.remove(id, it) } ?: activeJobs.remove(id)
                    sourceSnapshots.remove(id)
                    val current = repository.getDownload(id) ?: return@withFolderLock
                    if (current.status == DownloadStatus.COMPLETED && deleteFile) {
                        val remaining = deletePublishedFiles(current)
                        if (remaining.isNotEmpty()) {
                            repository.saveDownload(current.copy(error = "Some downloaded files could not be deleted from the selected folder. Check the folder permission and retry.", updatedAt = System.currentTimeMillis()))
                            return@withFolderLock
                        }
                    }
                    repository.removeDownload(id)
                    val folder = current.outputFolderUri
                    if (folder != null && folder != settings.settings.first().defaultFolderUri && repository.folderUsage(folder) == 0) {
                        runCatching { contentResolver.releasePersistableUriPermission(Uri.parse(folder), Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
                    }
                }
            } finally {
                deletingIds.remove(id)
            }
            startQueued()
            renderCurrentNotification()
        }
    }

    /** Returns URIs that still exist; missing documents count as already deleted. */
    private fun deletePublishedFiles(task: DownloadTask): List<String> {
        val uris = task.publishedUris.ifEmpty { listOfNotNull(task.savedUri) }
        val remaining = mutableListOf<String>()
        for (raw in uris) {
            val uri = runCatching { Uri.parse(raw) }.getOrNull() ?: continue
            val deleted = runCatching { DocumentsContract.deleteDocument(contentResolver, uri) }.getOrDefault(false)
            if (deleted) continue
            val stillExists = runCatching { DocumentFile.fromSingleUri(this, uri)?.exists() == true }.getOrDefault(true)
            if (stillExists) remaining += raw
        }
        return remaining
    }

    private suspend fun startQueued() {
        val max = settings.settings.first().maxConcurrentDownloads
        val slots = (max - activeJobs.size).coerceAtLeast(0)
        if (slots == 0) return
        repository.queuedDownloads(slots).filter { if (it.source == DownloadSource.HTTP) !it.savedUri.isNullOrBlank() else !(it.outputFolderUri ?: it.savedUri).isNullOrBlank() }.forEach { enqueue(it.id) }
    }

    private suspend fun renderCurrentNotification() {
        if (stopping) return
        val tasks = activeJobs.keys.mapNotNull { repository.getDownload(it) }
        if (tasks.isEmpty()) {
            updateTaskNotifications(emptyList())
            // Do not tear the service down in the middle of a pause/cancel/resume
            // hand-off: a queued restart may be about to create the next job.
            if (stopJob?.isActive != true) {
                stopJob = serviceScope.launch {
                    kotlinx.coroutines.delay(1500)
                    if (!stopping && activeJobs.isEmpty()) {
                        if (isForeground) {
                            notifications.cancel(NOTIFICATION_ID)
                            stopForeground(STOP_FOREGROUND_REMOVE)
                            isForeground = false
                        }
                        stopSelf()
                    }
                }
            }
            return
        }
        stopJob?.cancel()
        stopJob = null
        val live = (engine.snapshots().first().associateBy(TransferSnapshot::downloadId) + sourceSnapshots.snapshots().value)
            .filterKeys { activeJobs.containsKey(it) }
        val first = tasks.first()
        if (!isForeground) {
            startForeground(NOTIFICATION_ID, notification(first, live[first.id]?.downloadedBytes ?: 0, live[first.id]?.totalBytes ?: 0, first.status, true))
            isForeground = true
        } else {
            notifications.notify(NOTIFICATION_ID, notification(first, live[first.id]?.downloadedBytes ?: 0, live[first.id]?.totalBytes ?: 0, first.status, true))
        }
        updateTaskNotifications(tasks.drop(1), live)
    }

    @Synchronized
    private fun updateTaskNotifications(tasks: List<DownloadTask>, snapshots: Map<String, TransferSnapshot> = emptyMap()) {
        val ids = tasks.map { taskNotificationId(it.id) }.toSet()
        (liveNotificationIds - ids).forEach(notifications::cancel)
        liveNotificationIds.clear()
        tasks.forEach { task ->
            val snapshot = snapshots[task.id]
            liveNotificationIds.add(taskNotificationId(task.id))
            notifications.notify(taskNotificationId(task.id), notification(task, snapshot?.downloadedBytes ?: 0, snapshot?.totalBytes ?: 0, task.status, false))
        }
    }

    private fun notification(title: String, progress: Int, ongoing: Boolean): Notification {
        val open = PendingIntent.getActivity(this, 200, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_cyron)
            .setContentTitle("Cyron Manager")
            .setContentText(title)
            .setContentIntent(open)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(ongoing)
            .setOnlyAlertOnce(true)
            .setProgress(100, progress.coerceAtLeast(0), progress < 0)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, tr("Open", "بازکردن"), open)
            .build()
    }

    private fun notification(task: DownloadTask, downloaded: Long, total: Long, status: DownloadStatus, foreground: Boolean): Notification {
        val id = taskNotificationId(task.id)
        val open = PendingIntent.getActivity(this, id, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val paused = status == DownloadStatus.PAUSED || status == DownloadStatus.QUEUED
        val progress = if (total > 0) ((downloaded.toDouble() / total) * 100).toInt().coerceIn(0, 100) else -1
        val state = when {
            status == DownloadStatus.PUBLISHING -> tr("Saving file", "در حال ذخیرهٔ فایل")
            status == DownloadStatus.PROBING -> tr("Checking source", "در حال بررسی منبع")
            paused -> tr("Waiting to download", "در انتظار دانلود")
            else -> tr("Downloading", "در حال دانلود")
        }
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_cyron)
            .setContentTitle(task.fileName)
            .setContentText(state)
            .setContentIntent(open)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, progress.coerceAtLeast(0), progress < 0)
            .addAction(0, tr("Open", "بازکردن"), open)
        if (foreground) builder.setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        if (paused) {
            val resume = PendingIntent.getService(this, id + 1, Intent(this, DownloadForegroundService::class.java).setAction(ACTION_RESUME).putExtra(EXTRA_DOWNLOAD_ID, task.id), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            builder.addAction(0, tr("Resume", "ادامه"), resume)
        } else {
            val pause = PendingIntent.getService(this, id + 1, Intent(this, DownloadForegroundService::class.java).setAction(ACTION_PAUSE).putExtra(EXTRA_DOWNLOAD_ID, task.id), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            builder.addAction(0, tr("Pause", "توقف"), pause)
        }
        val cancel = PendingIntent.getService(this, id + 2, Intent(this, DownloadForegroundService::class.java).setAction(ACTION_CANCEL_PROMPT).putExtra(EXTRA_DOWNLOAD_ID, task.id), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        builder.addAction(0, tr("Cancel", "لغو"), cancel)
        return builder.build()
    }

    /**
     * A notification button sits one mis-tap away from hours of transfer, so the destructive step gets a
     * second, explicit tap. "Pause" stays the one-tap action.
     */
    private fun askCancelConfirmation(id: String) {
        val body = tr("Its partial data will be deleted. This cannot be undone.", "داده‌های ناقص حذف می‌شوند و این کار برگشت‌پذیر نیست.")
        val confirm = PendingIntent.getService(this, 303, Intent(this, DownloadForegroundService::class.java).setAction(ACTION_CANCEL).putExtra(EXTRA_DOWNLOAD_ID, id), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val keep = PendingIntent.getService(this, 304, Intent(this, DownloadForegroundService::class.java).setAction(ACTION_CANCEL_KEPT), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        notifications.notify(CANCEL_PROMPT_NOTIFICATION_ID, NotificationCompat.Builder(this, COMPLETED_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_cyron)
            .setContentTitle(tr("Cancel this download?", "این دانلود لغو شود؟"))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .addAction(0, tr("Cancel download", "لغو دانلود"), confirm)
            .addAction(0, tr("Keep downloading", "ادامهٔ دانلود"), keep)
            .setAutoCancel(true)
            .setTimeoutAfter(CANCEL_PROMPT_TTL_MS)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build())
    }

    private fun showFinished(task: DownloadTask, status: DownloadStatus) {
        val notificationId = taskNotificationId(task.id)
        val open = PendingIntent.getActivity(this, notificationId, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        notifications.notify(notificationId, NotificationCompat.Builder(this, COMPLETED_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_cyron)
            .setContentTitle(if (status == DownloadStatus.COMPLETED) tr("Download complete", "دانلود تمام شد") else tr("Download failed", "دانلود انجام نشد"))
            .setContentText(task.fileName)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build())
    }

    private fun createChannel() {
        notifications.createNotificationChannel(NotificationChannel(CHANNEL_ID, tr("Downloads", "دانلودها"), NotificationManager.IMPORTANCE_LOW).apply {
            description = tr("Shows live file download progress and recovery actions.", "پیشرفت زندهٔ دانلود و گزینه‌های بازیابی را نشان می‌دهد.")
            setShowBadge(false)
        })
        notifications.createNotificationChannel(NotificationChannel(COMPLETED_CHANNEL_ID, tr("Download results", "نتیجهٔ دانلود"), NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = tr("Tells you when a download finishes or fails.", "پایان یا شکست دانلود را اطلاع می‌دهد.")
        })
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        wakeRenewal?.cancel()
        val power = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "CyronManager:DownloadTransfer")
            .apply { setReferenceCounted(false); acquire(WAKELOCK_WINDOW_MS) }
        // Android silently drops a wake lock at its timeout. Renew it while transfers
        // are still active instead of letting a long download lose CPU access.
        wakeRenewal = serviceScope.launch {
            while (true) {
                kotlinx.coroutines.delay(WAKELOCK_WINDOW_MS / 2)
                if (activeJobs.isEmpty()) break
                wakeLock?.let { lock -> if (lock.isHeld) runCatching { lock.release() } }
                wakeLock?.acquire(WAKELOCK_WINDOW_MS)
            }
        }
    }

    private fun releaseWakeLock() {
        wakeRenewal?.cancel()
        wakeRenewal = null
        wakeLock?.let { if (it.isHeld) runCatching { it.release() } }
        wakeLock = null
    }

    /**
     * Android 14/15 cap dataSync foreground services. Persist the current tasks as
     * paused and stop cleanly instead of disappearing silently at the daily limit.
     */
    override fun onTimeout(startId: Int, fgsType: Int) {
        Log.w(TAG, "Foreground service timeout (fgsType=$fgsType); pausing active downloads.")
        // Jobs cancelled below run their cleanup; without this flag that cleanup re-enqueues waiting
        // tasks right before stopSelf(), leaving them DOWNLOADING with no job to move them.
        stopping = true
        launchSafely {
            activeJobs.keys.toList().forEach { id ->
                repository.getDownload(id)?.let { task ->
                    repository.saveDownload(
                        task.copy(
                            status = DownloadStatus.PAUSED,
                            error = "Android stopped the background service after its daily limit. Tap Resume to continue.",
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }
            }
            activeJobs.values.forEach { it.cancel() }
            releaseWakeLock()
            stopSelf()
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // Work continues in the foreground service when the user dismisses the Activity.
    }

    override fun onDestroy() {
        isRunning = false
        stopping = true
        stopJob?.cancel()
        activeJobs.values.forEach { it.cancel() }
        runBlocking { torrentManager.pauseAll() }
        releaseWakeLock()
        serviceScope.cancel()
        updateTaskNotifications(emptyList())
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "CyronDownloadService"
        const val CHANNEL_ID = "cyron_downloads_v1"
        const val COMPLETED_CHANNEL_ID = "cyron_download_results_v1"
        const val NOTIFICATION_ID = 7401
        const val CANCEL_PROMPT_NOTIFICATION_ID = 7402
        private const val CANCEL_PROMPT_TTL_MS = 60_000L
        private fun taskNotificationId(id: String): Int = 1_000_000 + (id.hashCode() and 0x3FFFFFFF)
        const val ACTION_START = "com.cyron.manager.download.START"
        const val ACTION_RETRY = "com.cyron.manager.download.RETRY"
        const val ACTION_RECOVER = "com.cyron.manager.download.RECOVER"
        const val ACTION_PAUSE = "com.cyron.manager.download.PAUSE"
        const val ACTION_RESUME = "com.cyron.manager.download.RESUME"
        const val ACTION_CANCEL = "com.cyron.manager.download.CANCEL"
        const val ACTION_CANCEL_PROMPT = "com.cyron.manager.download.CANCEL_PROMPT"
        const val ACTION_CANCEL_KEPT = "com.cyron.manager.download.CANCEL_KEPT"
        const val ACTION_DELETE = "com.cyron.manager.download.DELETE"
        const val ACTION_PAUSE_ALL = "com.cyron.manager.download.PAUSE_ALL"
        const val ACTION_RESUME_ALL = "com.cyron.manager.download.RESUME_ALL"
        const val EXTRA_DOWNLOAD_ID = "download_id"
        const val EXTRA_DELETE_FILE = "delete_file"
        @Volatile var isRunning = false
        const val PROGRESS_PERSIST_INTERVAL_MS = 500L
        private const val WAKELOCK_WINDOW_MS = 3 * 60 * 60 * 1000L

        fun launch(context: Context, action: String, id: String? = null, deleteFile: Boolean = true) {
            val intent = Intent(context, DownloadForegroundService::class.java).setAction(action).apply {
                if (id != null) putExtra(EXTRA_DOWNLOAD_ID, id)
                if (action == ACTION_DELETE) putExtra(EXTRA_DELETE_FILE, deleteFile)
            }
            ContextCompat.startForegroundService(context, intent)
        }
    }
}

/** One throttled look at every live transfer: what must be written to Room and whether the notification is due. */
internal class ProgressTick(val toPersist: List<TransferSnapshot>, val shouldRender: Boolean)

/**
 * Terminal states keep the final row the completion worker wrote. Live progress is considered at most once
 * per interval for each download; media streams report a percentage instead of bytes, so only transfers
 * with a real byte total are persisted.
 */
internal fun progressTick(
    snapshots: Collection<TransferSnapshot>,
    lastPersistAt: MutableMap<String, Long>,
    now: Long
): ProgressTick {
    val toPersist = mutableListOf<TransferSnapshot>()
    var shouldRender = false
    for (snapshot in snapshots) {
        if (snapshot.status in TERMINAL_STATUSES) continue
        if (now - (lastPersistAt[snapshot.downloadId] ?: 0L) < DownloadForegroundService.PROGRESS_PERSIST_INTERVAL_MS) continue
        lastPersistAt[snapshot.downloadId] = now
        shouldRender = true
        if (snapshot.totalBytes > 0) toPersist += snapshot
    }
    return ProgressTick(toPersist, shouldRender)
}

private val TERMINAL_STATUSES = setOf(DownloadStatus.COMPLETED, DownloadStatus.FAILED, DownloadStatus.CANCELLED)
