package ge.hackerman.gza

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class GzaApplication :
    Application(),
    Configuration.Provider {
    // WorkManager is initialized on demand (its startup initializer is removed in the
    // manifest), so it builds the data layer's @HiltWorker classes with this factory.
    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()
}
