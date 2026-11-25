package com.cyron.manager.data

import com.cyron.manager.domain.model.DownloadSettings
import com.cyron.manager.domain.model.DownloadStatus
import com.cyron.manager.domain.model.DownloadTask
import com.cyron.manager.domain.repository.SettingsRepository
import com.cyron.manager.domain.repository.DownloadRepository as DownloadRepositoryContract
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class RoomDownloadRepository @Inject constructor(
    private val database: CyronDatabase
) : DownloadRepositoryContract {
    private val dao get() = database.downloadDao()
    override fun observeDownloads(): Flow<List<DownloadTask>> = dao.observeAll().map { items -> items.map(DownloadEntity::toDomain) }
    override suspend fun getDownload(id: String) = dao.get(id)?.toDomain()
    override suspend fun saveDownload(task: DownloadTask) = dao.upsert(task.toEntity())
    override suspend fun updateProgress(id: String, downloadedBytes: Long, totalBytes: Long) { dao.updateProgress(id, downloadedBytes, totalBytes) }
    override suspend fun updateStatus(id: String, status: DownloadStatus, error: String?) {
        dao.updateStatus(id, status.name, error, System.currentTimeMillis())
    }
    override suspend fun pauseIfActive(id: String, status: DownloadStatus, error: String?) {
        dao.pauseIfActive(id, status.name, error, System.currentTimeMillis())
    }
    override suspend fun removeDownload(id: String) = dao.delete(id)
    override suspend fun interruptedDownloads() = dao.interrupted().map(DownloadEntity::toDomain)
    override suspend fun resumableDownloads() = dao.resumable().map(DownloadEntity::toDomain)
    override suspend fun queuedDownloads(limit: Int) = dao.queued(limit.coerceIn(1, 64)).map(DownloadEntity::toDomain)
    override suspend fun updatePriority(id: String, priority: Int) = dao.updatePriority(id, priority.coerceIn(-10, 10), System.currentTimeMillis())
    override suspend fun pruneHistory(before: Long) = dao.pruneHistory(before)
    override suspend fun activeCount() = dao.activeCount()
    override suspend fun folderUsage(folderUri: String) = dao.folderUsage(folderUri)

    override suspend fun recoverInterrupted() {
        dao.interrupted().forEach { entity ->
            val task = entity.toDomain()
            val hasSourceDestination = if (task.source == com.cyron.manager.domain.model.DownloadSource.HTTP) !task.savedUri.isNullOrBlank() else !(task.outputFolderUri ?: task.savedUri).isNullOrBlank()
            if (!hasSourceDestination) {
                dao.upsert(entity.copy(status = DownloadStatus.FAILED, error = "Choose the destination folder again to resume securely.", updatedAt = System.currentTimeMillis()))
            } else {
                dao.upsert(entity.copy(status = if (task.status == DownloadStatus.PUBLISHING) DownloadStatus.PUBLISHING else DownloadStatus.QUEUED, error = null, updatedAt = System.currentTimeMillis()))
            }
        }
    }
}

@Singleton
class DataStoreSettingsRepository @Inject constructor(private val dataStore: SettingsStore) : SettingsRepository {
    override val settings: Flow<DownloadSettings> = dataStore.settings
    override suspend fun updateMaxConcurrent(value: Int) { dataStore.setMaxConcurrent(value) }
    override suspend fun updateConnections(value: Int) { dataStore.setConnections(value) }
    override suspend fun updateTheme(dark: Boolean) { dataStore.setDarkTheme(dark) }
    override suspend fun updateFollowSystemTheme(enabled: Boolean) { dataStore.setFollowSystemTheme(enabled) }
    override suspend fun updateLanguage(language: String) { dataStore.setLanguage(language) }
    override suspend fun updateNotifications(enabled: Boolean) { dataStore.setNotifications(enabled) }
    override suspend fun updateWifiOnly(enabled: Boolean) { dataStore.setWifiOnly(enabled) }
    override suspend fun updateHistoryRetentionDays(days: Int) { dataStore.setHistoryRetentionDays(days) }
    override suspend fun updateDestinationFolder(treeUri: String) { dataStore.setFolder(treeUri) }
}
