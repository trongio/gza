package ge.hackerman.gza.core.data.di

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import ge.hackerman.gza.core.data.coroutines.IoDispatcher
import ge.hackerman.gza.core.data.database.GzaDatabase
import ge.hackerman.gza.core.data.database.dao.RouteDao
import ge.hackerman.gza.core.data.database.dao.RouteDataDao
import ge.hackerman.gza.core.data.database.dao.ScheduleDao
import ge.hackerman.gza.core.data.database.dao.StopDao
import ge.hackerman.gza.core.data.database.dao.StopRoutesDao
import ge.hackerman.gza.core.data.database.dao.SyncStateDao
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher

@Module
@InstallIn(SingletonComponent::class)
internal object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        @IoDispatcher ioDispatcher: CoroutineDispatcher
    ): GzaDatabase = Room.databaseBuilder<GzaDatabase>(context, GzaDatabase.NAME)
        // Framework SQLite: no native library to ship. The bundled driver is only needed
        // for FTS5 (maybe T08); switching is this one line.
        .setDriver(AndroidSQLiteDriver())
        .setQueryCoroutineContext(ioDispatcher)
        // Everything in this database is re-fetchable from the gateway; user data lives in
        // DataStore. Remove this once Room holds anything that is not (observations, T19+).
        .fallbackToDestructiveMigration(dropAllTables = true)
        .build()

    @Provides
    fun provideStopDao(database: GzaDatabase): StopDao = database.stopDao()

    @Provides
    fun provideRouteDao(database: GzaDatabase): RouteDao = database.routeDao()

    @Provides
    fun provideStopRoutesDao(database: GzaDatabase): StopRoutesDao = database.stopRoutesDao()

    @Provides
    fun provideRouteDataDao(database: GzaDatabase): RouteDataDao = database.routeDataDao()

    @Provides
    fun provideScheduleDao(database: GzaDatabase): ScheduleDao = database.scheduleDao()

    @Provides
    fun provideSyncStateDao(database: GzaDatabase): SyncStateDao = database.syncStateDao()
}
