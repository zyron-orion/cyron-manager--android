package com.cyron.manager.presentation

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cyron.manager.background.DownloadForegroundService
import com.cyron.manager.download.DownloadEngine
import com.cyron.manager.download.ProbeFileUseCase
import com.cyron.manager.download.SourceTransferSnapshots
import com.cyron.manager.di.FolderAccessCoordinator
import com.cyron.manager.domain.model.DownloadSource
import com.cyron.manager.media.MediaDownloadManager
import com.cyron.manager.media.MediaFormatChoice
import com.cyron.manager.media.MediaInfo
import com.cyron.manager.domain.model.DashboardStats
import com.cyron.manager.domain.model.DownloadName
import com.cyron.manager.domain.model.DownloadResult
import com.cyron.manager.domain.model.DownloadSettings
import com.cyron.manager.domain.model.DownloadStatus
import com.cyron.manager.domain.model.DownloadTask
import com.cyron.manager.domain.model.TransferSnapshot
import com.cyron.manager.domain.repository.DownloadRepository
import com.cyron.manager.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class DownloadViewModel @Inject constructor(
    private val repository: DownloadRepository,
    private val settingsRepository: SettingsRepository,
    private val engine: DownloadEngine,
    private val probeFile: ProbeFileUseCase,
    private val mediaManager: MediaDownloadManager,
    private val sourceSnapshots: SourceTransferSnapshots,
    private val folderAccess: FolderAccessCoordinator,
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context
) : ViewModel() {
    private val _screen = MutableStateFlow(ScreenState())
    val screen: StateFlow<ScreenState> = _screen.asStateFlow()

    private var probing = false
    @Volatile private var lastProbe: com.cyron.manager.download.ProbedDownload? = null
    private val observed = combine(repository.observeDownloads(), engine.snapshots(), sourceSnapshots.snapshots(), settingsRepository.settings) { downloads, httpSnapshots, sourceSnapshots, settings ->
        Triple(downloads, httpSnapshots.associateBy(TransferSnapshot::downloadId) + sourceSnapshots, settings)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, Triple(emptyList(), emptyMap(), DownloadSettings()))

    init {
        viewModelScope.launch {
            observed.collect { (downloads, snapshots, settings) ->
                val counts = downloads.asSequence().filter { it.status == DownloadStatus.COMPLETED }.sumOf { it.totalBytes.coerceAtLeast(0) }
                val activeBytes = downloads.filter { it.status == DownloadStatus.DOWNLOADING }.sumOf { task ->
                    snapshots[task.id]?.bytesPerSecond ?: 0L
                }
                val zone = java.time.ZoneId.systemDefault()
                val today = java.time.LocalDate.now(zone)
                val byDay = downloads.filter { it.status == DownloadStatus.COMPLETED }.groupBy {
                    java.time.Instant.ofEpochMilli(it.updatedAt).atZone(zone).toLocalDate()
                }
                val history = (6L downTo 0L).map { offset ->
                    byDay[today.minusDays(offset)]?.sumOf { it.totalBytes.coerceAtLeast(0) } ?: 0L
                }
                _screen.value = _screen.value.copy(
                    downloads = downloads,
                    snapshots = snapshots,
                    settings = settings,
                    stats = DashboardStats(
                        totalDownloadedBytes = counts,
                        activeCount = downloads.count { it.status in setOf(DownloadStatus.PROBING, DownloadStatus.DOWNLOADING, DownloadStatus.PUBLISHING) },
                        completedToday = downloads.count { it.status == DownloadStatus.COMPLETED && System.currentTimeMillis() - it.updatedAt < 86_400_000 },
                        currentSpeedBytesPerSecond = activeBytes,
                        dailyBytes = history
                    )
                )
            }
        }
    }

    fun probeUrl(rawUrl: String, connections: Int) {
        if (probing) return
        probing = true
        lastProbe = null
        _screen.value = _screen.value.copy(isProbing = true, validationError = null, probedUrl = rawUrl.trim(), probedEffectiveUrl = "", probedName = "", probedBytes = 0, supportsRanges = false, etag = null, lastModified = null)
        viewModelScope.launch {
            try {
                val prepared = withContext(Dispatchers.IO) { probeFile(rawUrl.trim()) }
                lastProbe = prepared
                _screen.value = _screen.value.copy(isProbing = false, probedName = prepared.fileName, mimeType = prepared.mimeType, probedBytes = prepared.result.totalBytes, supportsRanges = prepared.result.rangeSupported && (prepared.result.etag != null || prepared.result.lastModified != null), connections = connections.coerceIn(1, 16), etag = prepared.result.etag, lastModified = prepared.result.lastModified, probedEffectiveUrl = prepared.result.effectiveUrl, validationError = null)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { lastProbe = null; _screen.value = _screen.value.copy(isProbing = false, probedBytes = 0, validationError = error.message ?: "Unable to inspect this download link.") }
            finally { probing = false }
        }
    }

    fun enqueue(rawUrl: String, fileName: String, expectedHash: String?, folderUri: String, connections: Int) {
        val prepared = lastProbe
        if (prepared == null) {
            _screen.value = _screen.value.copy(validationError = "Inspect the link again before starting the download.")
            return
        }
        if (_screen.value.isProbing || _screen.value.isEnqueuing) return
        if (rawUrl.trim() != _screen.value.probedUrl || prepared.result.effectiveUrl != _screen.value.probedEffectiveUrl) {
            _screen.value = _screen.value.copy(validationError = "Inspect the link again before starting the download.")
            return
        }
        _screen.value = _screen.value.copy(isEnqueuing = true, validationError = null, enqueueSucceeded = false)
        viewModelScope.launch {
            try {
                if (prepared.result.totalBytes <= 0) throw IllegalArgumentException("The server did not supply a valid file size.")
                val uri = Uri.parse(folderUri)
                require(uri.scheme == "content") { "Choose a destination folder using the system folder picker." }
                val effectiveName = DownloadName.sanitize(fileName.ifBlank { prepared.fileName })
                val task = DownloadTask(
                    url = prepared.result.effectiveUrl,
                    fileName = effectiveName,
                    mimeType = prepared.mimeType,
                    totalBytes = prepared.result.totalBytes,
                    rangeSupported = prepared.result.rangeSupported,
                    connectionCount = connections.coerceIn(1, 16),
                    savedUri = uri.toString(),
                    outputFolderUri = uri.toString(),
                    expectedSha256 = expectedHash?.trim()?.takeIf(String::isNotBlank)?.also { checksum -> require(checksum.matches(Regex("[a-fA-F0-9]{64}"))) { "Enter exactly 64 valid hexadecimal characters for SHA-256." } },
                    etag = prepared.result.etag,
                    lastModified = prepared.result.lastModified,
                    status = DownloadStatus.QUEUED
                )
                repository.saveDownload(task)
                com.cyron.manager.background.DownloadQueueScheduler.nudge(appContext)
                DownloadForegroundService.launch(appContext, DownloadForegroundService.ACTION_START, task.id)
                lastProbe = null
                _screen.value = _screen.value.copy(
                    message = "Added ${task.fileName} to downloads.",
                    validationError = null,
                    enqueueSucceeded = true,
                    // A completed probe is single-use. Leaving it behind makes the
                    // next dialog look verified and silently refuses to start.
                    probedUrl = "",
                    probedEffectiveUrl = "",
                    probedName = "",
                    probedBytes = 0,
                    supportsRanges = false,
                    etag = null,
                    lastModified = null
                )
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: SecurityException) { _screen.value = _screen.value.copy(validationError = "Choose a writable folder using the Android folder picker.") }
            catch (error: Exception) { _screen.value = _screen.value.copy(validationError = error.message ?: "The download could not be added.") }
            finally { _screen.value = _screen.value.copy(isEnqueuing = false) }
        }
    }

    fun inspectMedia(url: String) {
        if (_screen.value.isInspectingMedia) return
        _screen.value = _screen.value.copy(isInspectingMedia = true, mediaInfo = null, mediaChoices = emptyList(), validationError = null)
        viewModelScope.launch {
            try {
                val info = mediaManager.inspect(url.trim())
                val choices = MediaFormatChoice.choices(info, preferMerged = MediaDownloadManager.isYouTubeUrl(url))
                _screen.value = _screen.value.copy(isInspectingMedia = false, mediaInfo = info, mediaChoices = choices, selectedMediaSelector = choices.firstOrNull()?.selector)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { _screen.value = _screen.value.copy(isInspectingMedia = false, validationError = error.message ?: "Could not inspect this media link.") }
        }
    }

    fun selectMediaFormat(selector: String) { _screen.value = _screen.value.copy(selectedMediaSelector = selector) }
    fun clearMediaInfo() { _screen.value = _screen.value.copy(mediaInfo = null, mediaChoices = emptyList(), selectedMediaSelector = null, isInspectingMedia = false) }

    fun enqueueMedia(rawUrl: String, title: String, folderUri: String) {
        val selector = _screen.value.selectedMediaSelector ?: run { inspectMedia(rawUrl); return }
        enqueueSource(DownloadTask(url = rawUrl.trim(), fileName = DownloadName.sanitize(title.ifBlank { _screen.value.mediaInfo?.title ?: "media" }), displayName = DownloadName.sanitize(title.ifBlank { _screen.value.mediaInfo?.title ?: "media" }), outputFolderUri = folderUri, mimeType = if (selector == "__audio_only__" || selector == "__audio_mp3__") "audio/*" else "video/*", totalBytes = -1, rangeSupported = false, connectionCount = _screen.value.settings.defaultConnections.coerceIn(1, 16), savedUri = null, source = DownloadSource.MEDIA, formatSelector = selector, status = DownloadStatus.QUEUED))
    }

    /** Fallback when inspection is unavailable: let yt-dlp pick its progressive best format. */
    fun enqueueMediaBest(rawUrl: String, title: String, folderUri: String) {
        val link = rawUrl.trim()
        if (!link.startsWith("http", true)) { _screen.value = _screen.value.copy(validationError = "Paste an HTTP(S) media link first."); return }
        if (folderUri.isBlank()) { _screen.value = _screen.value.copy(validationError = "Choose a destination folder first."); return }
        val safeName = DownloadName.sanitize(title.ifBlank { "media" })
        enqueueSource(DownloadTask(url = link, fileName = safeName, displayName = safeName, outputFolderUri = folderUri, mimeType = "video/*", totalBytes = -1, rangeSupported = false, connectionCount = _screen.value.settings.defaultConnections.coerceIn(1, 16), savedUri = null, source = DownloadSource.MEDIA, formatSelector = "best[vcodec!=none][acodec!=none]", status = DownloadStatus.QUEUED))
    }

    fun enqueueTorrent(sourceUri: String, folderUri: String, name: String = "torrent") {
        val link = sourceUri.trim()
        if (!link.startsWith("magnet:", true) && !link.startsWith("content:", true)) { _screen.value = _screen.value.copy(validationError = "Enter a magnet link or choose a .torrent file."); return }
        val display = name.takeIf { it.isNotBlank() && !it.equals("torrent", true) }
            ?: magnetDisplayName(link)
        enqueueSource(DownloadTask(url = link, fileName = DownloadName.sanitize(display), mimeType = "application/x-bittorrent", totalBytes = -1, rangeSupported = false, connectionCount = 1, savedUri = folderUri, outputFolderUri = folderUri, source = DownloadSource.TORRENT, status = DownloadStatus.QUEUED))
    }

    private fun magnetDisplayName(link: String): String {
        if (!link.startsWith("magnet:", true)) return "torrent"
        val dn = runCatching { Uri.parse(link).getQueryParameter("dn") }.getOrNull()?.trim()
        if (!dn.isNullOrBlank()) return dn
        val hash = com.cyron.manager.torrent.TorrentDownloadManager.parseMagnetHash(link)
        return if (hash.length >= 8) "torrent-${hash.take(8)}" else "torrent"
    }

    private fun enqueueSource(task: DownloadTask) {
        val folder = task.outputFolderUri ?: task.savedUri
        if (folder.isNullOrBlank()) { _screen.value = _screen.value.copy(validationError = "Choose a destination folder first."); return }
        val selectedTree = Uri.parse(folder)
        if (selectedTree.scheme != "content") { _screen.value = _screen.value.copy(validationError = "Choose a destination folder using the Android folder picker."); return }
        val queuedTask = task.copy(savedUri = if (task.source == DownloadSource.TORRENT) folder else null, outputFolderUri = folder)
        viewModelScope.launch {
            try {
                folderAccess.withFolderLock { repository.saveDownload(queuedTask) }
                sourceSnapshots.reset(queuedTask.id, queuedTask.totalBytes)
                com.cyron.manager.background.DownloadQueueScheduler.nudge(appContext)
                DownloadForegroundService.launch(appContext, DownloadForegroundService.ACTION_START, queuedTask.id)
                _screen.value = _screen.value.copy(message = "Added ${queuedTask.fileName} to downloads.", enqueueSucceeded = true, mediaInfo = null, mediaChoices = emptyList())
            } catch (error: Exception) { _screen.value = _screen.value.copy(validationError = error.message ?: "Could not add this download.") }
        }
    }

    fun enqueueBatch(rawInput: String, folderUri: String, mode: String, connections: Int) {
        val links = extractDownloadLinks(rawInput)
        if (links.isEmpty()) {
            _screen.value = _screen.value.copy(validationError = "Paste at least one valid download link.")
            return
        }
        if (_screen.value.isEnqueuing) return
        val folder = Uri.parse(folderUri)
        if (folder.scheme != "content") {
            _screen.value = _screen.value.copy(validationError = "Choose a destination folder using the system folder picker.")
            return
        }
        _screen.value = _screen.value.copy(isEnqueuing = true, validationError = null, enqueueSucceeded = false)
        viewModelScope.launch {
            var added = 0
            val failures = mutableListOf<String>()
            try {
                val existing = _screen.value.downloads.map { it.url.trim() }.toSet()
                links.distinct().filterNot(existing::contains).forEach { link ->
                    try {
                        val task = batchTask(link, folderUri, mode, connections.coerceIn(1, 16))
                        folderAccess.withFolderLock { repository.saveDownload(task) }
                        sourceSnapshots.reset(task.id, task.totalBytes)
                        com.cyron.manager.background.DownloadQueueScheduler.nudge(appContext)
                        DownloadForegroundService.launch(appContext, DownloadForegroundService.ACTION_START, task.id)
                        added += 1
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        failures += error.message ?: "Could not add $link"
                    }
                }
                when {
                    added > 0 -> {
                        lastProbe = null
                        _screen.value = _screen.value.copy(
                            message = if (failures.isEmpty()) "Added $added downloads." else "Added $added downloads; ${failures.size} could not be added.",
                            validationError = null,
                            enqueueSucceeded = true,
                            probedUrl = "",
                            probedEffectiveUrl = "",
                            probedName = "",
                            probedBytes = 0,
                            supportsRanges = false,
                            etag = null,
                            lastModified = null
                        )
                    }
                    failures.isNotEmpty() -> _screen.value = _screen.value.copy(validationError = failures.first())
                    else -> _screen.value = _screen.value.copy(message = "All links are already in the download list.")
                }
            } finally {
                _screen.value = _screen.value.copy(isEnqueuing = false)
            }
        }
    }

    private suspend fun batchTask(link: String, folderUri: String, mode: String, connections: Int): DownloadTask = when (mode) {
        "file" -> {
            val prepared = withContext(Dispatchers.IO) { probeFile(link) }
            require(prepared.result.totalBytes > 0) { "The server did not supply a valid file size." }
            DownloadTask(
                url = prepared.result.effectiveUrl,
                fileName = DownloadName.sanitize(prepared.fileName),
                mimeType = prepared.mimeType,
                totalBytes = prepared.result.totalBytes,
                rangeSupported = prepared.result.rangeSupported,
                connectionCount = connections,
                savedUri = folderUri,
                outputFolderUri = folderUri,
                etag = prepared.result.etag,
                lastModified = prepared.result.lastModified,
                status = DownloadStatus.QUEUED
            )
        }
        "torrent" -> {
            require(link.startsWith("magnet:", true)) { "Batch torrent import accepts magnet links." }
            DownloadTask(
                url = link,
                fileName = DownloadName.sanitize(magnetDisplayName(link)),
                mimeType = "application/x-bittorrent",
                totalBytes = -1,
                rangeSupported = false,
                connectionCount = 1,
                savedUri = folderUri,
                outputFolderUri = folderUri,
                source = DownloadSource.TORRENT,
                status = DownloadStatus.QUEUED
            )
        }
        else -> {
            require(link.startsWith("http", true) && Uri.parse(link).userInfo.isNullOrBlank()) { "Enter an HTTP(S) media link without embedded credentials." }
            val info = if (mode == "youtube") null else mediaManager.inspect(link)
            val choice = info?.let { MediaFormatChoice.choices(it, preferMerged = MediaDownloadManager.isYouTubeUrl(link)).firstOrNull() }
            val title = info?.title ?: mediaBatchName(link)
            val selector = choice?.selector ?: "best[vcodec!=none][acodec!=none]"
            DownloadTask(
                url = link,
                fileName = DownloadName.sanitize(title),
                displayName = DownloadName.sanitize(title),
                outputFolderUri = folderUri,
                mimeType = if (selector == "__audio_only__" || selector == "__audio_mp3__") "audio/*" else "video/*",
                totalBytes = -1,
                rangeSupported = false,
                connectionCount = connections,
                savedUri = null,
                source = DownloadSource.MEDIA,
                formatSelector = selector,
                status = DownloadStatus.QUEUED
            )
        }
    }

    private fun mediaBatchName(link: String): String {
        val uri = Uri.parse(link)
        val segment = uri.lastPathSegment?.takeIf(String::isNotBlank) ?: uri.host ?: "media"
        return segment.substringBeforeLast('.').take(80)
    }

    fun setPriority(task: DownloadTask, change: Int) {
        viewModelScope.launch {
            val current = repository.getDownload(task.id) ?: return@launch
            repository.updatePriority(current.id, (current.priority + change).coerceIn(-10, 10))
        }
    }

    fun pauseAll() {
        DownloadForegroundService.launch(appContext, DownloadForegroundService.ACTION_PAUSE_ALL)
    }

    fun resumeAll() {
        DownloadForegroundService.launch(appContext, DownloadForegroundService.ACTION_RESUME_ALL)
    }

    fun clearHistory() {
        viewModelScope.launch {
            val removed = folderAccess.withFolderLock {
                val folders = repository.observeDownloads().first().mapNotNull { it.outputFolderUri ?: it.savedUri }.toSet()
                val count = repository.pruneHistory(Long.MAX_VALUE)
                folders.forEach { releaseFolderIfUnusedLocked(it) }
                count
            }
            _screen.value = _screen.value.copy(message = "Removed $removed history entries.")
        }
    }

    private suspend fun releaseFolderIfUnusedLocked(folder: String?) {
        if (folder.isNullOrBlank() || folder == settingsRepository.settings.first().defaultFolderUri) return
        if (repository.folderUsage(folder) > 0) return
        runCatching {
            appContext.contentResolver.releasePersistableUriPermission(
                Uri.parse(folder),
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
    }

    /** Changes where a not-yet-started/paused task will be published. */
    fun changeFolder(task: DownloadTask, folderUri: String) {
        if (task.status !in setOf(DownloadStatus.QUEUED, DownloadStatus.PAUSED, DownloadStatus.FAILED, DownloadStatus.CANCELLED)) {
            _screen.value = _screen.value.copy(message = "Pause the download before changing its destination.")
            return
        }
        val uri = Uri.parse(folderUri)
        if (uri.scheme != "content") { _screen.value = _screen.value.copy(validationError = "Choose a destination folder using the Android folder picker."); return }
        viewModelScope.launch {
            var changed = false
            folderAccess.withFolderLock {
                val current = repository.getDownload(task.id) ?: return@withFolderLock
                if (current.status !in setOf(DownloadStatus.QUEUED, DownloadStatus.PAUSED, DownloadStatus.FAILED, DownloadStatus.CANCELLED)) {
                    _screen.value = _screen.value.copy(message = "Pause the download before changing its destination.")
                    return@withFolderLock
                }
                val previousFolder = current.outputFolderUri ?: current.savedUri
                repository.saveDownload(current.copy(outputFolderUri = folderUri, savedUri = if (current.source == DownloadSource.HTTP) folderUri else current.savedUri, error = null, updatedAt = System.currentTimeMillis()))
                releaseFolderIfUnusedLocked(previousFolder)
                changed = true
            }
            if (changed) _screen.value = _screen.value.copy(message = "Destination folder changed.")
        }
    }

    /** Deletes the record; for a completed task the caller chooses whether the file is kept. */
    fun delete(task: DownloadTask, keepFile: Boolean) {
        DownloadForegroundService.launch(appContext, DownloadForegroundService.ACTION_DELETE, task.id, deleteFile = !keepFile)
    }

    fun action(task: DownloadTask, action: DownloadAction) {
        when (action) {
            DownloadAction.Pause -> DownloadForegroundService.launch(appContext, DownloadForegroundService.ACTION_PAUSE, task.id)
            DownloadAction.Resume -> DownloadForegroundService.launch(appContext, DownloadForegroundService.ACTION_RESUME, task.id)
            DownloadAction.Retry -> DownloadForegroundService.launch(appContext, if (task.source == DownloadSource.TORRENT) DownloadForegroundService.ACTION_RESUME else DownloadForegroundService.ACTION_RETRY, task.id)
            DownloadAction.Cancel -> DownloadForegroundService.launch(appContext, DownloadForegroundService.ACTION_CANCEL, task.id)
            DownloadAction.Delete -> DownloadForegroundService.launch(appContext, DownloadForegroundService.ACTION_DELETE, task.id)
            DownloadAction.ChangeFolder -> Unit
            DownloadAction.Open -> openOutput(task)
            DownloadAction.Share -> shareOutput(task)
            DownloadAction.PriorityUp -> setPriority(task, 1)
            DownloadAction.PriorityDown -> setPriority(task, -1)
        }
    }

    fun openOutput(task: DownloadTask, outputUri: String? = null) {
        val raw = outputUri ?: task.publishedUris.firstOrNull() ?: task.savedUri ?: return
        runCatching {
            val uri = Uri.parse(raw)
            val mime = appContext.contentResolver.getType(uri) ?: task.mimeType
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW)
                .setDataAndType(uri, mime)
                .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            appContext.startActivity(intent)
        }.onFailure { _screen.value = _screen.value.copy(message = "No compatible app could open this file.") }
    }

    fun shareOutput(task: DownloadTask, outputUri: String? = null) {
        val raw = outputUri ?: task.publishedUris.firstOrNull() ?: task.savedUri ?: return
        viewModelScope.launch {
            val persian = runCatching { settingsRepository.settings.first().language == "fa" }.getOrDefault(false)
            runCatching {
                val uri = Uri.parse(raw)
                val mime = appContext.contentResolver.getType(uri) ?: task.mimeType
                val send = android.content.Intent(android.content.Intent.ACTION_SEND)
                    .setType(mime)
                    .putExtra(android.content.Intent.EXTRA_STREAM, uri)
                    .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                val title = if (persian) "اشتراک‌گذاری ${task.fileName}" else "Share ${task.fileName}"
                appContext.startActivity(android.content.Intent.createChooser(send, title).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
            }.onFailure { _screen.value = _screen.value.copy(message = "The completed file is not available to share.") }
        }
    }

    fun updateSettings(action: SettingsAction) {
        viewModelScope.launch {
            when (action) {
                is SettingsAction.Concurrent -> settingsRepository.updateMaxConcurrent(action.value)
                is SettingsAction.Connections -> settingsRepository.updateConnections(action.value)
                is SettingsAction.Theme -> settingsRepository.updateTheme(action.dark)
                is SettingsAction.FollowSystemTheme -> settingsRepository.updateFollowSystemTheme(action.enabled)
                is SettingsAction.HistoryRetention -> settingsRepository.updateHistoryRetentionDays(action.days)
                is SettingsAction.Language -> settingsRepository.updateLanguage(action.language)
                is SettingsAction.Notifications -> settingsRepository.updateNotifications(action.enabled)
                is SettingsAction.WifiOnly -> settingsRepository.updateWifiOnly(action.enabled)
                is SettingsAction.Destination -> folderAccess.withFolderLock {
                    val previous = settingsRepository.settings.first().defaultFolderUri
                    settingsRepository.updateDestinationFolder(action.uri)
                    releaseFolderIfUnusedLocked(previous)
                }
            }
        }
    }

    fun clearMessage() { _screen.value = _screen.value.copy(message = null) }
    fun showError(message: String) { _screen.value = _screen.value.copy(validationError = message) }
    fun clearValidationError() { _screen.value = _screen.value.copy(validationError = null) }
    fun clearEnqueueResult() { _screen.value = _screen.value.copy(enqueueSucceeded = false) }
}

data class ScreenState(
    val downloads: List<DownloadTask> = emptyList(),
    val snapshots: Map<String, TransferSnapshot> = emptyMap(),
    val settings: DownloadSettings = DownloadSettings(),
    val stats: DashboardStats = DashboardStats(),
    val isProbing: Boolean = false,
    val isInspectingMedia: Boolean = false,
    val mediaInfo: MediaInfo? = null,
    val mediaChoices: List<MediaFormatChoice> = emptyList(),
    val selectedMediaSelector: String? = null,
    val isEnqueuing: Boolean = false,
    val enqueueSucceeded: Boolean = false,
    val probedName: String = "",
    val probedUrl: String = "",
    val probedEffectiveUrl: String = "",
    val probedBytes: Long = 0,
    val mimeType: String = "application/octet-stream",
    val supportsRanges: Boolean = false,
    val connections: Int = 4,
    val etag: String? = null,
    val lastModified: String? = null,
    val validationError: String? = null,
    val message: String? = null
)

enum class DownloadAction { Pause, Resume, Cancel, Retry, Delete, Open, Share, ChangeFolder, PriorityUp, PriorityDown }
sealed interface SettingsAction {
    data class Concurrent(val value: Int) : SettingsAction
    data class Connections(val value: Int) : SettingsAction
    data class Theme(val dark: Boolean) : SettingsAction
    data class FollowSystemTheme(val enabled: Boolean) : SettingsAction
    data class HistoryRetention(val days: Int) : SettingsAction
    data class Language(val language: String) : SettingsAction
    data class Notifications(val enabled: Boolean) : SettingsAction
    data class WifiOnly(val enabled: Boolean) : SettingsAction
    data class Destination(val uri: String) : SettingsAction
}

internal fun extractDownloadLinks(input: String): List<String> =
    Regex("(?i)\\b(?:https?://|magnet:\\?)[^\\s<>\"']+")
        .findAll(input)
        .map { it.value.trimEnd(',', ';', '.', '!', ')', ']', '}') }
        .filter { it.length > 10 }
        .toList()
