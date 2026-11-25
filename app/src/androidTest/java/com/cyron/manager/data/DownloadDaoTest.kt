package com.cyron.manager.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import com.cyron.manager.domain.model.DownloadStatus
import com.cyron.manager.domain.model.DownloadTask
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DownloadDaoTest {
    private lateinit var database: CyronDatabase

    @Before fun openDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, CyronDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After fun closeDatabase() { database.close() }

    @Test fun persistedTaskRoundTripsValidatorsAndStatus() = runBlocking {
        val original = DownloadTask(
            id = "room-round-trip",
            url = "https://example.test/archive.zip",
            fileName = "archive.zip",
            totalBytes = 4096,
            downloadedBytes = 1024,
            rangeSupported = true,
            status = DownloadStatus.DOWNLOADING,
            connectionCount = 4,
            savedUri = "content://provider/tree/downloads",
            expectedSha256 = "a".repeat(64),
            etag = "\"asset-v2\"",
            lastModified = "Wed, 01 Jan 2025 00:00:00 GMT"
        )
        database.downloadDao().upsert(original.toEntity())

        val restored = database.downloadDao().get(original.id)?.toDomain()
        assertNotNull(restored)
        assertEquals(original, restored)
        assertEquals(1, database.downloadDao().updateProgress(original.id, 2048, original.totalBytes))
        assertEquals(2048L, database.downloadDao().get(original.id)?.downloadedBytes)

        database.downloadDao().upsert(original.copy(status = DownloadStatus.COMPLETED).toEntity())
        assertEquals(0, database.downloadDao().updateProgress(original.id, 4096, original.totalBytes))
        assertEquals(1024L, database.downloadDao().get(original.id)?.downloadedBytes)

        database.downloadDao().delete(original.id)
        assertEquals(null, database.downloadDao().get(original.id))
    }
}
