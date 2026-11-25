package com.cyron.manager.media

import android.content.Context
import android.net.Uri
import android.util.Log
import android.webkit.MimeTypeMap
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegSession
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import com.arthenica.ffmpegkit.ReturnCode
import com.arthenica.ffmpegkit.SessionState
import com.cyron.manager.domain.model.DownloadName
import com.cyron.manager.storage.SafFileStore
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.ffmpegkit_maintained.ytdlp.DownloadProgressCallback
import dev.ffmpegkit_maintained.ytdlp.LogCallback
import dev.ffmpegkit_maintained.ytdlp.YtDlp
import dev.ffmpegkit_maintained.ytdlp.YtDlpRequest
import dev.ffmpegkit_maintained.ytdlp.YtDlpResponse
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * yt-dlp wrapper.
 *
 * The bundled Java runner only maps a small allow-list of CLI flags and discards stdout.
 * Metadata is therefore read by calling the bundled `yt_dlp` Python module directly,
 * which avoids the old "inspect" path silently downloading the whole video.
 *
 * The free yt-dlp build has no ffmpeg executable, so `bestvideo+bestaudio` cannot be merged
 * by yt-dlp itself. Separate streams are downloaded here and muxed with FFmpegKit.
 *
 * It intentionally does not add DRM circumvention, browser impersonation, or auth-cookie support.
 */
