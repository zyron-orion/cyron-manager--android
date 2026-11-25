package com.cyron.manager.domain.repository

import com.cyron.manager.domain.model.DownloadSettings
import com.cyron.manager.domain.model.DownloadTask
import kotlinx.coroutines.flow.Flow

interface DownloadRepository {
    fun observeDownloads(): Flow<List<DownloadTask>>
    suspend fun getDownload(id: String): DownloadTask?
    suspend fun saveDownload(task: DownloadTask)
    suspend fun updateProgress(id: String, downloadedBytes: Long, totalBytes: Long)
    suspend fun updateStatus(id: String, status: com.cyron.manager.domain.model.DownloadStatus, error: String? = null)
    suspend fun pauseIfActive(id: String, status: com.cyron.manager.domain.model.DownloadStatus, error: String? = null)
    suspend fun removeDownload(id: String)
    suspend fun interruptedDownloads(): List<DownloadTask>
    suspend fun resumableDownloads(): List<DownloadTask>
    suspend fun queuedDownloads(limit: Int): List<DownloadTask>
    suspend fun updatePriority(id: String, priority: Int)
    suspend fun pruneHistory(before: Long): Int
    suspend fun activeCount(): Int
    suspend fun folderUsage(folderUri: String): Int
    suspend fun recoverInterrupted()
}

interface SettingsRepository {
    val settings: Flow<DownloadSettings>
    suspend fun updateMaxConcurrent(value: Int)
    suspend fun updateConnections(value: Int)
    suspend fun updateTheme(dark: Boolean)
    suspend fun updateFollowSystemTheme(enabled: Boolean)
    suspend fun updateLanguage(language: String)
    suspend fun updateNotifications(enabled: Boolean)
    suspend fun updateWifiOnly(enabled: Boolean)
    suspend fun updateHistoryRetentionDays(days: Int)
    suspend fun updateDestinationFolder(treeUri: String)
}
