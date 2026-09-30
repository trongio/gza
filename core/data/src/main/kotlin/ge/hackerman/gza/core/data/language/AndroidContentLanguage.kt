package ge.hackerman.gza.core.data.language

import android.app.LocaleManager
import android.content.ComponentCallbacks
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import ge.hackerman.gza.core.model.Language
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Follows the per-app language (Android 13+) or the system one, and its changes. */
@Singleton
internal class AndroidContentLanguage @Inject constructor(@ApplicationContext private val context: Context) :
    ContentLanguage {
    private val state = MutableStateFlow(read())

    override val language: StateFlow<Language> = state.asStateFlow()

    init {
        context.registerComponentCallbacks(
            object : ComponentCallbacks {
                override fun onConfigurationChanged(newConfig: Configuration) = refresh()

                @Deprecated("Required by the interface; nothing to free here.")
                override fun onLowMemory() = Unit
            }
        )
    }

    override fun refresh() {
        state.value = read()
    }

    private fun read(): Language {
        val appLocale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java)?.applicationLocales?.takeIf { !it.isEmpty }?.get(0)
        } else {
            null
        }
        val locale: Locale = appLocale ?: context.resources.configuration.locales[0]
        return ContentLanguage.languageFor(locale)
    }
}
