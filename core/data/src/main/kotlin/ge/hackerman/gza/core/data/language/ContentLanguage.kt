package ge.hackerman.gza.core.data.language

import ge.hackerman.gza.core.model.Language
import java.util.Locale
import kotlinx.coroutines.flow.StateFlow

/**
 * The language names are shown in. A flow, not a value read once: view models outlive a
 * language change, and cached names switch without a new sync.
 */
interface ContentLanguage {
    val language: StateFlow<Language>

    /** Re-reads the app's locale; cheap, call it whenever the app comes to the foreground. */
    fun refresh()

    companion object {
        /** The app ships English and Georgian only, so anything that is not Georgian is English. */
        fun languageFor(locale: Locale): Language = if (locale.language ==
            Language.KA.code
        ) {
            Language.KA
        } else {
            Language.EN
        }
    }
}
