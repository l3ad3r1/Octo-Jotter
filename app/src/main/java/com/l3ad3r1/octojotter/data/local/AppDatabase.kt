package com.l3ad3r1.octojotter.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [NoteEntity::class, DraftEntity::class, TagEntity::class, NoteTagCrossRef::class, PluginEntity::class, NoteEmbeddingEntity::class, TemplateEntity::class], version = 13, exportSchema = true)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun pluginDao(): PluginDao
    abstract fun noteEmbeddingDao(): NoteEmbeddingDao
    abstract fun templateDao(): TemplateDao

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

        // v10 -> v11: add the on-device AI embedding index (Phase 1 of
        // docs/ON-DEVICE-AI.md). Additive & local-only; existing notes are
        // untouched. Rows cascade-delete with their note. MUST be registered
        // below — without it, the destructive fallback would wipe every note.
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS note_embeddings (
                        id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        noteId INTEGER NOT NULL,
                        chunkIndex INTEGER NOT NULL,
                        chunkText TEXT NOT NULL,
                        vector BLOB NOT NULL,
                        contentHash TEXT NOT NULL,
                        model TEXT NOT NULL,
                        embeddedAt INTEGER NOT NULL,
                        FOREIGN KEY(noteId) REFERENCES notes(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_note_embeddings_noteId ON note_embeddings(noteId)"
                )
            }
        }

        // v11 -> v12: color-coded notes, Daily Notes, note-level reminders
        // (Task Reminders plugin), and the Templates plugin's own table.
        // Additive & nullable/defaulted, so every existing note is unaffected.
        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN color TEXT")
                db.execSQL("ALTER TABLE notes ADD COLUMN isDailyNote INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE notes ADD COLUMN reminderAt INTEGER")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS templates (
                        id TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        content TEXT NOT NULL,
                        createdAt INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
            }
        }

        // v12 -> v13: remember which filename a note occupies inside its Gist,
        // so a rename can be sent as a rename (old key + new `filename`) rather
        // than silently adding a second file. Additive & nullable; existing
        // notes back-fill on their next pull.
        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN remoteFilename TEXT")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "gist_notes_database"
                )
                    .addMigrations(
                        MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10,
                        MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13,
                    )
                    // Only the pre-v6 schemas may be dropped. They shipped before
                    // schemas were exported, so there is nothing to write a real
                    // migration against.
                    //
                    // Deliberately NOT a blanket fallbackToDestructiveMigration:
                    // that quietly deleted every note whenever a migration was
                    // missing, including one forgotten during a future schema
                    // bump. Room now throws instead, which fails in development
                    // rather than on someone's phone.
                    .fallbackToDestructiveMigrationFrom(
                        dropAllTables = true,
                        1, 2, 3, 4, 5,
                    )
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
