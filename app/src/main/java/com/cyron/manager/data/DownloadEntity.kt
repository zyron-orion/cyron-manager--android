package com.cyron.manager.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.cyron.manager.domain.model.DownloadStatus
import com.cyron.manager.domain.model.DownloadTask

@Entity(tableName = "downloads", indices = [Index("status", "priority", "createdAt")])
data class DownloadEntity(
    @PrimaryKey val id: String,
    val url: String,
    val fileName: String,
    val mimeType: String,
    val totalBytes: Long,
    val rangeSupported: Boolean,
    val downloadedBytes: Long,
    val status: DownloadStatus,
    val connectionCount: Int,
    val priority: Int,
    val savedUri: String?,
    val publishedUris: String,
    val expectedSha256: String?,
    val actualSha256: String?,
    val etag: String?,
    val lastModified: String?,
    val source: String,
    val formatSelector: String?,
    val error: String?,
    val displayName: String?,
    val outputFolderUri: String?,
    val createdAt: Long,
    val updatedAt: Long
)

fun DownloadTask.toEntity() = DownloadEntity(
    id = id, url = url, fileName = fileName, mimeType = mimeType, totalBytes = totalBytes,
    rangeSupported = rangeSupported, downloadedBytes = downloadedBytes, status = status, connectionCount = connectionCount, priority = priority,
    savedUri = savedUri, publishedUris = org.json.JSONArray(publishedUris).toString(), expectedSha256 = expectedSha256, actualSha256 = actualSha256, etag = etag, lastModified = lastModified,
    source = source.name, formatSelector = formatSelector, error = error, displayName = displayName, outputFolderUri = outputFolderUri,
    createdAt = createdAt, updatedAt = updatedAt
)

fun DownloadEntity.toDomain() = DownloadTask(
    id = id, url = url, fileName = fileName, mimeType = mimeType, totalBytes = totalBytes,
    rangeSupported = rangeSupported, downloadedBytes = downloadedBytes, displayName = displayName, outputFolderUri = outputFolderUri, status = status, connectionCount = connectionCount, priority = priority,
    savedUri = savedUri, publishedUris = runCatching {
        val array = org.json.JSONArray(publishedUris)
        (0 until array.length()).mapNotNull { index -> array.optString(index).takeIf(String::isNotBlank) }
    }.getOrDefault(emptyList()), expectedSha256 = expectedSha256, actualSha256 = actualSha256,
    etag = etag, lastModified = lastModified,
    source = runCatching { com.cyron.manager.domain.model.DownloadSource.valueOf(source) }.getOrDefault(com.cyron.manager.domain.model.DownloadSource.HTTP),
    formatSelector = formatSelector, error = error, createdAt = createdAt, updatedAt = updatedAt
)
