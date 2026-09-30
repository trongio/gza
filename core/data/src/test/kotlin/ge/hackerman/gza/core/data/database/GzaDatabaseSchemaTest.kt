package ge.hackerman.gza.core.data.database

import android.content.Context
import androidx.room3.testing.MigrationTestHelper
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ge.hackerman.gza.core.data.testing.TestDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The migration harness, proven on version 1 before any migration exists: a database created
 * from the exported `schemas/.../1.json` must open with the current entities. The next version
 * adds its `Migration` and a `runMigrationsAndValidate(2, ...)` case here.
 */
@RunWith(AndroidJUnit4::class)
class GzaDatabaseSchemaTest {
    private val file = ApplicationProvider.getApplicationContext<Context>().getDatabasePath("schema-test.db")

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        file,
        AndroidSQLiteDriver(),
        GzaDatabase::class
    )

    @Test
    fun `version 1 from the exported schema validates and opens with the current entities`() = runBlocking {
        helper.createDatabase(1).use { connection ->
            connection.execSQL(
                "INSERT INTO stops (id, code, name_en, name_ka, lat, lon, kind) " +
                    "VALUES ('1:970', '970', 'Ana', 'ანა', 41.72, 44.70, 'BUS')"
            )
        }
        helper.runMigrationsAndValidate(1, emptyList()).close()

        val db = TestDatabase.onFile(file)
        try {
            val stops = db.stopDao().observeAll().first()
            assertEquals(listOf("ანა"), stops.map { it.nameKa })
        } finally {
            db.close()
        }
    }
}
