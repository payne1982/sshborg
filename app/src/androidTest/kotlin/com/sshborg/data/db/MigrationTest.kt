package com.sshborg.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A database somebody already has must survive an update, and there is no second chance: a
 * migration that gets it wrong meets the user as "the app won't open any more", with their hosts
 * and keys inside. This is also the one part of the app that has already gone wrong here — a
 * 13→14 migration was added and then removed, which left a database newer than the schema.
 *
 * Room checks the result itself: [MigrationTestHelper.runMigrationsAndValidate] compares what the
 * migration produced against the schema exported from the entities, and fails on any difference —
 * a column with the wrong type, a forgotten index, a table left behind.
 *
 * Instrumented, because Room needs a real SQLite; it runs on the TV emulator like on anything else.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    /**
     * Version 12, written out by hand. There is no exported schema for it — exporting started at
     * 13 — and that is fine here: if these statements were not what 12 really was, the migration
     * would end up producing a schema Room refuses in the check below. Taken from 13's own SQL
     * minus exactly what [AppDatabase] adds in the 12→13 step: the two host columns and the group
     * position.
     */
    private val version12 = listOf(
        """CREATE TABLE IF NOT EXISTS `hosts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
           `label` TEXT NOT NULL, `hostname` TEXT NOT NULL, `port` INTEGER NOT NULL,
           `username` TEXT NOT NULL, `keyId` INTEGER, `password` TEXT, `encryptedPassword` TEXT,
           `knownHostsEntry` TEXT, `agentForwarding` INTEGER NOT NULL, `lastConnected` INTEGER,
           `jumpHosts` TEXT, `jumpHostKeys` TEXT, `portForwardings` TEXT,
           `jumpMode` TEXT NOT NULL, `jumpHostIdList` TEXT, `sftpStartMode` TEXT NOT NULL,
           `sftpStartDir` TEXT, `sftpShowHidden` INTEGER NOT NULL,
           `allowLegacyCiphers` INTEGER NOT NULL, `groupId` INTEGER, `color` INTEGER)""",
        """CREATE TABLE IF NOT EXISTS `ssh_keys` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
           `label` TEXT NOT NULL, `keyType` TEXT NOT NULL, `privateKeyPem` TEXT NOT NULL,
           `publicKey` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `encryptedBlob` TEXT)""",
        """CREATE TABLE IF NOT EXISTS `host_groups` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
           `name` TEXT NOT NULL, `color` INTEGER NOT NULL, `collapsed` INTEGER NOT NULL)""",
    )

    private fun createVersion12(): SupportSQLiteDatabase {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(NAME)
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(NAME)
            .callback(object : SupportSQLiteOpenHelper.Callback(12) {
                override fun onCreate(db: SupportSQLiteDatabase) = version12.forEach(db::execSQL)
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()
        return FrameworkSQLiteOpenHelperFactory().create(configuration).writableDatabase
    }

    // No spaces in the name: unlike a JVM unit test, an instrumented one is dexed, and dex
    // does not allow them.
    @Test fun aVersion12DatabaseKeepsItsHostsThroughTheUpgrade() {
        createVersion12().use { db ->
            db.execSQL(
                """INSERT INTO hosts (label, hostname, port, username, agentForwarding, jumpMode,
                   sftpStartMode, sftpShowHidden, allowLegacyCiphers)
                   VALUES ('fatberry', '10.0.0.9', 2222, 'payne', 0, 'simple', 'last', 0, 0)"""
            )
            db.execSQL("INSERT INTO host_groups (name, color, collapsed) VALUES ('casa', 42, 0)")
            db.execSQL(
                """INSERT INTO ssh_keys (label, keyType, privateKeyPem, publicKey, createdAt)
                   VALUES ('la chiave', 'ed25519', 'PEM', 'ssh-ed25519 AAAA', 1700000000)"""
            )
        }

        // Runs every migration the app ships and then holds the result against the schema the
        // entities describe. Anything the migration forgot fails here.
        val migrated = helper.runMigrationsAndValidate(NAME, 13, true, *AppDatabase.MIGRATIONS)

        migrated.query("SELECT label, hostname, port, position, connectCount FROM hosts").use {
            assertTrue("the host did not survive the upgrade", it.moveToFirst())
            assertEquals("fatberry", it.getString(0))
            assertEquals("10.0.0.9", it.getString(1))
            assertEquals(2222, it.getInt(2))
            // The two columns 12→13 adds, with the meaning the code counts on: no manual
            // position yet, and a host nobody has connected to.
            assertTrue("position should start unset", it.isNull(3))
            assertEquals(0, it.getInt(4))
            assertEquals("one host in, one host out", 1, it.count)
        }
        migrated.query("SELECT name, color, position FROM host_groups").use {
            assertTrue(it.moveToFirst())
            assertEquals("casa", it.getString(0))
            assertEquals(42, it.getInt(1))
            assertTrue("a group's position should start unset too", it.isNull(2))
        }
        migrated.query("SELECT label, publicKey FROM ssh_keys").use {
            assertTrue("the key did not survive the upgrade", it.moveToFirst())
            assertEquals("la chiave", it.getString(0))
            assertEquals("ssh-ed25519 AAAA", it.getString(1))
        }
    }

    private companion object {
        const val NAME = "migration-test.db"
    }
}
