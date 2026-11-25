package com.cyron.manager.torrent

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import android.provider.DocumentsContract
import android.system.Os
import com.cyron.manager.domain.model.DownloadName
import com.cyron.manager.storage.SafFileStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.libtorrent4j.AlertListener
import org.libtorrent4j.SessionManager
import org.libtorrent4j.SessionParams
import org.libtorrent4j.SettingsPack
import org.libtorrent4j.TorrentHandle
import org.libtorrent4j.TorrentInfo
import org.libtorrent4j.alerts.AddTorrentAlert
import org.libtorrent4j.alerts.Alert
import org.libtorrent4j.alerts.AlertType
import org.libtorrent4j.alerts.TorrentAlert
import org.libtorrent4j.alerts.TorrentErrorAlert
import org.libtorrent4j.swig.torrent_flags_t
import org.libtorrent4j.Priority

/** Local libtorrent session: data remains in app-private storage until verified completion and SAF export. */
@Singleton
class TorrentDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val resolver: ContentResolver
) {
    private val root = File(context.noBackupFilesDir, "cyron-torrents").apply { if (!exists() && !mkdirs()) error("Could not prepare private torrent storage.") }
    private val session = SessionManager(false)
    private val lock = Any()
    private val entries = ConcurrentHashMap<String, TorrentEntry>()
    private val exportMutex = Mutex()
    @Volatile private var started = false

    private val listener = object : AlertListener {
        override fun types(): IntArray = intArrayOf(AlertType.ADD_TORRENT.swig(), AlertType.TORRENT_FINISHED.swig(), AlertType.TORRENT_ERROR.swig())
        override fun alert(alert: Alert<*>) {
            when (alert.type()) {
                AlertType.ADD_TORRENT -> {
                    val added = alert as AddTorrentAlert
                    val params = added.params()
                    val hash = runCatching {
                        params.getTorrentInfo()?.infoHash()?.toHex()
                            ?: params.getInfoHashes().takeIf { it.hasV1() || it.hasV2() }?.getBest()?.toHex()
                    }.getOrNull()
                    val entry = hash?.let { value -> entries.values.firstOrNull { it.hash.equals(value, true) } } ?: return
                    if (added.error().getValue() != 0) {
                        entry.error = "Could not add this torrent: ${added.error().getMessage()}"
                    } else {
                        entry.handle = added.handle()
                        entry.handle?.takeIf { it.isValid }?.let { handle ->
                            handle.resume()
                            entry.error = null
                        }
                    }
                }
                AlertType.TORRENT_FINISHED -> {
                    val torrent = alert as TorrentAlert<*>
                    val hash = runCatching { torrent.handle().infoHash().toHex() }.getOrNull() ?: return
                    entries.values.firstOrNull { it.hash.equals(hash, true) }?.finished = true
                }
                AlertType.TORRENT_ERROR -> {
                    val torrent = alert as TorrentErrorAlert
                    val hash = runCatching { torrent.handle().infoHash().toHex() }.getOrNull() ?: return
                    entries.values.firstOrNull { it.hash.equals(hash, true) }?.error = "Torrent transfer error: ${torrent.error().getMessage()}"
                }
                else -> Unit
            }
        }
    }

    fun observe(id: String): Flow<TorrentSnapshot> = flow {
        while (true) {
            val entry = entries[id] ?: break
            val handle = entry.handle
            val status = runCatching { handle?.status(true) }.getOrNull()
            val done = status?.totalWantedDone() ?: 0L
            val total = status?.totalWanted() ?: -1L
            entry.handle?.torrentFile()?.let { info ->
                if (total > 0 && done >= total && (status?.isFinished == true || status?.isSeeding == true)) entry.finished = true
            }
            emit(TorrentSnapshot(id, done, total, status?.downloadRate()?.toLong() ?: 0L, entry.finished, entry.error))
            delay(1000)
        }
    }

    suspend fun start(id: String, source: String, destination: String, displayName: String) = withContext(Dispatchers.IO) {
        val target = Uri.parse(destination)
        require(target.scheme == "content" && DocumentFile.fromTreeUri(context, target)?.canWrite() == true) { "Select a writable folder using the Android folder picker." }
        ensureStarted()
        val dataDir = dataDirectory(id).apply { if (!exists() && !mkdirs()) error("Could not prepare private torrent data.") }
        val isMagnet = source.startsWith("magnet:", true)
        val info = if (isMagnet) {
            require(magnetHash(source).isNotBlank()) { "Magnet link must include a valid BitTorrent info-hash." }
            null
        } else {
            require(source.startsWith("content:", true) || source.startsWith("file:", true)) { "Choose a .torrent file using Android's document picker." }
            val file = if (source.startsWith("file:", true)) File(Uri.parse(source).path ?: error("Invalid torrent file.")) else {
                val input = resolver.openInputStream(Uri.parse(source)) ?: error("Cannot read the selected .torrent file.")
                val destinationFile = metadataFile(id)
                input.use { sourceStream -> FileOutputStream(destinationFile).use { targetStream -> sourceStream.copyTo(targetStream) } }
                destinationFile
            }
            TorrentInfo(file).also { require(it.isValid) { "This is not a valid .torrent file." } }
        }
        val hash = if (info != null) info.infoHash().toHex() else magnetHash(source)
        require(hash.isNotBlank()) { "Magnet link must include a valid BitTorrent info-hash." }
        require(entries.values.none { it.id != id && it.hash.equals(hash, true) }) { "This torrent is already in the download list." }
        val entry = entries[id] ?: TorrentEntry(id, hash, dataDir, target, DownloadName.sanitize(displayName)).also { entries[id] = it }
        require(entry.hash.equals(hash, true)) { "The torrent source cannot be changed after this task has started." }
        entry.destination = target
        entry.displayName = DownloadName.sanitize(displayName)
        synchronized(lock) {
            val existing = entry.handle?.takeIf { it.isValid } ?: session.find(org.libtorrent4j.Sha1Hash.parseHex(hash))
            if (existing != null && existing.isValid) {
                entry.handle = existing
                existing.resume()
                entry.error = null
            } else if (info != null) {
                session.download(info, dataDir, null, Priority.array(Priority.DEFAULT, info.numFiles()), null, torrent_flags_t())
            } else {
                session.download(source, dataDir, torrent_flags_t())
            }
        }
    }

    suspend fun pause(id: String) = withContext(Dispatchers.IO) {
        synchronized(lock) { entries[id]?.handle?.takeIf { it.isValid }?.pause() }
    }

    suspend fun pauseAll() = withContext(Dispatchers.IO) {
        synchronized(lock) { pauseValidHandles() }
    }

    suspend fun resume(id: String) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val entry = entries[id] ?: return@synchronized
            val handle = entry.handle?.takeIf { it.isValid } ?: return@synchronized
            handle.resume()
            entry.error = null
        }
    }

    suspend fun cancel(id: String, removeData: Boolean) = withContext(Dispatchers.IO) {
        val entry = entries.remove(id)
        // The session may still own the torrent even when metadata discovery had
        // not attached a handle yet. Find it by info-hash so a later download of
        // the same magnet is not rejected as a duplicate torrent.
        val handle = entry?.handle?.takeIf { it.isValid } ?: entry?.hash?.let { hash ->
            runCatching { session.find(org.libtorrent4j.Sha1Hash.parseHex(hash)) }.getOrNull()
        }
        handle?.let { if (it.isValid) runCatching { session.remove(it) } }
        if (removeData) {
            // These paths depend only on the task id, so deletion still works after
            // process death when the in-memory entry (and its info-hash) is gone.
            (entry?.dataDir ?: dataDirectory(id)).deleteRecursively()
            runCatching { metadataFile(id).delete() }
        }
    }

    /** Pauses active handles and shuts down DHT and the native session. Safe to call more than once. */
    suspend fun stop() = withContext(Dispatchers.IO) {
        synchronized(lock) {
            pauseValidHandles()
            if (session.isRunning()) runCatching { session.stopDht() }
            runCatching { session.removeListener(listener) }
            runCatching { session.stop() }
            entries.values.forEach { it.handle = null }
            started = false
        }
    }

    suspend fun exportCompleted(id: String): TorrentExportResult = exportMutex.withLock {
        withContext(Dispatchers.IO) {
            val entry = entries[id] ?: error("Torrent session was not found.")
            val handle = entry.handle ?: error("Torrent metadata is still being discovered.")
            check(entry.finished || handle.status(true).isFinished || handle.status(true).isSeeding) { "Torrent is not complete yet." }
            val info = handle.torrentFile() ?: error("Torrent metadata is unavailable.")
            val tree = DocumentFile.fromTreeUri(context, entry.destination) ?: error("Selected folder is no longer available.")
            require(tree.canWrite()) { "Selected folder is no longer writable." }
            var exported = 0L
            var firstUri: Uri? = null
            var firstFileName: String? = null
            var fileCount = 0
            val publishedUris = mutableListOf<Uri>()
            cleanupInterruptedExport(id)
            try {
                for (index in 0 until info.numFiles()) {
                    if (info.files().padFileAt(index)) continue
                    val relative = info.files().filePath(index).replace('\\', '/').split('/')
                        .filter { it.isNotBlank() && it != "." }
                    require(relative.isNotEmpty() && relative.none { it == ".." }) { "Unsafe file path in torrent metadata." }
                    var parent = tree
                    relative.dropLast(1).forEach { part ->
                        val safePart = DownloadName.sanitize(part)
                        parent = parent.findFile(safePart)
                            ?: parent.createDirectory(safePart)
                            ?: error("Could not create a destination subfolder.")
                    }
                    val name = uniqueName(parent, DownloadName.sanitize(relative.last()))
                    val sourceFile = File(info.files().filePath(index, entry.dataDir.absolutePath)).canonicalFile
                    require(sourceFile.path.startsWith(entry.dataDir.canonicalPath + File.separator) && sourceFile.isFile) {
                        "A torrent file is missing or outside private storage."
                    }
                    val extension = relative.last().substringAfterLast('.', "").lowercase(java.util.Locale.ROOT)
                    val entryMime = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
                        ?: "application/octet-stream"
                    val temporaryName = ".cyron-${java.util.UUID.randomUUID()}.partial"
                    val temporary = DocumentsContract.createDocument(resolver, parent.uri, entryMime, temporaryName)
                        ?: error("Could not create a temporary file in the selected destination.")
                    try {
                        writeExportJournal(id, publishedUris, parent.uri, name, temporary)
                    } catch (error: Exception) {
                        runCatching { DocumentsContract.deleteDocument(resolver, temporary) }
                        throw error
                    }
                    val published = try {
                        val descriptor = resolver.openFileDescriptor(temporary, "w")
                            ?: error("Could not open a destination torrent file.")
                        descriptor.use { pfd ->
                            FileInputStream(sourceFile).use { input ->
                                FileOutputStream(pfd.fileDescriptor).use { output ->
                                    input.copyTo(output)
                                    output.flush()
                                    Os.fsync(pfd.fileDescriptor)
                                }
                            }
                        }
                        DocumentsContract.renameDocument(resolver, temporary, name)
                            ?: error("The selected storage provider cannot safely publish the completed file.")
                    } catch (error: Exception) {
                        runCatching { DocumentsContract.deleteDocument(resolver, temporary) }
                        throw error
                    }
                    publishedUris += published
                    writeExportJournal(id, publishedUris, null, null, null)
                    if (firstUri == null) {
                        firstUri = published
                        firstFileName = name
                    }
                    fileCount++
                    exported += sourceFile.length()
                }
            } catch (error: Exception) {
                // A multi-file export is not atomic; remove files already published so
                // a failed export never leaves a half-visible torrent in the folder.
                cleanupInterruptedExport(id)
                throw error
            }
            val primaryName = firstFileName ?: entry.displayName
            val extension = primaryName.substringAfterLast('.', "").lowercase(java.util.Locale.ROOT)
            val mimeType = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
                ?: "application/octet-stream"
            TorrentExportResult(
                savedUri = firstUri?.toString() ?: entry.destination.toString(),
                publishedUris = publishedUris.map(Uri::toString),
                // A single-file torrent gets its real file name; multi-file torrents keep the torrent name.
                fileName = if (fileCount == 1) primaryName else entry.displayName,
                bytes = exported,
                fileCount = fileCount,
                mimeType = mimeType
            )
        }
    }

    /** Stops seeding and removes the private copy after the completed data has been exported. */
    suspend fun releaseAfterExport(id: String) = withContext(Dispatchers.IO) {
        runCatching { exportJournal(id).delete() }
        val entry = entries.remove(id)
        entry?.handle?.let { handle -> if (handle.isValid) runCatching { session.remove(handle) } }
        runCatching { (entry?.dataDir ?: dataDirectory(id)).deleteRecursively() }
        runCatching { metadataFile(id).delete() }
    }

    private fun uniqueName(parent: DocumentFile, requested: String): String {
        if (parent.findFile(requested) == null) return requested
        val extension = requested.substringAfterLast('.', "").let { if (it.isNotEmpty() && it != requested) ".$it" else "" }
        val base = requested.removeSuffix(extension).ifBlank { "torrent-file" }
        for (index in 2..999) {
            val candidate = "$base ($index)$extension"
            if (parent.findFile(candidate) == null) return candidate
        }
        return "$base-${System.currentTimeMillis()}$extension"
    }

    private fun dataDirectory(id: String) = File(root, torrentStorageId(id))

    private fun metadataFile(id: String) =
        File(root, "metadata-${torrentStorageId(id)}.torrent")

    private fun exportJournal(id: String) =
        android.util.AtomicFile(File(root, "export-${torrentStorageId(id)}.json"))

    private fun writeExportJournal(
        id: String,
        published: List<Uri>,
        parent: Uri?,
        finalName: String?,
        temporary: Uri?
    ) {
        val pending = if (parent != null && finalName != null && temporary != null) {
            JSONObject().put("parent", parent.toString()).put("name", finalName).put("temporary", temporary.toString())
        } else null
        val json = JSONObject()
            .put("published", JSONArray(published.map(Uri::toString)))
            .put("pending", pending ?: JSONObject.NULL)
        val atomic = exportJournal(id)
        val output = atomic.startWrite()
        try {
            output.write(json.toString().toByteArray(Charsets.UTF_8))
            atomic.finishWrite(output)
        } catch (error: Exception) {
            atomic.failWrite(output)
            throw error
        }
    }

    private fun cleanupInterruptedExport(id: String) {
        val atomic = exportJournal(id)
        if (!atomic.baseFile.exists() && !File("${atomic.baseFile.path}.bak").exists()) return
        val json = runCatching {
            atomic.openRead().bufferedReader(Charsets.UTF_8).use { JSONObject(it.readText()) }
        }.getOrNull() ?: run { atomic.delete(); return }
        json.optJSONArray("published")?.let { published ->
            for (index in 0 until published.length()) {
                runCatching { DocumentsContract.deleteDocument(resolver, Uri.parse(published.getString(index))) }
            }
        }
        json.optJSONObject("pending")?.let { pending ->
            val temporary = pending.optString("temporary").takeIf(String::isNotBlank)
            if (temporary != null) runCatching { DocumentsContract.deleteDocument(resolver, Uri.parse(temporary)) }
        }
        atomic.delete()
    }

    private fun torrentStorageId(id: String): String =
        id.replace(Regex("[^A-Za-z0-9_-]"), "_").ifEmpty { "_" }

    suspend fun cleanupOrphans(retainedIds: Set<String>) = withContext(Dispatchers.IO) {
        val retained = (retainedIds + entries.keys).map(::torrentStorageId).toSet()
        root.listFiles()?.forEach { file ->
            val storageId = file.name.removePrefix("metadata-").removePrefix("export-").removeSuffix(".torrent").removeSuffix(".json")
            val owned = file.name.startsWith("metadata-") || file.name.startsWith("export-") || file.isDirectory
            if (owned && storageId !in retained) {
                if (file.isDirectory) file.deleteRecursively() else file.delete()
            }
        }
    }

    private fun pauseValidHandles() {
        entries.values.forEach { entry ->
            entry.handle?.takeIf { it.isValid }?.let { runCatching { it.pause() } }
        }
    }

    private fun ensureStarted() {
        if (started) return
        synchronized(lock) {
            if (!started) {
                session.addListener(listener)
                val settings = SettingsPack()
                // libtorrent uses zero for unlimited transfer rates. No user speed cap is read.
                settings.downloadRateLimit(0)
                settings.uploadRateLimit(0)
                settings.setEnableDht(true)
                settings.setEnableLsd(false)
                settings.connectionsLimit(80)
                session.start(SessionParams(settings))
                started = true
            }
        }
    }

    companion object {
        fun parseMagnetHash(magnet: String): String = Regex("(?:[?&])xt=urn:btih:([A-Fa-f0-9]{40}|[A-Z2-7]{32})(?:&|$)", RegexOption.IGNORE_CASE)
        .find(magnet)?.groupValues?.get(1)?.let { value ->
            if (value.length == 40) value.lowercase() else runCatching {
                val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
                var buffer = 0
                var bits = 0
                val bytes = buildList {
                    value.uppercase().forEach { char ->
                        val digit = alphabet.indexOf(char)
                        require(digit >= 0)
                        buffer = (buffer shl 5) or digit
                        bits += 5
                        if (bits >= 8) { bits -= 8; add((buffer shr bits) and 255) }
                    }
                }
                require(bytes.size == 20 && bits == 0)
                bytes.joinToString("") { it.toString(16).padStart(2, '0') }
            }.getOrDefault("")
        } ?: ""
    }

    private fun magnetHash(magnet: String): String = parseMagnetHash(magnet)

    private data class TorrentEntry(
        val id: String,
        val hash: String,
        val dataDir: File,
        var destination: Uri,
        var displayName: String,
        @Volatile var handle: TorrentHandle? = null,
        @Volatile var finished: Boolean = false,
        @Volatile var error: String? = null
    )
}

data class TorrentSnapshot(val id: String, val downloadedBytes: Long, val totalBytes: Long, val bytesPerSecond: Long, val complete: Boolean, val error: String?)
data class TorrentExportResult(val savedUri: String, val publishedUris: List<String>, val fileName: String, val bytes: Long, val fileCount: Int, val mimeType: String)
