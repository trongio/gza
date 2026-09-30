package ge.hackerman.gza.core.data.language

import ge.hackerman.gza.core.model.Language
import java.util.Locale
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

class ContentLanguageTest {
    @Test
    fun `georgian locales are georgian, everything else english`() {
        assertEquals(Language.KA, ContentLanguage.languageFor(Locale.forLanguageTag("ka")))
        assertEquals(Language.KA, ContentLanguage.languageFor(Locale.forLanguageTag("ka-GE")))
        assertEquals(Language.EN, ContentLanguage.languageFor(Locale.forLanguageTag("en")))
        assertEquals(Language.EN, ContentLanguage.languageFor(Locale.forLanguageTag("en-GB")))
        // No Russian names are synced; the app has no Russian strings.
        assertEquals(Language.EN, ContentLanguage.languageFor(Locale.forLanguageTag("ru")))
        assertEquals(Language.EN, ContentLanguage.languageFor(Locale.forLanguageTag("de")))
        assertEquals(Language.EN, ContentLanguage.languageFor(Locale.ROOT))
    }
}
