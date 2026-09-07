package com.l3ad3r1.octojotter

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.l3ad3r1.octojotter.data.local.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The schema upgrade path, exercised against the checked-in schema JSON.
 *
 * `fallbackToDestructiveMigration` is scoped to versions 1–5, so from v6 up a
 * missing or wrong migration does not wipe notes — it throws, on launch, for
 * every existing install. That makes this the highest-consequence thing in the
 * database layer and the one worth testing directly rather than by inspection.
 *
 * Each test opens a database at the older version, writes a note into it, runs
 * the migration, and then lets Room validate the result against the exported
 * schema. Room's own validation catches column/type drift; the assertions here
 * cover the part it does not — that the user's rows survived.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DatabaseMigrationTest {

    private companion object {
        const val TEST_DB = "migration-test"
    }

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    /** Open the migrated database through Room so its schema validation runs. */
    private fun openWithRoom(): AppDatabase =
        Room.databaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
            TEST_DB,
        )
            .addMigrations(
                AppDatabase.MIGRATION_6_7, AppDatabase.MIGRATION_7_8, AppDatabase.MIGRATION_8_9,
                AppDatabase.MIGRATION_9_10, AppDatabase.MIGRATION_10_11, AppDatabase.MIGRATION_11_12,
                AppDatabase.MIGRATION_12_13,
            )
            // Verification queries below run inline on the test thread.
            .allowMainThreadQueries()
            .build()
            .also { helper.closeWhenFinished(it) }

    @Test
    fun `v12 to v13 adds remoteFilename and keeps existing notes`() {
        helper.createDatabase(TEST_DB, 12).use { db ->
            db.execSQL(
                """
                INSERT INTO notes (id, gistId, title, content, lastModifiedLocally, needsSync,
                                   pinned, tags, pendingRemoteDelete, locked, encrypted,
                                   encryptionVersion, isDailyNote)
                VALUES (1, 'gist-abc', 'Kept note', 'body text', 111, 0, 0, '[]', 0, 0, 0, 0, 0)
                """.trimIndent()
            )
        }

        helper.runMigrationsAndValidate(TEST_DB, 13, true, AppDatabase.MIGRATION_12_13)

        openWithRoom().query("SELECT title, content, gistId, remoteFilename FROM notes WHERE id = 1", null)
            .use { cursor ->
                assertTrue("the pre-migration note was lost", cursor.moveToFirst())
                assertEquals("Kept note", cursor.getString(0))
                assertEquals("body text", cursor.getString(1))
                assertEquals("gist-abc", cursor.getString(2))
                // New column exists and back-fills as null, not as a bogus value.
                assertTrue(cursor.isNull(3))
            }
    }

    @Test
    fun `the v11 to v13 chain runs and preserves the original note`() {
        // v11 is as far back as this can reach: schema export was switched on
        // late, so schemas/ starts at 11.json and MigrationTestHelper cannot
        // materialise a v6-v10 database to migrate from. Everything from 11
        // onward is covered, and running the steps as a chain matters — each
        // can pass alone while the sequence does not.
        helper.createDatabase(TEST_DB, 11).use { db ->
            db.execSQL(
                """
                INSERT INTO notes (id, gistId, title, content, lastModifiedLocally, needsSync,
                                   pinned, tags, pendingRemoteDelete, locked, encrypted,
                                   encryptionVersion)
                VALUES (1, 'gist-old', 'Older note', 'still here', 42, 0, 1, '[]', 0, 0, 0, 0)
                """.trimIndent()
            )
        }

        helper.runMigrationsAndValidate(
            TEST_DB, 13, true,
            AppDatabase.MIGRATION_11_12, AppDatabase.MIGRATION_12_13,
        )

        openWithRoom().query(
            "SELECT title, content, pinned, deletedAt, pendingRemoteDelete, remoteFilename FROM notes WHERE id = 1",
            null,
        ).use { cursor ->
            assertTrue("a note from v11 did not survive the upgrade chain", cursor.moveToFirst())
            assertEquals("Older note", cursor.getString(0))
            assertEquals("still here", cursor.getString(1))
            assertEquals(1, cursor.getInt(2))          // pinned preserved
            assertTrue(cursor.isNull(3))               // not in the trash
            assertEquals(0, cursor.getInt(4))          // not queued for deletion
            assertTrue(cursor.isNull(5))
        }
    }

    @Test
    fun `templates and embeddings tables exist after the chain`() {
        // `templates` is created by MIGRATION_11_12 rather than by Room's own
        // createSql, so a typo in that CREATE TABLE only shows up here.
        helper.createDatabase(TEST_DB, 11).close()
        helper.runMigrationsAndValidate(
            TEST_DB, 13, true,
            AppDatabase.MIGRATION_11_12, AppDatabase.MIGRATION_12_13,
        )

        val db = openWithRoom()
        db.query("SELECT name FROM sqlite_master WHERE type='table' AND name IN ('templates','note_embeddings','plugins')", null)
            .use { cursor ->
                val tables = buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) }
                assertEquals(setOf("templates", "note_embeddings", "plugins"), tables)
            }
    }

    @Test
    fun `a v13 note round-trips remoteFilename`() {
        // Guards the column actually being writable and readable, not merely
        // present — the Gist rename fix depends on persisting it.
        helper.createDatabase(TEST_DB, 12).close()
        helper.runMigrationsAndValidate(TEST_DB, 13, true, AppDatabase.MIGRATION_12_13)

        val db = openWithRoom()
        db.compileStatement(
            """
            INSERT INTO notes (id, title, content, lastModifiedLocally, needsSync, pinned, tags,
                               pendingRemoteDelete, locked, encrypted, encryptionVersion,
                               isDailyNote, remoteFilename)
            VALUES (2, 'Renamed', 'body', 7, 0, 0, '[]', 0, 0, 0, 0, 0, 'Old title.md')
            """.trimIndent()
        ).executeInsert()

        db.query("SELECT remoteFilename FROM notes WHERE id = 2", null).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Old title.md", cursor.getString(0))
        }
    }

    @Test
    fun `migrating does not resurrect a trashed note as pending deletion`() {
        // pendingRemoteDelete now means "queued for permanent deletion,
        // remote included". A note merely sitting in the Trash before the
        // upgrade must not come out of it queued for a remote delete.
        helper.createDatabase(TEST_DB, 12).use { db ->
            db.execSQL(
                """
                INSERT INTO notes (id, gistId, title, content, lastModifiedLocally, needsSync,
                                   pinned, tags, deletedAt, pendingRemoteDelete, locked,
                                   encrypted, encryptionVersion, isDailyNote)
                VALUES (3, 'gist-x', 'Trashed', 'body', 5, 0, 0, '[]', 999, 0, 0, 0, 0, 0)
                """.trimIndent()
            )
        }
        helper.runMigrationsAndValidate(TEST_DB, 13, true, AppDatabase.MIGRATION_12_13)

        openWithRoom().query("SELECT deletedAt, pendingRemoteDelete FROM notes WHERE id = 3", null)
            .use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(999, cursor.getInt(0))   // still in the trash
                assertEquals(0, cursor.getInt(1))     // but not queued for purge
            }
    }

    @Test
    fun `a fresh v13 database opens without migrations`() {
        // Catches entity/schema drift for new installs, which take createSql
        // rather than any migration.
        val db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).build()
        runBlocking { assertNull(db.noteDao().getNoteById(1)) }
        db.close()
    }
}
