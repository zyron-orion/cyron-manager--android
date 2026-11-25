package com.cyron.manager.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE id = :id LIMIT 1")
    suspend fun get(id: String): DownloadEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DownloadEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<DownloadEntity>)

    @Query("UPDATE downloads SET downloadedBytes = :downloadedBytes, totalBytes = CASE WHEN :totalBytes > 0 THEN :totalBytes ELSE totalBytes END WHERE id = :id AND status = 'DOWNLOADING'")
    suspend fun updateProgress(id: String, downloadedBytes: Long, totalBytes: Long): Int

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM downloads WHERE status IN ('DOWNLOADING','PROBING','PUBLISHING') ORDER BY priority DESC, createdAt ASC")
    suspend fun interrupted(): List<DownloadEntity>

    @Query("SELECT * FROM downloads WHERE status IN ('QUEUED','PAUSED','DOWNLOADING','PROBING','PUBLISHING') ORDER BY priority DESC, createdAt ASC")
    suspend fun resumable(): List<DownloadEntity>

    @Query("UPDATE downloads SET priority = :priority, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updatePriority(id: String, priority: Int, updatedAt: Long)

    @Query("UPDATE downloads SET status = :status, error = :error, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, error: String?, updatedAt: Long)

    @Query("UPDATE downloads SET status = :status, error = :error, updatedAt = :updatedAt WHERE id = :id AND status IN ('QUEUED','PROBING','DOWNLOADING')")
    suspend fun pauseIfActive(id: String, status: String, error: String?, updatedAt: Long): Int

    @Query("DELETE FROM downloads WHERE status IN ('COMPLETED','FAILED','CANCELLED') AND updatedAt < :before")
    suspend fun pruneHistory(before: Long): Int

    @Query("SELECT COUNT(*) FROM downloads WHERE status IN ('DOWNLOADING','PROBING','PUBLISHING')")
    suspend fun activeCount(): Int

    @Query("SELECT COUNT(*) FROM downloads WHERE outputFolderUri = :folder OR (savedUri = :folder AND outputFolderUri IS NULL)")
    suspend fun folderUsage(folder: String): Int

    @Query("SELECT * FROM downloads WHERE status = 'QUEUED' ORDER BY priority DESC, createdAt ASC LIMIT :limit")
    suspend fun queued(limit: Int): List<DownloadEntity>
}
