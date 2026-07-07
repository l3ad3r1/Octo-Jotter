package com.l3ad3r1.octojotter.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [NoteEntity::class, DraftEntity::class, TagEntity::class, NoteTagCrossRef::class, PluginEntity::class], version = 10, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun pluginDao(): PluginDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // v6 -> v7: add repository-sync columns to `notes`. Additive & nullable,
        // so existing gist/offline notes are preserved untouched.
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN repository TEXT")
                db.execSQL("ALTER TABLE notes ADD COLUMN path TEXT")
                db.execSQL("ALTER TABLE notes ADD COLUMN sha TEXT")
            }
        }

        // v7 -> v8: add the community-plugins table.
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS plugins (
                        id TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        version TEXT NOT NULL,
                        type TEXT NOT NULL,
                        author TEXT,
                        description TEXT,
                        enabled INTEGER NOT NULL DEFAULT 0,
                        sourceUrl TEXT,
                        payloadJson TEXT NOT NULL,
                        permissions TEXT NOT NULL DEFAULT '',
                        installedAt INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN deletedAt INTEGER")
                db.execSQL("ALTER TABLE notes ADD COLUMN pendingRemoteDelete INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE notes ADD COLUMN locked INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE notes ADD COLUMN encrypted INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE notes ADD COLUMN encryptionVersion INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE notes ADD COLUMN remoteUpdatedAt TEXT")
                db.execSQL("ALTER TABLE notes ADD COLUMN lastSyncedContentHash TEXT")
                db.execSQL("ALTER TABLE notes ADD COLUMN conflictState TEXT")
                db.execSQL("ALTER TABLE notes ADD COLUMN conflictedRemoteContent TEXT")
                db.execSQL("ALTER TABLE notes ADD COLUMN conflictedRemoteModifiedAt INTEGER")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS index_note_tag_cross_ref_tagName ON note_tag_cross_ref(tagName)")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "gist_notes_database"
                )
                    .addMigrations(MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
