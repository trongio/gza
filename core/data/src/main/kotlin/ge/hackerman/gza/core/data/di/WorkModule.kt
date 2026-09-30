package ge.hackerman.gza.core.data.di

import android.content.Context
import androidx.work.WorkManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal object WorkModule {
    // WorkManager keeps one instance per process; getInstance is cheap. Initialized on demand
    // with the app's Hilt worker factory (GzaApplication is its Configuration.Provider).
    @Provides
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager = WorkManager.getInstance(context)
}
