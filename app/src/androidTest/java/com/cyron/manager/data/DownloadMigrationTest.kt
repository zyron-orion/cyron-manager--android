package com.cyron.manager.data

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DownloadMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        CyronDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test fun migratesVersion1DownloadsWithoutLosingRecords() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "migration-v1-test"
        context.deleteDatabase(databaseName)

        helper.createDatabase(databaseName, 1).apply {
            execSQL(
                """INSERT INTO downloads (
                    id, url, fileName, mimeType, totalBytes, rangeSupported, downloadedBytes, status,
                    connectionCount, savedUri, expectedSha256, actualSha256, etag, lastModified, error, createdAt, updatedAt
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                arrayOf<Any?>("legacy", "https://example.test/file.zip", "file.zip", "application/zip", 42L,
                    1, 12L, "PAUSED", 3, "content://provider/tree/downloads", null, null, "\"v1\"", null, null, 10L, 11L)
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            databaseName,
            6,
            true,
            CyronDatabase.MIGRATION_1_2,
            CyronDatabase.MIGRATION_2_3,
            CyronDatabase.MIGRATION_3_4,
            CyronDatabase.MIGRATION_4_5,
            CyronDatabase.MIGRATION_5_6
        )
        migrated.query("SELECT id, source, formatSelector, displayName, outputFolderUri, downloadedBytes, publishedUris, priority FROM downloads").use { cursor ->
            assertEquals(1, cursor.count)
            cursor.moveToFirst()
            assertEquals("legacy", cursor.getString(0))
            assertEquals("HTTP", cursor.getString(1))
            assertEquals(null, cursor.getString(2))
            assertEquals(null, cursor.getString(3))
            assertEquals(null, cursor.getString(4))
            assertEquals(12L, cursor.getLong(5))
            assertEquals("[]", cursor.getString(6))
            assertEquals(0, cursor.getInt(7))
        }
        migrated.close()
        context.deleteDatabase(databaseName)
    }
}
