package ge.hackerman.gza.core.data.datastore

import android.content.Context
import androidx.datastore.dataStoreFile
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ge.hackerman.gza.core.data.database.GzaDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The app's backup rules include `file` domain path `datastore/user_prefs.json` and nothing
 * else. This checks the files really live where those rules assume: the preferences at exactly
 * that path, the gateway config beside it (so not included), and the database outside the
 * `file` domain altogether.
 */
@RunWith(AndroidJUnit4::class)
class BackupPathsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun pathInFilesDomain(name: String): String =
        context.dataStoreFile(name).relativeTo(context.filesDir).invariantSeparatorsPath

    @Test
    fun `the preferences file is at the path the backup rules include`() {
        assertEquals("datastore/user_prefs.json", pathInFilesDomain(DataStoreFiles.USER_PREFERENCES))
    }

    @Test
    fun `the gateway config is a different file, so the include never covers it`() {
        val config = pathInFilesDomain(DataStoreFiles.TTC_CONFIG)
        assertEquals("datastore/ttc_config.json", config)
        assertFalse(config == pathInFilesDomain(DataStoreFiles.USER_PREFERENCES))
    }

    @Test
    fun `the database lives outside the files domain`() {
        val db = context.getDatabasePath(GzaDatabase.NAME)
        assertEquals("gza.db", db.name)
        assertFalse(db.canonicalPath.startsWith(context.filesDir.canonicalPath + "/"))
    }
}
