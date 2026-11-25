package com.cyron.manager.domain.model

import java.util.UUID

enum class DownloadStatus { QUEUED, PROBING, DOWNLOADING, PUBLISHING, PAUSED, COMPLETED, FAILED, CANCELLED }
enum class DownloadSource { HTTP, MEDIA, TORRENT }

data class DownloadTask(
    val id: String = UUID.randomUUID().toString(),
    val url: String,
    val fileName: String,
    val mimeType: String = "application/octet-stream",
    val totalBytes: Long = -1,
    val rangeSupported: Boolean = false,
    val downloadedBytes: Long = 0,
    val displayName: String? = null,
    val outputFolderUri: String? = null,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val connectionCount: Int = 1,
    val priority: Int = 0,
    val savedUri: String? = null,
    /** Every file published for this task. Multi-file torrents have more than one. */
    val publishedUris: List<String> = emptyList(),
    val expectedSha256: String? = null,
    val actualSha256: String? = null,
    val etag: String? = null,
    val lastModified: String? = null,
    val source: DownloadSource = DownloadSource.HTTP,
    val formatSelector: String? = null,
    val error: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    val progress: Float
        get() = if (totalBytes <= 0) 0f else (downloadedBytes.toDouble() / totalBytes).coerceIn(0.0, 1.0).toFloat()

    init {
        require(id.isNotBlank()) { "Download ID is required." }
        require(fileName.isNotBlank()) { "Filename is required." }
        require(totalBytes >= -1) { "File size cannot be less than -1." }
        require(downloadedBytes >= 0) { "Downloaded bytes cannot be negative." }
        require(connectionCount in 1..16) { "Connection count must be between 1 and 16." }
    }
}

/**
 * A user-requested retry starts from a fresh probe. The stored validators and size describe the
 * representation seen when the task was created; keeping them made "Retry" fail forever as soon as the
 * remote file changed. Saved segments stay bound to the old validators in their manifest, so the engine
 * still discards them when the representation really differs.
 */
fun DownloadTask.resetForRetry(now: Long = System.currentTimeMillis()): DownloadTask = copy(
    status = DownloadStatus.QUEUED,
    error = null,
    downloadedBytes = 0,
    totalBytes = -1,
    etag = null,
    lastModified = null,
    updatedAt = now
)

data class TransferSnapshot(
    val downloadId: String,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val bytesPerSecond: Long,
    val status: DownloadStatus,
    val error: String? = null,
    /** Real progress when the source reports a percentage but no byte total (media streams). */
    val fraction: Float? = null
) {
    val progress: Float
        get() = fraction?.coerceIn(0f, 1f)
            ?: if (totalBytes <= 0) 0f else (downloadedBytes.toDouble() / totalBytes).coerceIn(0.0, 1.0).toFloat()
    val remainingMillis: Long?
        get() = if (bytesPerSecond <= 0 || totalBytes <= 0) null else ((totalBytes - downloadedBytes).coerceAtLeast(0) * 1000 / bytesPerSecond)
}

/**
 * Room is the durable source of truth. An in-memory snapshot may still describe the
 * previous run (especially after Retry), so it must not turn a QUEUED task into
 * "failed", a queued media task into "downloading", or a completed task back into
 * an active transfer. Only an actually running row may take its live status.
 */
fun DownloadTask.visibleStatus(snapshot: TransferSnapshot?): DownloadStatus {
    if (snapshot == null) return status
    return when (status) {
        DownloadStatus.DOWNLOADING, DownloadStatus.PROBING, DownloadStatus.PUBLISHING -> snapshot.status
        else -> status
    }
}

fun DownloadTask.visibleProgress(snapshot: TransferSnapshot?): Float {
    val live = status == DownloadStatus.DOWNLOADING || status == DownloadStatus.PROBING || status == DownloadStatus.PUBLISHING
    return if (live) snapshot?.progress ?: progress else progress
}

data class ProbeResult(
    val effectiveUrl: String,
    val totalBytes: Long,
    val rangeSupported: Boolean,
    val etag: String?,
    val lastModified: String?,
    val mimeType: String,
    val suggestedName: String? = null
) {
    val hasStrongValidator: Boolean
        get() = !etag.isNullOrBlank() && !etag.trimStart().startsWith("W/", ignoreCase = true) || !lastModified.isNullOrBlank()
}

data class ByteRange(val index: Int, val start: Long, val endInclusive: Long) {
    val length: Long get() = endInclusive - start + 1
    init { require(index >= 0 && start >= 0 && endInclusive >= start) }
}

data class DownloadSettings(
    val maxConcurrentDownloads: Int = 3,
    val defaultConnections: Int = 8,
    val darkTheme: Boolean = true,
    val followSystemTheme: Boolean = false,
    val notificationsEnabled: Boolean = true,
    val wifiOnly: Boolean = false,
    val historyRetentionDays: Int = 30,
    val defaultFolderUri: String? = null,
    val language: String = "en"
)

data class DashboardStats(
    val totalDownloadedBytes: Long = 0,
    val activeCount: Int = 0,
    val completedToday: Int = 0,
    val currentSpeedBytesPerSecond: Long = 0,
    val dailyBytes: List<Long> = emptyList()
)

sealed interface DownloadResult {
    data class Success(val savedUri: String, val sha256: String, val totalBytes: Long = -1) : DownloadResult
    data class Failure(val reason: DownloadFailure, val message: String, val retryable: Boolean) : DownloadResult
}

enum class DownloadFailure { INVALID_URL, HTTP_ERROR, NETWORK, STORAGE, INTEGRITY, RESOURCE_CHANGED, CANCELLED }

object DownloadName {
    private val invalid = Regex("[\\\\/:*?\"<>|\\u0000-\\u001f]")

    fun sanitize(input: String): String {
        val leaf = input.trim().substringAfterLast('/').substringAfterLast('\\')
        val clean = leaf.replace(invalid, "_").trimStart('.', ' ').trimEnd('.', ' ')
        val reserved = clean.substringBefore('.').uppercase(java.util.Locale.ROOT)
        val safe = if (reserved in setOf("CON", "PRN", "AUX", "NUL") || Regex("COM[1-9]|LPT[1-9]").matches(reserved)) "_$clean" else clean
        return safe.take(180).ifBlank { "download.bin" }
    }
}
