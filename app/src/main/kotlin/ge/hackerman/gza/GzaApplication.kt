package ge.hackerman.gza

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import ge.hackerman.gza.core.data.sync.AppOpenSync
import javax.inject.Inject

@HiltAndroidApp
class GzaApplication :
    Application(),
    Configuration.Provider {
    // WorkManager is initialized on demand (its startup initializer is removed in the
    // manifest), so it builds the data layer's @HiltWorker classes with this factory.
    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var appOpenSync: AppOpenSync

    override fun onCreate() {
        super.onCreate()
        // Every time the app comes to the foreground: catalog schedule, stale routes, language.
        ProcessLifecycleOwner.get().lifecycle.addObserver(appOpenSync)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()
}
