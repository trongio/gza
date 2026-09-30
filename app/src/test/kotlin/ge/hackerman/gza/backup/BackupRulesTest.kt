package ge.hackerman.gza.backup

import android.content.Context
import android.content.res.XmlResourceParser
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ge.hackerman.gza.R
import ge.hackerman.gza.core.data.datastore.DataStoreFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.xmlpull.v1.XmlPullParser

/** Backs up the user's preferences and nothing else: never the cache or the gateway key. */
@RunWith(AndroidJUnit4::class)
class BackupRulesTest {
    private class Rule(val section: String, val tag: String, val attributes: Map<String, String>)

    private val userPrefsPath = "datastore/" + DataStoreFiles.USER_PREFERENCES

    private fun rules(resId: Int): Pair<Map<String, Map<String, String>>, List<Rule>> {
        val parser: XmlResourceParser = ApplicationProvider.getApplicationContext<Context>().resources.getXml(resId)
        val sections = mutableMapOf<String, Map<String, String>>()
        val rules = mutableListOf<Rule>()
        var section = ""
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            val attributes = (0 until parser.attributeCount).associate {
                parser.getAttributeName(it) to
                    parser.getAttributeValue(it)
            }
            when (parser.depth) {
                1 -> section = parser.name

                2 -> if (parser.name == "include" || parser.name == "exclude") {
                    rules += Rule(section, parser.name, attributes)
                } else {
                    section = parser.name
                    sections[section] = attributes
                }

                else -> rules += Rule(section, parser.name, attributes)
            }
        }
        return sections to rules
    }

    private fun assertOnlyUserPreferences(rules: List<Rule>, section: String) {
        val inSection = rules.filter { it.section == section }
        assertEquals("$section has exactly one rule", 1, inSection.size)
        val rule = inSection.single()
        assertEquals("include", rule.tag)
        assertEquals("file", rule.attributes["domain"])
        assertEquals(userPrefsPath, rule.attributes["path"])
    }

    private fun assertNothingSensitive(rules: List<Rule>) {
        rules.forEach { rule ->
            assertFalse(DataStoreFiles.TTC_CONFIG in rule.attributes["path"].orEmpty())
            assertFalse(rule.attributes["domain"] == "database")
        }
    }

    @Test
    fun `android 12 and higher back up and transfer only the preferences file`() {
        val (sections, rules) = rules(R.xml.data_extraction_rules)
        assertEquals(setOf("cloud-backup", "device-transfer"), sections.keys)
        assertOnlyUserPreferences(rules, "cloud-backup")
        assertOnlyUserPreferences(rules, "device-transfer")
        assertEquals("true", sections.getValue("cloud-backup")["disableIfNoEncryptionCapabilities"])
        assertNothingSensitive(rules)
    }

    @Test
    fun `android 11 and lower back up only the preferences file, encrypted`() {
        val (_, rules) = rules(R.xml.backup_rules)
        assertOnlyUserPreferences(rules, "full-backup-content")
        assertEquals("clientSideEncryption", rules.single().attributes["requireFlags"])
        assertNothingSensitive(rules)
    }
}