@Singleton
class MediaDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fileStore: SafFileStore
) {
    private val initialized = AtomicBoolean(false)
    private val initializationLock = Any()
    private val activeFutures = java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.Future<*>>()
    private val activeConversions = java.util.concurrent.ConcurrentHashMap<String, FFmpegSession>()
    private val activeHelpers = java.util.concurrent.ConcurrentHashMap<String, PyObject>()
    private val helperLock = Any()
    @Volatile private var helperLoaded = false

    /**
     * Reads real metadata straight from the bundled yt-dlp Python module. The Java
     * runner only allow-lists a few CLI flags and drops `-J`, so asking it to dump
     * JSON silently downloaded the whole video instead. Calling Python directly gives
     * the title and every available format without touching the network media stream.
     */
    suspend fun inspect(url: String): MediaInfo = withContext(Dispatchers.IO) {
        require(url.startsWith("https://", true) || url.startsWith("http://", true)) { "Enter an HTTP or HTTPS media page." }
        initialize()
        val json = try {
            // A Kotlin Map reaches Python as a java.util.HashMap proxy, whose .get()
            // takes one argument; yt-dlp calls opts.get(key, default) and crashed.
            // Passing JSON and parsing it inside Python keeps every option a real dict.
            helperModule().callAttr("cyron_inspect", url, INSPECT_OPTIONS_JSON).toString()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            throw IllegalStateException(mediaError(listOf("ERROR" to (error.message ?: "")), "This media page could not be inspected. The site may be unsupported or may restrict access."))
        }
        val info = MediaFormatChoice.parseInfo(json, fallbackTitle(url))
        info.copy(title = DownloadName.sanitize(info.title))
    }

    private fun fallbackTitle(url: String): String =
        url.substringBefore('?').substringAfterLast('/').substringBeforeLast('.').ifBlank { Uri.parse(url).host ?: "media" }

    fun choiceFromSelector(selector: String?): MediaFormatChoice = MediaFormatChoice.fromSelector(selector)

    suspend fun download(
        id: String,
        url: String,
        title: String,
        format: MediaFormatChoice,
        destination: String,
        connections: Int = DEFAULT_MEDIA_CONNECTIONS,
        onPublishing: suspend () -> Unit = {},
        onProgress: (MediaProgress) -> Unit
    ): MediaDownloadResult = withContext(Dispatchers.IO) {
        require(url.startsWith("https://", true) || url.startsWith("http://", true))
        val treeUri = Uri.parse(destination)
        require(treeUri.scheme == "content") { "Select a destination folder in Android's folder picker." }
        val connectionCount = normalizeMediaConnections(connections)
        initialize()
        fileStore.ensureWritable(treeUri)
        val directory = mediaDirectory().apply { if (!exists() && !mkdirs()) error("Could not prepare private media storage.") }
        val prefix = mediaPrefix(id)
        val selected = format.normalized()

        var mediaFile: File = when {
            selected.audioOnly -> {
                val audio = runYtDlp(id, url, directory, "$prefix-audio", "bestaudio/best", 0f, 0.97f, connectionCount, onProgress)
                if (audio.extension.lowercase() in VIDEO_EXTENSIONS) {
                    val extracted = File(directory, "$prefix-extracted.m4a")
                    // Copy the codec when the container allows it. Re-encoding a
                    // perfectly good AAC/Opus stream only loses quality and time.
                    val copied = runFfmpeg(id, arrayOf("-y", "-i", audio.absolutePath, "-vn", "-c:a", "copy", extracted.absolutePath))
                    if (copied && extracted.isFile && extracted.length() > 0) {
                        audio.delete()
                        extracted
                    } else if (runFfmpeg(id, arrayOf("-y", "-i", audio.absolutePath, "-vn", "-c:a", "aac", "-b:a", "192k", extracted.absolutePath)) && extracted.isFile && extracted.length() > 0) {
                        audio.delete()
                        extracted
                    } else {
                        throw IllegalStateException("Audio extraction failed. The downloaded source has been retained in private app storage.")
                    }
                } else {
                    audio
                }
            }
            selected.videoSelector != null -> downloadMerged(id, url, directory, prefix, selected, connectionCount, onProgress)
            else -> runYtDlp(id, url, directory, "$prefix-single", selected.selector, 0f, 1f, connectionCount, onProgress)
        }

        if (selected.convertToMp3) {
            val mp3 = File(directory, "$prefix-converted.mp3")
            if (!runFfmpeg(id, arrayOf("-y", "-i", mediaFile.absolutePath, "-vn", "-codec:a", "libmp3lame", "-q:a", "2", mp3.absolutePath)) || !mp3.isFile || mp3.length() == 0L) {
                throw IllegalStateException("Audio conversion failed. The downloaded source has been retained in private app storage.")
            }
            mediaFile.delete()
            mediaFile = mp3
        }

        onProgress(MediaProgress(1f))
        val safeTitle = DownloadName.sanitize(title)
        val extension = if (selected.convertToMp3) "mp3" else mediaFile.extension.takeIf { it.matches(Regex("[A-Za-z0-9]{1,8}")) } ?: if (selected.audioOnly) "m4a" else "mp4"
        val finalName = DownloadName.sanitize("$safeTitle.$extension")
        val hash = fileStore.sha256(mediaFile)
        val mimeType = resolveMimeType(extension, selected)
        // commit() deletes the private staging file after publishing it, so the size must be
        // captured before publication or completed media downloads report 0 bytes.
        val publishedBytes = mediaFile.length()
        onPublishing()
        val published = fileStore.commit(id, mediaFile, treeUri, finalName, mimeType)
        MediaDownloadResult(published.toString(), finalName, publishedBytes, hash, mimeType)
    }

    /**
     * Downloads a video-only stream plus an audio-only stream and muxes them locally.
     *
     * The two streams are separate files, so they are fetched at the same time instead of one
     * after the other: the audio no longer adds its own wait to the total time, and each stream
     * keeps its own `.part` file so a paused download resumes from the bytes already on disk.
     * Any failure falls back to a progressive single-file format.
     */
    private suspend fun downloadMerged(
        id: String,
        url: String,
        directory: File,
        prefix: String,
        selected: MediaFormatChoice,
        connections: Int,
        onProgress: (MediaProgress) -> Unit
    ): File = coroutineScope {
        val reporter = MergedMediaProgress(onProgress)
        val videoJob = async(Dispatchers.IO) {
            try {
                attempt {
                    runYtDlp(
                        id, url, directory, "$prefix-video", selected.videoSelector!!,
                        0f, 1f, connections, reporter::onVideo, STREAM_VIDEO
                    )
                }
            } finally {
                reporter.finishVideo()
            }
        }
        // Retry only the missing audio stream. Falling back to a single-file
        // download here used to re-download the whole video and could silently
        // publish a video-only file when the progressive selector had no audio.
        val audioJob = async(Dispatchers.IO) {
            try {
                attempt {
                    runYtDlp(id, url, directory, "$prefix-audio", "bestaudio", 0f, 1f, connections, reporter::onAudio, STREAM_AUDIO)
                } ?: attempt {
                    runYtDlp(id, url, directory, "$prefix-audio", "bestaudio[ext=m4a]/bestaudio", 0f, 1f, connections, reporter::onAudio, STREAM_AUDIO)
                }
            } finally {
                reporter.finishAudio()
            }
        }

        val video = try {
            videoJob.await()
        } catch (cancelled: CancellationException) {
            // A pause stops the other stream too; neither .part file is deleted, so the next
            // attempt continues from the bytes that are already on disk.
            cancelStreamHelpers(id, STREAM_AUDIO)
            throw cancelled
        }
        if (video == null) {
            // Stop the audio before the progressive fallback starts, so two downloads never
            // compete for the same connection.
            cancelStreamHelpers(id, STREAM_AUDIO)
            audioJob.await()
            return@coroutineScope fallbackSingle(id, url, directory, prefix, selected, connections, onProgress)
        }

        val audio = audioJob.await() ?: run {
            video.delete()
            throw IllegalStateException("The video downloaded, but its audio stream could not be downloaded. Choose another quality and try again.")
        }
        reporter.finishAll()

        val output = attempt { merge(id, video, audio, directory, prefix) }
            ?: run {
                video.delete()
                audio.delete()
                throw IllegalStateException("The video and audio streams downloaded, but they could not be merged. Try another quality.")
            }

        video.delete()
        audio.delete()
        output
    }

    private suspend fun fallbackSingle(
        id: String,
        url: String,
        directory: File,
        prefix: String,
        selected: MediaFormatChoice,
        connections: Int,
        onProgress: (MediaProgress) -> Unit
    ): File = runYtDlp(id, url, directory, "$prefix-single", selected.fallbackSelector ?: "best[vcodec!=none][acodec!=none]", 0f, 1f, connections, onProgress)

    private suspend fun merge(id: String, video: File, audio: File, directory: File, prefix: String): File? {
        val videoExtension = video.extension.lowercase()
        val audioExtension = audio.extension.lowercase()
        val container = when {
            videoExtension in MP4_VIDEO_EXTENSIONS && audioExtension in MP4_AUDIO_EXTENSIONS -> "mp4"
            videoExtension == "webm" && audioExtension in WEBM_AUDIO_EXTENSIONS -> "webm"
            else -> "mkv"
        }
        val output = File(directory, "$prefix-merged.$container")
        val args = mutableListOf(
            "-y", "-i", video.absolutePath, "-i", audio.absolutePath,
            "-map", "0:v:0", "-map", "1:a:0", "-c", "copy"
        )
        if (container == "mp4") args += listOf("-movflags", "+faststart")
        args += output.absolutePath
        if (runFfmpeg(id, args.toTypedArray()) && output.isFile && output.length() > 0) return output

        // Some providers label streams with an incompatible container. A single
        // AAC/MP4 retry keeps the video stream untouched and is widely playable.
        if (videoExtension in MP4_VIDEO_EXTENSIONS) {
            val retry = File(directory, "$prefix-merged.mp4")
            val retryArgs = arrayOf(
                "-y", "-i", video.absolutePath, "-i", audio.absolutePath,
                "-map", "0:v:0", "-map", "1:a:0",
                "-c:v", "copy", "-c:a", "aac", "-b:a", "192k",
                "-movflags", "+faststart", retry.absolutePath
            )
            if (runFfmpeg(id, retryArgs) && retry.isFile && retry.length() > 0) return retry
        }
        return null
    }

    private suspend fun runYtDlp(
        id: String,
        url: String,
        directory: File,
        outputPrefix: String,
        selector: String,
        startFraction: Float,
        endFraction: Float,
        connections: Int,
        onProgress: (MediaProgress) -> Unit,
        stream: String = STREAM_MAIN
    ): File {
        // YouTube refuses media URLs extracted with an obsolete client. The bundled
        // yt-dlp wheel is current and normally chooses its JS-free client, but a
        // client/format may still be rejected for a specific video. Retry the same
        // selector with supported clients instead of failing the task. No cookies,
        // account, DRM bypass, or browser impersonation is involved.
        val candidates = if (isYouTubeUrl(url)) YOUTUBE_EXTRACTOR_ARG_CANDIDATES else listOf(null)
        var directError: Exception? = null
        for ((index, extractorArgs) in candidates.withIndex()) {
            // Only a retry with a different player client throws the staging files away. Keeping
            // them on the first attempt is what lets a paused YouTube download resume from the
            // bytes already on disk instead of going back to zero.
            if (index > 0) clearStagedOutput(directory, outputPrefix)
            try {
                return runYtDlpDirect(
                    id, url, directory, outputPrefix, selector, startFraction, endFraction, connections, onProgress,
                    extractorArgs, helperKey(id, stream)
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                directError = error
                if (index == candidates.lastIndex || !isClientRetryable(error)) break
            }
        }
        // The Java runner cannot pass extractor args, but it is still useful for
        // non-YouTube sites that do not need a specific player client.
        if (!isYouTubeUrl(url)) {
            try {
                return runYtDlpViaRunner(id, url, directory, outputPrefix, selector, startFraction, endFraction, onProgress)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Keep the actionable direct error.
            }
        }
        throw directError ?: IllegalStateException("The media download did not complete.")
    }

    private fun isClientRetryable(error: Exception): Boolean {
        val text = error.message.orEmpty()
        return Regex("403|forbidden|requested format is not available|selected quality is not available|not available for this link|unable to extract|player response|not a bot|sign in to confirm", RegexOption.IGNORE_CASE).containsMatchIn(text)
    }

    private fun clearStagedOutput(directory: File, outputPrefix: String) {
        runCatching { directory.listFiles()?.filter { it.name.startsWith(outputPrefix) }?.forEach { it.delete() } }
    }

    /**
     * Downloads through the Python module directly. Unlike the Java runner this accepts
     * extractor_args (needed for YouTube player clients) and exposes live progress.
     */
    private suspend fun runYtDlpDirect(
        id: String,
        url: String,
        directory: File,
        outputPrefix: String,
        selector: String,
        startFraction: Float,
        endFraction: Float,
        connections: Int,
        onProgress: (MediaProgress) -> Unit,
        extractorArgsJson: String? = null,
        helperKey: String = id
    ): File = coroutineScope {
        val helper = newHelper()
        activeHelpers[helperKey] = helper
        val poller = launch(Dispatchers.IO) {
            var lastBytes = -1L
            var lastProgressAt = System.currentTimeMillis()
            // Speed is derived from byte growth of the stream running right now, so the
            // size and rate shown in the UI are real numbers, not a guess from the percent.
            var sampleBytes = -1L
            var sampleAt = System.currentTimeMillis()
            var speed = 0L
            while (true) {
                val raw = runCatching { helper.callAttr("progress").toString() }.getOrNull()
                if (raw != null) {
                    val obj = runCatching { Json.parseToJsonElement(raw).jsonObject }.getOrNull()
                    val percent = obj?.get("percent")?.jsonPrimitive?.contentOrNull?.toDoubleOrNull() ?: 0.0
                    val bytes = obj?.get("bytes")?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L
                    val total = obj?.get("total")?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L
                    val now = System.currentTimeMillis()
                    if (bytes != lastBytes) {
                        lastBytes = bytes
                        lastProgressAt = now
                    }
                    if (now - lastProgressAt > MEDIA_STALL_TIMEOUT_MS) {
                        // yt-dlp can sit on a dead socket forever; the hook aborts on its next tick.
                        runCatching { helper.callAttr("stall") }
                    }
                    if (bytes < sampleBytes) {
                        // A new stream (video then audio) starts its own rate window.
                        sampleBytes = bytes
                        sampleAt = now
                        speed = 0L
                    } else if (sampleBytes < 0) {
                        sampleBytes = bytes
                        sampleAt = now
                    }
                    val elapsed = now - sampleAt
                    if (elapsed >= 500) {
                        val delta = bytes - sampleBytes
                        val instant = if (delta > 0) delta * 1000 / elapsed else 0L
                        speed = if (speed <= 0) instant else (speed * 2 + instant) / 3
                        sampleBytes = bytes
                        sampleAt = now
                    }
                    onProgress(
                        MediaProgress(
                            fraction = startFraction + (endFraction - startFraction) * percent.toFloat().coerceIn(0f, 1f),
                            downloadedBytes = bytes,
                            totalBytes = total.takeIf { it > 0 } ?: -1L,
                            bytesPerSecond = speed
                        )
                    )
                }
                delay(350)
            }
        }
        try {
            helper.callAttr("run", url, File(directory, "$outputPrefix.%(ext)s").absolutePath, selector, extractorArgsJson, connections)
            currentCoroutineContext().ensureActive()
        } catch (cancelled: CancellationException) {
            runCatching { helper.callAttr("cancel") }
            throw cancelled
        } catch (error: Exception) {
            currentCoroutineContext().ensureActive()
            throw IllegalStateException(mediaError(listOf("ERROR" to (error.message ?: "")), "The media download did not complete. Check the link and the site's access terms."))
        } finally {
            poller.cancelAndJoin()
            activeHelpers.remove(helperKey, helper)
        }
        findOutput(directory, outputPrefix)
    }

    private suspend fun runYtDlpViaRunner(
        id: String,
        url: String,
        directory: File,
        outputPrefix: String,
        selector: String,
        startFraction: Float,
        endFraction: Float,
        onProgress: (MediaProgress) -> Unit
    ): File {
        val request = YtDlpRequest(url)
            .setOutputTemplate(File(directory, "$outputPrefix.%(ext)s").absolutePath)
            .addOption("--no-playlist")
            .addOption("-f", selector)
        // The fallback runner likewise receives no --limit-rate or throttle option.
        val logs = java.util.Collections.synchronizedList(mutableListOf<Pair<String, String>>())
        // The Java runner is only stopped when it goes quiet: no log line, no progress update and no
        // growth of its output files for MEDIA_STALL_TIMEOUT_MS. An earlier fixed three-minute deadline
        // killed every download that merely took longer than that.
        val watchdog = MediaStallWatchdog(MEDIA_STALL_TIMEOUT_MS, nowMillis())
        val lastPercent = java.util.concurrent.atomic.AtomicReference<Float?>()
        val future = YtDlp.executeDebug(request, LogCallback { level, line ->
            logs += level to line
            watchdog.onActivity(nowMillis())
        }, DownloadProgressCallback { progress, _, _ ->
            if (lastPercent.getAndSet(progress) != progress) watchdog.onActivity(nowMillis())
            // The Java runner reports a percentage only; no byte total is invented here.
            val fraction = (progress.toDouble().coerceIn(0.0, 100.0) / 100.0).toFloat()
            onProgress(MediaProgress(startFraction + (endFraction - startFraction) * fraction))
        })
        activeFutures[id] = future
        val response = try {
            var completed = null as YtDlpResponse?
            var staged = stagedBytes(directory, outputPrefix)
            var checkedAt = nowMillis()
            while (completed == null) {
                currentCoroutineContext().ensureActive()
                val now = nowMillis()
                if (now - checkedAt >= OUTPUT_GROWTH_CHECK_MILLIS) {
                    checkedAt = now
                    val grown = stagedBytes(directory, outputPrefix)
                    if (grown != staged) {
                        staged = grown
                        watchdog.onActivity(now)
                    }
                }
                if (watchdog.stalled(now)) {
                    future.cancel(true)
                    throw IllegalStateException("The media download stalled. Check your connection or VPN and try again.")
                }
                try {
                    completed = future.get(300, TimeUnit.MILLISECONDS)
                } catch (_: java.util.concurrent.TimeoutException) {
                    // Keep polling so pause/cancel can interrupt safely.
                }
            }
            completed
        } catch (cancelled: CancellationException) {
            // Future.cancel() also surfaces as CancellationException, so a media pause
            // is persisted as PAUSED instead of being reported as a failed download.
            future.cancel(true)
            throw cancelled
        } catch (_: InterruptedException) {
            future.cancel(true)
            Thread.currentThread().interrupt()
            throw CancellationException("Media download interrupted.")
        } catch (_: Exception) {
            throw IllegalStateException(mediaError(logs, "The media download did not complete. Check the link and the site's access terms."))
        } finally {
            activeFutures.remove(id, future)
        }
        if (!response.isSuccess) {
            throw IllegalStateException(mediaError(logs, "The media download did not complete. Check the link and the site's access terms."))
        }
        return findOutput(directory, outputPrefix)
    }

    private fun findOutput(directory: File, outputPrefix: String): File =
        directory.listFiles()
            ?.filter { it.isFile && it.name.startsWith(outputPrefix) && !it.name.endsWith(".part") && !it.name.endsWith(".ytdl") && !it.name.endsWith(".temp") }
            ?.maxByOrNull(File::lastModified)
            ?: throw IllegalStateException("The media engine returned no completed output file.")

    private suspend fun runFfmpeg(id: String, arguments: Array<String>): Boolean {
        val session = FFmpegKit.executeWithArgumentsAsync(arguments, null)
        activeConversions[id] = session
        try {
            while (session.state == SessionState.CREATED || session.state == SessionState.RUNNING) {
                currentCoroutineContext().ensureActive()
                delay(250)
            }
        } catch (cancelled: CancellationException) {
            session.cancel()
            throw cancelled
        } finally {
            activeConversions.remove(id, session)
        }
        currentCoroutineContext().ensureActive()
        return ReturnCode.isSuccess(session.returnCode)
    }

    private suspend fun <T> attempt(block: suspend () -> T): T? = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        val message = error.message.orEmpty()
        if (message.contains("cyron-download-cancelled") || message.contains("cyron-download-stalled")) {
            // A pause/watchdog must stop the merged pipeline, not start fallbackSingle.
            currentCoroutineContext().ensureActive()
            throw error
        }
        null
    }

    private fun mediaError(logs: List<Pair<String, String>>, fallback: String): String {
        val text = logs.joinToString("\n") { it.second }
        val friendly = when {
            Regex("sign in to confirm|not a bot", RegexOption.IGNORE_CASE).containsMatchIn(text) ->
                "YouTube is blocking automated access from this network. Try again later or on a different connection."
            Regex("no supported javascript runtime|js runtime|jsinterp|\\bejs\\b", RegexOption.IGNORE_CASE).containsMatchIn(text) ->
                "YouTube needs a JavaScript runtime that is not included in this build. Try another link or use a direct media file."
            Regex("unable to download api page|ssl|timed out|connection reset|network is unreachable|failed to resolve", RegexOption.IGNORE_CASE).containsMatchIn(text) ->
                "The site could not be reached. Check your connection or VPN and try again."
            Regex("unable to extract|nsig|player response|signature", RegexOption.IGNORE_CASE).containsMatchIn(text) ->
                "YouTube changed its player. Try another link or update the app."
            Regex("cyron-download-cancelled", RegexOption.IGNORE_CASE).containsMatchIn(text) ->
                "The download was cancelled."
            Regex("cyron-download-stalled", RegexOption.IGNORE_CASE).containsMatchIn(text) ->
                "The media download stalled. Check your connection or VPN and try again."
            Regex("video unavailable|private video|members-only|removed by the uploader", RegexOption.IGNORE_CASE).containsMatchIn(text) ->
                "This video is unavailable, private, or restricted."
            Regex("unsupported url", RegexOption.IGNORE_CASE).containsMatchIn(text) ->
                "This link is not supported by the media engine."
            Regex("http error 403|forbidden", RegexOption.IGNORE_CASE).containsMatchIn(text) ->
                "The site refused the media request (403). Try again later."
            Regex("requested format is not available", RegexOption.IGNORE_CASE).containsMatchIn(text) ->
                "The selected quality is not available for this link. Choose another format."
            else -> null
        }
        if (friendly != null) return friendly
        // PyException carries a full traceback; the actionable cause is on the last line.
        val lines = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        val error = lines.lastOrNull { it.startsWith("ERROR:", ignoreCase = true) }
            ?: lines.lastOrNull { it.contains("DownloadError", ignoreCase = true) }
            ?: lines.lastOrNull()
        return error?.let(::redact) ?: fallback
    }

    private fun redact(message: String): String = message
        .replace(Regex("https?://\\S+"), "[link]")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(220)

    /** Deletes leftover yt-dlp/FFmpeg staging files after the task is finished or discarded. */
    suspend fun cleanupOrphans(retainedIds: Set<String>) = withContext(Dispatchers.IO) {
        val prefixes = (retainedIds + activeFutures.keys + activeHelpers.keys).map(::mediaPrefix).toSet()
        mediaDirectory().listFiles()?.forEach { file ->
            if (file.isFile && prefixes.none(file.name::startsWith)) file.delete()
        }
    }

    suspend fun cleanup(id: String) = withContext(Dispatchers.IO) {
        val prefix = mediaPrefix(id)
        runCatching {
            mediaDirectory().listFiles()?.filter { it.name.startsWith(prefix) }?.forEach { it.delete() }
        }
    }

    private fun mediaDirectory() = File(context.noBackupFilesDir, "cyron-media")

    private fun mediaPrefix(id: String) = "media-${id.replace(Regex("[^A-Za-z0-9_-]"), "_").take(64)}"

    private fun resolveMimeType(extension: String, choice: MediaFormatChoice): String {
        if (choice.convertToMp3) return "audio/mpeg"
        val mapped = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
        return when {
            mapped == null -> if (choice.audioOnly) "audio/*" else "video/*"
            choice.audioOnly && mapped.startsWith("video/") -> mapped.replaceFirst("video/", "audio/")
            else -> mapped
        }
    }

    fun cancel(id: String) {
        cancelStreamHelpers(id, null)
        activeFutures[id]?.cancel(true)
        activeConversions[id]?.cancel()
    }

    /**
     * Stops one stream (video or audio) or, with [stream] null, every stream of a task. Helpers are
     * registered per stream so the merged download can stop one half when the other one fails.
     */
    private fun cancelStreamHelpers(id: String, stream: String?) {
        val exact = stream?.let { helperKey(id, it) }
        val keys = activeHelpers.keys.filter { key ->
            if (exact != null) key == exact else key == id || key.startsWith("$id|")
        }
        for (key in keys) {
            activeHelpers.remove(key)?.let { helper -> runCatching { helper.callAttr("cancel") } }
        }
    }

    private fun helperKey(id: String, stream: String) = "$id|$stream"

    private fun helperModule(): PyObject = synchronized(helperLock) {
        val main = Python.getInstance().getModule("__main__")
        if (!helperLoaded) {
            Python.getInstance().getModule("builtins").callAttr("exec", HELPER_SCRIPT, main.get("__dict__"))
            helperLoaded = true
        }
        main
    }

    // Chaquopy's callAttr invokes the attribute with the supplied arguments, so
    // callAttr("CyronMediaDownload") already returns a new instance. Calling the
    // result again throws "object is not callable".
    private fun newHelper(): PyObject = helperModule().callAttr("CyronMediaDownload")

    private fun initialize() {
        if (initialized.get()) return
        synchronized(initializationLock) {
            if (!initialized.get()) {
                // Start Python first, then put the current yt-dlp wheel at the front
                // of sys.path and drop any older module the runner may have loaded.
                YtDlp.init(context)
                runCatching {
                    val wheel = installModernYtDlp()
                    val python = Python.getInstance()
                    val main = python.getModule("__main__")
                    main.set("CYRON_YTDLP_WHEEL", PyObject.fromJava(wheel.absolutePath))
                    python.getModule("builtins").callAttr("exec", PYTHON_PRELUDE, main.get("__dict__"))
                }.onFailure { error -> Log.w(TAG, "Could not activate the bundled yt-dlp wheel; using the packaged module.", error) }
                initialized.set(true)
            }
        }
    }

    /**
     * yt-dlp changes its YouTube clients frequently. The AAR's packaged copy is old,
     * so a current pure-Python wheel is shipped as an asset and imported through
     * Python's zipimport. It needs no ffmpeg, cookies, login, or browser runtime.
     */
    private fun installModernYtDlp(): File {
        val target = File(context.filesDir, YTDLP_WHEEL_ASSET)
        if (!target.isFile || target.length() < 1_000_000L) {
            context.assets.open(YTDLP_WHEEL_ASSET).use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
        }
        require(target.isFile && target.length() > 1_000_000L) { "The bundled yt-dlp module is missing." }
        return target
    }

    companion object {
        private val VIDEO_EXTENSIONS = setOf("mp4", "m4v", "mov", "webm", "mkv", "avi", "flv", "ts")
        private val MP4_VIDEO_EXTENSIONS = setOf("mp4", "m4v", "mov")
        private val MP4_AUDIO_EXTENSIONS = setOf("m4a", "mp4", "aac")
        private val WEBM_AUDIO_EXTENSIONS = setOf("webm", "opus", "ogg")
        private const val INSPECT_OPTIONS_JSON = """{"quiet":true,"no_warnings":true,"skip_download":true,"noplaylist":true,"socket_timeout":20}"""
        private const val TAG = "CyronMedia"
        private const val YTDLP_WHEEL_ASSET = "yt_dlp-2026.10.26-py3-none-any.whl"
        private const val DEFAULT_MEDIA_CONNECTIONS = 8
        private const val MEDIA_STALL_TIMEOUT_MS = 3 * 60 * 1000L
        private val YOUTUBE_EXTRACTOR_ARG_CANDIDATES: List<String?> = listOf(
            null, // yt-dlp default; selects its JS-free client when no runtime exists
            """{"youtube":{"player_client":["visionos"]}}""",
            """{"youtube":{"player_client":["android_vr"]}}""",
            """{"youtube":{"player_client":["ios"]}}"""
        )
        private const val PYTHON_PRELUDE = """
import sys
_path = globals().get('CYRON_YTDLP_WHEEL')
if _path and _path not in sys.path:
    sys.path.insert(0, _path)
for _name in [n for n in sys.modules if n == 'yt_dlp' or n.startswith('yt_dlp.')]:
    del sys.modules[_name]
"""
        private const val OUTPUT_GROWTH_CHECK_MILLIS = 2_000L
        private val HELPER_SCRIPT = """
import json
import sys as _sys
from urllib.parse import urlparse as _urlparse
_wheel = globals().get('CYRON_YTDLP_WHEEL')
if _wheel and _wheel not in _sys.path:
    _sys.path.insert(0, _wheel)

def _cyron_is_youtube(url):
    try:
        host = (_urlparse(url).hostname or '').lower()
    except Exception:
        return False
    return host == 'youtu.be' or host.endswith('youtube.com') or host.endswith('youtube-nocookie.com')


class CyronMediaDownload:
    def __init__(self):
        self.cancelled = False
        self.stalled = False
        self.percent = 0.0
        self.bytes = 0
        self.total = 0

    def _hook(self, d):
        if d.get('status') == 'downloading':
            total = d.get('total_bytes') or d.get('total_bytes_estimate') or 0
            done = d.get('downloaded_bytes') or 0
            self.percent = (float(done) / float(total)) if total else 0.0
            self.bytes = done
            self.total = total
        if self.cancelled:
            raise Exception('cyron-download-cancelled')
        if self.stalled:
            raise Exception('cyron-download-stalled')

    def run(self, url, outtmpl, fmt, extractor_args_json=None, connections=8):
        import yt_dlp
        opts = {
            'outtmpl': outtmpl,
            'format': fmt,
            'noplaylist': True,
            'quiet': True,
            'no_warnings': True,
            'noprogress': True,
            'socket_timeout': 30,
            'retries': 8,
            'fragment_retries': 8,
            'extractor_retries': 8,
            # Resume the .part file left by a paused download instead of restarting it.
            'continuedl': True,
            'nopart': False,
            'progress_hooks': [self._hook],
            # YouTube serves separate fragmented streams. Downloading several fragments at
            # once stops a single connection's round-trip time from capping the throughput.
            'concurrent_fragment_downloads': connections,
            'buffersize': 4 * 1024 * 1024,
            # Deliberately no yt-dlp ratelimit or throttledratelimit option: transfers are uncapped.
        }
        if _cyron_is_youtube(url):
            # Chunked range requests keep the older, non-fragmented formats from being
            # throttled per request by the server.
            opts['http_chunk_size'] = 20 * 1024 * 1024
        if extractor_args_json:
            opts['extractor_args'] = json.loads(extractor_args_json)
        with yt_dlp.YoutubeDL(opts) as ydl:
            ydl.download([url])

    def cancel(self):
        self.cancelled = True

    def stall(self):
        self.stalled = True

    def progress(self):
        return json.dumps({'percent': self.percent, 'bytes': self.bytes, 'total': self.total})

def cyron_inspect(url, options_json, extractor_args_json=None):
    import yt_dlp
    opts = json.loads(options_json)
    if extractor_args_json:
        opts['extractor_args'] = json.loads(extractor_args_json)
    with yt_dlp.YoutubeDL(opts) as ydl:
        info = ydl.extract_info(url, download=False)
        return json.dumps(ydl.sanitize_info(info))
"""

        fun isYouTubeUrl(url: String): Boolean = runCatching {
            val host = Uri.parse(url.trim()).host?.lowercase() ?: return@runCatching false
            host == "youtu.be" || host == "youtube.com" || host.endsWith(".youtube.com") ||
                host == "youtube-nocookie.com" || host.endsWith(".youtube-nocookie.com")
        }.getOrDefault(false)
    }
}

