package com.sshborg.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [HostEntity::class, SshKeyEntity::class, GroupEntity::class],
    version = 15,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun hostDao(): HostDao
    abstract fun sshKeyDao(): SshKeyDao
    abstract fun groupDao(): GroupDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        /**
         * Every migration, in order, as the builder gets them — and as the migration test runs
         * them. Kept in one place so the two cannot drift apart: a migration added to the
         * builder alone would ship untested, which is the mistake this list exists to prevent.
         */
        internal val MIGRATIONS: Array<Migration> get() = arrayOf(
            MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6,
            MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11,
            MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15,
        )

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE hosts ADD COLUMN jumpHosts TEXT")
                db.execSQL("ALTER TABLE hosts ADD COLUMN jumpHostKeys TEXT")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE ssh_keys ADD COLUMN encryptedBlob TEXT")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE hosts ADD COLUMN password TEXT")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE hosts ADD COLUMN encryptedPassword TEXT")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE hosts ADD COLUMN portForwardings TEXT")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE hosts ADD COLUMN jumpMode TEXT NOT NULL DEFAULT 'simple'")
                db.execSQL("ALTER TABLE hosts ADD COLUMN jumpHostIdList TEXT")
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE hosts ADD COLUMN sftpStartMode TEXT NOT NULL DEFAULT 'last'")
                db.execSQL("ALTER TABLE hosts ADD COLUMN sftpStartDir TEXT")
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE hosts ADD COLUMN allowLegacyCiphers INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `host_groups` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`color` INTEGER NOT NULL, " +
                        "`collapsed` INTEGER NOT NULL DEFAULT 0)"
                )
                db.execSQL("ALTER TABLE hosts ADD COLUMN groupId INTEGER")
            }
        }

        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE hosts ADD COLUMN color INTEGER")
            }
        }

        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE hosts ADD COLUMN sftpShowHidden INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Nullable on purpose: "no manual position yet". Seeding them lazily when the
                // manual order is first used keeps every insert path free of ordering logic.
                db.execSQL("ALTER TABLE hosts ADD COLUMN position INTEGER")
                db.execSQL("ALTER TABLE hosts ADD COLUMN connectCount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE host_groups ADD COLUMN position INTEGER")
            }
        }

        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // The per-host mosh switch. Off for everybody who already has a database, which
                // is also its default for a new host: it is experimental and it needs something
                // installed on the server.
                //
                // Version 14 was briefly used on this branch's history for two `ssh_keys` columns
                // that were then taken out again (see MigrationTest). No release ever carried
                // that schema, so no user database is at 14; a development one still holding it
                // has to be wiped, which is what happened at the time.
                db.execSQL("ALTER TABLE hosts ADD COLUMN mosh INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // The three things about a mosh session that cannot be decided for somebody else:
                // which address family it is born on, which ports their firewall will carry, and
                // where their server keeps the program. Defaults that mean "as before".
                db.execSQL("ALTER TABLE hosts ADD COLUMN moshFamily TEXT NOT NULL DEFAULT 'auto'")
                db.execSQL("ALTER TABLE hosts ADD COLUMN moshPorts TEXT")
                db.execSQL("ALTER TABLE hosts ADD COLUMN moshCommand TEXT")
            }
        }

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "sshborg.db",
                ).addMigrations(*MIGRATIONS).build().also { instance = it }
            }
    }
}
