package com.cyron.manager.download

import android.net.Uri
import com.cyron.manager.domain.model.ByteRange
import com.cyron.manager.domain.model.DownloadFailure
import com.cyron.manager.domain.model.DownloadResult
import com.cyron.manager.domain.model.DownloadStatus
import com.cyron.manager.domain.model.DownloadTask
import com.cyron.manager.domain.model.ProbeResult
import com.cyron.manager.domain.model.TransferSnapshot
import com.cyron.manager.storage.SafFileStore
import com.cyron.manager.storage.LocalStorageException
import com.cyron.manager.storage.StorageMessage
import com.cyron.manager.storage.hasEnoughStorage
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import okhttp3.Call
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/** RFC 9110 forbids a weak validator in If-Range. */
internal fun strongEtagForIfRange(etag: String?): String? =
    etag?.takeUnless { it.trimStart().startsWith("W/", ignoreCase = true) }

internal fun parseRetryAfterMillis(value: String?): Long? {
    val text = value?.trim()?.takeIf(String::isNotEmpty) ?: return null
    text.toLongOrNull()?.let { seconds ->
        return seconds.coerceIn(0L, 24 * 60 * 60L) * 1_000L
    }
    return runCatching {
        val instant = Instant.from(DateTimeFormatter.RFC_1123_DATE_TIME.parse(text))
        (instant.toEpochMilli() - System.currentTimeMillis()).coerceIn(0L, 24 * 60 * 60 * 1_000L)
    }.getOrNull() ?: runCatching {
        val instant = java.time.OffsetDateTime.parse(text, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant()
        (instant.toEpochMilli() - System.currentTimeMillis()).coerceIn(0L, 24 * 60 * 60 * 1_000L)
    }.getOrNull()
}

fun interface ConnectionCountProvider { fun configuredConnections(download: DownloadTask): Int }

/** A transfer failure the engine understands: its outcome, the user-facing message and whether another attempt can help. */
internal class EngineException(
    val failure: DownloadFailure,
    override val message: String,
    val retryable: Boolean,
    val retryAfterMillis: Long? = null
) : IOException(message)

/** Local storage failures never get better by resending the same HTTP request. */
internal fun isRetryableTransferFailure(error: IOException): Boolean = when (error) {
    is LocalStorageException -> false
    is EngineException -> error.retryable && (error.failure == DownloadFailure.NETWORK || error.failure == DownloadFailure.HTTP_ERROR)
    else -> true
}

/** Engine-level API. No Compose, Activity, or Service dependencies. */
interface DownloadEngine {
    fun snapshots(): Flow<List<TransferSnapshot>>
    suspend fun probe(url: String): ProbeResult
    suspend fun download(task: DownloadTask): DownloadResult
    suspend fun pause(id: String)
    suspend fun cancel(id: String)
    suspend fun release(id: String)
}

@Singleton
class OkHttpDownloadEngine @Inject constructor(
    private val client: OkHttpClient,
    private val files: SafFileStore,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val connectionCountProvider: ConnectionCountProvider,
    private val network: NetworkMonitor
) : DownloadEngine {
    private val running = ConcurrentHashMap<String, Control>()
    private val calls = ConcurrentHashMap<String, MutableSet<Call>>()
    private val controlsLock = Any()
    private val pauseBeforeStart = ConcurrentHashMap.newKeySet<String>()
    private val snapshotsState = MutableStateFlow<List<TransferSnapshot>>(emptyList())
    private val lastSnapshotAt = ConcurrentHashMap<String, Long>()
    override fun snapshots(): Flow<List<TransferSnapshot>> = snapshotsState.asStateFlow()

    override suspend fun probe(url: String): ProbeResult = withContext(dispatcher) { probeBlocking(url) }

    override suspend fun pause(id: String) {
        synchronized(controlsLock) {
            val control = running[id]
            if (control == null) pauseBeforeStart += id else control.paused.value = true
        }
        cancelCalls(id)
    }

    override suspend fun cancel(id: String) {
        synchronized(controlsLock) {
            pauseBeforeStart.remove(id)
            running[id]?.also { it.cancelled.value = true; it.paused.value = false }
        }
        cancelCalls(id)
    }

    override suspend fun release(id: String) {
        synchronized(controlsLock) {
            pauseBeforeStart.remove(id)
            running.remove(id)?.let { it.cancelled.value = true; it.paused.value = false }
        }
        cancelCalls(id)
        runCatching { files.deleteParts(id) }
        snapshotsState.value = snapshotsState.value.filterNot { it.downloadId == id }
        lastSnapshotAt.remove(id)
    }

    override suspend fun download(task: DownloadTask): DownloadResult = withContext(dispatcher) {
        if (!task.url.toHttpUrlOrNull().let { it != null && (it.scheme == "http" || it.scheme == "https") && it.username.isEmpty() && it.password.isEmpty() }) {
            return@withContext failure(task, DownloadFailure.INVALID_URL, "Enter a valid HTTP or HTTPS link without embedded credentials.", false)
        }
        val control = Control()
        val alreadyRunning = synchronized(controlsLock) {
            if (running.putIfAbsent(task.id, control) != null) {
                true
            } else {
                if (pauseBeforeStart.remove(task.id)) control.paused.value = true
                false
            }
        }
        if (alreadyRunning) return@withContext failure(task, DownloadFailure.NETWORK, "This download is already running.", true)
        var completed = false
        var activeProbe: ProbeResult? = null
        try {
            val target = task.savedUri?.let(Uri::parse)
                ?: return@withContext failure(task, DownloadFailure.STORAGE, "Choose a destination folder using the system folder picker.", true)
            // Check the SAF grant before spending the user's data on a transfer.
            files.ensureWritable(target)
            publish(task.id, 0, task.totalBytes, 0, DownloadStatus.PROBING)
            val probe = probeBlocking(task.url, task.id)
            activeProbe = probe
            val requested = connectionCountProvider.configuredConnections(task).coerceIn(1, 16)
            if (task.totalBytes > 0 && task.totalBytes != probe.totalBytes) throw EngineException(DownloadFailure.RESOURCE_CHANGED, "The remote file size changed since probing.", true)
            // A server can stop advertising Range support between attempts. Rather than
            // leaving the task permanently FAILED, discard the now-unusable segments and
            // perform one full single-connection transfer.
            if (task.rangeSupported && !probe.rangeSupported) files.invalidateParts(task.id)
            val storedStrongEtag = strongEtagForIfRange(task.etag)
            val validatorChanged = when {
                storedStrongEtag != null -> storedStrongEtag != strongEtagForIfRange(probe.etag)
                task.lastModified != null -> task.lastModified != probe.lastModified
                else -> false
            }
            if (validatorChanged) {
                files.invalidateParts(task.id)
                throw EngineException(DownloadFailure.RESOURCE_CHANGED, "The remote file changed since the original request. Unsafe partial data was discarded.", true)
            }
            val canSafelyResume = probe.hasStrongValidator
            if (!canSafelyResume && files.existingBytes(task.id, probe.totalBytes) > 0) files.invalidateParts(task.id)
            val canSafelyParallelize = probe.rangeSupported && canSafelyResume
            val plan = splitRange(probe.totalBytes, if (canSafelyParallelize) requested else 1)
            // prepare() may discard a stale partial download, so measure what is
            // actually reusable before deciding how much free space is required.
            files.prepare(task.id, probe.effectiveUrl, probe, plan)
            if (task.expectedSha256 != null && !task.expectedSha256.matches(Regex("[a-fA-F0-9]{64}"))) {
                return@withContext failure(task, DownloadFailure.INTEGRITY, "The SHA-256 checksum must contain exactly 64 hexadecimal characters.", false)
            }
            val already = plan.sumOf { files.partLength(task.id, it.index).coerceAtMost(it.length) }
            // The merge now streams directly into the SAF document, so only the
            // still-missing bytes need private storage. The old code also reserved
            // a full merged copy here, which doubled the space requirement.
            val remainingBytes = (probe.totalBytes - already).coerceAtLeast(0L)
            val stagingBytes = runCatching {
                Math.addExact(remainingBytes, STORAGE_HEADROOM_BYTES)
            }.getOrDefault(Long.MAX_VALUE)
            val usableBytes = files.usableBytes()
            if (!hasEnoughStorage(usableBytes, stagingBytes)) {
                throw EngineException(
                    DownloadFailure.STORAGE,
                    "Not enough free storage for this download (about ${stagingBytes / (1024 * 1024)} MB of temporary space is needed). Free some space and retry.",
                    false
                )
            }
            val count = AtomicLong(already)
            val speed = SpeedMeter(count)
            publish(task.id, count.get(), probe.totalBytes, 0, DownloadStatus.DOWNLOADING)
            coroutineScope {
                val semaphore = Semaphore(minOf(requested, plan.size).coerceAtLeast(1))
                plan.forEach { part ->
                    launch {
                        semaphore.withPermit {
                            transferWithResume(task, probe, part, plan.size, control, count, speed)
                        }
                    }
                }
            }
            currentCoroutineContext().ensureActive()
            if (control.cancelled.value) throw CancellationException("Cancelled")
            if (control.paused.value) throw CancellationException("Paused")
            publish(task.id, probe.totalBytes, probe.totalBytes, 0, DownloadStatus.PUBLISHING)
            val published = files.mergeAndPublish(task.id, plan, probe.totalBytes, task.expectedSha256, target, task.fileName, task.mimeType)
            publish(task.id, probe.totalBytes, probe.totalBytes, 0, DownloadStatus.COMPLETED)
            completed = true
            DownloadResult.Success(published.uri.toString(), published.sha256, probe.totalBytes)
        } catch (cancelled: CancellationException) {
            if (control.cancelled.value) {
                files.deleteParts(task.id)
                publish(task.id, 0, activeProbe?.totalBytes ?: -1, 0, DownloadStatus.CANCELLED)
                DownloadResult.Failure(DownloadFailure.CANCELLED, "Download cancelled.", false)
            } else {
                val probe = activeProbe
                publish(task.id, safePartBytes(task.id, probe), probe?.totalBytes ?: -1, 0, DownloadStatus.PAUSED)
                DownloadResult.Failure(DownloadFailure.CANCELLED, "Download paused; verified partial data is preserved.", true)
            }
        } catch (e: com.cyron.manager.storage.ChecksumMismatchException) {
            files.deleteParts(task.id)
            failure(task, DownloadFailure.INTEGRITY, "The file did not match its expected SHA-256 checksum. Incomplete data was discarded.", false)
        } catch (e: LocalStorageException) {
            // A full disk, an unreadable segment or a failing document provider is a storage problem.
            // Reporting it as a network error sends the user looking for the wrong cause.
            failure(task, DownloadFailure.STORAGE, e.message ?: StorageMessage.SEGMENT_WRITE_FAILED, e.retryable)
        } catch (e: EngineException) {
            failure(task, e.failure, e.message, e.retryable)
        } catch (e: IOException) {
            when {
                control.cancelled.value -> {
                    files.deleteParts(task.id)
                    publish(task.id, 0, activeProbe?.totalBytes ?: -1, 0, DownloadStatus.CANCELLED)
                    DownloadResult.Failure(DownloadFailure.CANCELLED, "Download cancelled.", false)
                }
                control.paused.value -> {
                    publish(task.id, safePartBytes(task.id, activeProbe), activeProbe?.totalBytes ?: -1, 0, DownloadStatus.PAUSED)
                    DownloadResult.Failure(DownloadFailure.CANCELLED, "Download paused; verified partial data is preserved.", true)
                }
                else -> failure(task, DownloadFailure.NETWORK, "Network error. Verified partial data has been preserved.", true)
            }
        } catch (e: SecurityException) {
            failure(task, DownloadFailure.STORAGE, "Storage access is unavailable. Choose the destination folder again.", true)
        } finally {
            calls.remove(task.id)
            synchronized(controlsLock) {
                running.remove(task.id, control)
                pauseBeforeStart.remove(task.id)
            }
            if (completed) runCatching { files.cleanupParts(task.id) }
        }
    }

    private suspend fun transferWithResume(
        task: DownloadTask,
        probe: ProbeResult,
        part: ByteRange,
        partCount: Int,
        control: Control,
        total: AtomicLong,
        speed: SpeedMeter
    ) {
        var attempt = 0
        while (true) {
            currentCoroutineContext().ensureActive()
            if (control.cancelled.value) throw CancellationException("Cancelled")
            if (control.paused.value) throw CancellationException("Paused")
            val existing = files.partLength(task.id, part.index)
            if (existing > part.length) {
                files.invalidateParts(task.id)
                throw EngineException(DownloadFailure.RESOURCE_CHANGED, "A saved segment exceeds the expected range. The incomplete download was discarded.", true)
            }
            if (existing == part.length) return
            val offset = existing
            val begin = part.start + offset
            val supportsRanges = partCount > 1 || probe.rangeSupported
            val builder = Request.Builder().get().header("Accept-Encoding", "identity").header("User-Agent", USER_AGENT)
            if (supportsRanges) builder.header("Range", "bytes=$begin-${part.endInclusive}")
            // RFC 9110: a weak validator cannot be used in If-Range. Sending one
            // makes some servers ignore Range and return the whole file, which
            // then looks like a misleading multi-connection failure.
            val strongEtag = strongEtagForIfRange(probe.etag)
            if (strongEtag != null) builder.header("If-Range", strongEtag)
            else if (probe.lastModified != null) builder.header("If-Range", probe.lastModified)
            val call = client.newCall(builder.url(probe.effectiveUrl).build())
            calls.computeIfAbsent(task.id) { ConcurrentHashMap.newKeySet() }.add(call)
            try {
                call.execute().use { response ->
                    validateRemoteVersion(probe, response.header("ETag"), response.header("Last-Modified"))
                    if (!response.isSuccessful) throw httpException(response.code, response.header("Retry-After"))
                    if (supportsRanges && partCount > 1) {
                        if (response.code != 206) {
                            files.invalidateParts(task.id)
                            throw EngineException(DownloadFailure.RESOURCE_CHANGED, "The server no longer honours Range requests; partial files were discarded.", true)
                        }
                        validateContentRange(response.header("Content-Range"), begin, part.endInclusive, probe.totalBytes)
                    } else if (partCount == 1 && probe.rangeSupported) {
                        if (offset > 0 && response.code != 206) {
                            files.truncatePart(task.id, part.index)
                            total.addAndGet(-offset)
                            throw EngineException(DownloadFailure.RESOURCE_CHANGED, "The server did not honour the resume request. Retry to safely restart this segment.", true)
                        }
                        if (response.code == 206) validateContentRange(response.header("Content-Range"), begin, part.endInclusive, probe.totalBytes)
                    } else if (offset > 0) {
                        files.truncatePart(task.id, part.index)
                        total.addAndGet(-offset)
                        throw EngineException(DownloadFailure.RESOURCE_CHANGED, "This server does not support resuming partial downloads. Retry to start again safely.", true)
                    }
                    val body = response.body ?: throw EngineException(DownloadFailure.NETWORK, "The server returned an empty response.", true)
                    val expectedResponseBytes = part.length - offset
                    val contentLength = body.contentLength()
                    if (contentLength >= 0 && contentLength != expectedResponseBytes) {
                        files.invalidateParts(task.id)
                        throw EngineException(DownloadFailure.RESOURCE_CHANGED, "The server response size changed; incomplete data was discarded.", true)
                    }
                    privateStorage { files.openPart(task.id, part.index, offset) }.use { output ->
                        body.byteStream().use { input ->
                            val buffer = ByteArray(32 * 1024)
                            var written = offset
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                if (control.cancelled.value) throw CancellationException("Cancelled")
                                if (control.paused.value) {
                                    output.fd.sync()
                                    call.cancel()
                                    throw CancellationException("Paused safely; resume will revalidate HTTP entity tags.")
                                }
                                val read = input.read(buffer)
                                if (read == -1) break
                                if (written + read > part.length) {
                                    files.invalidateParts(task.id)
                                    throw EngineException(DownloadFailure.RESOURCE_CHANGED, "The server sent data beyond the verified byte range.", true)
                                }
                                privateStorage { files.appendPart(output, buffer, read) }
                                written += read
                                total.addAndGet(read.toLong())
                                val currentSpeed = speed.sample()
                                publish(task.id, total.get(), probe.totalBytes, currentSpeed, DownloadStatus.DOWNLOADING)
                            }
                            privateStorage { output.fd.sync() }
                            if (written != part.length) throw IOException("The server closed the connection before this byte range finished.")
                        }
                    }
                }
                return
            } catch (e: IOException) {
                if (control.cancelled.value) throw CancellationException("Cancelled")
                if (control.paused.value) throw CancellationException("Paused safely; resume will revalidate HTTP entity tags.")
                if (call.isCanceled()) {
                    currentCoroutineContext().ensureActive()
                    continue
                }
                val retryable = isRetryableTransferFailure(e)
                if (!retryable || attempt >= MAX_TRANSFER_RETRIES) throw e
                attempt++
                // A dropped Wi-Fi/mobile handover should wait for a real network, not fail the file.
                // Poll in short steps so pause/cancel stay responsive while waiting.
                var waited = 0L
                while (waited < NETWORK_WAIT_MILLIS && !network.isOnline()) {
                    if (control.cancelled.value) throw CancellationException("Cancelled")
                    if (control.paused.value) throw CancellationException("Paused")
                    currentCoroutineContext().ensureActive()
                    delay(NETWORK_POLL_MILLIS)
                    waited += NETWORK_POLL_MILLIS
                }
                val serverDelay = (e as? EngineException)?.retryAfterMillis
                pauseAwareDelay(control, maxOf(backoffMillis(attempt), serverDelay ?: 0L))
                continue
            } finally {
                calls[task.id]?.remove(call)
            }
        }
    }

    private fun <T> executeProbe(downloadId: String?, request: Request, consume: (okhttp3.Response) -> T): T {
        val call = client.newCall(request)
        if (downloadId != null) {
            calls.computeIfAbsent(downloadId) { ConcurrentHashMap.newKeySet() }.add(call)
            val control = running[downloadId]
            if (control?.paused?.value == true || control?.cancelled?.value == true) call.cancel()
        }
        return try {
            call.execute().use(consume)
        } finally {
            if (downloadId != null) calls[downloadId]?.remove(call)
        }
    }

    private suspend fun probeBlocking(url: String, downloadId: String? = null): ProbeResult {
        val parsed = url.toHttpUrlOrNull()
            ?: throw EngineException(DownloadFailure.INVALID_URL, "Enter a valid HTTP or HTTPS link.", false)
        if ((parsed.scheme != "http" && parsed.scheme != "https") || parsed.host.isBlank() || parsed.username.isNotEmpty() || parsed.password.isNotEmpty()) {
            throw EngineException(DownloadFailure.INVALID_URL, "Only HTTP and HTTPS links without embedded credentials are supported.", false)
        }
        var length = -1L
        var etag: String? = null
        var lastModified: String? = null
        var mime = "application/octet-stream"
        var suggestedName: String? = null
        var effectiveUrl = url
        executeProbe(downloadId, Request.Builder().url(parsed).head().header("Accept-Encoding", "identity").header("User-Agent", USER_AGENT).build()) { response ->
            effectiveUrl = response.request.url.toString()
            if (response.isSuccessful) {
                length = response.header("Content-Length")?.toLongOrNull()?.takeIf { it > 0 } ?: -1L
                etag = response.header("ETag")
                lastModified = response.header("Last-Modified")
                mime = response.header("Content-Type")?.substringBefore(';')?.trim()?.ifBlank { mime } ?: mime
                suggestedName = dispositionFileName(response.header("Content-Disposition")) ?: suggestedName
            } else if (response.code !in setOf(405, 501, 403)) {
                throw httpException(response.code, response.header("Retry-After"))
            }
        }
        val probeRequest = Request.Builder().url(effectiveUrl).get()
            .header("Range", "bytes=0-0").header("Accept-Encoding", "identity").header("User-Agent", USER_AGENT).build()
        var rangedResult: ProbeResult? = null
        executeProbe(downloadId, probeRequest) { response ->
            effectiveUrl = response.request.url.toString()
            if (response.code == 206) {
                val parsedRange = CONTENT_RANGE.matchEntire(response.header("Content-Range").orEmpty())
                    ?: throw EngineException(DownloadFailure.NETWORK, "The server returned an invalid Content-Range.", true)
                if (parsedRange.groupValues[1].toLong() != 0L || parsedRange.groupValues[2].toLong() != 0L) {
                    throw EngineException(DownloadFailure.NETWORK, "The range-support probe returned unexpected bytes.", true)
                }
                val total = parsedRange.groupValues[3].toLongOrNull()
                    ?: throw EngineException(DownloadFailure.NETWORK, "The remote file size is unknown.", false)
                if (total <= 0) throw EngineException(DownloadFailure.NETWORK, "The remote file is empty or its size is unknown.", false)
                if (length > 0 && length != total) throw EngineException(DownloadFailure.RESOURCE_CHANGED, "The server reported inconsistent resource lengths while probing.", true)
                val body = response.body ?: throw EngineException(DownloadFailure.NETWORK, "The Range probe has no response body.", true)
                if (body.byteStream().read() == -1) throw EngineException(DownloadFailure.NETWORK, "The Range probe returned no file data.", true)
                length = total
                etag = response.header("ETag")
                lastModified = response.header("Last-Modified")
                mime = response.header("Content-Type")?.substringBefore(';')?.trim()?.ifBlank { mime } ?: mime
                suggestedName = dispositionFileName(response.header("Content-Disposition")) ?: suggestedName
                rangedResult = ProbeResult(effectiveUrl, length, true, etag, lastModified, mime, suggestedName)
            } else {
            if (!response.isSuccessful) throw httpException(response.code, response.header("Retry-After"))
            val responseLength = response.header("Content-Length")?.toLongOrNull()?.takeIf { it > 0 } ?: -1L
            if (length > 0 && responseLength > 0 && length != responseLength) throw EngineException(DownloadFailure.RESOURCE_CHANGED, "The server reported inconsistent resource lengths while probing.", true)
            if (length <= 0) length = responseLength
            etag = response.header("ETag")
            lastModified = response.header("Last-Modified")
            mime = response.header("Content-Type")?.substringBefore(';')?.trim()?.ifBlank { mime } ?: mime
            suggestedName = dispositionFileName(response.header("Content-Disposition")) ?: suggestedName
            }
        }
        rangedResult?.let { return it }
        if (length <= 0) throw EngineException(DownloadFailure.NETWORK, "The server did not report a usable Content-Length; downloads of unknown-size streams are intentionally declined.", false)
        return ProbeResult(effectiveUrl, length, false, etag, lastModified, mime, suggestedName)
    }

    private fun dispositionFileName(header: String?): String? {
        if (header.isNullOrBlank()) return null
        val extended = Regex("""filename\*\s*=\s*[^']*'[^']*'([^;]+)""", RegexOption.IGNORE_CASE)
            .find(header)?.groupValues?.get(1)
        val plain = Regex("""filename\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
            .find(header)?.groupValues?.get(1)
            ?: Regex("""filename\s*=\s*([^;]+)""", RegexOption.IGNORE_CASE)
                .find(header)?.groupValues?.get(1)?.trim()?.trim('"', '\'')
        val raw = extended ?: plain ?: return null
        // URLDecoder treats '+' as a space (form encoding), but a filename is a
        // URI path component where '+' is a literal plus sign.
        return runCatching { java.net.URLDecoder.decode(raw.trim().trim('"').replace("+", "%2B"), "UTF-8") }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    private fun validateRemoteVersion(probe: ProbeResult, etag: String?, lastModified: String?) {
        val strongEtag = strongEtagForIfRange(probe.etag)
        if (strongEtag != null && strongEtag != strongEtagForIfRange(etag)) {
            throw EngineException(DownloadFailure.RESOURCE_CHANGED, "The remote ETag changed. Previously downloaded segments were not merged.", true)
        }
        if (strongEtag == null && probe.lastModified != null && probe.lastModified != lastModified) {
            throw EngineException(DownloadFailure.RESOURCE_CHANGED, "The remote Last-Modified changed. Previously downloaded segments were not merged.", true)
        }
    }

    private fun validateContentRange(value: String?, expectedStart: Long, expectedEnd: Long, expectedTotal: Long) {
        val parsed = CONTENT_RANGE.matchEntire(value.orEmpty())
            ?: throw EngineException(DownloadFailure.RESOURCE_CHANGED, "The server returned a missing or malformed Content-Range.", true)
        if (parsed.groupValues[1].toLongOrNull() != expectedStart ||
            parsed.groupValues[2].toLongOrNull() != expectedEnd ||
            parsed.groupValues[3].toLongOrNull() != expectedTotal
        ) throw EngineException(DownloadFailure.RESOURCE_CHANGED, "The server changed the resource or returned a mismatched byte range. Segments were not merged.", true)
    }

    private fun httpException(code: Int, retryAfter: String?): EngineException {
        val retryable = code == 408 || code == 425 || code == 429 || code >= 500
        return EngineException(
            DownloadFailure.HTTP_ERROR,
            "Server returned HTTP $code.",
            retryable,
            parseRetryAfterMillis(retryAfter)
        )
    }

    private suspend fun failure(task: DownloadTask, type: DownloadFailure, message: String, retryable: Boolean): DownloadResult.Failure {
        val current = snapshotsState.value.firstOrNull { it.downloadId == task.id }
        publish(task.id, current?.downloadedBytes ?: 0, current?.totalBytes ?: task.totalBytes, 0, DownloadStatus.FAILED, message)
        return DownloadResult.Failure(type, message, retryable)
    }

    private suspend fun safePartBytes(id: String, probe: ProbeResult?): Long = probe?.let { plan ->
        runCatching { files.existingBytes(id, plan.totalBytes) }.getOrDefault(0L)
    } ?: 0L

    private fun publish(id: String, downloaded: Long, total: Long, speed: Long, status: DownloadStatus, error: String? = null) {
        val now = System.nanoTime()
        val previous = snapshotsState.value.firstOrNull { it.downloadId == id }
        if (status == DownloadStatus.DOWNLOADING && previous?.status == status && now - (lastSnapshotAt[id] ?: 0L) < 250_000_000L) return
        lastSnapshotAt[id] = now
        val snapshot = TransferSnapshot(id, downloaded.coerceAtLeast(0), total, speed.coerceAtLeast(0), status, error)
        snapshotsState.value = snapshotsState.value.filterNot { it.downloadId == id } + snapshot
    }

    private fun cancelCalls(id: String) {
        calls.remove(id)?.forEach { it.cancel() }
    }

    private class Control(
        val paused: MutableStateFlow<Boolean> = MutableStateFlow(false),
        val cancelled: MutableStateFlow<Boolean> = MutableStateFlow(false)
    )

    /** Creating, writing or syncing a private segment is local storage; a failure there is never a network error. */
    private inline fun <T> privateStorage(block: () -> T): T = try {
        block()
    } catch (e: IOException) {
        throw e.asLocalStorageFailure()
    }

    private fun IOException.asLocalStorageFailure(): LocalStorageException =
        this as? LocalStorageException ?: LocalStorageException(StorageMessage.SEGMENT_WRITE_FAILED, this)

    private suspend fun pauseAwareDelay(control: Control, millis: Long) {
        var remaining = millis
        while (remaining > 0) {
            if (control.cancelled.value) throw CancellationException("Cancelled")
            if (control.paused.value) throw CancellationException("Paused")
            val step = minOf(remaining, 250L)
            delay(step)
            remaining -= step
        }
    }

    private class SpeedMeter(private val bytes: AtomicLong) {
        private val lock = Mutex()
        private var sampledAt = System.nanoTime()
        private var sampledBytes = bytes.get()
        @Volatile var bytesPerSecond: Long = 0
            private set

        suspend fun sample(): Long = lock.withLock {
            val now = System.nanoTime()
            val elapsed = now - sampledAt
            // Several connections report chunks independently; keep a stable ~5 Hz reading.
            if (elapsed < 200_000_000L) return@withLock bytesPerSecond
            val current = bytes.get()
            val delta = (current - sampledBytes).coerceAtLeast(0)
            bytesPerSecond = delta * 1_000_000_000L / elapsed.coerceAtLeast(1)
            sampledAt = now
            sampledBytes = current
            bytesPerSecond
        }
    }

    companion object {
        private val CONTENT_RANGE = Regex("bytes (\\d+)-(\\d+)/(\\d+)")
        private const val STORAGE_HEADROOM_BYTES = 64L * 1024 * 1024
        // A regular browser user agent avoids hosts that reject OkHttp's default one.
        private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36 CyronManager/1.0"
        private const val MAX_TRANSFER_RETRIES = 8
        private const val NETWORK_WAIT_MILLIS = 20_000L
        private const val NETWORK_POLL_MILLIS = 1_000L

        private fun backoffMillis(attempt: Int): Long =
            minOf(30_000L, 1_000L shl (attempt - 1).coerceIn(0, 5))
        fun splitRange(totalBytes: Long, requested: Int): List<ByteRange> {
            require(totalBytes > 0) { "File size must be known and positive before splitting." }
            require(requested in 1..16) { "Connections must be between 1 and 16." }
            val count = minOf(totalBytes, requested.toLong()).toInt()
            val quotient = totalBytes / count
            val remainder = totalBytes % count
            var start = 0L
            return List(count) { index ->
                val length = quotient + if (index < remainder) 1 else 0
                ByteRange(index, start, start + length - 1).also { start += length }
            }.also { ranges -> check(ranges.first().start == 0L && ranges.last().endInclusive == totalBytes - 1) }
        }
    }
}