internal fun normalizeMediaConnections(connections: Int): Int = connections.coerceIn(1, 16)

private fun nowMillis(): Long = System.nanoTime() / 1_000_000L

/** Helpers are registered per stream so video and audio can be cancelled independently. */
private const val STREAM_MAIN = "main"
private const val STREAM_VIDEO = "video"
private const val STREAM_AUDIO = "audio"

/** A merged download spends the rest of the 0..1 range on muxing. */
private const val MEDIA_VIDEO_BUDGET = 0.9f
private const val MEDIA_MERGE_BUDGET = 0.97f

/**
 * Flags a media transfer as stalled only after it has produced no output for [timeoutMillis]. Any log line,
 * progress update or growth of the staged files restarts the window, so a slow but healthy transfer is
 * never cut off. Time is monotonic: a wall-clock adjustment cannot cause a false stall.
 */
internal class MediaStallWatchdog(private val timeoutMillis: Long, startedAt: Long) {
    private val lastActivityAt = java.util.concurrent.atomic.AtomicLong(startedAt)

    fun onActivity(now: Long) {
        // Callbacks arrive from several threads; an older timestamp must not shorten the window.
        lastActivityAt.updateAndGet { previous -> maxOf(previous, now) }
    }

    fun stalled(now: Long): Boolean = now - lastActivityAt.get() > timeoutMillis
}

