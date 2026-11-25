package com.cyron.manager.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [DownloadEntity::class], version = 6, exportSchema = true)
abstract class CyronDatabase : RoomDatabase() {
    abstract fun downloadDao(): DownloadDao

    companion object {
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE downloads ADD COLUMN source TEXT NOT NULL DEFAULT 'HTTP'")
                database.execSQL("ALTER TABLE downloads ADD COLUMN formatSelector TEXT")
            }
        }

        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE downloads ADD COLUMN displayName TEXT")
            }
        }

        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE downloads ADD COLUMN outputFolderUri TEXT")
            }
        }

        val MIGRATION_4_5: Migration = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE downloads ADD COLUMN publishedUris TEXT NOT NULL DEFAULT '[]'")
            }
        }

        val MIGRATION_5_6: Migration = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE downloads ADD COLUMN priority INTEGER NOT NULL DEFAULT 0")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_downloads_status_priority_createdAt ON downloads(status, priority, createdAt)")
            }
        }
    }
}
