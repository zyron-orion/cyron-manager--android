package com.cyron.manager.storage

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.system.Os
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ApplicationContext
import com.cyron.manager.domain.model.ByteRange
import com.cyron.manager.domain.model.DownloadName
import com.cyron.manager.domain.model.ProbeResult
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

@Singleton
class SafFileStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val resolver: ContentResolver
) {
    private val root = File(context.noBackupFilesDir, "cyron-download-segments").apply {
        if (!exists() && !mkdirs()) error("Could not initialize private app storage for download segments.")
    }
    private val publicationMutex = Mutex()

    private fun safeId(id: String): String {
        require(id.matches(Regex("[A-Za-z0-9._-]{1,80}"))) { "Invalid download ID." }
        return id
    }

    private fun directory(id: String): File = File(root, safeId(id)).canonicalFile.also {
        check(it.parentFile == root.canonicalFile) { "Temporary-file path escaped the application sandbox." }
    }

    private fun segment(id: String, index: Int): File {
        require(index in 0..15)
        return File(directory(id), "$index.part")
    }

    private fun manifest(id: String) = android.util.AtomicFile(File(directory(id), "manifest.json"))
    private fun publicationJournal(id: String) = android.util.AtomicFile(File(directory(id), "publication.json"))

    private data class PublicationJournal(
        val documentUri: String,
        val treeUri: String,
        val temporaryName: String,
        val finalName: String,
        val sha256: String?,
        val bytes: Long
    )

    private fun readPublicationJournal(id: String): PublicationJournal? = runCatching {
        publicationJournal(id).openRead().bufferedReader(Charsets.UTF_8).use { input ->
            val json = JSONObject(input.readText())
            PublicationJournal(
                json.getString("documentUri"),
                json.getString("treeUri"),
                json.getString("temporaryName"),
                json.getString("finalName"),
                json.optString("sha256").takeIf(String::isNotBlank),
                json.optLong("bytes", -1L)
            )
        }
    }.getOrNull()

    private fun writePublicationJournal(id: String, journal: PublicationJournal) {
        val atomic = publicationJournal(id)
        val json = JSONObject()
            .put("documentUri", journal.documentUri)
            .put("treeUri", journal.treeUri)
            .put("temporaryName", journal.temporaryName)
            .put("finalName", journal.finalName)
            .put("sha256", journal.sha256.orEmpty())
            .put("bytes", journal.bytes)
        val output = atomic.startWrite()
        try {
            output.write(json.toString().toByteArray(Charsets.UTF_8))
            atomic.finishWrite(output)
        } catch (error: Exception) {
            atomic.failWrite(output)
            throw error
        }
    }

    private fun removePublicationJournal(id: String) {
        publicationJournal(id).delete()
    }

    suspend fun recoverPublication(id: String): PublishedFile? = withContext(Dispatchers.IO) {
        val journal = readPublicationJournal(id) ?: return@withContext null
        val tree = DocumentFile.fromTreeUri(context, Uri.parse(journal.treeUri))
        val document = DocumentFile.fromSingleUri(context, Uri.parse(journal.documentUri))?.takeIf { it.exists() }
            ?: tree?.findFile(journal.finalName)?.takeIf { it.exists() }
        if (document == null || document.name == journal.temporaryName) {
            document?.let { runCatching { DocumentsContract.deleteDocument(resolver, it.uri) } }
            removePublicationJournal(id)
            return@withContext null
        }
        val verified = if (!journal.sha256.isNullOrBlank() && journal.bytes >= 0) {
            journal.sha256 to journal.bytes
        } else {
            digestDocument(document.uri)
        }
        PublishedFile(document.uri, verified.first, verified.second)
    }

    suspend fun finalizePublication(id: String) = withContext(Dispatchers.IO) {
        runCatching {
            removePublicationJournal(id)
            deleteParts(id)
        }
    }

    private fun digestDocument(uri: Uri): Pair<String, Long> {
        val digest = MessageDigest.getInstance("SHA-256")
        var bytes = 0L
        resolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
                bytes += count
            }
        } ?: throw LocalStorageException(StorageMessage.SEGMENT_READ_FAILED)
        return digest.digest().joinToString("") { "%02x".format(it) } to bytes
    }

    suspend fun prepare(id: String, url: String, probe: ProbeResult, ranges: List<ByteRange>) = withContext(Dispatchers.IO) {
        require(ranges.isNotEmpty() && ranges.size <= 16)
        val dir = directory(id)
        if (!dir.exists() && !dir.mkdirs()) error(StorageMessage.DIRECTORY_UNAVAILABLE)
        val atomic = manifest(id)
        val saved = runCatching {
            atomic.openRead().bufferedReader(Charsets.UTF_8).use { JSONObject(it.readText()) }
        }.getOrNull()
        val signatureMatches = saved != null && probe.hasStrongValidator && saved.optString("url") == url &&
            saved.optLong("length", -1) == probe.totalBytes &&
            saved.optString("etag").takeIf(String::isNotBlank) == probe.etag &&
            saved.optString("lastModified").takeIf(String::isNotBlank) == probe.lastModified &&
            saved.optString("ranges") == ranges.joinToString(",") { "${it.start}-${it.endInclusive}" }
        if (!signatureMatches) {
            dir.listFiles()?.forEach { file -> if (!file.delete() && file.exists()) throw LocalStorageException(StorageMessage.STALE_SEGMENT_DISCARD_FAILED) }
            val json = JSONObject().put("url", url).put("length", probe.totalBytes)
                .put("etag", probe.etag.orEmpty()).put("lastModified", probe.lastModified.orEmpty())
                .put("ranges", ranges.joinToString(",") { "${it.start}-${it.endInclusive}" })
            val output = atomic.startWrite()
            try {
                output.write(json.toString().toByteArray(Charsets.UTF_8))
                atomic.finishWrite(output)
            } catch (error: Exception) {
                atomic.failWrite(output)
                throw error
            }
        }
        ranges.forEach { part ->
            val file = segment(id, part.index)
            require(file.canonicalFile.parentFile == dir.canonicalFile)
            if (!file.exists() && !file.createNewFile()) throw LocalStorageException(StorageMessage.SEGMENT_CREATE_FAILED)
            if (file.length() > part.length) {
                FileOutputStream(file, false).use { it.fd.sync() }
            }
        }
        dir.listFiles()?.filter { it.name.endsWith(".merge") }?.forEach { if (!it.delete()) throw LocalStorageException(StorageMessage.STALE_MERGE_REMOVE_FAILED) }
    }

    suspend fun existingBytes(id: String, totalBytes: Long): Long = withContext(Dispatchers.IO) {        (directory(id).listFiles()?.filter { it.name.endsWith(".part") } ?: emptyList())
            .sumOf { it.length() }.coerceIn(0, totalBytes)
    }

    suspend fun partLength(id: String, index: Int): Long = withContext(Dispatchers.IO) { segment(id, index).length() }

    /**
     * Free bytes on the private partition that stages segments and the merged file. -1 means the system
     * could not report the size, so a real zero (a full disk) is never mistaken for "unknown".
     */
     fun usableBytes(): Long = runCatching { if (root.totalSpace > 0L) root.usableSpace else -1L }.getOrDefault(-1L)

    suspend fun truncatePart(id: String, index: Int) = withContext(Dispatchers.IO) {
        try {
            FileOutputStream(segment(id, index), false).use { it.fd.sync() }
        } catch (error: java.io.IOException) {
            throw LocalStorageException(StorageMessage.SEGMENT_WRITE_FAILED, error)
        }
    }

    suspend fun openPart(id: String, index: Int, offset: Long): FileOutputStream = withContext(Dispatchers.IO) {
        val dir = directory(id)
        val file = segment(id, index).canonicalFile
        require(file.parentFile == dir.canonicalFile)
        require(file.isFile && file.length() >= offset) { "The segment was not validated." }
        FileOutputStream(file, offset > 0)
    }

    fun appendPart(output: FileOutputStream, bytes: ByteArray, count: Int) {
        require(count in 1..bytes.size)
        output.write(bytes, 0, count)
    }

    suspend fun mergeAndVerify(id: String, ranges: List<ByteRange>, expectedBytes: Long, expectedSha256: String?): MergedFile = withContext(Dispatchers.IO) {
        val dir = directory(id)
        dir.listFiles()?.filter { it.name.endsWith(".merge") }?.forEach { if (!it.delete()) throw LocalStorageException(StorageMessage.ABANDONED_MERGE_REPLACE_FAILED) }
        val free = usableBytes()
        if (!hasEnoughStorage(free, expectedBytes + MERGE_HEADROOM_BYTES)) {
            throw LocalStorageException(StorageMessage.MERGE_SPACE_INSUFFICIENT)
        }
        val outputFile = File(dir, "verified-${UUID.randomUUID()}.merge").canonicalFile
        require(outputFile.parentFile == dir.canonicalFile)
        try {
            FileOutputStream(outputFile).use { output ->
                for (part in ranges.sortedBy { it.start }) {
                    val inputFile = segment(id, part.index).canonicalFile
                    if (inputFile.parentFile != dir.canonicalFile || !inputFile.isFile || inputFile.length() != part.length) {
                        throw LocalStorageException(StorageMessage.SEGMENT_MISSING)
                    }
                    FileInputStream(inputFile).use { input ->
                        val buffer = ByteArray(32 * 1024)
                        var bytesCopied = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count == -1) break
                            bytesCopied += count
                            if (bytesCopied > part.length) throw LocalStorageException(StorageMessage.SEGMENT_TOO_LONG)
                            output.write(buffer, 0, count)
                        }
                        if (bytesCopied != part.length) throw LocalStorageException(StorageMessage.SEGMENT_TOO_SHORT)
                    }
                }
                output.fd.sync()
            }
            if (outputFile.length() != expectedBytes) throw LocalStorageException(StorageMessage.MERGED_SIZE_MISMATCH)
            val actual = digest(outputFile)
            if (expectedSha256 != null && !actual.equals(expectedSha256.trim(), ignoreCase = true)) {
                throw ChecksumMismatchException("The completed file does not match its expected SHA-256 checksum.")
            }
            // Keep the segments until commit() has published the file. If publishing
            // fails, the verified segments are what let the next attempt resume
            // instead of starting the whole download from zero.
            MergedFile(outputFile, actual)
        } catch (error: Exception) {
            runCatching { outputFile.delete() }
            // Reading or writing the private staging files failed; that is local storage, not the network.
            throw if (error is java.io.IOException && error !is LocalStorageException && error !is ChecksumMismatchException) {
                LocalStorageException(StorageMessage.SEGMENT_READ_FAILED, error)
            } else error
        }
    }

    suspend fun sha256(file: File): String = withContext(Dispatchers.IO) { digest(file) }

    /**
     * Verifies the destination before the network transfer starts. A provider can
     * lose its grant while the app is in the background; discovering that only
     * after downloading the whole file wastes the user's data and time.
     */
    suspend fun ensureWritable(treeUri: Uri) = withContext(Dispatchers.IO) {
        val tree = DocumentFile.fromTreeUri(context, treeUri)
            ?: throw SecurityException("The selected system folder is no longer accessible.")
        if (!tree.isDirectory) throw SecurityException("The selected system folder is not writable.")
        val probe = DocumentsContract.createDocument(resolver, tree.uri, "application/octet-stream", ".cyron-${UUID.randomUUID()}.probe")
            ?: throw LocalStorageException(StorageMessage.DESTINATION_REFUSED)
        if (!runCatching { DocumentsContract.deleteDocument(resolver, probe) }.getOrDefault(false)) {
            throw LocalStorageException(StorageMessage.DESTINATION_WRITE_FAILED)
        }
    }

    /**
     * Merges the verified segments straight into the destination provider and hashes
     * each byte on the way. This avoids the old sequence of a private merged copy
     * plus a second SAF copy, which temporarily required roughly twice the file size.
     * Segments are retained on an I/O/publish failure so a retry can continue.
     */
    suspend fun mergeAndPublish(
        id: String,
        ranges: List<ByteRange>,
        expectedBytes: Long,
        expectedSha256: String?,
        treeUri: Uri,
        fileName: String,
        mimeType: String
    ): PublishedFile = withContext(Dispatchers.IO) {
        val dir = directory(id)
        val tree = DocumentFile.fromTreeUri(context, treeUri)
            ?: throw SecurityException("The selected system folder is no longer accessible.")
        if (!tree.isDirectory) throw SecurityException("The selected system folder is not writable.")
        val temporaryName = ".cyron-${UUID.randomUUID()}.partial"
        val temp = DocumentsContract.createDocument(resolver, tree.uri, mimeType.ifBlank { "application/octet-stream" }, temporaryName)
            ?: throw LocalStorageException(StorageMessage.DESTINATION_REFUSED)
        val requestedName = DownloadName.sanitize(fileName)
        try {
            writePublicationJournal(
                id,
                PublicationJournal(temp.toString(), treeUri.toString(), temporaryName, requestedName, null, -1L)
            )
        } catch (error: Exception) {
            runCatching { DocumentsContract.deleteDocument(resolver, temp) }
            throw error
        }
        var renamed = false
        var checksumFailed = false
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            var total = 0L
            val descriptor = resolver.openFileDescriptor(temp, "w", null)
                ?: throw LocalStorageException(StorageMessage.DESTINATION_WRITE_FAILED)
            descriptor.use { pfd ->
                FileOutputStream(pfd.fileDescriptor).use { output ->
                    for (part in ranges.sortedBy { it.start }) {
                        val inputFile = segment(id, part.index).canonicalFile
                        if (inputFile.parentFile != dir.canonicalFile || !inputFile.isFile || inputFile.length() != part.length) {
                            throw LocalStorageException(StorageMessage.SEGMENT_MISSING)
                        }
                        FileInputStream(inputFile).use { input ->
                            val buffer = ByteArray(64 * 1024)
                            var copied = 0L
                            while (true) {
                                val count = input.read(buffer)
                                if (count == -1) break
                                copied += count
                                if (copied > part.length) throw LocalStorageException(StorageMessage.SEGMENT_TOO_LONG)
                                output.write(buffer, 0, count)
                                digest.update(buffer, 0, count)
                                total += count
                            }
                            if (copied != part.length) throw LocalStorageException(StorageMessage.SEGMENT_TOO_SHORT)
                        }
                    }
                    output.flush()
                    Os.fsync(pfd.fileDescriptor)
                }
            }
            if (total != expectedBytes) throw LocalStorageException(StorageMessage.MERGED_SIZE_MISMATCH)
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            if (expectedSha256 != null && !actual.equals(expectedSha256.trim(), ignoreCase = true)) {
                checksumFailed = true
                throw ChecksumMismatchException("The completed file does not match its expected SHA-256 checksum.")
            }
            val published = publicationMutex.withLock {
                val finalName = uniqueName(tree, requestedName)
                writePublicationJournal(
                    id,
                    PublicationJournal(temp.toString(), treeUri.toString(), temporaryName, finalName, actual, total)
                )
                val uri = DocumentsContract.renameDocument(resolver, temp, finalName)
                    ?: throw LocalStorageException(StorageMessage.PUBLISH_FAILED)
                runCatching {
                    writePublicationJournal(
                        id,
                        PublicationJournal(uri.toString(), treeUri.toString(), temporaryName, finalName, actual, total)
                    )
                }
                uri
            }
            renamed = true
            PublishedFile(published, actual, total)
        } catch (error: Exception) {
            if (!renamed) {
                runCatching { DocumentsContract.deleteDocument(resolver, temp) }
                removePublicationJournal(id)
            }
            if (checksumFailed) {
                runCatching { deleteParts(id) }
                removePublicationJournal(id)
            }
            throw if (error is java.io.IOException && error !is LocalStorageException && error !is ChecksumMismatchException) {
                LocalStorageException(StorageMessage.DESTINATION_WRITE_FAILED, error)
            } else error
        }
    }

    /** Writes under a random hidden provider-created name; exposes the final filename only after all bytes have been fsynced. */
    suspend fun commit(id: String, file: File, treeUri: Uri, fileName: String, mimeType: String): Uri = withContext(Dispatchers.IO) {
        val tree = DocumentFile.fromTreeUri(context, treeUri)
            ?: throw SecurityException("The selected system folder is no longer accessible.")
        if (!tree.isDirectory || !tree.canWrite()) throw SecurityException("The selected system folder is not writable.")
        val temporaryName = ".cyron-${UUID.randomUUID()}.partial"
        val temp = DocumentsContract.createDocument(resolver, tree.uri, mimeType.ifBlank { "application/octet-stream" }, temporaryName)
            ?: throw LocalStorageException(StorageMessage.DESTINATION_REFUSED)
        val requestedName = DownloadName.sanitize(fileName)
        try {
            writePublicationJournal(
                id,
                PublicationJournal(temp.toString(), treeUri.toString(), temporaryName, requestedName, null, -1L)
            )
        } catch (error: Exception) {
            runCatching { DocumentsContract.deleteDocument(resolver, temp) }
            throw error
        }
        var renamed = false
        try {
            val descriptor = resolver.openFileDescriptor(temp, "w", null)
                ?: throw LocalStorageException(StorageMessage.DESTINATION_WRITE_FAILED)
            descriptor.use { pfd ->
                FileInputStream(file).use { input ->
                    FileOutputStream(pfd.fileDescriptor).use { output ->
                        val buffer = ByteArray(32 * 1024)
                        while (true) {
                            val count = input.read(buffer)
                            if (count == -1) break
                            output.write(buffer, 0, count)
                        }
                        output.flush()
                        Os.fsync(pfd.fileDescriptor)
                    }
                }
            }
            val published = publicationMutex.withLock {
                val finalName = uniqueName(tree, requestedName)
                writePublicationJournal(
                    id,
                    PublicationJournal(temp.toString(), treeUri.toString(), temporaryName, finalName, null, -1L)
                )
                val uri = DocumentsContract.renameDocument(resolver, temp, finalName)
                    ?: throw LocalStorageException(StorageMessage.PUBLISH_FAILED)
                runCatching {
                    writePublicationJournal(
                        id,
                        PublicationJournal(uri.toString(), treeUri.toString(), temporaryName, finalName, null, -1L)
                    )
                }
                uri
            }
            renamed = true
            runCatching { file.delete() }
            published
        } catch (error: Exception) {
            if (!renamed) {
                runCatching { DocumentsContract.deleteDocument(resolver, temp) }
                removePublicationJournal(id)
            }
            // The document provider can fail with any IOException (a full card, a dropped cloud provider);
            // none of them is a network error of the HTTP transfer.
            throw if (error is java.io.IOException && error !is LocalStorageException) {
                LocalStorageException(StorageMessage.DESTINATION_WRITE_FAILED, error)
            } else error
        }
    }

    private fun uniqueName(tree: DocumentFile, requested: String): String {
        if (tree.findFile(requested) == null) return requested
        val extension = requested.substringAfterLast('.', "").let { if (it.isNotEmpty() && it != requested) ".$it" else "" }
        val base = requested.removeSuffix(extension).ifBlank { "download" }
        for (index in 2..999) {
            val candidate = "$base ($index)$extension"
            if (tree.findFile(candidate) == null) return candidate
        }
        return "$base-${System.currentTimeMillis()}$extension"
    }

    suspend fun cleanupParts(id: String) = withContext(Dispatchers.IO) {
        directory(id).listFiles()?.forEach { file ->
            if (file.name != "publication.json" && file.name != "publication.json.bak") file.delete()
        }
    }

    suspend fun deleteParts(id: String) = withContext(Dispatchers.IO) {
        val dir = directory(id)
        dir.listFiles()?.forEach { if (!it.delete()) throw LocalStorageException(StorageMessage.PARTIAL_REMOVE_FAILED) }
        if (dir.exists() && !dir.delete()) throw LocalStorageException(StorageMessage.TEMP_DIRECTORY_REMOVE_FAILED)
    }

    suspend fun invalidateParts(id: String) = withContext(Dispatchers.IO) {
        directory(id).listFiles()?.filter { it.extension == "part" || it.extension == "merge" }
            ?.forEach { if (!it.delete()) throw LocalStorageException(StorageMessage.SEGMENT_INVALIDATE_FAILED) }
    }

    private fun digest(file: File): String {        val md = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(32 * 1024)
            while (true) {
                val bytes = input.read(buffer)
                if (bytes < 0) break
                md.update(buffer, 0, bytes)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}

class ChecksumMismatchException(message: String) : java.io.IOException(message)

/** A verified merge result: the staging file and the single SHA-256 pass over it. */
data class MergedFile(val file: File, val sha256: String)

/** A file already published to the user's folder; no private copy remains. */
data class PublishedFile(val uri: Uri, val sha256: String, val bytes: Long)

private const val MERGE_HEADROOM_BYTES = 16L * 1024 * 1024

/** A failure of the app's own storage or of the user's document provider; it is never a network problem. */
class LocalStorageException(message: String, cause: Throwable? = null, val retryable: Boolean = true) : java.io.IOException(message, cause)

/** -1 means the free space is unknown; a real zero (a full disk) must fail instead of skipping the check. */
internal fun hasEnoughStorage(usableBytes: Long, requiredBytes: Long): Boolean = usableBytes < 0L || usableBytes >= requiredBytes

/** Every message here reaches the user, so each one has a Persian entry in UiText; a unit test keeps them in step. */
internal object StorageMessage {
    const val DIRECTORY_UNAVAILABLE = "Could not allocate an app-private download directory."
    const val STALE_SEGMENT_DISCARD_FAILED = "Could not safely discard a stale partial segment."
    const val SEGMENT_CREATE_FAILED = "Could not allocate a private segment file."
    const val STALE_MERGE_REMOVE_FAILED = "A previous unverified merge could not be safely removed."
    const val MERGE_SPACE_INSUFFICIENT = "Not enough free storage to merge and verify this download. Free some space and retry; the verified segments are kept."
    const val ABANDONED_MERGE_REPLACE_FAILED = "An abandoned merge could not be safely replaced."
    const val SEGMENT_MISSING = "A segment is missing or its size does not match the verified byte range; refusing to merge."
    const val SEGMENT_TOO_LONG = "A segment exceeded its validated byte range."
    const val SEGMENT_TOO_SHORT = "A segment ended before its validated byte range."
    const val MERGED_SIZE_MISMATCH = "The merged file size does not match the verified HTTP resource length."
    const val DESTINATION_REFUSED = "The selected folder refused a temporary download file."
    const val DESTINATION_WRITE_FAILED = "Could not write to the selected destination."
    const val PUBLISH_FAILED = "The provider cannot safely publish a verified file. Your incomplete download remains in private storage."
    const val PARTIAL_REMOVE_FAILED = "Could not securely remove a partial download."
    const val TEMP_DIRECTORY_REMOVE_FAILED = "Could not remove the download's temporary directory."
    const val SEGMENT_INVALIDATE_FAILED = "Could not safely invalidate a mismatched file segment."
    const val SEGMENT_WRITE_FAILED = "The download could not be written to storage. Free some space and retry; verified partial data is preserved."
    const val SEGMENT_READ_FAILED = "A downloaded segment could not be read from private storage."
}
