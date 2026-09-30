package ge.hackerman.gza.backup

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ge.hackerman.gza.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.xmlpull.v1.XmlPullParser

/** The manifest turns backup on, so the rule files are what limits it, and they only include. */
@RunWith(AndroidJUnit4::class)
class BackupManifestTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `backup is allowed, so the rules decide what goes`() {
        assertTrue(context.applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP != 0)
    }

    // An exclude with no include would back up everything else, including the key.
    @Test
    fun `no rule file relies on an exclude`() {
        listOf(R.xml.data_extraction_rules, R.xml.backup_rules).forEach { res ->
            val parser = context.resources.getXml(res)
            val tags = mutableListOf<String>()
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG) tags += parser.name
            }
            assertEquals(emptyList<String>(), tags.filter { it == "exclude" })
            assertTrue(tags.contains("include"))
        }
    }
}
