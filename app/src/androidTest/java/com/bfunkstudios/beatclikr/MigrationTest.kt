package com.bfunkstudios.beatclikr

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bfunkstudios.beatclikr.data.db.BeatClikrDatabase
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        BeatClikrDatabase::class.java
    )

    @Test
    fun migrate1to2() {
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL("INSERT INTO songs VALUES ('id1', 'Title', 'Artist', 120.0, 4, 'STRAIGHT', NULL)")
            close()
        }
        helper.runMigrationsAndValidate(TEST_DB, 2, true, BeatClikrDatabase.MIGRATION_1_2)
    }

    @Test
    fun migrate2to3() {
        helper.createDatabase(TEST_DB, 2).apply { close() }
        helper.runMigrationsAndValidate(TEST_DB, 3, true, BeatClikrDatabase.MIGRATION_2_3)
    }

    @Test
    fun migrate3to4() {
        helper.createDatabase(TEST_DB, 3).apply { close() }
        helper.runMigrationsAndValidate(TEST_DB, 4, true, BeatClikrDatabase.MIGRATION_3_4)
    }

    @Test
    fun migrateAll() {
        helper.createDatabase(TEST_DB, 1).apply { close() }
        helper.runMigrationsAndValidate(
            TEST_DB, 4, true,
            BeatClikrDatabase.MIGRATION_1_2,
            BeatClikrDatabase.MIGRATION_2_3,
            BeatClikrDatabase.MIGRATION_3_4
        )
    }

    companion object {
        private const val TEST_DB = "migration-test"
    }
}