/** Total size of the files yt-dlp writes for one output prefix; their growth proves the transfer is alive. */
internal fun stagedBytes(directory: File, outputPrefix: String): Long =
    directory.listFiles()?.filter { it.name.startsWith(outputPrefix) }?.sumOf { it.length() } ?: 0L

data class MediaInfo(val title: String, val availableHeights: List<Int>, val audioOnlyAvailable: Boolean)

data class MediaFormatChoice(
    val label: String,
    val selector: String,
    val audioOnly: Boolean = false,
    val convertToMp3: Boolean = false,
    val videoSelector: String? = null,
    val fallbackSelector: String? = null
) {
    fun normalized(): MediaFormatChoice = when (selector) {
        "__audio_only__" -> copy(selector = "bestaudio/best", audioOnly = true)
        "__audio_mp3__" -> copy(selector = "bestaudio/best", audioOnly = true, convertToMp3 = true)
        else -> this
    }

    companion object {
        private const val MERGE_PREFIX = "merge::"

        /** Parses the sanitized JSON returned by yt-dlp's `extract_info`. */
        fun parseInfo(raw: String, fallbackTitle: String): MediaInfo {
            val root = Json.parseToJsonElement(raw).jsonObject
            if (root["_type"]?.jsonPrimitive?.contentOrNull == "playlist") {
                throw IllegalStateException("Playlists are not supported. Paste a single video link.")
            }
            val info: JsonObject = root
            val title = info["title"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                ?: info["fulltitle"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                ?: fallbackTitle
            val heights = mutableSetOf<Int>()
            var audioOnly = false
            val formats = info["formats"] as? JsonArray
            if (formats != null) {
                for (element in formats) {
                    val format = element as? JsonObject ?: continue
                    val height = format["height"]?.jsonPrimitive?.intOrNull ?: 0
                    val videoCodec = format["vcodec"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    val audioCodec = format["acodec"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    if (videoCodec.isBlank() || videoCodec == "none") {
                        if (audioCodec.isNotBlank() && audioCodec != "none") audioOnly = true
                    } else if (height in 144..4320) {
                        heights += height
                    }
                }
            } else {
                val height = info["height"]?.jsonPrimitive?.intOrNull ?: 0
                if (height in 144..4320) heights += height
                val videoCodec = info["vcodec"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val audioCodec = info["acodec"]?.jsonPrimitive?.contentOrNull.orEmpty()
                audioOnly = (videoCodec.isBlank() || videoCodec == "none") && audioCodec.isNotBlank() && audioCodec != "none"
            }
            if (!audioOnly) audioOnly = heights.isEmpty()
            return MediaInfo(title.trim(), heights.sortedDescending(), audioOnly)
        }

        fun choices(info: MediaInfo, preferMerged: Boolean = false): List<MediaFormatChoice> {
            val heights = info.availableHeights.distinct().sortedDescending()
            val bestHeight = heights.filter { it <= 1080 }.maxOrNull() ?: heights.maxOrNull()
            val single = MediaFormatChoice("Best single file", "best[vcodec!=none][acodec!=none]/best[ext=mp4]")
            val merged = heights.map { height ->
                val video = "bestvideo[height<=$height]"
                // YouTube often has no progressive format at a given height; ask for the
                // best single file instead of a selector that resolves to nothing.
                val fallback = "best[vcodec!=none][acodec!=none]"
                MediaFormatChoice(
                    label = "${height}p",
                    selector = "$MERGE_PREFIX$video::$fallback",
                    videoSelector = video,
                    fallbackSelector = fallback
                )
            }
            val promotedHeight = bestHeight.takeIf { preferMerged }
            return buildList {
                if (promotedHeight != null) {
                    merged.firstOrNull { it.label == "${promotedHeight}p" }?.copy(label = "Best quality")?.let(::add)
                }
                add(single)
                addAll(merged.filterNot { promotedHeight != null && it.label == "${promotedHeight}p" })
                if (info.audioOnlyAvailable) {
                    add(MediaFormatChoice("Audio only", "__audio_only__", audioOnly = true))
                    add(MediaFormatChoice("Audio · MP3", "__audio_mp3__", audioOnly = true, convertToMp3 = true))
                }
            }.distinctBy(MediaFormatChoice::label)
        }

        fun fromSelector(selector: String?): MediaFormatChoice {
            val value = selector.orEmpty().trim()
            return when {
                value == "__audio_only__" -> MediaFormatChoice("Audio only", value, audioOnly = true)
                value == "__audio_mp3__" -> MediaFormatChoice("Audio · MP3", value, audioOnly = true, convertToMp3 = true)
                value == "best" -> MediaFormatChoice("Selected quality", "best[vcodec!=none][acodec!=none]")
                value.startsWith(MERGE_PREFIX) -> {
                    val parts = value.removePrefix(MERGE_PREFIX).split("::")
                    val video = parts.getOrNull(0).orEmpty().ifBlank { "bestvideo" }
                    val fallback = parts.getOrNull(1).orEmpty().ifBlank { "best[vcodec!=none][acodec!=none]" }
                    MediaFormatChoice("Selected quality", value, videoSelector = video, fallbackSelector = fallback)
                }
                '+' in value -> {
                    // Compatibility with selectors saved by older builds that expected yt-dlp to merge.
                    val video = value.substringBefore('+').ifBlank { "bestvideo" }
                    val fallback = value.substringAfter('/', "best[vcodec!=none][acodec!=none]").ifBlank { "best[vcodec!=none][acodec!=none]" }
                    MediaFormatChoice("Selected quality", value, videoSelector = video, fallbackSelector = fallback)
                }
                else -> MediaFormatChoice("Selected quality", value.ifBlank { "best[vcodec!=none][acodec!=none]/best[ext=mp4]" })
            }
        }
    }
}

/**
 * Combines the two concurrent stream reports (video and audio) into the single reading the UI
 * shows: bytes are summed, the fraction is the share of the combined expected size, and the rate
 * is the sum of both streams. Reports are monotonic per stream, so a slow fragment never makes a
 * merged download look like it restarted.
 */
private class MergedMediaProgress(private val emit: (MediaProgress) -> Unit) {
    private val lock = Any()
    private var videoBytes = 0L
    private var videoTotal = -1L
    private var videoSpeed = 0L
    private var audioBytes = 0L
    private var audioTotal = -1L
    private var audioSpeed = 0L

    fun onVideo(progress: MediaProgress) = synchronized(lock) {
        videoBytes = maxOf(videoBytes, progress.downloadedBytes)
        if (progress.totalBytes > 0) videoTotal = progress.totalBytes
        videoSpeed = progress.bytesPerSecond
        publish()
    }

    fun onAudio(progress: MediaProgress) = synchronized(lock) {
        audioBytes = maxOf(audioBytes, progress.downloadedBytes)
        if (progress.totalBytes > 0) audioTotal = progress.totalBytes
        audioSpeed = progress.bytesPerSecond
        publish()
    }

    /** A finished stream must stop contributing its last rate to the combined speed. */
    fun finishVideo() = synchronized(lock) {
        videoSpeed = 0L
        publish()
    }

    fun finishAudio() = synchronized(lock) {
        audioSpeed = 0L
        publish()
    }

    /** Both streams are complete, so the counters snap to their known sizes. */
    fun finishAll() = synchronized(lock) {
        if (videoTotal > 0) videoBytes = videoTotal
        if (audioTotal > 0) audioBytes = audioTotal
        videoSpeed = 0L
        audioSpeed = 0L
        publish()
    }

    private fun publish() {
        val downloaded = videoBytes + audioBytes
        val total = if (videoTotal > 0 && audioTotal > 0) videoTotal + audioTotal else -1L
        val fraction = when {
            total > 0 -> (downloaded.toDouble() / total.toDouble()).toFloat().coerceIn(0f, 1f) * MEDIA_MERGE_BUDGET
            videoTotal > 0 -> (videoBytes.toDouble() / videoTotal.toDouble()).toFloat().coerceIn(0f, 1f) * MEDIA_VIDEO_BUDGET
            else -> 0f
        }
        emit(MediaProgress(fraction, downloaded, total, videoSpeed + audioSpeed))
    }
}

/**
 * Live state of one media stream: the overall fraction is what the ring shows, while the
 * byte counters and rate describe the stream currently downloading (video, then audio).
 */
data class MediaProgress(
    val fraction: Float,
    val downloadedBytes: Long = 0,
    val totalBytes: Long = -1,
    val bytesPerSecond: Long = 0
)

data class MediaDownloadResult(val savedUri: String, val fileName: String, val bytes: Long, val sha256: String, val mimeType: String)
